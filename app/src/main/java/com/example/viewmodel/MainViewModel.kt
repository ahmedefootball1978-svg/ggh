package com.example.viewmodel

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.AppDatabase
import com.example.database.KeywordImportStats
import com.example.database.KeywordRepository
import com.example.engine.analysis.AnalyzerEngine
import com.example.engine.apk.ApkExtractor
import com.example.engine.export.ExportHelper
import com.example.model.AnalysisResult
import com.example.model.ApkFileInfo
import com.example.model.CodeClassification
import com.example.model.ConfidenceLevel
import com.example.model.DebugPerformanceReport
import com.example.model.KeywordEntity
import com.example.model.ScanProgress
import com.example.model.ScanSettings
import com.example.model.ScanStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ResultFilters(
    val allowedConfidences: Set<ConfidenceLevel> = ConfidenceLevel.entries.toSet(),
    val allowedClassifications: Set<CodeClassification> = setOf(
        CodeClassification.APP_CODE,
        CodeClassification.THIRD_PARTY,
        CodeClassification.FRAMEWORK,
        CodeClassification.UNKNOWN
    ),
    val selectedDex: String = "الكل",
    val searchQuery: String = "",
    val onlyExceptional: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    val keywordRepository = KeywordRepository(db.keywordDao())
    val apkExtractor = ApkExtractor(application)
    private val analyzerEngine = AnalyzerEngine(apkExtractor)

    // Keywords State
    val allKeywords: StateFlow<List<KeywordEntity>> = keywordRepository.allKeywords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalKeywordsCount: StateFlow<Int> = keywordRepository.totalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val enabledKeywordsCount: StateFlow<Int> = keywordRepository.enabledCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val exceptionalKeywordsCount: StateFlow<Int> = keywordRepository.exceptionalCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Selected APK file
    private val _selectedApkInfo = MutableStateFlow<ApkFileInfo?>(null)
    val selectedApkInfo: StateFlow<ApkFileInfo?> = _selectedApkInfo.asStateFlow()

    private var currentLocalApkFile: File? = null

    // Scan Progress
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    // Results & Debug
    private val _rawResults = MutableStateFlow<List<AnalysisResult>>(emptyList())
    val rawResults: StateFlow<List<AnalysisResult>> = _rawResults.asStateFlow()

    private val _performanceReport = MutableStateFlow<DebugPerformanceReport?>(null)
    val performanceReport: StateFlow<DebugPerformanceReport?> = _performanceReport.asStateFlow()

    // Settings
    private val _scanSettings = MutableStateFlow(ScanSettings())
    val scanSettings: StateFlow<ScanSettings> = _scanSettings.asStateFlow()

    // Filters
    private val _filters = MutableStateFlow(ResultFilters())
    val filters: StateFlow<ResultFilters> = _filters.asStateFlow()

    // Filtered Results - strictly sorted by score percentage descending, exceptional first
    val filteredResults: StateFlow<List<AnalysisResult>> = combine(_rawResults, _filters) { results, filter ->
        results.filter { item ->
            val confOk = filter.allowedConfidences.contains(item.confidenceLevel)
            val classOk = filter.allowedClassifications.contains(item.classification)
            val dexOk = filter.selectedDex == "الكل" || item.dexName == filter.selectedDex
            val expOk = !filter.onlyExceptional || item.hasExceptionalMatch
            val query = filter.searchQuery.trim().lowercase()
            val queryOk = query.isEmpty() ||
                    item.className.lowercase().contains(query) ||
                    item.methodName.lowercase().contains(query) ||
                    item.matchedKeyword.lowercase().contains(query) ||
                    item.matchedKeywords.any { it.lowercase().contains(query) }

            confOk && classOk && dexOk && expOk && queryOk
        }.sortedWith(
            compareByDescending<AnalysisResult> { it.hasExceptionalMatch }
                .thenByDescending { it.score }
                .thenByDescending { (if (it.returnBoolean) 2 else 0) + it.branchCount + (if (it.callsEntitlement) 1 else 0) }
                .thenByDescending { it.matchedKeywords.size }
                .thenByDescending { it.classification == CodeClassification.APP_CODE }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Currently viewed result in detail sheet
    private val _selectedResult = MutableStateFlow<AnalysisResult?>(null)
    val selectedResult: StateFlow<AnalysisResult?> = _selectedResult.asStateFlow()

    private var scanJob: Job? = null
    private var timerJob: Job? = null

    // UI Toast / Snack message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        viewModelScope.launch {
            keywordRepository.ensureDefaultKeywords()
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    /**
     * Handles APK / APKS file selection from Uri.
     * Clears old state and displays metadata immediately without starting deep scan.
     */
    fun onFileSelected(uri: Uri, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Clear any previous results and states completely
                stopScanInternal()
                _rawResults.value = emptyList()
                _performanceReport.value = null
                _scanProgress.value = ScanProgress()

                currentLocalApkFile?.delete()
                val tempFile = apkExtractor.copyUriToTemp(uri, displayName)
                currentLocalApkFile = tempFile

                val info = apkExtractor.inspectFile(tempFile)
                _selectedApkInfo.value = info
                _userMessage.value = "تم تجهيز: ${info.appName}"
            } catch (e: Exception) {
                _userMessage.value = "تعذر قراءة ملف APK: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Starts the 2-Stage Analysis.
     */
    fun startScan() {
        val file = currentLocalApkFile
        if (file == null || !file.exists()) {
            _userMessage.value = "يرجى اختيار ملف APK أو APKS أولاً"
            return
        }

        viewModelScope.launch {
            val enabledKws = keywordRepository.getEnabledKeywords()
            if (enabledKws.isEmpty()) {
                _userMessage.value = "قاعدة الكلمات المفتاحية فارغة! يرجى إضافة كلمات مفتاحية أولاً"
                return@launch
            }

            // Reset previous results
            _rawResults.value = emptyList()
            _performanceReport.value = null
            stopScanInternal()

            // Initialize progress state
            _scanProgress.value = ScanProgress(
                stage = ScanStage.PREPARING_APK,
                stageSubtitle = "جاري التحضير واستخراج ملفات DEX...",
                percent = 0.05f,
                isRunning = true
            )

            // Start dedicated Timer Coroutine with monotonic clock (every 200ms)
            val startTimeRealtime = SystemClock.elapsedRealtime()
            timerJob = viewModelScope.launch(Dispatchers.Default) {
                while (isActive) {
                    val elapsedMs = SystemClock.elapsedRealtime() - startTimeRealtime
                    val elapsedSec = elapsedMs / 1000
                    _scanProgress.update { current ->
                        if (current.isRunning) {
                            current.copy(elapsedTimeSeconds = elapsedSec)
                        } else {
                            current
                        }
                    }
                    delay(200)
                }
            }

            // Launch scan in background
            scanJob = launch(Dispatchers.IO) {
                try {
                    val (finalResults, report) = analyzerEngine.analyze(
                        apkFile = file,
                        keywords = enabledKws,
                        settings = _scanSettings.value,
                        coroutineScope = this,
                        onProgress = { progress ->
                            _scanProgress.update { current ->
                                progress.copy(elapsedTimeSeconds = current.elapsedTimeSeconds)
                            }
                        },
                        onResultDiscovered = { singleResult ->
                            _rawResults.update { current ->
                                current + singleResult
                            }
                        }
                    )

                    val finalElapsedMs = SystemClock.elapsedRealtime() - startTimeRealtime
                    val finalElapsedSec = finalElapsedMs / 1000
                    stopTimer()
                    _scanProgress.update { current ->
                        current.copy(
                            stage = ScanStage.COMPLETED,
                            stageSubtitle = "اكتمل الفحص بنجاح (${finalResults.size} نتيجة)",
                            percent = 1.0f,
                            isRunning = false,
                            elapsedTimeSeconds = finalElapsedSec,
                            isStep1Done = true,
                            isStep2Done = true,
                            isStep3Done = true,
                            isStep4Done = true,
                            isStep5Done = true,
                            isStep6Done = true
                        )
                    }
                    _rawResults.value = finalResults
                    _performanceReport.value = report
                    _userMessage.value = "اكتمل الفحص: تم العثور على ${finalResults.size} نتيجة في $finalElapsedSec ثانية"

                } catch (_: CancellationException) {
                    val cancelElapsedSec = (SystemClock.elapsedRealtime() - startTimeRealtime) / 1000
                    stopTimer()
                    _scanProgress.update { current ->
                        current.copy(
                            stage = ScanStage.CANCELLED,
                            stageSubtitle = "تم إيقاف الفحص من قبل المستخدم",
                            isRunning = false,
                            elapsedTimeSeconds = cancelElapsedSec
                        )
                    }
                } catch (e: Exception) {
                    val errorElapsedSec = (SystemClock.elapsedRealtime() - startTimeRealtime) / 1000
                    stopTimer()
                    _scanProgress.update { current ->
                        current.copy(
                            stage = ScanStage.ERROR,
                            stageSubtitle = "فشل الفحص: ${e.localizedMessage ?: "خطأ غير متوقع"}",
                            isRunning = false,
                            errorMessage = e.localizedMessage,
                            elapsedTimeSeconds = errorElapsedSec
                        )
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun stopScanInternal() {
        stopTimer()
        scanJob?.cancel()
        scanJob = null
    }

    fun stopScan() {
        stopScanInternal()
        _scanProgress.update { current ->
            current.copy(
                stage = ScanStage.CANCELLED,
                stageSubtitle = "تم إيقاف الفحص من قبل المستخدم",
                isRunning = false
            )
        }
        _userMessage.value = "تم إيقاف الفحص"
    }

    fun resetAll() {
        stopScanInternal()
        _selectedApkInfo.value = null
        currentLocalApkFile?.delete()
        currentLocalApkFile = null
        _rawResults.value = emptyList()
        _performanceReport.value = null
        _scanProgress.value = ScanProgress()
        _userMessage.value = "تمت إعادة ضبط البيانات"
    }

    // Keyword operations
    fun addKeyword(word: String, isExceptional: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (word.isNotBlank()) {
                keywordRepository.insertKeyword(word.trim(), isExceptional = isExceptional)
            }
        }
    }

    fun importKeywordsFromText(text: String, onComplete: (KeywordImportStats) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val stats = keywordRepository.importFromText(text)
            withContext(Dispatchers.Main) {
                onComplete(stats)
            }
        }
    }

    fun toggleKeyword(id: Long, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.toggleKeyword(id, isEnabled)
        }
    }

    fun toggleKeywordExceptional(id: Long, isExceptional: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.toggleKeywordExceptional(id, isExceptional)
        }
    }

    fun setKeywordsExceptional(ids: List<Long>, isExceptional: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.setKeywordsExceptional(ids, isExceptional)
        }
    }

    fun toggleAllKeywords(isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.toggleAllKeywords(isEnabled)
        }
    }

    fun deleteKeyword(keyword: KeywordEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.deleteKeyword(keyword)
        }
    }

    fun deleteKeywords(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.deleteKeywordsByIds(ids)
        }
    }

    fun deleteAllKeywords() {
        viewModelScope.launch(Dispatchers.IO) {
            keywordRepository.deleteAllKeywords()
        }
    }

    suspend fun exportKeywords(): String = withContext(Dispatchers.IO) {
        keywordRepository.exportToText()
    }

    // Filter controls
    fun updateFilters(newFilters: ResultFilters) {
        _filters.value = newFilters
    }

    fun resetFilters() {
        _filters.value = ResultFilters()
    }

    fun selectResult(result: AnalysisResult?) {
        _selectedResult.value = result
    }

    fun updateSettings(settings: ScanSettings) {
        _scanSettings.value = settings
    }

    // Export reports
    fun exportCurrentResults(format: String): String {
        val info = _selectedApkInfo.value ?: ApkFileInfo(appName = "Unknown")
        val list = _rawResults.value
        return when (format.uppercase()) {
            "JSON" -> ExportHelper.exportToJson(info, list)
            "CSV" -> ExportHelper.exportToCsv(info, list)
            else -> ExportHelper.exportToTxt(info, list)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScanInternal()
        currentLocalApkFile?.delete()
    }
}
