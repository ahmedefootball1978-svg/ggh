package com.example.database

import com.example.model.KeywordEntity
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class KeywordImportStats(
    val totalLines: Int,
    val duplicatesRemoved: Int,
    val invalidLines: Int,
    val finalKeywords: Int
)

class KeywordRepository(private val keywordDao: KeywordDao) {

    val allKeywords: Flow<List<KeywordEntity>> = keywordDao.getAllKeywords()
    val totalCount: Flow<Int> = keywordDao.getKeywordsCount()
    val enabledCount: Flow<Int> = keywordDao.getEnabledKeywordsCount()
    val exceptionalCount: Flow<Int> = keywordDao.getExceptionalKeywordsCount()

    suspend fun getEnabledKeywords(): List<KeywordEntity> = keywordDao.getEnabledKeywords()

    suspend fun getAllKeywordsSnapshot(): List<KeywordEntity> = keywordDao.getAllKeywordsSnapshot()

    suspend fun insertKeyword(word: String, category: String = "General", isExceptional: Boolean = false): Long {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return -1L
        val normalized = normalizeKeyword(trimmed)
        val entity = KeywordEntity(
            word = trimmed,
            normalizedWord = normalized,
            isEnabled = true,
            category = category,
            isExceptional = isExceptional
        )
        return keywordDao.insertKeyword(entity)
    }

    suspend fun updateKeyword(keyword: KeywordEntity) {
        val normalized = normalizeKeyword(keyword.word)
        keywordDao.updateKeyword(keyword.copy(normalizedWord = normalized))
    }

    suspend fun toggleKeyword(id: Long, isEnabled: Boolean) {
        keywordDao.setKeywordEnabled(id, isEnabled)
    }

    suspend fun toggleKeywordExceptional(id: Long, isExceptional: Boolean) {
        keywordDao.setKeywordExceptional(id, isExceptional)
    }

    suspend fun setKeywordsExceptional(ids: List<Long>, isExceptional: Boolean) {
        if (ids.isNotEmpty()) {
            keywordDao.setKeywordsExceptionalByIds(ids, isExceptional)
        }
    }

    suspend fun toggleAllKeywords(isEnabled: Boolean) {
        keywordDao.setAllKeywordsEnabled(isEnabled)
    }

    suspend fun deleteKeyword(keyword: KeywordEntity) {
        keywordDao.deleteKeyword(keyword)
    }

    suspend fun deleteKeywordsByIds(ids: List<Long>) {
        if (ids.isNotEmpty()) {
            keywordDao.deleteKeywordsByIds(ids)
        }
    }

    suspend fun deleteAllKeywords() {
        keywordDao.deleteAllKeywords()
    }

    /**
     * Imports keywords from raw TXT lines exactly as provided.
     * Uploaded as-is without dropping duplicates or strict validation.
     */
    suspend fun importFromText(text: String): KeywordImportStats {
        val lines = text.lines()
        val toInsert = mutableListOf<KeywordEntity>()
        var emptyCount = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                emptyCount++
                continue
            }

            val category = detectCategory(line)
            toInsert.add(
                KeywordEntity(
                    word = line,
                    normalizedWord = normalizeKeyword(line),
                    isEnabled = true,
                    category = category
                )
            )
        }

        if (toInsert.isNotEmpty()) {
            keywordDao.insertKeywords(toInsert)
        }

        return KeywordImportStats(
            totalLines = lines.size,
            duplicatesRemoved = 0,
            invalidLines = emptyCount,
            finalKeywords = toInsert.size
        )
    }

    suspend fun exportToText(): String {
        val all = keywordDao.getAllKeywordsSnapshot()
        return all.joinToString("\n") { it.word }
    }

    suspend fun ensureDefaultKeywords() {
        val snapshot = keywordDao.getAllKeywordsSnapshot()
        if (snapshot.isEmpty()) {
            val entities = DefaultKeywords.initialList.map { word ->
                KeywordEntity(
                    word = word,
                    normalizedWord = normalizeKeyword(word),
                    isEnabled = true,
                    category = detectCategory(word)
                )
            }
            keywordDao.insertKeywords(entities)
        }
    }

    companion object {
        fun normalizeKeyword(word: String): String {
            return word
                .replace("_", "")
                .replace("-", "")
                .replace("$", "")
                .lowercase(Locale.ROOT)
        }

        fun detectCategory(word: String): String {
            val lower = word.lowercase(Locale.ROOT)
            return when {
                lower.contains("vip") -> "VIP"
                lower.contains("premium") -> "Premium"
                lower.contains("pro") -> "Pro"
                lower.contains("purchase") || lower.contains("buy") -> "Purchase"
                lower.contains("sub") || lower.contains("subscription") -> "Subscription"
                lower.contains("entitle") || lower.contains("access") -> "Entitlement"
                lower.contains("license") || lower.contains("billing") -> "Billing"
                else -> "General"
            }
        }
    }
}
