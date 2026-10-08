# 📜 Registro de Cambios (Changelog) - MichiTV

Todos los cambios notables en este proyecto serán documentados en este archivo. El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y este proyecto se adhiere a [Semantic Versioning (SemVer)](https://semver.org/lang/es/).

---

## [1.0.0] - 2026-10-08

### 🚀 Novedades y Características Principales
- **Sistema de Auto-Actualización OTA Inteligente**:
  - Consulta asíncrona a la API de **GitHub Releases**.
  - Comparador de versiones semánticas (`SemVer`) para identificar nuevas actualizaciones de forma fiable.
  - Notificación modal con estética *glassmorphism*, notas de la versión scrolleables, barra de progreso en tiempo real y disparo de instalador de sistema mediante `FileProvider`.
  - Opción de comprobación automática periódica al iniciar la aplicación.
  - Selector y configuración dinámica de repositorio GitHub en los Ajustes.

- **Módulo de Activación por Bot de Telegram 🐾**:
  - Generación de código determinista único por dispositivo (`MICHI-XXXX`).
  - Vinculación con bot de Telegram mediante comando `/activar MICHI-XXXX`.
  - Soporte de activación mediante clave/voucher desde la pantalla de Ajustes.
  - Almacenamiento seguro del estado de suscripción y licencia.

- **Diseño Adaptativo Multidispositivo**:
  - **Modo Móvil / Vertical**: Rediseño integral de la pantalla de detalle (`DetailScreen`) con backdrop heroico, póster centrado, metadatos claros, botones de acción sin cortes tipográficos y lista vertical de episodios.
  - **Modo Smart TV / Horizontal**: Sidebar lateral ergonómico con foco D-Pad optimizado, navegación rápida y carruseles fluidos.

- **Motor de Plugins y Scrapers Cinematográficos**:
  - Integración nativa de **QuickJS Android** para ejecución aislada de scripts JavaScript.
  - Soporte de catálogo con plugins de streaming (FuegoCine, CinemaHD, IPTV, etc.).
  - Compatibilidad para registrar repositorios externos mediante enlaces y formato de manifiestos.

- **Reproductor Multimedia de Alto Rendimiento**:
  - Desarrollado sobre **AndroidX Media3 ExoPlayer**.
  - Soporte de cabeceras HTTP personalizadas (User-Agent, Referer) para evasión de bloqueos en streams HLS y MP4.
  - Interfaz de controles cinematográficos y manejo automático de buffers y reintentos.

- **CI/CD Automatizado**:
  - Pipeline de GitHub Actions en `.github/workflows/release.yml` para compilar y generar releases de APK automáticamente al crear tags `v*.*.*`.
