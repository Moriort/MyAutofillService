package com.example.myautofillservice.compatibility

import android.app.assist.AssistStructure
import android.util.Log
import com.example.myautofillservice.service.MyAutofillService

/**
 * Gestor de compatibilidad específica para bancos chilenos y otros sitios problemáticos
 */
object BankCompatibilityManager {
    
    private const val TAG = "BankCompatibility"
    
    data class BankConfig(
        val domain: String,
        val name: String,
        val usernameFieldNames: List<String>,
        val passwordFieldNames: List<String>,
        val usernameHints: List<String>,
        val passwordHints: List<String>,
        val hasOverlay: Boolean = false,
        val requiresSpecialHandling: Boolean = false
    )
    
    private val CHILEAN_BANKS = mapOf(
        "bancoestado.cl" to BankConfig(
            domain = "bancoestado.cl",
            name = "BancoEstado",
            usernameFieldNames = listOf("rut", "usuario", "username"),
            passwordFieldNames = listOf("pass", "password", "clave"),
            usernameHints = listOf("off", "username", "rut"),
            passwordHints = listOf("new-password", "current-password", "password"),
            hasOverlay = true,
            requiresSpecialHandling = true
        ),
        "santander.cl" to BankConfig(
            domain = "santander.cl",
            name = "Banco Santander",
            usernameFieldNames = listOf("rut", "usuario", "username"),
            passwordFieldNames = listOf("pass", "password", "clave"),
            usernameHints = listOf("off", "username", "rut"),
            passwordHints = listOf("new-password", "current-password", "password"),
            hasOverlay = true,
            requiresSpecialHandling = true
        ),
        "bci.cl" to BankConfig(
            domain = "bci.cl",
            name = "Banco BCI",
            usernameFieldNames = listOf("rut", "usuario", "username"),
            passwordFieldNames = listOf("pass", "password", "clave"),
            usernameHints = listOf("off", "username", "rut"),
            passwordHints = listOf("new-password", "current-password", "password"),
            hasOverlay = true,
            requiresSpecialHandling = true
        ),
        "bancodechile.cl" to BankConfig(
            domain = "bancodechile.cl",
            name = "Banco de Chile",
            usernameFieldNames = listOf("rut", "usuario", "username"),
            passwordFieldNames = listOf("pass", "password", "clave"),
            usernameHints = listOf("off", "username", "rut"),
            passwordHints = listOf("new-password", "current-password", "password"),
            hasOverlay = true,
            requiresSpecialHandling = true
        ),
        "corpbanca.cl" to BankConfig(
            domain = "corpbanca.cl",
            name = "CorpBanca",
            usernameFieldNames = listOf("rut", "usuario", "username"),
            passwordFieldNames = listOf("pass", "password", "clave"),
            usernameHints = listOf("off", "username", "rut"),
            passwordHints = listOf("new-password", "current-password", "password"),
            hasOverlay = true,
            requiresSpecialHandling = true
        ),
        "bancofalabella.cl" to BankConfig(
            domain = "bancofalabella.cl",
            name = "Banco Falabella",
            usernameFieldNames = listOf("rut", "usuario", "username"),
            passwordFieldNames = listOf("pass", "password", "clave"),
            usernameHints = listOf("off", "username", "rut"),
            passwordHints = listOf("new-password", "current-password", "password"),
            hasOverlay = true,
            requiresSpecialHandling = true
        )
    )
    
    fun getBankConfig(domain: String): BankConfig? {
        val cleanDomain = domain.removePrefix("www.").lowercase()
        return CHILEAN_BANKS[cleanDomain]
    }
    
    fun isChileanBank(domain: String): Boolean {
        return getBankConfig(domain) != null
    }
    
    fun enhanceFieldDetection(
        domain: String,
        fields: List<MyAutofillService.AutofillField>
    ): List<MyAutofillService.AutofillField> {
        val bankConfig = getBankConfig(domain) ?: return fields
        
        Log.d(TAG, "🏦 Applying ${bankConfig.name} compatibility enhancements")
        Log.d(TAG, "Original fields: ${fields.size}")
        
        val enhancedFields = fields.map { field ->
            val newHint = when {
                // Detectar campos de RUT/Usuario
                bankConfig.usernameHints.contains(field.hint) ||
                field.text?.contains("rut", ignoreCase = true) == true ||
                isRutField(field) -> {
                    Log.d(TAG, "🆔 Enhanced RUT field: ${field.hint} -> username")
                    "username"
                }
                
                // Detectar campos de contraseña
                bankConfig.passwordHints.contains(field.hint) ||
                field.hint.contains("password") ||
                isPasswordField(field) -> {
                    Log.d(TAG, "🔒 Enhanced password field: ${field.hint} -> password")
                    "password"
                }
                
                else -> field.hint
            }
            
            field.copy(hint = newHint)
        }
        
        Log.d(TAG, "Enhanced fields: ${enhancedFields.size}")
        enhancedFields.forEach { field ->
            Log.d(TAG, "  - ${field.hint}: ${field.autofillId}")
        }
        
        return enhancedFields
    }
    
    private fun isRutField(field: MyAutofillService.AutofillField): Boolean {
        // Detectar campos de RUT por patrones específicos
        val indicators = listOf("rut", "usuario", "user", "login")
        
        return indicators.any { indicator ->
            field.text?.contains(indicator, ignoreCase = true) == true ||
            field.autofillId.toString().contains(indicator, ignoreCase = true)
        }
    }
    
    private fun isPasswordField(field: MyAutofillService.AutofillField): Boolean {
        // Detectar campos de contraseña por patrones específicos
        val indicators = listOf("pass", "clave", "password", "pwd")
        
        return indicators.any { indicator ->
            field.hint.contains(indicator, ignoreCase = true) ||
            field.autofillId.toString().contains(indicator, ignoreCase = true)
        }
    }
    
    fun createBankSpecificDatasets(
        bankConfig: BankConfig,
        fields: List<MyAutofillService.AutofillField>
    ): List<BankDataset> {
        Log.d(TAG, "🏦 Creating ${bankConfig.name} specific datasets")
        
        return listOf(
            BankDataset(
                title = "RUT de Ejemplo - ${bankConfig.name}",
                username = "12345678-9",
                password = "MiClave123"
            ),
            BankDataset(
                title = "Usuario Demo - ${bankConfig.name}",
                username = "98765432-1",
                password = "ClaveSegura456"
            )
        )
    }
    
    data class BankDataset(
        val title: String,
        val username: String,
        val password: String
    )
    
    fun shouldUseSpecialHandling(domain: String): Boolean {
        val bankConfig = getBankConfig(domain)
        return bankConfig?.requiresSpecialHandling == true
    }
    
    fun hasOverlayIssues(domain: String): Boolean {
        val bankConfig = getBankConfig(domain)
        return bankConfig?.hasOverlay == true
    }
    
    fun getDisplayName(domain: String): String {
        val bankConfig = getBankConfig(domain)
        return bankConfig?.name ?: domain
    }
}

// Extension function para AutofillField
private fun MyAutofillService.AutofillField.copy(
    autofillId: android.view.autofill.AutofillId = this.autofillId,
    hint: String = this.hint,
    text: String? = this.text
): MyAutofillService.AutofillField {
    return MyAutofillService.AutofillField(autofillId, hint, text)
}