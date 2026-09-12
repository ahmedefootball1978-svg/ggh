package com.example.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.KeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords ORDER BY createdAt DESC")
    fun getAllKeywords(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords WHERE isEnabled = 1")
    suspend fun getEnabledKeywords(): List<KeywordEntity>

    @Query("SELECT * FROM keywords")
    suspend fun getAllKeywordsSnapshot(): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeywords(keywords: List<KeywordEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(keyword: KeywordEntity): Long

    @Update
    suspend fun updateKeyword(keyword: KeywordEntity)

    @Delete
    suspend fun deleteKeyword(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE id IN (:ids)")
    suspend fun deleteKeywordsByIds(ids: List<Long>)

    @Query("DELETE FROM keywords")
    suspend fun deleteAllKeywords()

    @Query("UPDATE keywords SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setKeywordEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE keywords SET isEnabled = :isEnabled")
    suspend fun setAllKeywordsEnabled(isEnabled: Boolean)

    @Query("UPDATE keywords SET isExceptional = :isExceptional WHERE id = :id")
    suspend fun setKeywordExceptional(id: Long, isExceptional: Boolean)

    @Query("UPDATE keywords SET isExceptional = :isExceptional WHERE id IN (:ids)")
    suspend fun setKeywordsExceptionalByIds(ids: List<Long>, isExceptional: Boolean)

    @Query("SELECT COUNT(*) FROM keywords")
    fun getKeywordsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM keywords WHERE isEnabled = 1")
    fun getEnabledKeywordsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM keywords WHERE isExceptional = 1")
    fun getExceptionalKeywordsCount(): Flow<Int>
}
