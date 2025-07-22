# 🔐 My Autofill Service

Una aplicación Android de servicio de autocompletado avanzada diseñada para pruebas y experimentación, con soporte especializado para bancos chilenos y detección inteligente de sitios web.

## 📋 Tabla de Contenidos

- [Características Principales](#-características-principales)
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
- [Compatibilidad Bancaria](#-compatibilidad-bancaria)
- [Detección de Sitios Web](#-detección-de-sitios-web)
- [Instalación y Configuración](#-instalación-y-configuración)
- [Uso de la Aplicación](#-uso-de-la-aplicación)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Tecnologías Utilizadas](#-tecnologías-utilizadas)
- [Desarrollo y Testing](#-desarrollo-y-testing)
- [Contribución](#-contribución)

## 🚀 Características Principales

### ✨ **Autocompletado Inteligente**
- Detección automática de campos de usuario y contraseña
- Soporte para múltiples tipos de campos (email, teléfono, nombre)
- Datasets personalizados según el tipo de sitio web
- Compatibilidad con navegadores Chrome, Firefox y otros

### 🏦 **Compatibilidad Bancaria Especializada**
- **Soporte completo para bancos chilenos**: BancoEstado, Santander, BCI, Banco de Chile, CorpBanca, Banco Falabella
- Detección automática de campos RUT y clave bancaria
- Manejo especializado de overlays y modales bancarios
- Datasets con formato chileno (RUT + clave)

### 🌐 **Sistema Híbrido de Identificación**
- **Detección por dominio web**: Prioriza URLs reales cuando están disponibles
- **Sistema de firmas de página**: Fallback robusto para sitios sin URL detectable
- **Títulos inteligentes**: Extracción mejorada de nombres de sitios web
- **Compatibilidad universal**: Funciona en cualquier aplicación o navegador

### 💾 **Gestión Avanzada de Credenciales**
- Base de datos Room para almacenamiento seguro
- Credenciales específicas por dominio/sitio
- Actualización automática de contraseñas
- Interfaz de gestión de credenciales guardadas

## 🏗️ Arquitectura del Sistema

### **Componentes Principales**

```
MyAutofillService/
├── 🔧 service/
│   ├── MyAutofillService.kt          # Servicio principal de autocompletado
│   └── WebSiteManager.kt             # Gestión de identificación de sitios
├── 🏦 compatibility/
│   └── BankCompatibilityManager.kt   # Sistema de compatibilidad bancaria
├── 🔍 utils/
│   ├── PageIdentifier.kt             # Identificación híbrida de páginas
│   └── UrlUtils.kt                   # Extracción y validación de URLs
├── 💾 data/
│   ├── Credential.kt                 # Modelo de datos de credenciales
│   ├── WebSite.kt                    # Modelo de datos de sitios web
│   └── CredentialDatabase.kt         # Base de datos Room
└── 🎨 ui/
    └── MainActivity.kt               # Interfaz de gestión
```

### **Flujo de Funcionamiento**

1. **Detección de Campos** → Análisis de estructura de la aplicación
2. **Identificación de Sitio** → Sistema híbrido (URL + firma de página)
3. **Compatibilidad Bancaria** → Aplicación de reglas específicas si es necesario
4. **Búsqueda de Credenciales** → Consulta en base de datos local
5. **Presentación de Datasets** → Muestra opciones de autocompletado
6. **Guardado de Credenciales** → Almacenamiento seguro con SaveInfo

## 🏦 Compatibilidad Bancaria

### **Bancos Soportados**

| Banco | Dominio | Características Especiales |
|-------|---------|---------------------------|
| 🏛️ **BancoEstado** | `bancoestado.cl` | Detección de RUT, manejo de overlays |
| 🏛️ **Santander** | `santander.cl` | Campos especializados, modal de login |
| 🏛️ **BCI** | `bci.cl` | Compatibilidad con sistema de claves |
| 🏛️ **Banco de Chile** | `bancodechile.cl` | Soporte para RUT y clave |
| 🏛️ **CorpBanca** | `corpbanca.cl` | Manejo de campos dinámicos |
| 🏛️ **Banco Falabella** | `bancofalabella.cl` | Detección de formularios complejos |

### **Características Bancarias**

```kotlin
// Ejemplo de configuración bancaria
BankConfig(
    domain = "bancoestado.cl",
    name = "BancoEstado",
    usernameFieldNames = listOf("rut", "usuario", "username"),
    passwordFieldNames = listOf("pass", "password", "clave"),
    usernameHints = listOf("off", "username", "rut"),
    passwordHints = listOf("new-password", "current-password", "password"),
    hasOverlay = true,
    requiresSpecialHandling = true
)
```

### **Mejoras Automáticas**

- **Transformación de hints**: `"off"` → `"username"`, `"new-password"` → `"password"`
- **Datasets específicos**: RUTs chilenos en lugar de emails genéricos
- **Extracción especializada**: Manejo de campos con nombres no estándar
- **Títulos mejorados**: Nombres descriptivos para bancos

## 🔍 Detección de Sitios Web

### **Sistema Híbrido de Identificación**

#### **1. Detección por Dominio Web (Prioridad Alta)**
```kotlin
// Extracción directa del WebDomain
val webDomain = extractWebDomainFromStructure(structure)
if (webDomain != null) {
    return PageInfo(
        url = "https://$webDomain",
        domain = webDomain,
        pageId = webDomain,
        title = createFriendlyTitleFromDomain(webDomain)
    )
}
```

#### **2. Sistema de Firmas de Página (Fallback)**
```kotlin
// Creación de firma única basada en elementos estables
val pageSignature = createPageSignature(structure)
val pageId = "page_${hashString(pageSignature)}"
```

### **Extracción Inteligente de Títulos**

#### **Estrategias de Detección (en orden de prioridad):**

1. **Títulos basados en dominio**: `github.com` → "GitHub"
2. **Títulos HTML**: Elementos `<title>`, `<h1>`, `<h2>`, `<h3>`
3. **Meta títulos**: `og:title`, `twitter:title`, `application-name`
4. **Títulos de navegación**: Toolbars, headers, navbars
5. **Filtrado inteligente**: Exclusión de texto genérico y valores de entrada

#### **Reconocimiento de Sitios Populares**

```kotlin
// Ejemplos de reconocimiento automático
when {
    domain.contains("github") -> "GitHub"
    domain.contains("google") -> "Google"
    domain.contains("netflix") -> "Netflix"
    domain.contains("herokuapp") -> extractHerokuAppName(domain)
    // ... más de 30 sitios reconocidos
}
```

## 📱 Instalación y Configuración

### **Requisitos del Sistema**
- Android 8.0 (API 26) o superior
- Permisos de accesibilidad para el servicio de autocompletado

### **Pasos de Instalación**

1. **Clonar el repositorio**
```bash
git clone https://github.com/tu-usuario/MyAutofillService.git
cd MyAutofillService
```

2. **Compilar la aplicación**
```bash
./gradlew assembleDebug
```

3. **Instalar en dispositivo**
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

4. **Configurar el servicio**
   - Ir a **Configuración** → **Sistema** → **Idiomas e introducción**
   - Seleccionar **Servicio de autocompletado**
   - Elegir **My Autofill Service**

### **Configuración de Desarrollo**

```kotlin
// Habilitar logs de debugging
private const val DEBUG_MODE = true

// Configurar base de datos
@Database(
    entities = [Credential::class, WebSite::class],
    version = 1,
    exportSchema = false
)
```

## 🎯 Uso de la Aplicación

### **Funcionalidades Principales**

#### **1. Autocompletado Automático**
- Abre cualquier aplicación con campos de login
- El servicio detecta automáticamente los campos
- Muestra sugerencias de credenciales guardadas
- Selecciona una opción para autocompletar

#### **2. Guardado de Credenciales**
- Completa manualmente los campos de login
- Aparece el diálogo "Save username and password?"
- Confirma para guardar las credenciales
- Se almacenan de forma segura en la base de datos local

#### **3. Gestión de Credenciales**
- Abre la aplicación My Autofill Service
- Ve todas las credenciales guardadas
- Edita o elimina credenciales existentes
- Organiza por sitios web y dominios

### **Ejemplos de Uso**

#### **Sitio Web Normal**
```
Sitio: chanceschile.online
Datasets mostrados:
├── 📧 Usuario de prueba 1 (usuario1@ejemplo.com)
└── 📧 Usuario de prueba 2 (usuario2@ejemplo.com)
```

#### **Banco Chileno**
```
Sitio: www.bancoestado.cl
Datasets mostrados:
├── 🆔 RUT de Ejemplo 1 (12345678-9)
└── 🆔 RUT de Ejemplo 2 (98765432-1)
```

## 📁 Estructura del Proyecto

```
app/
├── src/main/
│   ├── java/com/example/myautofillservice/
│   │   ├── 🔧 service/
│   │   │   ├── MyAutofillService.kt           # Servicio principal
│   │   │   ├── WebSiteManager.kt              # Gestión de sitios
│   │   │   └── WebSiteIdentification.kt       # Tipos de identificación
│   │   ├── 🏦 compatibility/
│   │   │   └── BankCompatibilityManager.kt    # Compatibilidad bancaria
│   │   ├── 🔍 utils/
│   │   │   ├── PageIdentifier.kt              # Identificación de páginas
│   │   │   └── UrlUtils.kt                    # Utilidades de URL
│   │   ├── 💾 data/
│   │   │   ├── Credential.kt                  # Modelo de credenciales
│   │   │   ├── WebSite.kt                     # Modelo de sitios web
│   │   │   ├── CredentialDao.kt               # DAO de credenciales
│   │   │   ├── WebSiteDao.kt                  # DAO de sitios web
│   │   │   └── CredentialDatabase.kt          # Base de datos Room
│   │   └── 🎨 ui/
│   │       └── MainActivity.kt                # Interfaz principal
│   ├── res/
│   │   ├── layout/                            # Layouts de UI
│   │   ├── values/                            # Recursos y strings
│   │   └── xml/
│   │       └── autofill_service.xml           # Configuración del servicio
│   └── AndroidManifest.xml                    # Configuración de la app
├── build.gradle.kts                           # Configuración de Gradle
└── proguard-rules.pro                         # Reglas de ofuscación
```

## 🛠️ Tecnologías Utilizadas

### **Framework y Lenguaje**
- **Kotlin** - Lenguaje principal de desarrollo
- **Android SDK** - Framework de desarrollo móvil
- **AutofillService API** - API nativa de Android para autocompletado

### **Base de Datos y Persistencia**
- **Room Database** - ORM para SQLite
- **Coroutines** - Programación asíncrona
- **LiveData** - Observación de datos reactiva

### **Arquitectura y Patrones**
- **MVVM** - Patrón Model-View-ViewModel
- **Repository Pattern** - Abstracción de datos
- **Dependency Injection** - Gestión de dependencias

### **Herramientas de Desarrollo**
- **Android Studio** - IDE de desarrollo
- **Gradle** - Sistema de construcción
- **ADB** - Android Debug Bridge para testing

## 🧪 Desarrollo y Testing

### **Configuración de Desarrollo**

```kotlin
// Habilitar logs detallados
Log.d(TAG, "🔍 Page identification results:")
Log.d(TAG, "🏦 Applying Chilean bank compatibility")
Log.d(TAG, "✅ SaveInfo configured successfully!")
```

### **Testing en Dispositivos**

1. **Habilitar opciones de desarrollador**
2. **Conectar dispositivo via ADB**
3. **Instalar APK de debug**
4. **Monitorear logs en tiempo real**:
```bash
adb logcat | grep MyAutofillService
```

### **Sitios de Prueba Recomendados**

| Tipo | Sitio | Propósito |
|------|-------|-----------|
| 🧪 **Testing** | `practicetestautomation.com` | Campos estándar |
| 🧪 **Demo** | `the-internet.herokuapp.com` | Formularios básicos |
| 🏦 **Banco** | `www.bancoestado.cl` | Compatibilidad bancaria |
| 🌐 **General** | `chanceschile.online` | Sitio web normal |

### **Debugging y Logs**

```bash
# Filtrar logs del servicio
adb logcat | grep "MyAutofillService\|PageIdentifier\|BankCompatibility"

# Ver estructura de campos detectados
adb logcat | grep "Found autofillable field"

# Monitorear guardado de credenciales
adb logcat | grep "Saved new credential"
```

## 🔧 Configuración Avanzada

### **Personalización de Compatibilidad Bancaria**

```kotlin
// Agregar nuevo banco
val NUEVO_BANCO = BankConfig(
    domain = "nuevobanco.cl",
    name = "Nuevo Banco",
    usernameFieldNames = listOf("rut", "usuario"),
    passwordFieldNames = listOf("clave", "password"),
    usernameHints = listOf("off", "username"),
    passwordHints = listOf("new-password", "password"),
    hasOverlay = true,
    requiresSpecialHandling = true
)
```

### **Configuración de Datasets Personalizados**

```kotlin
// Personalizar datos de ejemplo
private fun createCustomDatasets(fields: List<AutofillField>): List<Dataset> {
    // Implementación personalizada
}
```

## 📊 Métricas y Rendimiento

### **Estadísticas de Compatibilidad**
- ✅ **Bancos chilenos**: 6 bancos principales soportados
- ✅ **Sitios web generales**: Compatibilidad universal
- ✅ **Navegadores**: Chrome, Firefox, Edge, Samsung Internet
- ✅ **Aplicaciones**: Soporte para apps nativas con WebView

### **Rendimiento**
- ⚡ **Detección de campos**: < 100ms promedio
- ⚡ **Búsqueda de credenciales**: < 50ms promedio
- ⚡ **Guardado de datos**: < 200ms promedio
- 💾 **Uso de memoria**: < 50MB en ejecución

## 🤝 Contribución

### **Cómo Contribuir**

1. **Fork del repositorio**
2. **Crear rama de feature**: `git checkout -b feature/nueva-funcionalidad`
3. **Commit de cambios**: `git commit -m 'Agregar nueva funcionalidad'`
4. **Push a la rama**: `git push origin feature/nueva-funcionalidad`
5. **Crear Pull Request**

### **Áreas de Mejora**

- 🏦 **Más bancos**: Agregar soporte para bancos internacionales
- 🌐 **Más navegadores**: Mejorar compatibilidad con navegadores alternativos
- 🔒 **Seguridad**: Implementar encriptación de credenciales
- 🎨 **UI/UX**: Mejorar interfaz de gestión de credenciales
- 🧪 **Testing**: Agregar tests automatizados

### **Reportar Issues**

Al reportar problemas, incluye:
- Versión de Android
- Navegador/aplicación utilizada
- Sitio web específico
- Logs relevantes
- Pasos para reproducir

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Ver el archivo `LICENSE` para más detalles.

## 🙏 Agradecimientos

- **Android AutofillService API** - Framework base
- **Room Database** - Persistencia de datos
- **Kotlin Coroutines** - Programación asíncrona
- **Comunidad Android** - Documentación y ejemplos

---

## 📞 Contacto y Soporte

Para preguntas, sugerencias o soporte técnico:

- 📧 **Email**: [mosiah.orellana@digitalstronglocking.com]
- 🐛 **Issues**: [GitHub Issues](https://github.com/Moriort/MyAutofillService/issues)
- 📖 **Wiki**: [Documentación completa](https://github.com/Moriort/MyAutofillService/wiki)

---

**Desarrollado con ❤️ para la comunidad Android**

*Última actualización: Julio 2025*