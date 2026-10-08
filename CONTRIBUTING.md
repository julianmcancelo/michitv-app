# 🤝 Guía de Contribución para MichiTV

¡Gracias por tu interés en colaborar con **MichiTV**! Este es un proyecto de código abierto diseñado para brindar la mejor experiencia de streaming para la comunidad hispanohablante.

---

## 📋 Código de Conducta

Nos comprometemos a mantener un ambiente respetuoso, inclusivo y amigable para todos los desarrolladores y colaboradores.

---

## 🛠️ Flujo de Trabajo para Nuevas Funcionalidades

1. **Haz un Fork del repositorio**:
   Crea tu copia en GitHub y clónala localmente:
   ```bash
   git clone https://github.com/TU_USUARIO/MichiTV.git
   cd MichiTV/kino-tv-app
   ```

2. **Crea una nueva rama descriptiva**:
   ```bash
   git checkout -b feature/nombre-de-tu-mejora
   ```

3. **Normas de Desarrollo**:
   - Todo el código, comentarios y cadenas de interfaz deben estar redactados en **español neutro, claro y profesional**.
   - Sigue los lineamientos de diseño de **Jetpack Compose** y la paleta de colores obsidian/naranja/cian definida en `ui/theme/Color.kt`.
   - Asegúrate de que los componentes sean responsivos y funcionen tanto en resoluciones móviles como en pantallas 4K de TV con navegación por control remoto (D-Pad).

4. **Verificación y Pruebas**:
   Antes de abrir un Pull Request, compila el proyecto y asegúrate de que no existan advertencias ni errores:
   ```bash
   ./gradlew assembleDebug
   ```

5. **Enviar un Pull Request**:
   - Envía tu PR hacia la rama `main`.
   - Explica detalladamente los cambios realizados, screenshots de la UI si aplica y cómo probarlo.

---

## 🚀 Publicación de Versiones

Las publicaciones oficiales de nuevas versiones y compilaciones OTA son generadas automáticamente por el equipo de mantenedores mediante etiquetas de Git (`vX.Y.Z`) que disparan el flujo de GitHub Actions.
