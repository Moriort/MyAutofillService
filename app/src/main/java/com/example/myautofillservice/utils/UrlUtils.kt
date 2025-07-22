package com.example.myautofillservice.utils

import android.app.assist.AssistStructure
import android.util.Log
import java.net.URL

object UrlUtils {
    
    private const val TAG = "UrlUtils"
    
    fun extractUrlFromStructure(structure: AssistStructure): String? {
        Log.d(TAG, "=== Starting URL extraction ===")
        
        // ESTRATEGIA PRINCIPAL: Buscar WebDomain directamente (más confiable)
        val webDomain = extractWebDomainFromStructure(structure)
        if (webDomain != null) {
            val fullUrl = "https://$webDomain"
            Log.d(TAG, "Found URL via WebDomain: $fullUrl")
            return fullUrl
        }
        
        // Estrategia 2: Buscar en títulos de ventana
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            
            windowNode.title?.toString()?.let { title ->
                Log.d(TAG, "Window title: $title")
                val urlFromTitle = extractUrlFromText(title)
                if (urlFromTitle != null) {
                    Log.d(TAG, "Found URL in window title: $urlFromTitle")
                    return urlFromTitle
                }
            }
        }
        
        // Estrategia 3: Buscar específicamente por navegador
        val activityComponent = structure.activityComponent
        if (activityComponent != null) {
            val packageName = activityComponent.packageName
            Log.d(TAG, "Package name: $packageName")
            
            when {
                packageName.contains("chrome") -> {
                    val chromeUrl = findChromeUrl(structure)
                    if (chromeUrl != null) {
                        Log.d(TAG, "Found URL via Chrome detection: $chromeUrl")
                        return chromeUrl
                    }
                }
                packageName.contains("firefox") -> {
                    val firefoxUrl = findFirefoxUrl(structure)
                    if (firefoxUrl != null) {
                        Log.d(TAG, "Found URL via Firefox detection: $firefoxUrl")
                        return firefoxUrl
                    }
                }
            }
        }
        
        // Estrategia 4: Buscar en todos los nodos (método general)
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val url = findUrlInNode(windowNode.rootViewNode)
            if (url != null) {
                Log.d(TAG, "Found URL in general node search: $url")
                return url
            }
        }
        
        // Estrategia 5: Buscar patrones de dominio en cualquier texto
        val domainUrl = findDomainInAnyText(structure)
        if (domainUrl != null) {
            Log.d(TAG, "Found domain pattern in text: $domainUrl")
            return domainUrl
        }
        
        Log.d(TAG, "No URL found in structure")
        return null
    }
    
    private fun findWebViewDomain(structure: AssistStructure): String? {
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val domain = findWebViewInNode(windowNode.rootViewNode)
            if (domain != null) {
                return "https://$domain"
            }
        }
        return null
    }
    
    private fun findWebViewInNode(node: AssistStructure.ViewNode): String? {
        if (node.className == "android.webkit.WebView") {
            node.webDomain?.let { domain ->
                Log.d(TAG, "Found WebView domain: $domain")
                return domain
            }
        }
        
        for (i in 0 until node.childCount) {
            val childDomain = findWebViewInNode(node.getChildAt(i))
            if (childDomain != null) {
                return childDomain
            }
        }
        
        return null
    }
    
    private fun findDomainInAnyText(structure: AssistStructure): String? {
        val allTexts = mutableListOf<String>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectAllTexts(windowNode.rootViewNode, allTexts)
        }
        
        // Buscar dominios en todos los textos recolectados
        for (text in allTexts) {
            if (text.isNotBlank() && text.length < 200) { // Evitar textos muy largos
                val url = extractUrlFromText(text)
                if (url != null) {
                    return url
                }
            }
        }
        
        return null
    }
    
    private fun collectAllTexts(node: AssistStructure.ViewNode, texts: MutableList<String>) {
        node.text?.toString()?.let { text ->
            if (text.isNotBlank()) {
                texts.add(text)
            }
        }
        
        node.contentDescription?.toString()?.let { desc ->
            if (desc.isNotBlank()) {
                texts.add(desc)
            }
        }
        
        node.hint?.let { hint ->
            if (hint.isNotBlank()) {
                texts.add(hint)
            }
        }
        
        for (i in 0 until node.childCount) {
            collectAllTexts(node.getChildAt(i), texts)
        }
    }
    
    private fun findChromeUrl(structure: AssistStructure): String? {
        // Buscar en todos los nodos de texto que puedan contener la URL
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            
            // Buscar específicamente en nodos que puedan contener la URL de Chrome
            val url = findUrlInChromeNodes(windowNode.rootViewNode)
            if (url != null) {
                return url
            }
        }
        return null
    }
    
    private fun findUrlInChromeNodes(node: AssistStructure.ViewNode): String? {
        // Buscar en nodos que típicamente contienen la URL en Chrome
        val className = node.className
        val idEntry = node.idEntry
        
        // Log para debugging - solo nodos importantes
        if (node.text != null || node.contentDescription != null || 
            idEntry?.contains("url") == true || idEntry?.contains("location") == true ||
            idEntry?.contains("omnibox") == true || idEntry?.contains("address") == true) {
            Log.d(TAG, "Chrome node analysis:")
            Log.d(TAG, "  Class: $className")
            Log.d(TAG, "  ID: $idEntry")
            Log.d(TAG, "  Text: ${node.text}")
            Log.d(TAG, "  ContentDescription: ${node.contentDescription}")
            Log.d(TAG, "  WebDomain: ${node.webDomain}")
        }
        
        // Buscar específicamente en WebView
        if (className == "android.webkit.WebView") {
            node.webDomain?.let { domain ->
                Log.d(TAG, "Found WebView domain: $domain")
                return "https://$domain"
            }
        }
        
        // Buscar en nodos específicos de Chrome que pueden contener la URL
        when (idEntry) {
            "url_bar", "location_bar", "omnibox_text", "location_bar_status", 
            "location_bar_verbose_status", "url_text", "address_bar" -> {
                node.text?.toString()?.let { text ->
                    val url = extractUrlFromText(text)
                    if (url != null) {
                        Log.d(TAG, "Found URL in Chrome UI element ($idEntry): $url")
                        return url
                    }
                }
                node.contentDescription?.toString()?.let { desc ->
                    val url = extractUrlFromText(desc)
                    if (url != null) {
                        Log.d(TAG, "Found URL in Chrome UI description ($idEntry): $url")
                        return url
                    }
                }
            }
        }
        
        // Buscar en el texto del nodo
        node.text?.toString()?.let { text ->
            val url = extractUrlFromText(text)
            if (url != null) return url
        }
        
        // Buscar en la descripción del contenido
        node.contentDescription?.toString()?.let { desc ->
            val url = extractUrlFromText(desc)
            if (url != null) return url
        }
        
        // Buscar recursivamente en nodos hijos
        for (i in 0 until node.childCount) {
            val childUrl = findUrlInChromeNodes(node.getChildAt(i))
            if (childUrl != null) {
                return childUrl
            }
        }
        
        return null
    }
    
    private fun extractUrlFromText(text: String): String? {
        // Buscar URLs completas
        if (text.startsWith("http://") || text.startsWith("https://")) {
            Log.d(TAG, "Found complete URL: $text")
            return text
        }
        
        // Buscar patrones de URL en el texto
        val urlPattern = Regex("https?://[^\\s]+")
        val urlMatch = urlPattern.find(text)
        if (urlMatch != null) {
            Log.d(TAG, "Found URL pattern: ${urlMatch.value}")
            return urlMatch.value
        }
        
        // Buscar dominios que parezcan URLs, pero filtrar falsos positivos
        val domainPattern = Regex("(?:www\\.)?[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}")
        val domainMatch = domainPattern.find(text)
        if (domainMatch != null && text.length < 100) {
            val domain = domainMatch.value
            
            // Filtrar falsos positivos comunes
            val invalidDomains = listOf(
                "com.android", "com.google", "com.chrome", "com.firefox",
                "org.mozilla", "com.microsoft", "com.samsung", "com.huawei"
            )
            
            if (invalidDomains.any { domain.startsWith(it) }) {
                Log.d(TAG, "Filtered out invalid domain: $domain")
                return null
            }
            
            // Verificar que no sea parte de un email o nombre de paquete
            if (!text.contains("@") || text.indexOf(domain) < text.indexOf("@")) {
                // Verificar que el dominio tenga sentido como URL web
                if (domain.contains(".com") || domain.contains(".org") || 
                    domain.contains(".net") || domain.contains(".edu") ||
                    domain.contains(".gov") || domain.contains(".io") ||
                    domain.contains(".co") || domain.contains(".app")) {
                    Log.d(TAG, "Found valid domain in text: $domain")
                    return "https://$domain"
                }
            }
        }
        
        return null
    }
    
    private fun findFirefoxUrl(structure: AssistStructure): String? {
        // Similar lógica para Firefox
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val url = findUrlInTextNodes(windowNode.rootViewNode)
            if (url != null) {
                return url
            }
        }
        return null
    }
    
    private fun findUrlInTextNodes(node: AssistStructure.ViewNode): String? {
        // Buscar en el texto del nodo
        node.text?.toString()?.let { text ->
            if (text.contains("http://") || text.contains("https://")) {
                // Extraer la URL del texto
                val urlPattern = Regex("https?://[^\\s]+")
                val match = urlPattern.find(text)
                if (match != null) {
                    Log.d(TAG, "Found URL in text node: ${match.value}")
                    return match.value
                }
            }
        }
        
        // Buscar en nodos hijos
        for (i in 0 until node.childCount) {
            val childUrl = findUrlInTextNodes(node.getChildAt(i))
            if (childUrl != null) {
                return childUrl
            }
        }
        
        return null
    }
    
    private fun findUrlInNode(node: AssistStructure.ViewNode): String? {
        // Buscar en WebView - esta es la forma más confiable
        if (node.className == "android.webkit.WebView") {
            node.webDomain?.let { domain ->
                Log.d(TAG, "Found WebView domain: $domain")
                return "https://$domain"
            }
        }
        
        // Buscar en información HTML
        node.htmlInfo?.let { htmlInfo ->
            // Buscar en atributos del nodo
            htmlInfo.attributes?.forEach { attribute ->
                val key = attribute.first
                val value = attribute.second
                if (key.lowercase(java.util.Locale.getDefault()).contains("url") || key.lowercase(java.util.Locale.getDefault()).contains("href")) {
                    Log.d(TAG, "Found URL in HTML attribute $key: $value")
                    return value
                }
            }
        }
        
        // Buscar en el texto del nodo por patrones de URL
        node.text?.toString()?.let { text ->
            // Buscar URLs completas
            if (text.startsWith("http://") || text.startsWith("https://")) {
                Log.d(TAG, "Found URL in text: $text")
                return text
            }
            
            // Buscar dominios que parezcan URLs (ej: "example.com", "www.example.com")
            val domainPattern = Regex("(?:www\\.)?[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}")
            val match = domainPattern.find(text)
            if (match != null && text.length < 100) { // Evitar textos largos que no sean URLs
                val domain = match.value
                Log.d(TAG, "Found domain pattern in text: $domain")
                return "https://$domain"
            }
        }
        
        // Buscar en el hint del nodo
        node.hint?.let { hint ->
            val domainPattern = Regex("(?:www\\.)?[a-zA-Z0-9][a-zA-Z0-9-]{1,61}[a-zA-Z0-9]\\.[a-zA-Z]{2,}")
            val match = domainPattern.find(hint)
            if (match != null) {
                val domain = match.value
                Log.d(TAG, "Found domain pattern in hint: $domain")
                return "https://$domain"
            }
        }
        
        // Buscar recursivamente en nodos hijos
        for (i in 0 until node.childCount) {
            val childUrl = findUrlInNode(node.getChildAt(i))
            if (childUrl != null) {
                return childUrl
            }
        }
        
        return null
    }
    
    fun extractDomain(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        
        return try {
            val urlObj = URL(url)
            val host = urlObj.host
            
            // Remover www. si existe
            if (host.startsWith("www.")) {
                host.substring(4)
            } else {
                host
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain from URL: $url", e)
            
            // Fallback: extraer dominio manualmente
            val cleanUrl = url.removePrefix("http://").removePrefix("https://")
            val domain = cleanUrl.split("/")[0].split("?")[0]
            
            if (domain.startsWith("www.")) {
                domain.substring(4)
            } else {
                domain
            }
        }
    }
    
    fun isSameDomain(url1: String?, url2: String?): Boolean {
        val domain1 = extractDomain(url1)
        val domain2 = extractDomain(url2)
        return domain1 != null && domain2 != null && domain1.equals(domain2, ignoreCase = true)
    }
    
    fun extractWebDomainFromStructure(structure: AssistStructure): String? {
        Log.d(TAG, "=== Extracting WebDomain directly ===")
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val webDomain = extractWebDomainFromNode(windowNode.rootViewNode)
            if (webDomain != null) {
                Log.d(TAG, "Found WebDomain: $webDomain")
                return webDomain
            }
        }
        
        Log.d(TAG, "No WebDomain found in structure")
        return null
    }
    
    private fun extractWebDomainFromNode(node: AssistStructure.ViewNode): String? {
        // Log para debugging - mostrar todos los nodos que tienen WebDomain
        if (node.webDomain != null) {
            Log.d(TAG, "Node with WebDomain found:")
            Log.d(TAG, "  Class: ${node.className}")
            Log.d(TAG, "  ContentDescription: ${node.contentDescription}")
            Log.d(TAG, "  WebDomain: ${node.webDomain}")
        }
        
        // Buscar específicamente el WebDomain en los nodos
        node.webDomain?.let { webDomain ->
            if (webDomain.isNotBlank() && isValidDomain(webDomain)) {
                Log.d(TAG, "Found valid WebDomain in node: $webDomain")
                return webDomain
            } else {
                Log.d(TAG, "WebDomain found but invalid: $webDomain")
            }
        }
        
        // Recursivamente buscar en nodos hijos
        for (i in 0 until node.childCount) {
            val childWebDomain = extractWebDomainFromNode(node.getChildAt(i))
            if (childWebDomain != null) {
                return childWebDomain
            }
        }
        
        return null
    }
    
    private fun isValidDomain(domain: String): Boolean {
        Log.d(TAG, "Validating domain: $domain")
        
        // Filtrar dominios inválidos o de aplicaciones
        val invalidDomains = listOf(
            "com.android", "com.google.android", "com.chrome", "com.firefox",
            "org.mozilla", "com.microsoft", "com.samsung", "com.huawei"
        )
        
        if (invalidDomains.any { domain.startsWith(it) }) {
            Log.d(TAG, "Filtered out invalid domain: $domain")
            return false
        }
        
        // Verificar que tenga formato de dominio válido (MEJORADO)
        // Permitir subdominios y dominios con guiones
        val domainPattern = Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}$")
        val isValid = domainPattern.matches(domain) && domain.length < 100 && domain.contains(".")
        
        Log.d(TAG, "Domain validation result for '$domain': $isValid")
        return isValid
    }
}