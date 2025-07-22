package com.example.myautofillservice.service

import android.content.Context
import android.util.Log
import com.example.myautofillservice.data.CredentialDatabase
import com.example.myautofillservice.data.WebSite
import com.example.myautofillservice.utils.PageIdentifier

class WebSiteManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WebSiteManager"
        private const val SIMILARITY_THRESHOLD = 0.6 // 60% de similitud
    }
    
    private val database = CredentialDatabase.getDatabase(context)
    private val webSiteDao = database.webSiteDao()
    
    suspend fun identifyOrCreateWebSite(pageInfo: PageIdentifier.PageInfo): WebSiteIdentification {
        Log.d(TAG, "Identifying website for pageId: ${pageInfo.pageId}")
        
        // 1. Buscar sitio exacto por pageId
        val exactMatch = webSiteDao.getWebSiteByPageId(pageInfo.pageId)
        if (exactMatch != null) {
            Log.d(TAG, "Found exact match: ${exactMatch.name}")
            webSiteDao.updateLastUsed(exactMatch.id)
            return WebSiteIdentification.ExactMatch(exactMatch)
        }
        
        // 2. Buscar sitios similares por firma
        val signature = createSignature(pageInfo)
        val similarSites = findSimilarSites(signature)
        
        if (similarSites.isNotEmpty()) {
            Log.d(TAG, "Found ${similarSites.size} similar sites")
            return WebSiteIdentification.SimilarSites(similarSites, pageInfo)
        }
        
        // 3. Crear nuevo sitio
        val newSite = createNewWebSite(pageInfo, signature)
        Log.d(TAG, "Created new website: ${newSite.name}")
        return WebSiteIdentification.NewSite(newSite)
    }
    
    private suspend fun findSimilarSites(signature: String): List<WebSite> {
        val allSites = webSiteDao.getAllWebSites()
        val similarSites = mutableListOf<WebSite>()
        
        for (site in allSites) {
            val similarity = calculateSimilarity(signature, site.signature)
            if (similarity >= SIMILARITY_THRESHOLD) {
                Log.d(TAG, "Similar site found: ${site.name} (similarity: $similarity)")
                similarSites.add(site)
            }
        }
        
        return similarSites.sortedByDescending { it.lastUsed }
    }
    
    private fun calculateSimilarity(signature1: String, signature2: String): Double {
        val elements1 = signature1.split("|").toSet()
        val elements2 = signature2.split("|").toSet()
        
        val intersection = elements1.intersect(elements2).size
        val union = elements1.union(elements2).size
        
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }
    
    private fun createSignature(pageInfo: PageIdentifier.PageInfo): String {
        // Crear una firma más detallada para comparación
        val elements = mutableListOf<String>()
        
        pageInfo.url?.let { elements.add("url:$it") }
        pageInfo.domain?.let { elements.add("domain:$it") }
        pageInfo.title?.let { elements.add("title:$it") }
        elements.add("pageId:${pageInfo.pageId}")
        
        return elements.sorted().joinToString("|")
    }
    
    private suspend fun createNewWebSite(pageInfo: PageIdentifier.PageInfo, signature: String): WebSite {
        val name = generateWebSiteName(pageInfo)
        val webSite = WebSite(
            name = name,
            pageId = pageInfo.pageId,
            url = pageInfo.url,
            domain = pageInfo.domain,
            signature = signature,
            isUserNamed = false
        )
        
        val id = webSiteDao.insertWebSite(webSite)
        return webSite.copy(id = id)
    }
    
    private fun generateWebSiteName(pageInfo: PageIdentifier.PageInfo): String {
        return when {
            pageInfo.domain != null -> pageInfo.domain
            pageInfo.title != null && pageInfo.title.length <= 30 -> pageInfo.title
            pageInfo.url != null -> pageInfo.url.take(30)
            else -> "Sitio ${pageInfo.pageId.take(8)}"
        }
    }
    
    suspend fun confirmSiteMatch(pageInfo: PageIdentifier.PageInfo, selectedSite: WebSite): WebSite {
        // Actualizar el sitio existente con la nueva información
        val updatedSite = selectedSite.copy(
            pageId = pageInfo.pageId, // Actualizar pageId para futuras coincidencias
            url = pageInfo.url ?: selectedSite.url,
            domain = pageInfo.domain ?: selectedSite.domain,
            lastUsed = System.currentTimeMillis()
        )
        
        webSiteDao.updateWebSite(updatedSite)
        Log.d(TAG, "Confirmed site match: ${updatedSite.name}")
        return updatedSite
    }
    
    suspend fun createNewSiteFromSimilar(pageInfo: PageIdentifier.PageInfo, baseName: String): WebSite {
        val signature = createSignature(pageInfo)
        val webSite = WebSite(
            name = baseName,
            pageId = pageInfo.pageId,
            url = pageInfo.url,
            domain = pageInfo.domain,
            signature = signature,
            isUserNamed = true
        )
        
        val id = webSiteDao.insertWebSite(webSite)
        Log.d(TAG, "Created new site from similar: $baseName")
        return webSite.copy(id = id)
    }
    
    suspend fun updateWebSiteName(siteId: Long, newName: String) {
        webSiteDao.updateWebSiteName(siteId, newName)
        Log.d(TAG, "Updated website name to: $newName")
    }
}

sealed class WebSiteIdentification {
    data class ExactMatch(val webSite: WebSite) : WebSiteIdentification()
    data class SimilarSites(val similarSites: List<WebSite>, val pageInfo: PageIdentifier.PageInfo) : WebSiteIdentification()
    data class NewSite(val webSite: WebSite) : WebSiteIdentification()
}