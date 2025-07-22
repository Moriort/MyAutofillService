package com.example.myautofillservice.service

import android.app.assist.AssistStructure
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.*
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import com.example.myautofillservice.data.Credential
import com.example.myautofillservice.data.CredentialDatabase
import com.example.myautofillservice.compatibility.BankCompatibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MyAutofillService : AutofillService() {

    companion object {
        private const val TAG = "MyAutofillService"
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        Log.d(TAG, "onFillRequest called")
        
        // Obtener la estructura de la actividad
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            Log.w(TAG, "No structure available")
            callback.onSuccess(null)
            return
        }

        // Identificar la página usando el sistema híbrido
        val pageInfo = com.example.myautofillservice.utils.PageIdentifier.identifyPage(structure)
        val webSiteManager = WebSiteManager(applicationContext)
        
        Log.d(TAG, "🔍 Page identification results:")
        Log.d(TAG, "  URL: ${pageInfo.url}")
        Log.d(TAG, "  Domain: ${pageInfo.domain}")
        Log.d(TAG, "  Page ID: ${pageInfo.pageId}")
        Log.d(TAG, "  Title: ${pageInfo.title}")
        
        if (!pageInfo.domain.isNullOrBlank()) {
            Log.d(TAG, "✅ Real domain detected successfully: ${pageInfo.domain}")
        }
        
        // Analizar los campos de entrada
        val autofillFields = parseStructure(structure)
        
        if (autofillFields.isEmpty()) {
            Log.d(TAG, "No autofill fields found")
            callback.onSuccess(null)
            return
        }

        // Buscar credenciales usando el sistema híbrido
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Priorizar el dominio web real si está disponible
                val currentDomain = if (!pageInfo.domain.isNullOrBlank()) {
                    Log.d(TAG, "Using real web domain: ${pageInfo.domain}")
                    pageInfo.domain!!
                } else {
                    // Fallback al sistema híbrido si no hay dominio web
                    val siteIdentification = webSiteManager.identifyOrCreateWebSite(pageInfo)
                    when (siteIdentification) {
                        is WebSiteIdentification.ExactMatch -> {
                            Log.d(TAG, "Using exact match: ${siteIdentification.webSite.name}")
                            siteIdentification.webSite.pageId
                        }
                        is WebSiteIdentification.NewSite -> {
                            Log.d(TAG, "Using new site: ${siteIdentification.webSite.name}")
                            siteIdentification.webSite.pageId
                        }
                        is WebSiteIdentification.SimilarSites -> {
                            // Por ahora, usar el primer sitio similar
                            // TODO: Implementar UI para que el usuario elija
                            Log.d(TAG, "Found similar sites, using first: ${siteIdentification.similarSites.first().name}")
                            siteIdentification.similarSites.first().pageId
                        }
                    }
                }
                
                val database = CredentialDatabase.getDatabase(applicationContext)
                val credentials = database.credentialDao().getCredentialsForDomain(currentDomain)
                
                Log.d(TAG, "Searching for credentials with domain: '$currentDomain'")
                Log.d(TAG, "Found ${credentials.size} credentials for domain: $currentDomain")
                
                // Debug: Mostrar todas las credenciales en la base de datos
                val allCredentials = database.credentialDao().getAllCredentials()
                Log.d(TAG, "Total credentials in database: ${allCredentials.size}")
                allCredentials.forEach { cred ->
                    Log.d(TAG, "  - Domain: '${cred.domain}', User: ${cred.username}, Title: ${cred.title}")
                }
                
                // Crear respuesta con credenciales reales o datos de ejemplo
                val fillResponse = if (credentials.isNotEmpty()) {
                    Log.d(TAG, "Using real credentials for response")
                    createFillResponseWithCredentials(autofillFields, credentials)
                } else {
                    Log.d(TAG, "No credentials found, using fallback response")
                    createFallbackFillResponse(autofillFields)
                }
                
                callback.onSuccess(fillResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving credentials", e)
                callback.onSuccess(createFallbackFillResponse(autofillFields))
            }
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        Log.d(TAG, "onSaveRequest called - SAVE DIALOG TRIGGERED!")
        
        Log.d(TAG, "SaveRequest details:")
        Log.d(TAG, "  - FillContexts: ${request.fillContexts.size}")
        Log.d(TAG, "  - ClientState: ${request.clientState}")
        
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            Log.w(TAG, "No structure available for saving")
            callback.onSuccess()
            return
        }
        
        // Identificar la página usando el sistema híbrido (igual que en onFillRequest)
        val pageInfo = com.example.myautofillservice.utils.PageIdentifier.identifyPage(structure)
        val webSiteManager = WebSiteManager(applicationContext)
        
        Log.d(TAG, "Saving credentials for page:")
        Log.d(TAG, "  URL: ${pageInfo.url}")
        Log.d(TAG, "  Domain: ${pageInfo.domain}")
        Log.d(TAG, "  Page ID: ${pageInfo.pageId}")
        Log.d(TAG, "  Title: ${pageInfo.title}")
        
        // Extraer credenciales de los campos
        val credentials = extractCredentialsFromStructure(structure)
        
        if (credentials.isNotEmpty()) {
            // Guardar credenciales usando el sistema híbrido
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Priorizar el dominio web real si está disponible
                    val currentDomain = if (!pageInfo.domain.isNullOrBlank()) {
                        Log.d(TAG, "Saving to real web domain: ${pageInfo.domain}")
                        pageInfo.domain!!
                    } else {
                        // Fallback al sistema híbrido si no hay dominio web
                        val siteIdentification = webSiteManager.identifyOrCreateWebSite(pageInfo)
                        when (siteIdentification) {
                            is WebSiteIdentification.ExactMatch -> {
                                Log.d(TAG, "Saving to exact match: ${siteIdentification.webSite.name}")
                                siteIdentification.webSite.pageId
                            }
                            is WebSiteIdentification.NewSite -> {
                                Log.d(TAG, "Saving to new site: ${siteIdentification.webSite.name}")
                                siteIdentification.webSite.pageId
                            }
                            is WebSiteIdentification.SimilarSites -> {
                                // Por ahora, usar el primer sitio similar
                                Log.d(TAG, "Saving to similar site: ${siteIdentification.similarSites.first().name}")
                                siteIdentification.similarSites.first().pageId
                            }
                        }
                    }
                    
                    val database = CredentialDatabase.getDatabase(applicationContext)
                    val dao = database.credentialDao()
                    
                    credentials.forEach { (username, password) ->
                        // Verificar si ya existe una credencial para este dominio y usuario
                        val existingCredential = dao.findExistingCredential(currentDomain, username)
                        
                        if (existingCredential != null) {
                            // Solo actualizar si la contraseña es diferente
                            if (existingCredential.password != password) {
                                val updatedCredential = existingCredential.copy(
                                    password = password,
                                    url = pageInfo.url ?: existingCredential.url,
                                    lastUsed = System.currentTimeMillis()
                                )
                                dao.updateCredential(updatedCredential)
                                Log.d(TAG, "Updated credential with new password for $username at $currentDomain")
                            } else {
                                // Solo actualizar el último uso
                                dao.updateLastUsed(existingCredential.id)
                                Log.d(TAG, "Updated last used time for $username at $currentDomain")
                            }
                        } else {
                            // Crear nueva credencial
                            val webSiteName = if (!pageInfo.domain.isNullOrBlank()) {
                                // Si usamos dominio web real, usar el título de la página o el dominio
                                pageInfo.title.takeIf { !it.isNullOrBlank() } ?: pageInfo.domain
                            } else {
                                // Si usamos sistema híbrido, obtener el nombre del sitio web
                                val siteIdentification = webSiteManager.identifyOrCreateWebSite(pageInfo)
                                when (siteIdentification) {
                                    is WebSiteIdentification.ExactMatch -> siteIdentification.webSite.name
                                    is WebSiteIdentification.NewSite -> siteIdentification.webSite.name
                                    is WebSiteIdentification.SimilarSites -> siteIdentification.similarSites.first().name
                                }
                            }
                            
                            val newCredential = Credential(
                                domain = currentDomain,
                                url = pageInfo.url ?: "https://$currentDomain",
                                username = username,
                                password = password,
                                title = webSiteName
                            )
                            dao.insertCredential(newCredential)
                            Log.d(TAG, "Saved new credential for $username at $currentDomain")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving credentials", e)
                }
            }
        }
        
        callback.onSuccess()
    }

    private fun parseStructure(structure: AssistStructure): List<AutofillField> {
        val fields = mutableListOf<AutofillField>()
        
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            parseNode(windowNode.rootViewNode, fields)
        }
        
        // NUEVO: Aplicar mejoras de compatibilidad bancaria
        val pageInfo = com.example.myautofillservice.utils.PageIdentifier.identifyPage(structure)
        if (!pageInfo.domain.isNullOrBlank() && BankCompatibilityManager.isChileanBank(pageInfo.domain)) {
            Log.d(TAG, "🏦 Applying Chilean bank compatibility for: ${pageInfo.domain}")
            return BankCompatibilityManager.enhanceFieldDetection(pageInfo.domain, fields)
        }
        
        return fields
    }

    private fun parseNode(node: AssistStructure.ViewNode, fields: MutableList<AutofillField>) {
        val autofillId = node.autofillId
        
        // Log información del nodo para debugging
        Log.d(TAG, "Analyzing node: ${node.className}")
        Log.d(TAG, "  AutofillId: $autofillId")
        Log.d(TAG, "  Text: ${node.text}")
        Log.d(TAG, "  Hints: ${node.autofillHints?.joinToString()}")
        Log.d(TAG, "  InputType: ${node.inputType}")
        Log.d(TAG, "  HtmlInfo: ${node.htmlInfo}")
        Log.d(TAG, "  IdEntry: ${node.idEntry}")
        
        if (autofillId != null && isAutofillableField(node)) {
            val hint = detectFieldType(node)
            Log.d(TAG, "Found autofillable field with detected hint: $hint")
            
            fields.add(AutofillField(
                autofillId = autofillId,
                hint = hint,
                text = node.text?.toString()
            ))
        }
        
        // Recursivamente analizar nodos hijos
        for (i in 0 until node.childCount) {
            parseNode(node.getChildAt(i), fields)
        }
    }
    
    private fun isAutofillableField(node: AssistStructure.ViewNode): Boolean {
        // Verificar si tiene hints explícitos
        if (!node.autofillHints.isNullOrEmpty()) {
            return true
        }
        
        // Verificar si es un campo de entrada de texto
        val className = node.className?.toString()?.lowercase(java.util.Locale.getDefault())
        if (className?.contains("edittext") == true || 
            className?.contains("textinputedittext") == true) {
            return true
        }
        
        // Para campos web, verificar información HTML
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val tag = htmlInfo.tag?.lowercase(java.util.Locale.getDefault())
            val type = htmlInfo.attributes?.find { it.first == "type" }?.second?.lowercase(java.util.Locale.getDefault())
            
            // Campos de entrada HTML
            if (tag == "input" && (type == "text" || type == "email" || type == "password" || type == "tel")) {
                return true
            }
        }
        
        // Verificar por inputType
        val inputType = node.inputType
        if (inputType != 0) {
            // Tipos de entrada de texto comunes
            val textInputTypes = listOf(
                0x00000001, // TYPE_CLASS_TEXT
                0x00000021, // TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                0x00000081, // TYPE_TEXT_VARIATION_PASSWORD
                0x00000003  // TYPE_CLASS_NUMBER
            )
            if (textInputTypes.any { (inputType and it) != 0 }) {
                return true
            }
        }
        
        return false
    }
    
    private fun detectFieldType(node: AssistStructure.ViewNode): String {
        // Primero verificar hints explícitos
        val hints = node.autofillHints
        if (!hints.isNullOrEmpty()) {
            return hints[0]
        }
        
        // Detectar por información HTML
        val htmlInfo = node.htmlInfo
        if (htmlInfo != null) {
            val type = htmlInfo.attributes?.find { it.first == "type" }?.second?.lowercase(java.util.Locale.getDefault())
            val name = htmlInfo.attributes?.find { it.first == "name" }?.second?.lowercase(java.util.Locale.getDefault())
            val id = htmlInfo.attributes?.find { it.first == "id" }?.second?.lowercase(java.util.Locale.getDefault())
            
            // Detectar por tipo HTML
            when (type) {
                "email" -> return "emailAddress"
                "password" -> return "password"
                "tel" -> return "phone"
            }
            
            // Detectar por nombre o ID
            listOf(name, id).forEach { attr ->
                attr?.let {
                    when {
                        it.contains("email") || it.contains("mail") -> return "emailAddress"
                        it.contains("user") || it.contains("login") -> return "username"
                        it.contains("pass") -> return "password"
                        it.contains("phone") || it.contains("tel") -> return "phone"
                        it.contains("name") && !it.contains("user") -> return "name"
                    }
                }
            }
        }
        
        // Detectar por inputType
        val inputType = node.inputType
        when {
            (inputType and 0x00000021) != 0 -> return "emailAddress" // EMAIL_ADDRESS
            (inputType and 0x00000081) != 0 -> return "password"    // PASSWORD
            (inputType and 0x00000003) != 0 -> return "phone"       // NUMBER (asumimos teléfono)
        }
        
        // Detectar por texto del campo o placeholder
        val text = node.text?.toString()?.lowercase(java.util.Locale.getDefault())
        val hint = node.hint?.lowercase(java.util.Locale.getDefault())
        
        listOf(text, hint).forEach { str ->
            str?.let {
                when {
                    it.contains("email") || it.contains("correo") -> return "emailAddress"
                    it.contains("user") || it.contains("usuario") -> return "username"
                    it.contains("pass") || it.contains("contraseña") -> return "password"
                    it.contains("phone") || it.contains("teléfono") || it.contains("telefono") -> return "phone"
                    it.contains("name") || it.contains("nombre") -> return "name"
                }
            }
        }
        
        // Por defecto, asumir que es un campo de texto genérico
        return "username"
    }

    // NUEVO: Función mejorada para crear datasets específicos por tipo de sitio
    private fun createSampleDatasets(fields: List<AutofillField>): List<Dataset> {
        val datasets = mutableListOf<Dataset>()
        
        if (fields.isEmpty()) {
            Log.w(TAG, "No fields available for sample datasets")
            return datasets
        }
        
        // Detectar si hay campos de banco (hints "off" o "new-password")
        val hasBankFields = fields.any { it.hint == "off" || it.hint == "new-password" }
        
        // Dataset 1
        val dataset1Builder = Dataset.Builder()
        val presentation1 = RemoteViews(packageName, android.R.layout.simple_list_item_1)
        
        var hasValidFields1 = false
        fields.forEach { field ->
            val (title, value) = if (hasBankFields) {
                "RUT de Ejemplo 1" to when (field.hint) {
                    "username", "emailAddress" -> "12345678-9"
                    "password" -> "MiClave123"
                    "off" -> "12345678-9"
                    "new-password" -> "MiClave123"
                    else -> ""
                }
            } else {
                "Usuario de prueba 1" to when (field.hint) {
                    "username", "emailAddress" -> "usuario1@ejemplo.com"
                    "password" -> "password123"
                    "name" -> "Juan Pérez"
                    "phone" -> "+1234567890"
                    else -> ""
                }
            }
            
            if (value.isNotEmpty()) {
                presentation1.setTextViewText(android.R.id.text1, title)
                dataset1Builder.setValue(
                    field.autofillId,
                    AutofillValue.forText(value),
                    presentation1
                )
                hasValidFields1 = true
            }
        }
        
        if (hasValidFields1) {
            datasets.add(dataset1Builder.build())
            Log.d(TAG, if (hasBankFields) "Added Chilean bank dataset 1" else "Added standard dataset 1")
        }
        
        // Dataset 2
        val dataset2Builder = Dataset.Builder()
        val presentation2 = RemoteViews(packageName, android.R.layout.simple_list_item_1)
        
        var hasValidFields2 = false
        fields.forEach { field ->
            val (title, value) = if (hasBankFields) {
                "RUT de Ejemplo 2" to when (field.hint) {
                    "username", "emailAddress" -> "98765432-1"
                    "password" -> "ClaveSegura456"
                    "off" -> "98765432-1"
                    "new-password" -> "ClaveSegura456"
                    else -> ""
                }
            } else {
                "Usuario de prueba 2" to when (field.hint) {
                    "username", "emailAddress" -> "usuario2@ejemplo.com"
                    "password" -> "mypassword456"
                    "name" -> "María García"
                    "phone" -> "+0987654321"
                    else -> ""
                }
            }
            
            if (value.isNotEmpty()) {
                presentation2.setTextViewText(android.R.id.text1, title)
                dataset2Builder.setValue(
                    field.autofillId,
                    AutofillValue.forText(value),
                    presentation2
                )
                hasValidFields2 = true
            }
        }
        
        if (hasValidFields2) {
            datasets.add(dataset2Builder.build())
            Log.d(TAG, if (hasBankFields) "Added Chilean bank dataset 2" else "Added standard dataset 2")
        }
        
        return datasets
    }

    private fun createFillResponseWithCredentials(
        fields: List<AutofillField>, 
        credentials: List<Credential>
    ): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // Crear un dataset para cada credencial guardada
        credentials.forEach { credential ->
            val datasetBuilder = Dataset.Builder()
            val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
            presentation.setTextViewText(android.R.id.text1, "${credential.username} - ${credential.title}")
            
            var hasValidFields = false
            
            fields.forEach { field ->
                val value = when (field.hint) {
                    "username", "emailAddress" -> credential.username
                    "password" -> credential.password
                    else -> ""
                }
                
                if (value.isNotEmpty()) {
                    datasetBuilder.setValue(
                        field.autofillId,
                        AutofillValue.forText(value),
                        presentation
                    )
                    hasValidFields = true
                }
            }
            
            // Solo agregar el dataset si tiene al menos un campo válido
            if (hasValidFields) {
                responseBuilder.addDataset(datasetBuilder.build())
                Log.d(TAG, "Added dataset for credential: ${credential.username}")
            } else {
                Log.w(TAG, "Skipped dataset for credential ${credential.username} - no valid fields")
            }
        }
        
        // SIEMPRE configurar SaveInfo para que ofrezca guardar credenciales
        configureSaveInfo(responseBuilder, fields)
        
        return responseBuilder.build()
    }
    
    private fun createFallbackFillResponse(fields: List<AutofillField>): FillResponse {
        val responseBuilder = FillResponse.Builder()
        
        // Crear datasets con datos de ejemplo cuando no hay credenciales guardadas
        val datasets = createSampleDatasets(fields)
        datasets.forEach { dataset ->
            responseBuilder.addDataset(dataset)
        }
        
        // SIEMPRE configurar SaveInfo para que ofrezca guardar credenciales
        configureSaveInfo(responseBuilder, fields)
        
        // Si no hay datasets ni SaveInfo, crear un dataset mínimo para evitar FillResponse vacío
        if (datasets.isEmpty() && fields.isNotEmpty()) {
            Log.w(TAG, "Creating minimal dataset to avoid empty FillResponse")
            val minimalDataset = createMinimalDataset(fields)
            if (minimalDataset != null) {
                responseBuilder.addDataset(minimalDataset)
            }
        }
        
        return responseBuilder.build()
    }
    
    private fun configureSaveInfo(responseBuilder: FillResponse.Builder, fields: List<AutofillField>) {
        Log.d(TAG, "=== Configuring SaveInfo ===")
        Log.d(TAG, "Total fields available: ${fields.size}")
        
        fields.forEachIndexed { index, field ->
            Log.d(TAG, "Field $index: hint='${field.hint}', text='${field.text}', id=${field.autofillId}")
        }
        
        // Identificar campos de usuario y contraseña
        val usernameFields = fields.filter { 
            it.hint == "username" || it.hint == "emailAddress" 
        }.map { it.autofillId }
        
        val passwordFields = fields.filter { 
            it.hint == "password" 
        }.map { it.autofillId }
        
        Log.d(TAG, "Username fields found: ${usernameFields.size}")
        Log.d(TAG, "Password fields found: ${passwordFields.size}")
        
        // Configurar SaveInfo con todos los campos relevantes
        val allRelevantFields = if (passwordFields.isNotEmpty()) {
            Log.d(TAG, "Using password fields for SaveInfo")
            passwordFields.toTypedArray()
        } else if (usernameFields.isNotEmpty()) {
            Log.d(TAG, "Using username fields for SaveInfo")
            usernameFields.toTypedArray()
        } else {
            // Fallback: usar todos los campos detectados
            Log.d(TAG, "No specific username/password fields found, using all ${fields.size} fields")
            fields.map { it.autofillId }.toTypedArray()
        }
        
        if (allRelevantFields.isNotEmpty()) {
            val saveInfoBuilder = SaveInfo.Builder(
                SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                allRelevantFields
            )
            
            // Configurar para que SIEMPRE pregunte si quiere guardar
            saveInfoBuilder.setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
            
            responseBuilder.setSaveInfo(saveInfoBuilder.build())
            
            Log.d(TAG, "✅ SaveInfo configured successfully!")
            Log.d(TAG, "  - Fields: ${allRelevantFields.size}")
            Log.d(TAG, "  - Data type: ${SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD}")
            Log.d(TAG, "  - Flags: ${SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE}")
        } else {
            Log.w(TAG, "❌ No fields available for SaveInfo configuration")
        }
        
        Log.d(TAG, "=== SaveInfo configuration complete ===")
    }
    
    // NUEVO: Función mejorada para extraer credenciales con soporte para bancos
    private fun extractCredentialsFromStructure(structure: AssistStructure): List<Pair<String, String>> {
        val credentials = mutableListOf<Pair<String, String>>()
        val fieldValues = mutableMapOf<String, String>()
        
        // Extraer valores de todos los campos
        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            extractFieldValues(windowNode.rootViewNode, fieldValues)
        }
        
        Log.d(TAG, "Extracted field values: $fieldValues")
        
        // NUEVO: Detectar si es un banco chileno para manejo especial
        val pageInfo = com.example.myautofillservice.utils.PageIdentifier.identifyPage(structure)
        val isChileanBank = !pageInfo.domain.isNullOrBlank() && BankCompatibilityManager.isChileanBank(pageInfo.domain)
        
        if (isChileanBank) {
            Log.d(TAG, "🏦 Processing Chilean bank credentials extraction")
            
            // Para bancos chilenos: buscar campos específicos
            val rutValue = fieldValues["off"] ?: fieldValues["username"] ?: ""
            val claveValue = fieldValues["new-password"] ?: fieldValues["password"] ?: ""
            
            if (rutValue.isNotBlank() && claveValue.isNotBlank()) {
                Log.d(TAG, "🏦 Found Chilean bank credentials: RUT=$rutValue, Clave=***")
                credentials.add(Pair(rutValue, claveValue))
            }
        } else {
            Log.d(TAG, "🌐 Processing standard credentials extraction")
            
            // Para sitios normales: buscar combinaciones estándar
            val userFields = fieldValues.filter { (key, _) ->
                key.contains("username") || key.contains("email") || key.contains("user") || key.contains("login")
            }
            
            val passwordFields = fieldValues.filter { (key, _) ->
                key.contains("password") || key.contains("pass")
            }
            
            // Crear pares de credenciales
            userFields.forEach { (_, username) ->
                passwordFields.forEach { (_, password) ->
                    if (username.isNotBlank() && password.isNotBlank()) {
                        credentials.add(Pair(username, password))
                    }
                }
            }
        }
        
        return credentials.distinctBy { it.first } // Evitar duplicados por usuario
    }
    
    private fun extractFieldValues(node: AssistStructure.ViewNode, fieldValues: MutableMap<String, String>) {
        val autofillId = node.autofillId
        
        if (autofillId != null && isAutofillableField(node)) {
            val fieldType = detectFieldType(node)
            val value = node.autofillValue?.textValue?.toString() ?: ""
            
            if (value.isNotBlank()) {
                fieldValues[fieldType] = value
                Log.d(TAG, "Extracted value for $fieldType: $value")
            } else {
                Log.d(TAG, "No value found for field $fieldType")
            }
        }
        
        // Recursivamente extraer de nodos hijos
        for (i in 0 until node.childCount) {
            extractFieldValues(node.getChildAt(i), fieldValues)
        }
    }

    private fun createMinimalDataset(fields: List<AutofillField>): Dataset? {
        if (fields.isEmpty()) return null
        
        val datasetBuilder = Dataset.Builder()
        val presentation = RemoteViews(packageName, android.R.layout.simple_list_item_1)
        presentation.setTextViewText(android.R.id.text1, "Datos de ejemplo")
        
        var hasValidFields = false
        
        // Usar el primer campo disponible con un valor genérico
        fields.take(1).forEach { field ->
            val value = when (field.hint) {
                "username", "emailAddress" -> "ejemplo@correo.com"
                "password" -> "ejemplo123"
                else -> "ejemplo"
            }
            
            datasetBuilder.setValue(
                field.autofillId,
                AutofillValue.forText(value),
                presentation
            )
            hasValidFields = true
        }
        
        return if (hasValidFields) {
            Log.d(TAG, "Created minimal dataset")
            datasetBuilder.build()
        } else {
            null
        }
    }

    data class AutofillField(
        val autofillId: AutofillId,
        val hint: String,
        val text: String?
    )
}