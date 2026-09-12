package com.example.engine.analysis

import com.example.engine.apk.ApkExtractor
import com.example.engine.dex.DexDisassembler
import com.example.engine.dex.DexParser
import com.example.engine.keyword.KeywordMatcher
import com.example.model.AnalysisResult
import com.example.model.CandidateMethod
import com.example.model.ConfidenceLevel
import com.example.model.DebugPerformanceReport
import com.example.model.KeywordEntity
import com.example.model.ScanProgress
import com.example.model.ScanSettings
import com.example.model.ScanStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

class AnalyzerEngine(
    private val apkExtractor: ApkExtractor
) {

    /**
     * Executes the 2-Stage Fast Discovery and Targeted Analysis Pipeline.
     */
    suspend fun analyze(
        apkFile: File,
        keywords: List<KeywordEntity>,
        settings: ScanSettings,
        coroutineScope: CoroutineScope,
        onProgress: suspend (ScanProgress) -> Unit,
        onResultDiscovered: suspend (AnalysisResult) -> Unit
    ): Pair<List<AnalysisResult>, DebugPerformanceReport> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var tempDir: File? = null
        val parsers = mutableListOf<DexParser>()
        val resultsMap = LinkedHashMap<String, AnalysisResult>()

        var dexProcessingTime = 0L
        var candidateDiscoveryTime = 0L
        var analysisTime = 0L
        var rankingTime = 0L
        var totalKeywordHits = 0
        var uniqueCandidatesCount = 0
        var deeplyAnalyzedCount = 0
        var thirdPartyFilteredCount = 0

        var currentProgress = ScanProgress(
            stage = ScanStage.PREPARING_APK,
            stageSubtitle = "المرحلة 1: تجهيز الملف واستخراج DEX",
            percent = 0.05f,
            isRunning = true
        )
        onProgress(currentProgress)

        try {
            // Stage 1: Prepare & Extract DEX files
            val extraction = apkExtractor.extractDexFiles(apkFile) { dexName ->
                // Progress callback during extraction
            }
            tempDir = extraction.tempDir
            val dexFiles = extraction.dexFiles

            if (dexFiles.isEmpty()) {
                throw IllegalStateException("لم يتم العثور على أي ملفات DEX داخل هذا التطبيق")
            }

            val appPackageName = extraction.apkInfo.packageName
            val thirdPartyDetector = ThirdPartyDetector(
                appPackageName = appPackageName,
                userExcludedPackages = emptyList(),
                userTrustedPackages = emptyList()
            )
            val keywordMatcher = KeywordMatcher(settings.searchMode, keywords)
            val scoringEngine = ScoringEngine(keywordMatcher, thirdPartyDetector)

            currentProgress = currentProgress.copy(
                stage = ScanStage.INDEXING_DEX,
                stageSubtitle = "المرحلة 2: فهرسة الرموز والنصوص في DEX",
                percent = 0.15f,
                totalDex = dexFiles.size,
                isStep1Done = true
            )
            onProgress(currentProgress)

            val stage2StartTime = System.currentTimeMillis()

            // Open DEX parsers
            for (dexItem in dexFiles) {
                if (!coroutineScope.isActive) throw CancellationException()
                try {
                    val parser = DexParser(dexItem.file, dexItem.dexName)
                    parsers.add(parser)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            dexProcessingTime = System.currentTimeMillis() - stage2StartTime

            currentProgress = currentProgress.copy(
                stage = ScanStage.FINDING_CANDIDATES,
                stageSubtitle = "المرحلة 3: البحث السريع عن المرشحين (Fast Discovery)",
                percent = 0.25f,
                isStep2Done = true
            )
            onProgress(currentProgress)

            // Stage 3: FAST CANDIDATE DISCOVERY (single-pass multi-pattern search, no bytecode scan)
            val stage3StartTime = System.currentTimeMillis()
            val candidatePool = mutableListOf<CandidateMethod>()

            for ((dexIdx, parser) in parsers.withIndex()) {
                if (!coroutineScope.isActive) throw CancellationException()

                val dexName = parser.dexName
                val header = parser.header

                // 1. Single-pass fast string index using Aho-Corasick / Trie
                val matchingStringIndices = HashMap<Int, Set<String>>(256)
                for (sIdx in 0 until header.stringIdsSize) {
                    val str = parser.getString(sIdx)
                    if (str.length >= 2) {
                        val matches = keywordMatcher.findMatches(str)
                        if (matches.isNotEmpty()) {
                            matchingStringIndices[sIdx] = matches
                            totalKeywordHits += matches.size
                        }
                    }
                }

                // 2. Identify candidate method_ids by nameIdx
                val candidateMethodIds = HashMap<Int, Set<String>>(512)
                for (mIdx in 0 until header.methodIdsSize) {
                    val methodId = parser.getMethodId(mIdx) ?: continue
                    val nameMatches = matchingStringIndices[methodId.nameIdx]
                    if (nameMatches != null) {
                        candidateMethodIds[mIdx] = nameMatches
                    }
                }

                // 3. Scan class defs and their methods
                for (cDefIdx in 0 until header.classDefsSize) {
                    if (!coroutineScope.isActive) throw CancellationException()

                    val classDef = parser.getClassDef(cDefIdx) ?: continue
                    if (classDef.classDataOff <= 0) continue

                    val classType = parser.getType(classDef.classIdx)
                    val classMatches = keywordMatcher.findMatches(classType)
                    val encodedMethods = parser.getClassMethods(classDef.classDataOff)

                    for (encMethod in encodedMethods) {
                        val mId = encMethod.methodIdx
                        val methodMatches = candidateMethodIds[mId]

                        val allMatches = mutableSetOf<String>()
                        if (classMatches.isNotEmpty()) allMatches.addAll(classMatches)
                        if (methodMatches != null) allMatches.addAll(methodMatches)

                        if (allMatches.isNotEmpty()) {
                            val methodId = parser.getMethodId(mId) ?: continue
                            val methodName = parser.getString(methodId.nameIdx)
                            val protoId = parser.getProtoId(methodId.protoIdx)
                            val returnType = if (protoId != null) parser.getType(protoId.returnTypeIdx) else ""
                            val signature = parser.getMethodSignature(mId)

                            val primaryKw = when {
                                keywordMatcher.hasAnyExceptional(allMatches) -> keywordMatcher.getExceptionalMatches(allMatches).first()
                                methodMatches != null && methodMatches.isNotEmpty() -> methodMatches.first()
                                classMatches.isNotEmpty() -> classMatches.first()
                                else -> allMatches.first()
                            }

                            candidatePool.add(
                                CandidateMethod(
                                    dexName = dexName,
                                    classIdx = classDef.classIdx,
                                    className = classType,
                                    methodIdx = mId,
                                    methodName = methodName,
                                    signature = signature,
                                    returnType = returnType,
                                    accessFlags = encMethod.accessFlags,
                                    codeOffset = encMethod.codeOff,
                                    primaryKeyword = primaryKw,
                                    matchedKeywords = allMatches,
                                    matchOrigin = if (methodMatches != null) "Method Name" else "Class / Type"
                                )
                            )
                        }
                    }
                }

                // Intermediate discovery progress
                val discoveryFraction = 0.25f + ((dexIdx + 1).toFloat() / parsers.size) * 0.15f
                currentProgress = currentProgress.copy(
                    percent = discoveryFraction,
                    currentDex = dexIdx + 1,
                    candidatesCount = candidatePool.size
                )
                onProgress(currentProgress)
            }

            candidateDiscoveryTime = System.currentTimeMillis() - stage3StartTime

            // Deduplicate candidates by unique signature
            val uniqueCandidates = candidatePool.groupBy { "${it.dexName}#${it.className}#${it.signature}" }
                .map { (_, list) ->
                    val first = list.first()
                    val mergedKeywords = list.flatMap { it.matchedKeywords }.toSet()
                    val bestPrimary = when {
                        keywordMatcher.hasAnyExceptional(mergedKeywords) -> keywordMatcher.getExceptionalMatches(mergedKeywords).first()
                        first.primaryKeyword.isNotEmpty() -> first.primaryKeyword
                        else -> mergedKeywords.firstOrNull() ?: "KEYWORD"
                    }
                    first.copy(primaryKeyword = bestPrimary, matchedKeywords = mergedKeywords)
                }

            uniqueCandidatesCount = uniqueCandidates.size

            // Apply candidate budget and smart prioritization
            val candidateBudget = settings.candidateBudget.coerceIn(50, 2000)
            val prioritizedCandidates = uniqueCandidates.sortedWith(
                compareByDescending<CandidateMethod> {
                    keywordMatcher.hasAnyExceptional(it.matchedKeywords)
                }.thenByDescending {
                    thirdPartyDetector.classify(it.className) == com.example.model.CodeClassification.APP_CODE
                }.thenByDescending { it.matchedKeywords.size }
            ).take(candidateBudget)

            currentProgress = currentProgress.copy(
                stage = ScanStage.ANALYZING_CANDIDATES,
                stageSubtitle = "المرحلة 4: تحليل bytecode للمرشحين (Targeted Analysis)",
                percent = 0.40f,
                candidatesCount = uniqueCandidatesCount,
                isStep3Done = true
            )
            onProgress(currentProgress)

            // Stage 4: TARGETED METHOD ANALYSIS (only analyze candidates)
            val stage4StartTime = System.currentTimeMillis()
            val totalToAnalyze = prioritizedCandidates.size
            val discoveredBatch = mutableListOf<AnalysisResult>()

            for ((idx, candidate) in prioritizedCandidates.withIndex()) {
                if (!coroutineScope.isActive) throw CancellationException()

                try {
                    val parser = parsers.find { it.dexName == candidate.dexName } ?: continue
                    val disassembler = DexDisassembler(parser)

                    val codeItem = if (candidate.codeOffset > 0) {
                        parser.getCodeItem(candidate.codeOffset)
                    } else null

                    val disassembly = disassembler.disassemble(
                        className = candidate.className,
                        methodName = candidate.methodName,
                        signature = candidate.signature,
                        returnType = candidate.returnType,
                        accessFlags = candidate.accessFlags,
                        codeItem = codeItem
                    )

                    val analysisResult = scoringEngine.evaluate(candidate, disassembly)
                    deeplyAnalyzedCount++

                    // Filters
                    var shouldInclude = true
                    if (settings.hideConstructors && analysisResult.isConstructor) shouldInclude = false
                    if (settings.hideClinit && analysisResult.isStaticInitializer) shouldInclude = false
                    if (settings.hideThirdParty && analysisResult.classification == com.example.model.CodeClassification.THIRD_PARTY) {
                        shouldInclude = false
                        thirdPartyFilteredCount++
                    }

                    if (shouldInclude) {
                        val key = analysisResult.uniqueKey
                        val existing = resultsMap[key]
                        if (existing != null) {
                            val mergedKw = (existing.matchedKeywords + analysisResult.matchedKeywords).distinct()
                            val bestKw = when {
                                keywordMatcher.isExceptional(analysisResult.matchedKeyword) -> analysisResult.matchedKeyword
                                keywordMatcher.isExceptional(existing.matchedKeyword) -> existing.matchedKeyword
                                else -> existing.matchedKeyword
                            }
                            resultsMap[key] = existing.copy(
                                matchedKeyword = bestKw,
                                matchedKeywords = mergedKw,
                                score = maxOf(existing.score, analysisResult.score),
                                hasExceptionalMatch = existing.hasExceptionalMatch || analysisResult.hasExceptionalMatch,
                                exceptionalKeywordsMatched = (existing.exceptionalKeywordsMatched + analysisResult.exceptionalKeywordsMatched).distinct()
                            )
                        } else {
                            val numberedResult = analysisResult.copy(id = (resultsMap.size + 1).toLong())
                            resultsMap[key] = numberedResult
                            if (settings.streamResultsDuringScan) {
                                discoveredBatch.add(numberedResult)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Crash-proof: log and proceed with remaining candidate methods
                    e.printStackTrace()
                }

                // Batch stream results & progress updates (every 5 items or at end)
                if (idx % 5 == 0 || idx == totalToAnalyze - 1) {
                    if (discoveredBatch.isNotEmpty()) {
                        for (res in discoveredBatch) {
                            onResultDiscovered(res)
                        }
                        discoveredBatch.clear()
                    }

                    val progressFraction = 0.40f + ((idx + 1).toFloat() / totalToAnalyze.coerceAtLeast(1)) * 0.50f
                    val elapsedSec = (System.currentTimeMillis() - startTime) / 1000
                    currentProgress = currentProgress.copy(
                        percent = progressFraction.coerceAtMost(0.92f),
                        analyzedCount = idx + 1,
                        resultsCount = resultsMap.size,
                        elapsedTimeSeconds = elapsedSec
                    )
                    onProgress(currentProgress)
                }
            }

            analysisTime = System.currentTimeMillis() - stage4StartTime

            // Stage 5: Ranking & Final Deduplication
            currentProgress = currentProgress.copy(
                stage = ScanStage.RANKING_RESULTS,
                stageSubtitle = "المرحلة 5: ترتيب وتصفية النتائج النهائية",
                percent = 0.95f,
                isStep4Done = true,
                isStep5Done = true
            )
            onProgress(currentProgress)

            val stage5StartTime = System.currentTimeMillis()
            val sortedResults = resultsMap.values.sortedWith(
                compareByDescending<AnalysisResult> { it.hasExceptionalMatch }
                    .thenByDescending { it.score }
                    .thenByDescending { (if (it.returnBoolean) 2 else 0) + it.branchCount + (if (it.callsEntitlement) 1 else 0) }
                    .thenByDescending { it.matchedKeywords.size }
                    .thenByDescending { it.classification == com.example.model.CodeClassification.APP_CODE }
            ).mapIndexed { index, res ->
                res.copy(id = (index + 1).toLong())
            }

            rankingTime = System.currentTimeMillis() - stage5StartTime

            val totalTime = System.currentTimeMillis() - startTime
            val totalMemoryMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)

            val report = DebugPerformanceReport(
                totalTimeMs = totalTime,
                dexProcessingTimeMs = dexProcessingTime,
                candidateDiscoveryTimeMs = candidateDiscoveryTime,
                analysisTimeMs = analysisTime,
                rankingTimeMs = rankingTime,
                dexCount = parsers.size,
                keywordHits = totalKeywordHits,
                uniqueCandidates = uniqueCandidatesCount,
                deeplyAnalyzed = deeplyAnalyzedCount,
                veryHighCount = sortedResults.count { it.confidenceLevel == ConfidenceLevel.VERY_HIGH },
                highCount = sortedResults.count { it.confidenceLevel == ConfidenceLevel.HIGH },
                mediumCount = sortedResults.count { it.confidenceLevel == ConfidenceLevel.MEDIUM },
                lowCount = sortedResults.count { it.confidenceLevel == ConfidenceLevel.LOW },
                veryLowCount = sortedResults.count { it.confidenceLevel == ConfidenceLevel.VERY_LOW },
                thirdPartyFilteredCount = thirdPartyFilteredCount,
                approximateMemoryMb = totalMemoryMb
            )

            val finalProgress = currentProgress.copy(
                stage = ScanStage.COMPLETED,
                stageSubtitle = "اكتمل الفحص بنجاح",
                percent = 1.0f,
                analyzedCount = deeplyAnalyzedCount,
                resultsCount = sortedResults.size,
                isStep4Done = true,
                isStep5Done = true,
                isStep6Done = true,
                isRunning = false,
                elapsedTimeSeconds = totalTime / 1000
            )
            onProgress(finalProgress)

            return@withContext Pair(sortedResults, report)

        } catch (e: CancellationException) {
            onProgress(
                ScanProgress(
                    stage = ScanStage.CANCELLED,
                    stageSubtitle = "تم إيقاف الفحص من قبل المستخدم",
                    isRunning = false,
                    isStep1Done = currentProgress.isStep1Done,
                    isStep2Done = currentProgress.isStep2Done,
                    candidatesCount = currentProgress.candidatesCount,
                    analyzedCount = currentProgress.analyzedCount,
                    resultsCount = resultsMap.size,
                    elapsedTimeSeconds = currentProgress.elapsedTimeSeconds
                )
            )
            throw e
        } catch (e: Exception) {
            onProgress(
                ScanProgress(
                    stage = ScanStage.ERROR,
                    stageSubtitle = "حدث خطأ: ${e.localizedMessage ?: "تعذر التحليل"}",
                    isRunning = false,
                    errorMessage = e.localizedMessage,
                    elapsedTimeSeconds = currentProgress.elapsedTimeSeconds
                )
            )
            throw e
        } finally {
            // Clean up: close all opened DEX parsers and delete temp directory
            for (parser in parsers) {
                try {
                    parser.close()
                } catch (_: Exception) {}
            }
            parsers.clear()
            tempDir?.let {
                apkExtractor.cleanup(it)
            }
            System.gc()
        }
    }
}
