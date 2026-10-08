# 🐾 MichiTV - Reproductor Multimedia y Streaming Inteligente para Android & Android TV

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg?style=for-the-badge&logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Plataforma-Android%20%7C%20Android%20TV-green.svg?style=for-the-badge&logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Glassmorphism-blue.svg?style=for-the-badge&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Licencia](https://img.shields.io/badge/Licencia-GPL--3.0-orange.svg?style=for-the-badge)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/Julian/MichiTV?color=FF6D00&style=for-the-badge&logo=github)](https://github.com/Julian/MichiTV/releases)

**MichiTV** es una plataforma de streaming y entretenimiento cinematográfico de última generación, desarrollada 100% nativa en Kotlin con Jetpack Compose. Diseñada para ofrecer una experiencia estética, responsiva y fluida tanto en dispositivos móviles táctiles como en Smart TVs (Android TV, Google TV, Fire TV Stick) con navegación por control remoto (D-Pad).

---

## 🌟 Características Principales

### 📱 Experiencia Adaptativa Universal
- **Modo Móvil / Vertical**: Navegación ergonómica mediante barra inferior (`BottomNavigation`), pósters detallados en alta resolución y selector vertical de episodios.
- **Modo Smart TV / Horizontal**: Barra lateral con foco D-Pad optimizado, carrusel *Hero* cinematográfico con fondos oscuros, gradientes cinemáticos y catálogo en cuadrícula fluida.

### 🔄 Actualizaciones Inteligentes OTA (GitHub Releases)
- **Detección Automática**: Comprueba silenciosamente en segundo plano la existencia de nuevas versiones mediante la API oficial de GitHub Releases.
- **Comparación SemVer**: Algoritmo que evalúa números de versión (`v1.0.1` vs `v1.0.0`) y descarga el APK correspondiente.
- **Descarga con Progreso en Tiempo Real**: Notificación modal en la interfaz con barra de descarga y porcentaje antes de instalar.
- **Instalación Segura**: Ejecución a través de `FileProvider` con permiso del sistema `REQUEST_INSTALL_PACKAGES`.
- Para más información técnica, consulta [ACTUALIZACIONES.md](ACTUALIZACIONES.md).

### ✈️ Vinculación y Activación con Bot de Telegram
- **Código Único de Dispositivo**: Generación determinista de un identificador de hardware corto (ejemplo: `MICHI-A84F`).
- **Activación Instantánea**: Los usuarios pueden enviar `/activar MICHI-XXXX` al bot de Telegram o ingresar su código/voucher desde la pantalla de Ajustes.
- **Gestión de Planes**: Soporte para licencias Pro/VIP, persistencia local con SharedPreferences y validación en línea.

### ⚡ Motor Extensible de Plugins (QuickJS Engine)
- Ejecución de scrapers y resolvers JavaScript en un entorno aislado y ultra liviano (`QuickJS Android`).
- Compatibilidad con catálogo de plugins de streaming, búsqueda universal en múltiples servidores (Voe, FuegoCine, StreamWish) y extracción de fuentes HLS / MP4.
- Compatibilidad con listas y addons de Stremio.

### 🎬 Reproductor Cinematográfico (ExoPlayer Media3)
- Reproducción de transmisiones en vivo (IPTV M3U8) y películas/series bajo demanda con cabeceras HTTP personalizadas (User-Agent, Referer).
- Controles multimedia personalizables y rendimiento acelerado por hardware.

---

## 📂 Estructura del Proyecto

```text
kino-tv-app/
├── .github/
│   └── workflows/
│       └── release.yml          # CI/CD: Compilación y publicación automática de Releases
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/kinotv/player/
│   │       │   ├── MainActivity.kt               # Punto de entrada y orquestador de UI Compose
│   │       │   ├── AppUpdateManager.kt           # Gestor de descargas OTA y GitHub API
│   │       │   ├── TelegramActivationManager.kt  # Módulo de activación y vinculación con Bot
│   │       │   ├── KinoPluginEngine.kt           # Motor de ejecución JS con QuickJS
│   │       │   ├── PluginManager.kt              # Registro y persistencia de plugins
│   │       │   ├── NetworkHelper.kt              # Cliente OkHttp singleton optimizado
│   │       │   ├── ui/
│   │       │   │   ├── components/
│   │       │   │   │   └── MichiUpdateModal.kt   # Modal Glassmorphism para actualizaciones
│   │       │   │   └── theme/
│   │       │   │       ├── Color.kt              # Paleta obsidian/naranja/cian MichiTV
│   │       │   │       ├── Theme.kt
│   │       │   │       └── Type.kt               # Tipografía Outfit y estilos
│   │       └── res/
│   │           └── xml/
│   │               └── file_paths.xml            # Configuración de FileProvider para APKs
│   └── build.gradle.kts
├── ACTUALIZACIONES.md                           # Guía detallada de lanzamientos y OTA
├── CHANGELOG.md                                 # Historial de cambios por versión
└── README.md                                    # Documentación principal
```

---

## 🛠️ Requisitos de Compilación

1. **Android Studio**: Ladybug (2024.2+) o superior.
2. **Java JDK**: Versión 17 (OpenJDK / Eclipse Temurin).
3. **Android SDK**: API 34 (Android 14) o superior (soporta desde Android 7.0 / API 24).
4. **Gradle**: 8.7 con Gradle Wrapper incluido.

---

## 🚀 Compilación y Despliegue Local

### 1. Clonar el Repositorio
```bash
git clone https://github.com/Julian/MichiTV.git
cd MichiTV/kino-tv-app
```

### 2. Compilar el APK de Depuración
```bash
./gradlew assembleDebug
```
El archivo generado se ubicará en:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Instalar en Emulador o Dispositivo Físico
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 Publicación de Actualizaciones con GitHub Actions

El repositorio incluye un flujo automatizado de CI/CD listo para usar en [`.github/workflows/release.yml`](.github/workflows/release.yml):

1. Realiza tus cambios en el código y actualiza `versionName` en `app/build.gradle.kts`.
2. Crea una etiqueta Git con el número de versión:
   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```
3. GitHub Actions compilará automáticamente el proyecto, renombrará el APK a `MichiTV-v1.0.1.apk` y creará la nueva release en GitHub con las notas de versión.
4. Todos los usuarios con MichiTV instalada recibirán la notificación de actualización de forma automática o podrán pulsar "Buscar Actualizaciones" en Ajustes.

---

## 🐾 Créditos y Agradecimientos

- Diseñado y desarrollado con pasión por el equipo de **MichiTV**.
- Desarrollado con tecnología nativa **Jetpack Compose**, **AndroidX Media3**, **QuickJS** y **OkHttp**.

---

## 📄 Licencia

Este proyecto se distribuye bajo la licencia **GPL-3.0**. Consulta el archivo `LICENSE` para más información.
