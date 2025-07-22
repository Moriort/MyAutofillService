package com.example.myautofillservice

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myautofillservice.data.Credential
import com.example.myautofillservice.data.CredentialDatabase
import com.example.myautofillservice.data.WebSite
import com.example.myautofillservice.ui.theme.MyAutofillServiceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAutofillServiceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AutofillServiceScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun AutofillServiceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var credentials by remember { mutableStateOf<List<Credential>>(emptyList()) }
    var webSites by remember { mutableStateOf<List<WebSite>>(emptyList()) }
    var showCredentials by remember { mutableStateOf(false) }
    var showWebSites by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Función para cargar credenciales
    fun loadCredentials() {
        scope.launch {
            try {
                val database = CredentialDatabase.getDatabase(context)
                credentials = database.credentialDao().getAllCredentials()
            } catch (e: Exception) {
                // Manejar error
            }
        }
    }
    
    // Función para cargar sitios web
    fun loadWebSites() {
        scope.launch {
            try {
                val database = CredentialDatabase.getDatabase(context)
                webSites = database.webSiteDao().getAllWebSites()
            } catch (e: Exception) {
                // Manejar error
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Gestor de Credenciales",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Botones de configuración
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    intent.data = android.net.Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Configurar", fontSize = 11.sp)
            }
            
            Button(
                onClick = {
                    showCredentials = !showCredentials
                    showWebSites = false
                    if (showCredentials) loadCredentials()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (showCredentials) "Ocultar" else "Credenciales", fontSize = 11.sp)
            }
            
            Button(
                onClick = {
                    showWebSites = !showWebSites
                    showCredentials = false
                    if (showWebSites) loadWebSites()
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (showWebSites) "Ocultar" else "Sitios Web", fontSize = 11.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (showCredentials) {
            // Lista de credenciales
            Text(
                text = "Credenciales Guardadas (${credentials.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (credentials.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay credenciales guardadas.\nInicia sesión en sitios web para que se guarden automáticamente.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(credentials) { credential ->
                        CredentialCard(
                            credential = credential,
                            onDelete = {
                                scope.launch {
                                    try {
                                        val database = CredentialDatabase.getDatabase(context)
                                        database.credentialDao().deleteCredential(credential)
                                        loadCredentials() // Recargar lista
                                    } catch (e: Exception) {
                                        // Manejar error
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else if (showWebSites) {
            // Lista de sitios web
            Text(
                text = "Sitios Web Identificados (${webSites.size})",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (webSites.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No hay sitios web identificados.\nUsa el autocompletado en diferentes sitios para que aparezcan aquí.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(webSites) { webSite ->
                        WebSiteCard(
                            webSite = webSite,
                            onDelete = {
                                scope.launch {
                                    try {
                                        val database = CredentialDatabase.getDatabase(context)
                                        database.webSiteDao().deleteWebSite(webSite)
                                        loadWebSites() // Recargar lista
                                    } catch (e: Exception) {
                                        // Manejar error
                                    }
                                }
                            },
                            onRename = { newName ->
                                scope.launch {
                                    try {
                                        val database = CredentialDatabase.getDatabase(context)
                                        database.webSiteDao().updateWebSiteName(webSite.id, newName)
                                        loadWebSites() // Recargar lista
                                    } catch (e: Exception) {
                                        // Manejar error
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            // Instrucciones
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Instrucciones:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "1. Toca 'Configurar Servicio' y activa este servicio\n" +
                                "2. Navega a sitios web e inicia sesión\n" +
                                "3. Las credenciales se guardarán automáticamente\n" +
                                "4. En futuras visitas, verás sugerencias de autocompletado\n" +
                                "5. Usa 'Ver Credenciales' para gestionar las guardadas"
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Estado del Servicio:",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Text(
                        text = "• El servicio detecta automáticamente campos de login\n" +
                                "• Funciona en navegadores web y apps\n" +
                                "• Guarda credenciales cuando inicias sesión\n" +
                                "• Proporciona sugerencias basadas en el dominio del sitio"
                    )
                }
            }
        }
    }
}

@Composable
fun CredentialCard(
    credential: Credential,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = credential.title ?: credential.domain,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = credential.domain,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Usuario: ${credential.username}",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Contraseña: ${"•".repeat(credential.password.length)}",
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Último uso: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(credential.lastUsed))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun WebSiteCard(
    webSite: WebSite,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(webSite.name) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = webSite.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (webSite.isUserNamed) {
                            Text(
                                text = "👤",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    
                    webSite.domain?.let { domain ->
                        Text(
                            text = "Dominio: $domain",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    webSite.url?.let { url ->
                        Text(
                            text = "URL: ${url.take(40)}${if (url.length > 40) "..." else ""}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = "ID: ${webSite.pageId.take(12)}...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    
                    Text(
                        text = "Último uso: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(webSite.lastUsed))}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Column {
                    Button(
                        onClick = { showRenameDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text("Renombrar", fontSize = 10.sp)
                    }
                    
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Eliminar", fontSize = 10.sp)
                    }
                }
            }
        }
    }
    
    // Diálogo para renombrar
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Renombrar Sitio Web") },
            text = {
                Column {
                    Text("Ingresa un nuevo nombre para este sitio:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nombre del sitio") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onRename(newName.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showRenameDialog = false
                        newName = webSite.name
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AutofillServiceScreenPreview() {
    MyAutofillServiceTheme {
        AutofillServiceScreen()
    }
}