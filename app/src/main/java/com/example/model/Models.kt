package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Information extracted from the chosen APK or APKS.
 */
data class ApkFileInfo(
    val fileName: String = "",
    val filePath: String = "",
    val appName: String = "-",
    val packageName: String = "-",
    val versionName: String = "-",
    val versionCode: Long = 0L,
    val fileSizeFormatted: String = "-",
    val fileSizeBytes: Long = 0L,
    val fileType: String = "-", // "APK" or "APKS"
    val dexCount: Int = 0,
    val status: String = "جاهز للفحص",
    val isApks: Boolean = false
)

/**
 * Room Entity for user-managed keywords.
 * Starts completely EMPTY on first install.
 */
@Entity(tableName = "keywords")
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val word: String,
    val normalizedWord: String,
    val isEnabled: Boolean = true,
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis(),
    val isExceptional: Boolean = false
)

/**
 * Keyword match search modes supported by the analyzer.
 */
enum class KeywordSearchMode(val label: String) {
    NORMALIZED_IDENTIFIER("Normalized Identifier (حذف المعرفات والشرطات)"),
    EXACT_IDENTIFIER("Exact Identifier (تطابق تام للمعرف)"),
    CASE_INSENSITIVE("Case-Insensitive (غير حساس للأحرف)"),
    CONTAINS("Contains (يحتوي على الكلمة)"),
    REGEX("Regular Expression (تعبير نمطي)")
}

/**
 * Confidence Level ratings.
 */
enum class ConfidenceLevel(val labelAr: String, val labelEn: String, val minScore: Int, val maxScore: Int) {
    VERY_HIGH("مرتفع جداً", "VERY HIGH", 90, 100),
    HIGH("مرتفع", "HIGH", 75, 89),
    MEDIUM("متوسط", "MEDIUM", 50, 74),
    LOW("منخفض", "LOW", 25, 49),
    VERY_LOW("منخفض جداً", "VERY LOW", 0, 24)
}

/**
 * Code origin classification.
 */
enum class CodeClassification(val label: String) {
    APP_CODE("كود التطبيق (App Code)"),
    THIRD_PARTY("مكتبة خارجية (Third-Party)"),
    FRAMEWORK("إطار النظام (Framework)"),
    UNKNOWN("كود محلي (Internal/App)")
}

/**
 * Reason detailing score calculations.
 */
data class ScoringReason(
    val description: String,
    val scoreDelta: Int,
    val isPositive: Boolean = scoreDelta >= 0
)

/**
 * Intermediate candidate discovered in Stage 1.
 */
data class CandidateMethod(
    val dexName: String,
    val classIdx: Int,
    val className: String,
    val methodIdx: Int,
    val methodName: String,
    val signature: String,
    val returnType: String,
    val accessFlags: Int,
    val codeOffset: Long,
    val primaryKeyword: String = "",
    val matchedKeywords: Set<String>,
    val matchOrigin: String // e.g. "Method Name", "Class Name", "Bytecode String"
)

/**
 * Final analyzed result.
 */
data class AnalysisResult(
    val id: Long = 0L,
    val dexName: String,
    val className: String,
    val methodName: String,
    val signature: String,
    val returnType: String,
    val accessFlags: Int,
    val score: Int, // 0 - 100
    val confidenceLevel: ConfidenceLevel,
    val classification: CodeClassification,
    val matchedKeyword: String, // Original keyword that triggered discovery
    val normalizedKeyword: String = "",
    val matchedKeywords: List<String> = emptyList(),
    val reasons: List<ScoringReason> = emptyList(),
    val smaliSnippet: String = "",
    val isConstructor: Boolean = false,
    val isStaticInitializer: Boolean = false,
    val isThirdParty: Boolean = false,
    val branchCount: Int = 0,
    val returnBoolean: Boolean = false,
    val callsEntitlement: Boolean = false,
    val accessesBilling: Boolean = false,
    val hasExceptionalMatch: Boolean = false,
    val exceptionalKeywordsMatched: List<String> = emptyList()
) {
    val packageName: String
        get() {
            val clean = className.removePrefix("L").removeSuffix(";").replace('/', '.')
            val lastDot = clean.lastIndexOf('.')
            return if (lastDot > 0) clean.substring(0, lastDot) else clean
        }

    val simpleClassName: String
        get() {
            val clean = className.removePrefix("L").removeSuffix(";").replace('/', '.')
            val lastDot = clean.lastIndexOf('.')
            return if (lastDot > 0) clean.substring(lastDot + 1) else clean
        }

    val uniqueKey: String
        get() = "$dexName#$className#$methodName#$signature"
}

/**
 * Scan Progress stages.
 */
enum class ScanStage(val titleAr: String) {
    IDLE("خامل"),
    PREPARING_APK("المرحلة 1: تجهيز الملف واستخراج DEX"),
    INDEXING_DEX("المرحلة 2: فهرسة الرموز والنصوص في DEX"),
    FINDING_CANDIDATES("المرحلة 3: البحث السريع عن المرشحين"),
    ANALYZING_CANDIDATES("المرحلة 4: تحليل المرشحين"),
    RANKING_RESULTS("المرحلة 5: ترتيب وتصفية النتائج"),
    COMPLETED("اكتمل الفحص"),
    CANCELLED("تم إيقاف الفحص"),
    ERROR("حدث خطأ")
}

/**
 * Live Progress metrics.
 */
data class ScanProgress(
    val stage: ScanStage = ScanStage.IDLE,
    val stageSubtitle: String = "",
    val percent: Float = 0f,
    val elapsedTimeSeconds: Long = 0L,
    val totalDex: Int = 0,
    val currentDex: Int = 0,
    val candidatesCount: Int = 0,
    val analyzedCount: Int = 0,
    val resultsCount: Int = 0,
    val isStep1Done: Boolean = false,
    val isStep2Done: Boolean = false,
    val isStep3Done: Boolean = false,
    val isStep4Done: Boolean = false,
    val isStep5Done: Boolean = false,
    val isStep6Done: Boolean = false,
    val isRunning: Boolean = false,
    val errorMessage: String? = null
)

/**
 * User Settings for the scan engine.
 */
enum class ScanMode(val label: String) {
    FAST("سريع (FAST)"),
    BALANCED("متوازن (BALANCED)"),
    DEEP("عميق (DEEP)")
}

data class ScanSettings(
    val scanMode: ScanMode = ScanMode.BALANCED,
    val candidateBudget: Int = 500,
    val searchMode: KeywordSearchMode = KeywordSearchMode.NORMALIZED_IDENTIFIER,
    val hideThirdParty: Boolean = false,
    val hideConstructors: Boolean = true,
    val hideClinit: Boolean = true,
    val minConfidence: ConfidenceLevel = ConfidenceLevel.HIGH,
    val streamResultsDuringScan: Boolean = true,
    val developerDebugMode: Boolean = false
)

/**
 * Performance & Debug diagnostics report.
 */
data class DebugPerformanceReport(
    val totalTimeMs: Long = 0L,
    val dexProcessingTimeMs: Long = 0L,
    val candidateDiscoveryTimeMs: Long = 0L,
    val analysisTimeMs: Long = 0L,
    val rankingTimeMs: Long = 0L,
    val dexCount: Int = 0,
    val keywordHits: Int = 0,
    val uniqueCandidates: Int = 0,
    val deeplyAnalyzed: Int = 0,
    val veryHighCount: Int = 0,
    val highCount: Int = 0,
    val mediumCount: Int = 0,
    val lowCount: Int = 0,
    val veryLowCount: Int = 0,
    val thirdPartyFilteredCount: Int = 0,
    val approximateMemoryMb: Long = 0L
)
