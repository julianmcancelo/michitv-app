# 🐾 MichiTV v2.0.0 — Solución detallada del problema "No encuentra stream"

Fecha: 2026-10-09
Commit base: `8f30a14` (v1.5.0) + fixes → tag `v2.0.0`
APK: `kino-tv-app/MichiTV-v2.0.0.apk` (versionCode 8, versionName 2.0.0)

---

## 1. Síntomas

- Las películas y series **se veían en el catálogo** (Cinemeta) pero al dar
  "Reproducir" salía *"No se encontró un stream activo para este título"*.
- Las **series no mostraban capítulos** (0 episodios en la ficha).
- El **anime funcionaba perfecto** (animeav1 / animeflvone).
- IPTV funcionaba (519 canales), Caracol devolvía 0 filas.

## 2. Diagnóstico (cómo se encontró la causa real)

1. Se ejecutó el plugin `fuegocine` **fuera de la app** (Node + mock de `kino`):
   `home()` → 520 items, `resolve()` película → HLS videro OK,
   `episodes()` + `resolve()` episodio → MP4 OK.
   **Conclusión: el JavaScript del plugin estaba sano.**
2. Se probó en el **emulador con logcat** (`adb logcat -d -s KinoPluginEngine:D
   KinoPluginJS:D TmdbEmulator:D`) tocando "Ver Ahora" → "Reproducir Título".
3. El log mostró la cadena real del fallo para cada película:
   `TmdbEmulator search 'X' -> N results` → `resolveStream ... plugin latino`
   → `not_found: no playable embed (0 listed)`.
   **FuegoCine nunca era intentado** aunque `resolveByName()` lo pone primero.

## 3. Causas raíz (3 bugs encadenados)

### Bug A — `search()` del engine nunca reintentaba (el principal)
`KinoPluginEngine.search()` llamaba `await search("texto")` (string plano) y solo
reintentaba con objeto si el resultado era **nulo**:
```js
var res = await search("Backrooms");
if (!res) res = await search({ q: "Backrooms" });
```
Pero `fuegocine.search(string)` devuelve `[]` (**array vacío = truthy**), así que
el fallback con `{ q }` (el único formato que entiende fuegocine) **nunca corría**.
Resultado: búsqueda FuegoCine siempre vacía en la app → se caía a Latino →
Latino no extraía embeds (sus fuentes lamovie/cinecalidad/seriesmetro dan
timeout o 0 embeds) → "no encuentra stream".
(Este fallback existía en trabajo previo no commiteado y se perdió con un
`git reset --hard`; por eso "la 1.3 andaba y la actual no".)

### Bug B — El puente QuickJS no tenía `kino.tmdb`
`setupKinoBridge()` no registraba `__native_tmdb` ni `kino.tmdb`, pero el plugin
**latino lo exige** (`getKino()` + `kino.tmdb`). Sin él, todo `search/resolve`
de latino moría con `unavailable: tmdb: unavailable`
(confirmado en logcat: sin ningún log de `TmdbEmulator`).
`TmdbEmulator.kt` existía en el directorio pero sin cablear.

### Bug C — Las fichas Cinemeta (sin plugin) nunca cargaban capítulos
`DetailScreen` solo pedía episodios si `item.pluginId` no era vacío. Los items de
Cinemeta tienen `pluginId = null` → **0 capítulos** y el play de episodio no
tenía de dónde resolver.

## 4. Fixes aplicados (v2.0.0)

| # | Archivo | Cambio |
|---|---------|--------|
| 1 | `KinoPluginEngine.kt` (`search`) | Reintentar con `{ q }` también cuando el resultado es **array vacío** |
| 2 | `KinoPluginEngine.kt` (`setupKinoBridge`) | Registrar `__native_tmdb` + `kino.tmdb` (usa `TmdbEmulator`, que a su vez usa Cinemeta) |
| 3 | `TmdbEmulator.kt` | Campos que latino necesita: `overview`, `backdrop_path`, `vote_average`, `external_ids.imdb_id`, `translations`, `episode_run_time`; `id` seguro |
| 4 | `KinoPluginEngine.kt` | `getEpisodesByName()` nuevo: busca episodios por nombre (FuegoCine primero, Latino respaldo) y devuelve el plugin de origen |
| 5 | `MainActivity.kt` (`DetailScreen`) | Cargar capítulos por nombre si la ficha no trae plugin; `episodePluginId` para reproducir cada capítulo con su plugin de origen |
| 6 | `KinoPluginEngine.kt` | `__native_atob` tolerante (base64url + padding, como Node `Buffer`) |
| 7 | `KinoPluginEngine.kt` | `kino_native_fetch` serializa `body` objeto a JSON (arregla POST de playmate) |
| 8 | `KinoPluginEngine.kt` | `kino.cookies` stub + `searchParams`/`toString` en el polyfill de `URL` (lo exige caracol-tv) |
| 9 | `fuegocine/plugin.js` | Servidor FC (`repfuegocinefree.blogspot.com`) con rank 5 = prioridad máxima |
| 10 | `MainActivity.kt` | `MichiMaintenanceScreen` con cuenta regresiva (polling a `/api/config` cada 5 s), QR también en celular + código copiable, diálogo de novedades 2.0.0 |
| 11 | `michitv-telegram-bot/bot.js` | `/mantenimiento on [min] [msg]`/`off`/`estado` (auto-apagado), `/soporte` + tickets con respuesta por Reply, endpoint `/api/config`, textos v2.0.0 |

## 5. Verificación en emulador (2026-10-09)

- Home con "Películas en Tendencia" y "Series Más Populares" OK.
- "Unabomber" → Reproducir → `TmdbEmulator search 'Unabomber' -> 12 results`
  → resolve vía FuegoCine (ya no cae solo a Latino).
- Serie "East of Eden" → ficha muestra **"Episodios y Temporadas · 7 caps"**
  con tarjetas T1 E1 etc. (captura `emu_s6.png`).
- Anime e IPTV siguen funcionando como antes.

## 6. Verificación final en emulador (serie + capítulo + play)

- Serie "Lanterns" (ficha Cinemeta sin plugin): muestra "Episodios y Temporadas ·
  8 caps" con tarjetas y miniaturas.
- Tocar T1 E1 → `getEpisodesByName` (FuegoCine) → `resolveStream` OK
  (`https://hugh.cdn.rumble.cloud/...mp4`) → reproductor ExoPlayer con video
  en pantalla ("Lanterns - T1 E1: Episodio 1").
- Ajuste extra: en `getEpisodesByName`, la rama Latino ahora prueba primero los
  hits de tipo serie (antes probaba refs de película `m:` y fallaba con
  "not a series ref").

## 7. Notas / pendientes conocidos

- Fuentes de latino (lamovie, cinecalidad, hackstore, seriesmetro) responden
  lento o vacío según red/horario; FuegoCine es ahora la vía principal y
  latino queda de respaldo.
- Caracol TV puede dar 403 fuera de Colombia (bloqueo geográfico del backend,
  no del plugin).
- Un episodio puntual de animeflvone falló con "no hay servidores compatibles"
  (caso por ítem, no general).
- El emulador Android 16KB muestra aviso de `libquickjs.so` no alineada; la app
  corre en modo compatibilidad sin impacto funcional.
- `database.json` del bot se commitea con `maintenance: false`. Si al abrir la
  app aparece mantenimiento, usar `/mantenimiento off` en Telegram.
