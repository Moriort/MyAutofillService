package com.example.myautofillservice.data

import androidx.room.*

@Dao
interface WebSiteDao {
    
    @Query("SELECT * FROM websites WHERE pageId = :pageId LIMIT 1")
    suspend fun getWebSiteByPageId(pageId: String): WebSite?
    
    @Query("SELECT * FROM websites WHERE signature = :signature LIMIT 1")
    suspend fun getWebSiteBySignature(signature: String): WebSite?
    
    @Query("SELECT * FROM websites WHERE signature LIKE '%' || :partialSignature || '%' ORDER BY lastUsed DESC")
    suspend fun findSimilarWebSites(partialSignature: String): List<WebSite>
    
    @Query("SELECT * FROM websites ORDER BY lastUsed DESC")
    suspend fun getAllWebSites(): List<WebSite>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebSite(webSite: WebSite): Long
    
    @Update
    suspend fun updateWebSite(webSite: WebSite)
    
    @Delete
    suspend fun deleteWebSite(webSite: WebSite)
    
    @Query("UPDATE websites SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE websites SET name = :name, isUserNamed = 1 WHERE id = :id")
    suspend fun updateWebSiteName(id: Long, name: String)
}