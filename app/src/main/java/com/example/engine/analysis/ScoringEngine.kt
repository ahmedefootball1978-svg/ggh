package com.example.engine.analysis

import com.example.database.KeywordRepository
import com.example.engine.dex.DisassemblyResult
import com.example.engine.keyword.KeywordMatcher
import com.example.model.AnalysisResult
import com.example.model.CandidateMethod
import com.example.model.CodeClassification
import com.example.model.ConfidenceLevel
import com.example.model.ScoringReason
import java.util.Locale

class ScoringEngine(
    private val keywordMatcher: KeywordMatcher,
    private val thirdPartyDetector: ThirdPartyDetector
) {

    fun evaluate(
        candidate: CandidateMethod,
        disassembly: DisassemblyResult
    ): AnalysisResult {
        val reasons = mutableListOf<ScoringReason>()
        var score = 0

        val className = candidate.className
        val methodName = candidate.methodName
        val returnType = candidate.returnType
        val isConstructor = methodName == "<init>"
        val isClinit = methodName == "<clinit>"
        val classification = thirdPartyDetector.classify(className)

        // All matched original keywords
        val extraKeywords = findKeywordsInDisassembly(disassembly)
        val allMatchedKeywords = (candidate.matchedKeywords + extraKeywords).distinct()

        // Exceptional Keywords Match
        val exceptionalMatches = keywordMatcher.getExceptionalMatches(allMatchedKeywords)
        val hasExceptional = exceptionalMatches.isNotEmpty()

        // Determine primary matched keyword (Original unaltered string)
        val primaryMatchedKeyword = when {
            hasExceptional -> exceptionalMatches.first()
            candidate.primaryKeyword.isNotEmpty() -> candidate.primaryKeyword
            allMatchedKeywords.isNotEmpty() -> allMatchedKeywords.first()
            else -> "KEYWORD"
        }

        // 1. Base Keyword Match (+10)
        score += 10
        reasons.add(ScoringReason("تطابق الكلمة المفتاحية (Keyword Match: $primaryMatchedKeyword)", +10))

        // 1.1 Exceptional Keyword bonus (+25)
        if (hasExceptional) {
            val expBonus = 25
            score += expBonus
            reasons.add(ScoringReason("⭐ كلمة استثنائية ذات أولوية (${exceptionalMatches.joinToString(", ")})", +expBonus))
        }

        // 2. Application Package bonus (+10)
        if (classification == CodeClassification.APP_CODE) {
            score += 10
            reasons.add(ScoringReason("كود التطبيق الأصلي (Application Code)", +10))
        }

        // 3. Boolean Return Type (+15)
        val hasBoolReturn = disassembly.returnBoolean || returnType == "Z"
        if (hasBoolReturn) {
            score += 15
            reasons.add(ScoringReason("إرجاع قيمة منطقية (Boolean Return)", +15))
        }

        // 4. Conditional Branches (+15)
        if (disassembly.branchCount > 0) {
            score += 15
            reasons.add(ScoringReason("تفريعات شرطية (Conditional Branch: ${disassembly.branchCount})", +15))
        }

        // 5. Value Comparison / Decision (+10)
        if (disassembly.hasComparison || (disassembly.booleanConstCount >= 2 && disassembly.branchCount > 0)) {
            score += 10
            reasons.add(ScoringReason("مقارنة قيم وشروط (Value Comparison)", +10))
        }

        // 6. Access / Entitlement Context (+15)
        val hasEntitlementContext = disassembly.callsEntitlement || isAccessDecisionMethodName(methodName.lowercase(Locale.ROOT))
        if (hasEntitlementContext) {
            score += 15
            reasons.add(ScoringReason("سياق الصلاحية والوصول (Entitlement / Access Context)", +15))
        }

        // 7. Relevant Method Calls (+10)
        val hasRelevantCall = disassembly.invokedMethods.any { m ->
            val ml = m.lowercase(Locale.ROOT)
            ml.contains("check") || ml.contains("verify") || ml.contains("ispremium") ||
                    ml.contains("isvip") || ml.contains("purchase") || ml.contains("query")
        }
        if (hasRelevantCall) {
            score += 10
            reasons.add(ScoringReason("استدعاء دوال فحص وتحقق مرتبطة (Relevant Method Call)", +10))
        }

        // 8. Multiple Related Signals (+10)
        var signalCount = 0
        if (hasBoolReturn) signalCount++
        if (disassembly.branchCount > 0) signalCount++
        if (hasEntitlementContext) signalCount++
        if (allMatchedKeywords.size > 1) signalCount++
        if (disassembly.hasComparison) signalCount++
        if (signalCount >= 2) {
            score += 10
            reasons.add(ScoringReason("تعدد المؤشرات المنطقية المرتبطة (Multiple Related Signals)", +10))
        }

        // Penalties
        // 9. Third-party library penalty (-20)
        if (classification == CodeClassification.THIRD_PARTY) {
            score -= 20
            reasons.add(ScoringReason("مكتبة خارجية (Third-Party Library)", -20))
        } else if (classification == CodeClassification.FRAMEWORK) {
            score -= 30
            reasons.add(ScoringReason("نظام أندرويد (Android Framework)", -30))
        }

        // 10. Constructor & Static Initializer (-15)
        if (isConstructor) {
            score -= 15
            reasons.add(ScoringReason("دالة إنشاء (Constructor <init>)", -15))
        } else if (isClinit) {
            score -= 15
            reasons.add(ScoringReason("تهيئة ثابتة (Static Initializer <clinit>)", -15))
        }

        // 11. Generic utility method (-15)
        val isGenericUtility = methodName in setOf("toString", "hashCode", "equals", "clone") ||
                ((methodName.startsWith("get") || methodName.startsWith("set")) &&
                        disassembly.branchCount == 0 && !disassembly.callsEntitlement && !hasBoolReturn)
        if (isGenericUtility) {
            score -= 15
            reasons.add(ScoringReason("دالة عامة عامية (Generic Utility Method)", -15))
        }

        // 12. Billing infrastructure boilerplate only (-20)
        if (disassembly.accessesBilling && !hasBoolReturn && disassembly.branchCount == 0) {
            score -= 20
            reasons.add(ScoringReason("بنية تحتية لمكتبة الدفع فقط (Billing Infrastructure)", -20))
        }

        // Clamp final score between 0 and 100
        val finalScore = score.coerceIn(0, 100)

        // Confidence level strictly assigned
        val confidence = when {
            finalScore >= 90 && signalCount >= 2 && classification == CodeClassification.APP_CODE -> ConfidenceLevel.VERY_HIGH
            finalScore >= 75 -> ConfidenceLevel.HIGH
            finalScore >= 50 -> ConfidenceLevel.MEDIUM
            finalScore >= 25 -> ConfidenceLevel.LOW
            else -> ConfidenceLevel.VERY_LOW
        }

        return AnalysisResult(
            dexName = candidate.dexName,
            className = className,
            methodName = methodName,
            signature = candidate.signature,
            returnType = returnType,
            accessFlags = candidate.accessFlags,
            score = finalScore,
            confidenceLevel = confidence,
            classification = classification,
            matchedKeyword = primaryMatchedKeyword,
            normalizedKeyword = KeywordRepository.normalizeKeyword(primaryMatchedKeyword),
            matchedKeywords = allMatchedKeywords,
            reasons = reasons,
            smaliSnippet = disassembly.smaliCode,
            isConstructor = isConstructor,
            isStaticInitializer = isClinit,
            isThirdParty = classification == CodeClassification.THIRD_PARTY,
            branchCount = disassembly.branchCount,
            returnBoolean = hasBoolReturn,
            callsEntitlement = disassembly.callsEntitlement,
            accessesBilling = disassembly.accessesBilling,
            hasExceptionalMatch = hasExceptional,
            exceptionalKeywordsMatched = exceptionalMatches
        )
    }

    private fun findKeywordsInDisassembly(disassembly: DisassemblyResult): Set<String> {
        val matches = mutableSetOf<String>()
        for (str in disassembly.referencedStrings) {
            matches.addAll(keywordMatcher.findMatches(str))
        }
        for (fld in disassembly.referencedFields) {
            matches.addAll(keywordMatcher.findMatches(fld))
        }
        return matches
    }

    private fun isAccessDecisionMethodName(name: String): Boolean {
        return name.startsWith("is") ||
                name.startsWith("has") ||
                name.startsWith("check") ||
                name.startsWith("can") ||
                name.startsWith("should") ||
                name.contains("vip") ||
                name.contains("premium") ||
                name.contains("pro") ||
                name.contains("access") ||
                name.contains("entitlement") ||
                name.contains("license") ||
                name.contains("active") ||
                name.contains("subscription")
    }
}
