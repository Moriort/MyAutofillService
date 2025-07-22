package com.example.myautofillservice.data

import androidx.room.*

@Dao
interface CredentialDao {
    
    @Query("SELECT * FROM credentials WHERE domain = :domain ORDER BY lastUsed DESC")
    suspend fun getCredentialsForDomain(domain: String): List<Credential>
    
    @Query("SELECT * FROM credentials WHERE url LIKE '%' || :urlPart || '%' ORDER BY lastUsed DESC")
    suspend fun getCredentialsForUrl(urlPart: String): List<Credential>
    
    @Query("SELECT * FROM credentials ORDER BY lastUsed DESC")
    suspend fun getAllCredentials(): List<Credential>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredential(credential: Credential): Long
    
    @Update
    suspend fun updateCredential(credential: Credential)
    
    @Delete
    suspend fun deleteCredential(credential: Credential)
    
    @Query("UPDATE credentials SET lastUsed = :timestamp WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT * FROM credentials WHERE domain = :domain AND username = :username LIMIT 1")
    suspend fun findExistingCredential(domain: String, username: String): Credential?
}