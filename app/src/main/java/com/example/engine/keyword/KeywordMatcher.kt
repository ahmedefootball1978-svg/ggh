package com.example.engine.keyword

import com.example.database.KeywordRepository
import com.example.model.KeywordEntity
import com.example.model.KeywordSearchMode
import java.util.ArrayDeque
import java.util.Locale
import java.util.regex.Pattern

/**
 * High-performance multi-pattern keyword matcher using:
 * 1. HashSet lookups for O(1) exact and case-insensitive matching
 * 2. Trie / Aho-Corasick automaton for O(N) multi-keyword substring search
 * 3. Normalized token segment matching for camelCase and snake_case identifiers
 */
class KeywordMatcher(
    private val searchMode: KeywordSearchMode,
    keywords: List<KeywordEntity>
) {
    // Enabled raw keywords
    private val rawKeywords: List<String> = keywords
        .filter { it.isEnabled && it.word.isNotBlank() }
        .map { it.word.trim() }

    // Exceptional keywords (high-priority, deeper inspection)
    val exceptionalKeywords: Set<String> = keywords
        .filter { it.isEnabled && it.isExceptional && it.word.isNotBlank() }
        .map { it.word.trim() }
        .toSet()

    // Fast exact lookup sets
    private val exactSet: Set<String> = rawKeywords.toSet()
    private val lowercaseSet: Set<String> = rawKeywords.map { it.lowercase(Locale.ROOT) }.toSet()
    private val normalizedSet: Set<String> = rawKeywords.map { KeywordRepository.normalizeKeyword(it) }.toSet()

    // Map normalized word -> original raw keywords
    private val normalizedToOriginal = HashMap<String, MutableList<String>>()
    private val lowercaseToOriginal = HashMap<String, MutableList<String>>()

    // Aho-Corasick automaton for fast CONTAINS matching
    private val ahoCorasick: AhoCorasickAutomaton?

    // Precompiled regex patterns if in REGEX mode
    private val regexPatterns: List<Pair<String, Pattern>> = if (searchMode == KeywordSearchMode.REGEX) {
        rawKeywords.mapNotNull { word ->
            try {
                Pair(word, Pattern.compile(word, Pattern.CASE_INSENSITIVE))
            } catch (_: Exception) {
                null
            }
        }
    } else emptyList()

    init {
        for (word in rawKeywords) {
            val norm = KeywordRepository.normalizeKeyword(word)
            normalizedToOriginal.getOrPut(norm) { mutableListOf() }.add(word)

            val lower = word.lowercase(Locale.ROOT)
            lowercaseToOriginal.getOrPut(lower) { mutableListOf() }.add(word)
        }

        ahoCorasick = if (searchMode == KeywordSearchMode.CONTAINS || searchMode == KeywordSearchMode.NORMALIZED_IDENTIFIER) {
            val builder = AhoCorasickAutomaton.Builder()
            for (word in rawKeywords) {
                if (word.length >= 2) {
                    builder.addWord(word.lowercase(Locale.ROOT), word)
                }
            }
            builder.build()
        } else null
    }

    /**
     * Finds all keywords matching the given text identifier or string literal.
     * Exceptional keywords are searched with deep precision across sub-tokens, prefixes, and substrings.
     */
    fun findMatches(text: String): Set<String> {
        if (text.isEmpty() || rawKeywords.isEmpty()) return emptySet()

        val results = mutableSetOf<String>()

        when (searchMode) {
            KeywordSearchMode.EXACT_IDENTIFIER -> {
                if (exactSet.contains(text)) results.add(text)
            }

            KeywordSearchMode.CASE_INSENSITIVE -> {
                val lower = text.lowercase(Locale.ROOT)
                val original = lowercaseToOriginal[lower]
                if (original != null) results.addAll(original)
            }

            KeywordSearchMode.NORMALIZED_IDENTIFIER -> {
                val normalizedText = KeywordRepository.normalizeKeyword(text)

                // 1. Direct normalized match
                normalizedToOriginal[normalizedText]?.let { results.addAll(it) }

                // 2. Token segment check (split camelCase and snake_case)
                if (results.isEmpty()) {
                    val segments = splitIdentifier(text)
                    for (seg in segments) {
                        val normSeg = KeywordRepository.normalizeKeyword(seg)
                        if (normSeg.length >= 3) {
                            normalizedToOriginal[normSeg]?.let { results.addAll(it) }
                        }
                    }
                }

                // 3. Fast prefix/suffix match (e.g. isPremium -> premium, hasLicense -> license)
                if (results.isEmpty() && normalizedText.length >= 5) {
                    val prefixes = listOf("is", "has", "get", "set", "check", "do", "can")
                    for (p in prefixes) {
                        if (normalizedText.startsWith(p)) {
                            val remainder = normalizedText.substring(p.length)
                            normalizedToOriginal[remainder]?.let { results.addAll(it) }
                        }
                    }
                }
            }

            KeywordSearchMode.CONTAINS -> {
                val lower = text.lowercase(Locale.ROOT)

                if (ahoCorasick != null) {
                    val found = ahoCorasick.search(lower)
                    for (originalWord in found) {
                        val kwLower = originalWord.lowercase(Locale.ROOT)
                        if (kwLower.length < 3) {
                            if (isWordBoundaryMatch(lower, kwLower)) {
                                results.add(originalWord)
                            }
                        } else {
                            results.add(originalWord)
                        }
                    }
                }
            }

            KeywordSearchMode.REGEX -> {
                for ((word, pattern) in regexPatterns) {
                    if (pattern.matcher(text).find()) {
                        results.add(word)
                    }
                }
            }
        }

        // DEEP EXCEPTIONAL KEYWORDS INSPECTION (يتم البحث عنها أكثر وبدقة أكثر)
        // Check exceptional keywords deeply regardless of general search mode
        if (exceptionalKeywords.isNotEmpty()) {
            val lowerText = text.lowercase(Locale.ROOT)
            val normalizedText = KeywordRepository.normalizeKeyword(text)
            val segments = splitIdentifier(text)

            for (expWord in exceptionalKeywords) {
                if (results.contains(expWord)) continue

                val expLower = expWord.lowercase(Locale.ROOT)
                val expNorm = KeywordRepository.normalizeKeyword(expWord)

                // 1. Substring contains check
                if (lowerText.contains(expLower) || normalizedText.contains(expNorm)) {
                    results.add(expWord)
                    continue
                }

                // 2. Token segments match
                for (seg in segments) {
                    val segLower = seg.lowercase(Locale.ROOT)
                    val segNorm = KeywordRepository.normalizeKeyword(seg)
                    if (segLower == expLower || segNorm == expNorm || segLower.contains(expLower)) {
                        results.add(expWord)
                        break
                    }
                }

                // 3. Prefix / suffix checks (e.g., is_vip, checkVip, has_vip_active)
                if (!results.contains(expWord) && (
                    lowerText.startsWith("is$expLower") ||
                    lowerText.startsWith("has$expLower") ||
                    lowerText.startsWith("check$expLower") ||
                    lowerText.startsWith("get$expLower") ||
                    lowerText.endsWith(expLower)
                )) {
                    results.add(expWord)
                }
            }
        }

        return results
    }

    fun isExceptional(keyword: String): Boolean = exceptionalKeywords.contains(keyword)

    fun hasAnyExceptional(matched: Collection<String>): Boolean = matched.any { exceptionalKeywords.contains(it) }

    fun getExceptionalMatches(matched: Collection<String>): List<String> = matched.filter { exceptionalKeywords.contains(it) }

    private fun isWordBoundaryMatch(target: String, keyword: String): Boolean {
        val index = target.indexOf(keyword)
        if (index < 0) return false
        val beforeOk = index == 0 || !target[index - 1].isLetterOrDigit()
        val endIdx = index + keyword.length
        val afterOk = endIdx == target.length || !target[endIdx].isLetterOrDigit()
        return beforeOk && afterOk
    }

    private fun splitIdentifier(identifier: String): List<String> {
        return identifier.split(Regex("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|_|\\$|/"))
            .filter { it.isNotBlank() }
    }

    /**
     * Calculates base keyword weight with bonus for exceptional keywords:
     * - Short general keywords ("pro", "vip") get base weight.
     * - Exceptional keywords get strong priority bonus (+25 points).
     */
    fun calculateKeywordWeight(matchedKeywords: Collection<String>): Int {
        var score = 0
        for (kw in matchedKeywords) {
            val isExp = isExceptional(kw)
            val len = kw.length
            val base = when {
                len <= 3 -> 6
                len <= 5 -> 12
                len <= 8 -> 18
                len <= 12 -> 24
                else -> 30
            }
            score += if (isExp) (base + 25) else base
        }
        return score.coerceAtMost(55)
    }

    /**
     * Aho-Corasick automaton for fast multi-pattern string matching.
     */
    private class AhoCorasickAutomaton private constructor(
        private val root: Node
    ) {
        private class Node {
            val children = HashMap<Char, Node>()
            var fail: Node? = null
            val outputs = mutableListOf<String>()
        }

        fun search(text: String): Set<String> {
            val results = mutableSetOf<String>()
            var current = root

            for (char in text) {
                while (current != root && !current.children.containsKey(char)) {
                    current = current.fail ?: root
                }
                current = current.children[char] ?: root
                if (current.outputs.isNotEmpty()) {
                    results.addAll(current.outputs)
                }
            }
            return results
        }

        class Builder {
            private val root = Node()

            fun addWord(wordLower: String, original: String) {
                var current = root
                for (char in wordLower) {
                    current = current.children.getOrPut(char) { Node() }
                }
                current.outputs.add(original)
            }

            fun build(): AhoCorasickAutomaton {
                val queue = ArrayDeque<Node>()
                for (child in root.children.values) {
                    child.fail = root
                    queue.add(child)
                }

                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    for ((char, nextNode) in current.children) {
                        queue.add(nextNode)
                        var failNode = current.fail
                        while (failNode != null && !failNode.children.containsKey(char)) {
                            failNode = failNode.fail
                        }
                        val finalFail = failNode?.children?.get(char) ?: root
                        nextNode.fail = finalFail
                        nextNode.outputs.addAll(finalFail.outputs)
                    }
                }
                return AhoCorasickAutomaton(root)
            }
        }
    }
}
