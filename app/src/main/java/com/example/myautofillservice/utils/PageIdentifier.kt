package com.example.myautofillservice.utils

import android.app.assist.AssistStructure
import android.util.Log
import java.security.MessageDigest

object PageIdentifier {
    
    private const val TAG = "PageIdentifier"
    
    data class PageInfo(
        val url: String?,
        val domain: String?,
        val pageId: String,
        val title: String?
    )
    
    fun identifyPage(structure: AssistStructure): PageInfo {
        Log.d(TAG, "=== Identifying page ===")
        
        // Intentar obtener URL real
        val url = UrlUtils.extractUrlFromStructure(structure)
        val domain = UrlUtils.extractDomain(url)
        
        // También intentar obtener el dominio directamente desde WebDomain
        val webDomain = UrlUtils.extractWebDomainFromStructure(structure)
        
        // Priorizar WebDomain si está disponible
        val finalDomain = webDomain ?: domain
        val finalUrl = url ?: finalDomain?.let { "https://$it" }
        
        if (finalDomain != null) {
            Log.d(TAG, "Found real domain: $finalDomain (from ${if (webDomain != null) "WebDomain" else "URL"})")
            return PageInfo(
                url = finalUrl,
                domain = finalDomain,
                pageId = finalDomain,
                title = finalDomain
            )
        }
        
        // Si no podemos obtener la URL, crear un identificador único basado en la página
        val pageSignature = createPageSignature(structure)
        val pageId = "page_${hashString(pageSignature)}"
        val title = extractPageTitle(structure) ?: pageId
        
        Log.d(TAG, "Created page ID: $pageId with title: $title")
        Log.d(TAG, "Page signature used: $pageSignature")
        
        return PageInfo(
            url = null,
            domain = null,
            pageId = pageId,
            title = title
        )
    }
    
    private fun createPageSignature(structure: AssistStructure): String {
        // Estrategia mejorada: combinar elementos estables con elementos únicos del sitio
        val stableElements = mutableSetOf<String>()
        val uniqueElements = mutableSetOf<String>()
        
        // Recopilar elementos estables y únicos
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectStableElements(windowNode.rootViewNode, stableElements)
            collectUniqueElements(windowNode.rootViewNode, uniqueElements)
        }
        
        Log.d(TAG, "Stable elements found: $stableElements")
        Log.d(TAG, "Unique elements found: $uniqueElements")
        
        // Estrategia ultra-simple: usar SOLO elementos básicos de formularios
        val basicElements = mutableSetOf<String>()
        
        // Solo agregar elementos estables básicos
        basicElements.addAll(stableElements)
        
        // Si no tenemos elementos básicos suficientes, usar una firma mínima
        if (basicElements.size < 2) {
            val activityComponent = structure.activityComponent
            val packageName = activityComponent?.packageName ?: "unknown"
            
            // Crear una firma ultra-básica basada en el navegador
            when {
                packageName.contains("chrome") -> {
                    basicElements.add("browser:chrome")
                    basicElements.add("form:login")
                }
                packageName.contains("firefox") -> {
                    basicElements.add("browser:firefox")
                    basicElements.add("form:login")
                }
                else -> {
                    basicElements.add("browser:generic")
                    basicElements.add("form:login")
                }
            }
        }
        
        // Crear firma ultra-simple
        val sortedElements = basicElements.sorted().joinToString("|")
        Log.d(TAG, "Ultra-simple signature: $sortedElements")
        
        return sortedElements
    }
    
    private fun collectUniqueElements(node: AssistStructure.ViewNode, uniqueElements: MutableSet<String>) {
        // Recopilar SOLO elementos que sean únicos del sitio pero ESTABLES
        
        // EXCLUIR textos que cambien según el input del usuario
        node.text?.toString()?.let { text ->
            val cleanText = text.trim()
            // Solo incluir textos que sean labels estáticos, NO valores de campos
            if (cleanText.length in 3..30 && 
                !cleanText.matches(Regex(".*\\d.*")) && // No textos con números (pueden ser valores)
                !cleanText.equals("username", true) &&
                !cleanText.equals("password", true) &&
                !cleanText.equals("email", true) &&
                !cleanText.equals("login", true) &&
                !cleanText.equals("preview", true) &&
                // Excluir textos que parezcan valores ingresados
                !cleanText.matches(Regex(".*[A-Z].*[0-9].*")) && // No patrones como "Password123"
                !cleanText.matches(Regex(".*[a-z]+[0-9]+.*")) && // No patrones como "student123"
                cleanText.length <= 15) { // Textos cortos más probables de ser labels
                uniqueElements.add("text:${cleanText.lowercase(java.util.Locale.getDefault())}")
            }
        }
        
        // Solo IDs muy estables (excluir IDs dinámicos de Chrome)
        node.idEntry?.let { id ->
            if (id.isNotBlank() && id.length in 3..25 && 
                !id.matches(Regex(".*\\d{5,}.*")) && // No IDs con muchos números
                // Excluir IDs específicos de Chrome que pueden cambiar
                !id.contains("ar_view") &&
                !id.contains("capture") &&
                !id.contains("overlay") &&
                !id.contains("toolbar") &&
                !id.contains("location_bar") &&
                !id.contains("action_chip") &&
                // Solo incluir IDs que sean probablemente del sitio web
                (id.contains("coordinator") || 
                 id.contains("container") ||
                 id.contains("form") ||
                 id.contains("login") ||
                 id.contains("main") ||
                 id.contains("content"))) {
                uniqueElements.add("id:$id")
            }
        }
        
        // Atributos HTML únicos y estables
        node.htmlInfo?.let { htmlInfo ->
            htmlInfo.attributes?.forEach { attribute ->
                val key = attribute.first
                val value = attribute.second
                
                if (key == "class" && value != null && value.length in 3..20 &&
                    !value.contains("dynamic") && !value.contains("temp")) {
                    uniqueElements.add("class:$value")
                } else if (key == "placeholder" && value != null && value.length in 3..30) {
                    uniqueElements.add("placeholder:${value.lowercase(java.util.Locale.getDefault())}")
                }
            }
        }
        
        // Recursivamente procesar nodos hijos
        for (i in 0 until node.childCount) {
            collectUniqueElements(node.getChildAt(i), uniqueElements)
        }
    }
    
    private fun collectStableElements(node: AssistStructure.ViewNode, stableElements: MutableSet<String>) {
        // Solo recopilar elementos que sean 100% estables
        
        // Textos de labels que nunca cambien
        node.text?.toString()?.let { text ->
            val cleanText = text.lowercase(java.util.Locale.getDefault()).trim()
            // Solo labels muy específicos y estables
            when {
                cleanText == "username" -> stableElements.add("label:username")
                cleanText == "password" -> stableElements.add("label:password")
                cleanText == "email" -> stableElements.add("label:email")
                cleanText == "login" -> stableElements.add("label:login")
                cleanText == "sign in" -> stableElements.add("label:signin")
                cleanText == "usuario" -> stableElements.add("label:usuario")
                cleanText == "contraseña" -> stableElements.add("label:contraseña")
                cleanText == "correo" -> stableElements.add("label:correo")
                cleanText == "iniciar sesión" -> stableElements.add("label:iniciar")
            }
        }
        
        // Atributos HTML muy estables
        node.htmlInfo?.let { htmlInfo ->
            htmlInfo.attributes?.forEach { attribute ->
                val key = attribute.first
                val value = attribute.second?.lowercase(java.util.Locale.getDefault())
                
                // Solo atributos name y type que sean estándar
                if (key == "name" && value != null) {
                    when (value) {
                        "username", "user", "email", "password", "pass" -> {
                            stableElements.add("name:$value")
                        }
                    }
                } else if (key == "type" && value != null) {
                    when (value) {
                        "email", "password", "text" -> {
                            stableElements.add("type:$value")
                        }
                    }
                }
            }
        }
        
        // Recursivamente procesar nodos hijos
        for (i in 0 until node.childCount) {
            collectStableElements(node.getChildAt(i), stableElements)
        }
    }
    

    
    private fun extractPageTitle(structure: AssistStructure): String? {
        Log.d(TAG, "=== Extracting page title ===")
        
        // ESTRATEGIA 1: Usar el dominio web como título principal
        val url = UrlUtils.extractUrlFromStructure(structure)
        val domain = UrlUtils.extractDomain(url)
        if (domain != null && domain != "com.android") {
            val friendlyTitle = createFriendlyTitleFromDomain(domain)
            Log.d(TAG, "Using domain-based title: $friendlyTitle (from $domain)")
            return friendlyTitle
        }
        
        // ESTRATEGIA 2: Buscar títulos HTML específicos
        val htmlTitles = extractHtmlTitles(structure)
        if (htmlTitles.isNotEmpty()) {
            val bestHtmlTitle = selectBestTitle(htmlTitles)
            Log.d(TAG, "Using HTML title: $bestHtmlTitle")
            return bestHtmlTitle
        }
        
        // ESTRATEGIA 3: Buscar títulos en metadatos y elementos específicos
        val metaTitles = extractMetaTitles(structure)
        if (metaTitles.isNotEmpty()) {
            val bestMetaTitle = selectBestTitle(metaTitles)
            Log.d(TAG, "Using meta title: $bestMetaTitle")
            return bestMetaTitle
        }
        
        // ESTRATEGIA 4: Buscar títulos en elementos de navegación
        val navTitles = extractNavigationTitles(structure)
        if (navTitles.isNotEmpty()) {
            val bestNavTitle = selectBestTitle(navTitles)
            Log.d(TAG, "Using navigation title: $bestNavTitle")
            return bestNavTitle
        }
        
        // ESTRATEGIA 5: Recopilar todos los textos posibles y filtrar
        val allTitles = mutableListOf<String>()
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectPossibleTitles(windowNode.rootViewNode, allTitles)
        }
        
        Log.d(TAG, "Collected ${allTitles.size} possible titles: $allTitles")
        
        // Filtrar y seleccionar el mejor título
        val filteredTitles = filterAndRankTitles(allTitles)
        if (filteredTitles.isNotEmpty()) {
            Log.d(TAG, "Using filtered title: ${filteredTitles.first()}")
            return filteredTitles.first()
        }
        
        // ESTRATEGIA 6: Crear título basado en la URL si está disponible
        url?.let { fullUrl ->
            val urlBasedTitle = createTitleFromUrl(fullUrl)
            if (urlBasedTitle != null) {
                Log.d(TAG, "Using URL-based title: $urlBasedTitle")
                return urlBasedTitle
            }
        }
        
        // Fallback: usar un título genérico pero descriptivo
        Log.d(TAG, "Using generic fallback title")
        return "Sitio Web"
    }
    
    private fun collectPossibleTitles(node: AssistStructure.ViewNode, titles: MutableList<String>) {
        node.text?.toString()?.let { text ->
            if (text.length in 3..100 && !text.matches(Regex("\\d+"))) {
                titles.add(text.trim())
            }
        }
        
        for (i in 0 until node.childCount) {
            collectPossibleTitles(node.getChildAt(i), titles)
        }
    }
    
    private fun createFriendlyTitleFromDomain(domain: String): String {
        return when {
            domain.contains("github") -> "GitHub"
            domain.contains("google") -> "Google"
            domain.contains("facebook") -> "Facebook"
            domain.contains("twitter") -> "Twitter"
            domain.contains("linkedin") -> "LinkedIn"
            domain.contains("instagram") -> "Instagram"
            domain.contains("youtube") -> "YouTube"
            domain.contains("netflix") -> "Netflix"
            domain.contains("amazon") -> "Amazon"
            domain.contains("microsoft") -> "Microsoft"
            domain.contains("apple") -> "Apple"
            domain.contains("stackoverflow") -> "Stack Overflow"
            domain.contains("reddit") -> "Reddit"
            domain.contains("wikipedia") -> "Wikipedia"
            domain.contains("medium") -> "Medium"
            domain.contains("dropbox") -> "Dropbox"
            domain.contains("slack") -> "Slack"
            domain.contains("discord") -> "Discord"
            domain.contains("zoom") -> "Zoom"
            domain.contains("paypal") -> "PayPal"
            domain.contains("stripe") -> "Stripe"
            domain.contains("shopify") -> "Shopify"
            domain.contains("wordpress") -> "WordPress"
            domain.contains("blogger") -> "Blogger"
            domain.contains("tumblr") -> "Tumblr"
            domain.contains("pinterest") -> "Pinterest"
            domain.contains("twitch") -> "Twitch"
            domain.contains("spotify") -> "Spotify"
            domain.contains("soundcloud") -> "SoundCloud"
            domain.contains("vimeo") -> "Vimeo"
            domain.contains("dailymotion") -> "Dailymotion"
            domain.contains("herokuapp") -> extractHerokuAppName(domain)
            domain.contains("vercel") -> extractVercelAppName(domain)
            domain.contains("netlify") -> extractNetlifyAppName(domain)
            domain.contains("firebase") -> extractFirebaseAppName(domain)
            domain.contains("github.io") -> extractGitHubPagesName(domain)
            domain.contains("testautomation") -> "Test Automation"
            domain.contains("practice") -> "Practice Site"
            domain.contains("demo") -> "Demo Site"
            domain.contains("test") -> "Test Site"
            domain.contains("staging") -> "Staging Site"
            domain.contains("dev") -> "Development Site"
            else -> {
                // Extraer nombre principal del dominio
                val parts = domain.split(".")
                if (parts.size >= 2) {
                    val mainPart = parts[parts.size - 2]
                    mainPart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                } else {
                    domain.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }
    
    private fun extractHerokuAppName(domain: String): String {
        val appName = domain.substringBefore(".herokuapp.com")
        return if (appName.isNotBlank() && appName != domain) {
            appName.split("-").joinToString(" ") { 
                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
        } else {
            "Heroku App"
        }
    }
    
    private fun extractVercelAppName(domain: String): String {
        val appName = domain.substringBefore(".vercel.app")
        return if (appName.isNotBlank() && appName != domain) {
            appName.split("-").joinToString(" ") { 
                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
        } else {
            "Vercel App"
        }
    }
    
    private fun extractNetlifyAppName(domain: String): String {
        val appName = domain.substringBefore(".netlify.app")
        return if (appName.isNotBlank() && appName != domain) {
            appName.split("-").joinToString(" ") { 
                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
        } else {
            "Netlify App"
        }
    }
    
    private fun extractFirebaseAppName(domain: String): String {
        val appName = domain.substringBefore(".firebaseapp.com")
        return if (appName.isNotBlank() && appName != domain) {
            appName.split("-").joinToString(" ") { 
                it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
        } else {
            "Firebase App"
        }
    }
    
    private fun extractGitHubPagesName(domain: String): String {
        val parts = domain.split(".")
        return if (parts.isNotEmpty()) {
            val username = parts[0]
            "$username's GitHub Page"
        } else {
            "GitHub Pages"
        }
    }
    
    private fun extractHtmlTitles(structure: AssistStructure): List<String> {
        val htmlTitles = mutableListOf<String>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectHtmlTitles(windowNode.rootViewNode, htmlTitles)
        }
        
        return htmlTitles.distinct()
    }
    
    private fun collectHtmlTitles(node: AssistStructure.ViewNode, titles: MutableList<String>) {
        // Buscar en elementos HTML específicos que suelen contener títulos
        node.htmlInfo?.let { htmlInfo ->
            val tag = htmlInfo.tag?.lowercase()
            
            when (tag) {
                "title" -> {
                    node.text?.toString()?.let { text ->
                        if (text.isNotBlank() && text.length in 3..100) {
                            titles.add(text.trim())
                        }
                    }
                }
                "h1", "h2", "h3" -> {
                    node.text?.toString()?.let { text ->
                        if (text.isNotBlank() && text.length in 3..60) {
                            titles.add(text.trim())
                        }
                    }
                }
            }
        }
        
        // Recursivamente buscar en nodos hijos
        for (i in 0 until node.childCount) {
            collectHtmlTitles(node.getChildAt(i), titles)
        }
    }
    
    private fun extractMetaTitles(structure: AssistStructure): List<String> {
        val metaTitles = mutableListOf<String>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectMetaTitles(windowNode.rootViewNode, metaTitles)
        }
        
        return metaTitles.distinct()
    }
    
    private fun collectMetaTitles(node: AssistStructure.ViewNode, titles: MutableList<String>) {
        node.htmlInfo?.let { htmlInfo ->
            if (htmlInfo.tag?.lowercase() == "meta") {
                htmlInfo.attributes?.forEach { attribute ->
                    val key = attribute.first.lowercase()
                    val value = attribute.second
                    
                    // Buscar meta tags de título
                    if ((key == "property" && (value == "og:title" || value == "twitter:title")) ||
                        (key == "name" && (value == "title" || value == "application-name"))) {
                        
                        // Buscar el contenido del meta tag
                        htmlInfo.attributes?.find { it.first.lowercase() == "content" }?.second?.let { content ->
                            if (content.isNotBlank() && content.length in 3..100) {
                                titles.add(content.trim())
                            }
                        }
                    }
                }
            }
        }
        
        // Recursivamente buscar en nodos hijos
        for (i in 0 until node.childCount) {
            collectMetaTitles(node.getChildAt(i), titles)
        }
    }
    
    private fun extractNavigationTitles(structure: AssistStructure): List<String> {
        val navTitles = mutableListOf<String>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            collectNavigationTitles(windowNode.rootViewNode, navTitles)
        }
        
        return navTitles.distinct()
    }
    
    private fun collectNavigationTitles(node: AssistStructure.ViewNode, titles: MutableList<String>) {
        // Buscar en elementos de navegación y encabezados
        val className = node.className?.toString()?.lowercase()
        val idEntry = node.idEntry?.lowercase()
        
        // Elementos que probablemente contengan títulos de navegación
        if (className?.contains("toolbar") == true ||
            className?.contains("header") == true ||
            className?.contains("navbar") == true ||
            className?.contains("navigation") == true ||
            idEntry?.contains("toolbar") == true ||
            idEntry?.contains("header") == true ||
            idEntry?.contains("title") == true ||
            idEntry?.contains("brand") == true) {
            
            node.text?.toString()?.let { text ->
                if (text.isNotBlank() && text.length in 3..50 && !isGenericText(text)) {
                    titles.add(text.trim())
                }
            }
        }
        
        // Recursivamente buscar en nodos hijos
        for (i in 0 until node.childCount) {
            collectNavigationTitles(node.getChildAt(i), titles)
        }
    }
    
    private fun selectBestTitle(titles: List<String>): String? {
        if (titles.isEmpty()) return null
        
        // Ordenar títulos por calidad
        val rankedTitles = titles
            .filter { !isGenericText(it) }
            .sortedByDescending { calculateTitleScore(it) }
        
        return rankedTitles.firstOrNull()
    }
    
    private fun filterAndRankTitles(titles: List<String>): List<String> {
        return titles
            .filter { title ->
                title.length in 3..60 &&
                !isGenericText(title) &&
                !isUserInputValue(title) &&
                !isBrowserUI(title)
            }
            .distinctBy { it.lowercase().trim() }
            .sortedByDescending { calculateTitleScore(it) }
            .take(5) // Solo los 5 mejores
    }
    
    private fun calculateTitleScore(title: String): Int {
        var score = 0
        
        // Longitud óptima
        when (title.length) {
            in 5..25 -> score += 10
            in 26..40 -> score += 8
            in 3..4 -> score += 5
            in 41..60 -> score += 3
        }
        
        // Penalizar números excesivos
        val numberCount = title.count { it.isDigit() }
        if (numberCount > title.length / 2) score -= 5
        
        // Bonificar palabras comunes de títulos
        val titleWords = listOf("login", "sign", "home", "dashboard", "app", "site", "page", "portal")
        if (titleWords.any { title.contains(it, ignoreCase = true) }) score += 3
        
        // Penalizar texto que parece ser valores de entrada
        if (title.matches(Regex(".*[a-z]+[0-9]+.*")) || 
            title.matches(Regex(".*[A-Z].*[0-9].*"))) score -= 8
        
        // Bonificar capitalización apropiada
        if (title.split(" ").all { word -> 
            word.isNotEmpty() && (word[0].isUpperCase() || word.all { it.isLowerCase() })
        }) score += 2
        
        return score
    }
    
    private fun isGenericText(text: String): Boolean {
        val genericTexts = listOf(
            "preview", "loading", "submit", "button", "click", "here",
            "username", "password", "email", "login", "signin", "signup",
            "register", "forgot", "remember", "back", "next", "continue",
            "cancel", "close", "ok", "yes", "no", "save", "delete",
            "edit", "update", "refresh", "reload", "search", "filter"
        )
        
        return genericTexts.any { text.equals(it, ignoreCase = true) } ||
               text.matches(Regex("\\d+")) || // Solo números
               text.length < 3
    }
    
    private fun isUserInputValue(text: String): Boolean {
        // Detectar valores que parecen ser entrada del usuario
        return text.matches(Regex(".*[a-z]+[0-9]+.*")) || // Como "student123"
               text.matches(Regex(".*[A-Z].*[0-9].*")) || // Como "Password123"
               text.contains("@") || // Emails
               text.matches(Regex("\\+?[0-9-()\\s]+")) // Números de teléfono
    }
    
    private fun isBrowserUI(text: String): Boolean {
        val browserUITexts = listOf(
            "chrome", "firefox", "safari", "edge", "browser",
            "tab", "window", "bookmark", "history", "settings",
            "menu", "toolbar", "address", "url", "search"
        )
        
        return browserUITexts.any { text.contains(it, ignoreCase = true) }
    }
    
    private fun createTitleFromUrl(url: String): String? {
        return when {
            url.contains("herokuapp.com") -> {
                val appName = url.substringAfter("://").substringBefore(".herokuapp.com")
                if (appName.isNotBlank()) {
                    appName.split("-").joinToString(" ") { 
                        it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
                    }
                } else "Heroku App"
            }
            url.contains("github.io") -> "GitHub Pages"
            url.contains("vercel.app") -> "Vercel App"
            url.contains("netlify.app") -> "Netlify App"
            url.contains("firebaseapp.com") -> "Firebase App"
            url.contains("testautomation") -> "Test Automation"
            url.contains("practice") -> "Practice Site"
            url.contains("demo") -> "Demo Site"
            url.contains("test") -> "Test Site"
            url.contains("staging") -> "Staging"
            url.contains("dev") -> "Development"
            else -> null
        }
    }
    
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(8)
    }
}