package com.example

import com.example.engine.analysis.ScoringEngine
import com.example.engine.analysis.ThirdPartyDetector
import com.example.engine.dex.DisassemblyResult
import com.example.engine.keyword.KeywordMatcher
import com.example.model.CandidateMethod
import com.example.model.KeywordEntity
import com.example.model.KeywordSearchMode
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testExceptionalKeywordMatchingAndScoring() {
        val keywords = listOf(
            KeywordEntity(id = 1L, word = "check_license", normalizedWord = "checklicense", isEnabled = true, isExceptional = false),
            KeywordEntity(id = 2L, word = "vip_status", normalizedWord = "vipstatus", isEnabled = true, isExceptional = true)
        )
        val matcher = KeywordMatcher(
            searchMode = KeywordSearchMode.CONTAINS,
            keywords = keywords
        )

        // Text containing normal keyword
        val normalText = "method check_license returned true"
        val normalMatches = matcher.findMatches(normalText)
        assertTrue(normalMatches.contains("check_license"))
        assertEquals(0, matcher.getExceptionalMatches(normalMatches).size)

        // Text containing exceptional keyword with word variation/substring
        val expText = "field m_vip_status_code is verified"
        val expMatches = matcher.findMatches(expText)
        assertTrue(expMatches.contains("vip_status"))
        val expMatchedList = matcher.getExceptionalMatches(expMatches)
        assertEquals(1, expMatchedList.size)
        assertTrue(expMatchedList.contains("vip_status"))

        // Candidate method definition
        val candidate = CandidateMethod(
            dexName = "classes.dex",
            classIdx = 0,
            className = "Lcom/example/BillingService;",
            methodIdx = 0,
            methodName = "checkVip",
            signature = "checkVip()Z",
            returnType = "Z",
            accessFlags = 1,
            codeOffset = 100L,
            primaryKeyword = "vip_status",
            matchedKeywords = expMatches,
            matchOrigin = "Method Name"
        )

        val disassembly = DisassemblyResult(
            smaliCode = "const/4 v0, 1\nreturn v0",
            branchCount = 0,
            booleanConstCount = 1,
            returnBoolean = true,
            invokedMethods = emptyList(),
            referencedStrings = listOf("vip_status"),
            referencedFields = emptyList(),
            callsEntitlement = false,
            accessesBilling = false
        )

        val detector = ThirdPartyDetector()
        val scoringEngine = ScoringEngine(
            keywordMatcher = matcher,
            thirdPartyDetector = detector
        )

        val result = scoringEngine.evaluate(
            candidate = candidate,
            disassembly = disassembly
        )

        assertTrue("Result must have hasExceptionalMatch set to true", result.hasExceptionalMatch)
        assertTrue("Result must list vip_status in exceptionalKeywordsMatched", result.exceptionalKeywordsMatched.contains("vip_status"))
        assertTrue("Result score should include the +25 exceptional bonus", result.score >= 25)
        assertTrue("Scoring reasons must mention exceptional keyword bonus", result.reasons.any { it.description.contains("استثنائية") || it.description.contains("⭐") })
        assertEquals("matchedKeyword must be exact original keyword", "vip_status", result.matchedKeyword)
    }

    @Test
    fun testOriginalKeywordPreservationAndMultiFactorScoring() {
        val keywords = listOf(
            KeywordEntity(id = 1L, word = "PREMIUM", normalizedWord = "premium", isEnabled = true, isExceptional = false),
            KeywordEntity(id = 2L, word = "has_active_purchase", normalizedWord = "hasactivepurchase", isEnabled = true, isExceptional = false)
        )
        val matcher = KeywordMatcher(
            searchMode = KeywordSearchMode.CONTAINS,
            keywords = keywords
        )

        val candidate = CandidateMethod(
            dexName = "classes2.dex",
            classIdx = 10,
            className = "Lcom/myapp/feature/SubManager;",
            methodIdx = 42,
            methodName = "isSubscribed",
            signature = "isSubscribed()Z",
            returnType = "Z",
            accessFlags = 1,
            codeOffset = 200L,
            primaryKeyword = "PREMIUM",
            matchedKeywords = setOf("PREMIUM", "has_active_purchase"),
            matchOrigin = "Method Name"
        )

        val disassembly = DisassemblyResult(
            smaliCode = "if-eqz v0, :cond_0\nreturn v1\n:cond_0\nreturn v2",
            branchCount = 2,
            booleanConstCount = 2,
            returnBoolean = true,
            hasComparison = true,
            invokedMethods = listOf("Lcom/myapp/feature/SubManager;->check()Z"),
            referencedStrings = listOf("PREMIUM"),
            referencedFields = emptyList(),
            callsEntitlement = true,
            accessesBilling = false
        )

        val scoringEngine = ScoringEngine(
            keywordMatcher = matcher,
            thirdPartyDetector = ThirdPartyDetector()
        )

        val result = scoringEngine.evaluate(candidate, disassembly)

        // Verify original keyword without normalization or casing change
        assertEquals("PREMIUM", result.matchedKeyword)
        assertNotEquals("P_R_E_M_I_U_M", result.matchedKeyword)
        assertNotEquals("premium", result.matchedKeyword)

        // Verify multi-factor signals
        assertTrue("Score must be high for app code with branches & boolean return", result.score >= 80)
        assertTrue(result.reasons.any { it.description.contains("Boolean Return") })
        assertTrue(result.reasons.any { it.description.contains("Conditional Branch") })
        assertTrue(result.reasons.any { it.description.contains("Value Comparison") })
        assertTrue(result.reasons.any { it.description.contains("Entitlement") })
    }
}
