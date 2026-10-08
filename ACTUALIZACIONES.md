# 🚀 Guía de Actualizaciones Inteligentes OTA & GitHub Releases - MichiTV

Este documento describe la arquitectura, flujo y pasos necesarios para publicar y distribuir actualizaciones automáticas inteligentes (**Over-The-Air / OTA**) en **MichiTV** mediante la API oficial de **GitHub Releases**.

---

## 🏗️ 1. Arquitectura del Sistema OTA

MichiTV incorpora un motor nativo y autónomo para Android compuesto por:

1. **`AppUpdateManager.kt`**:
   - Consulta el endpoint `GET https://api.github.com/repos/{owner}/{repo}/releases/latest` con cabeceras optimizadas.
   - Analiza el `tag_name` remoto (ej: `v1.0.1` o `1.0.1`) y lo compara contra la versión instalada (`BuildConfig.VERSION_NAME`) mediante un comparador semántico estricto (`isNewerVersion`).
   - Localiza dinámicamente el asset descargable con extensión `.apk`.
   - Descarga asíncronamente el archivo APK mediante **OkHttp** y emite el progreso de bytes y porcentaje mediante `StateFlow<UpdateDownloadState>`.
   - Ejecuta el instalador del sistema mediante **`FileProvider`** (`com.kinotv.player.provider`), cumpliendo con todas las directivas de seguridad de Android 7.0 a Android 15.

2. **`MichiUpdateModal.kt`**:
   - Componente visual interactivo desarrollado en **Jetpack Compose**.
   - Notificación moderna con estética *glassmorphism* oscura y acentos naranja/cian.
   - Muestra notas de la versión (**Changelog** scrolleable), tamaño en megabytes y botón para iniciar descarga.
   - Barra de progreso animada en tiempo real (`LinearProgressIndicator`) y botón automático "Instalar Ahora".

3. **`TelegramActivationManager.kt`**:
   - Generación determinista de código único de dispositivo (`MICHI-XXXX`).
   - Permite activar planes VIP / Premium vinculando con el bot oficial de Telegram o mediante códigos de activación / vouchers.

---

## 🛠️ 2. Cómo Publicar una Nueva Versión en GitHub

### Paso 1: Actualizar la Versión en el Código
Abre el archivo [`kino-tv-app/app/build.gradle.kts`](kino-tv-app/app/build.gradle.kts) y modifica:
```kotlin
defaultConfig {
    applicationId = "com.kinotv.player"
    minSdk = 24
    targetSdk = 34
    versionCode = 2        // <-- Incrementar en 1 cada vez
    versionName = "1.0.1"  // <-- Nueva versión SemVer (ej: 1.0.1)
    ...
}
```

### Paso 2: Crear el Tag y Subir al Repositorio
En tu terminal:
```bash
git add .
git commit -m "feat: Lanzamiento de versión v1.0.1 con nuevas mejoras"
git tag v1.0.1
git push origin main
git push origin v1.0.1
```

### Paso 3: Publicación Automática por GitHub Actions (Recomendado)
El workflow configurado en [`.github/workflows/release.yml`](.github/workflows/release.yml) se ejecutará automáticamente:
1. Compilará el APK con JDK 17 y Gradle.
2. Renombrará el APK a `MichiTV-v1.0.1.apk`.
3. Creará una nueva **GitHub Release** con el tag `v1.0.1` y adjuntará el archivo APK listo para descargar.

---

## 📦 3. Publicación Manual en GitHub (Alternativa sin Actions)

Si prefieres compilar tú mismo y subir el release manualmente:

1. Compila el APK localmente:
   ```bash
   cd kino-tv-app
   ./gradlew assembleDebug
   ```
2. Renombra el archivo generado `app/build/outputs/apk/debug/app-debug.apk` a `MichiTV-v1.0.1.apk`.
3. Entra a tu repositorio en GitHub: `https://github.com/Julian/MichiTV/releases/new`.
4. Elige o crea el tag `v1.0.1`.
5. Título del Release: `🐾 MichiTV v1.0.1`.
6. En la descripción, describe los cambios y novedades.
7. **Importante**: En la sección de archivos adjuntos (*Attach binaries*), sube el archivo `MichiTV-v1.0.1.apk`.
8. Pulsa **Publish release**.

---

## 📱 4. Experiencia del Usuario Final

1. **Al abrir la app**:
   - Si la opción *"Buscar actualizaciones automáticamente al iniciar"* está activada, MichiTV comprueba en segundo plano si existe un release superior.
   - Si existe una versión más nueva, se abre automáticamente el modal **MichiTV Actualización Disponible**.
2. **Desde Ajustes**:
   - En cualquier momento el usuario puede dirigirse a **Ajustes > Actualizaciones del Sistema** y hacer clic en **"Buscar Actualizaciones 🔄"**.
   - Si no hay actualizaciones, se muestra un mensaje confirmando que ya posee la versión más reciente.
   - El usuario o administrador puede personalizar el usuario y repositorio de GitHub directamente desde la interfaz sin recompilar la aplicación.
