package com.kinotv.player

import com.kinotv.player.data.local.MichiDatabase
import com.kinotv.player.data.local.MichiDao
import com.kinotv.player.data.local.WatchlistItem
import com.kinotv.player.data.local.WatchHistory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.Check
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalConfiguration
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kinotv.player.ui.theme.*
import com.kinotv.player.ui.components.*
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

enum class ScreenNav {
    HOME,
    MOVIES,
    SERIES,
    LIVE_TV,
    SEARCH,
    PLUGINS,
    SETTINGS
}

val LocalMichiDao = staticCompositionLocalOf<MichiDao> { error("No MichiDao provided") }

data class PlayRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val title: String = "",
    val item: CatalogItem? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val startPositionMs: Long = 0L
)

object AppState {
    var isDarkTheme = androidx.compose.runtime.mutableStateOf(true)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppState.isDarkTheme.value = ThemePrefs.isDarkMode(this)
        val database = MichiDatabase.getDatabase(this)
        setContent {
            CompositionLocalProvider(LocalMichiDao provides database.michiDao()) {
                MichiTheme(darkTheme = AppState.isDarkTheme.value) {
                    MainAppNavigation()
                }
            }
        }
    }
}

@Composable
fun MainAppNavigation() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(ScreenNav.HOME) }
    var playingStream by remember { mutableStateOf<PlayRequest?>(null) }
    var openedItem by remember { mutableStateOf<CatalogItem?>(null) }

    // Estado del gestor de actualizaciones GitHub OTA
    var availableUpdate by remember { mutableStateOf<ReleaseInfo?>(null) }
    val updateDownloadState by AppUpdateManager.downloadState.collectAsState()

    // --- BLOQUEO DE ACTIVACION OBLIGATORIA ---
    var activationInfo by remember { mutableStateOf(TelegramActivationManager.getActivationInfo(context)) }

    // --- MODO MANTENIMIENTO GLOBAL (Remote Config via Bot) ---
    var remoteConfig by remember { mutableStateOf(RemoteConfig()) }

    // Polling reactivo en tiempo real para auto-activacion sin escribir nada
    LaunchedEffect(activationInfo.isActivated) {
        if (!activationInfo.isActivated) {
            while (!activationInfo.isActivated) {
                kotlinx.coroutines.delay(2500)
                val updated = withContext(Dispatchers.IO) {
                    TelegramActivationManager.checkRemoteStatus(context)
                }
                if (updated != null && updated.isActivated) {
                    activationInfo = updated
                    break
                }
            }
        }
    }

    // Bucle de escucha de mantenimiento cada 5 segundos (en vivo, sin reabrir la app)
    LaunchedEffect(activationInfo.isActivated) {
        while (true) {
            val cfg = RemoteConfigManager.fetchConfig(context)
            remoteConfig = cfg
            kotlinx.coroutines.delay(5000)
        }
    }

    // Dialogo de novedades v2.0.0 (una vez por version)
    var showWhatsNew by remember {
        val prefs = context.getSharedPreferences("michi_whatsnew", android.content.Context.MODE_PRIVATE)
        mutableStateOf(prefs.getString("last_seen_version", "") != "2.0.0")
    }

    if (!activationInfo.isActivated) {
        MichiActivationWallScreen(
            activationInfo = activationInfo,
            onActivated = { activationInfo = TelegramActivationManager.getActivationInfo(context) }
        )
        return // Bloquea completamente el acceso a la app
    }

    // Bloqueo total por mantenimiento: ni catalogo ni reproductor intentan cargar
    if (remoteConfig.maintenance) {
        MichiMaintenanceScreen(
            message = remoteConfig.maintenanceMessage,
            timeLeftMs = remoteConfig.timeLeftMs
        )
        return
    }

    // Novedades v2.0.0 al iniciar (una vez por version)
    if (showWhatsNew) {
        MichiWhatsNewDialog(
            onDismiss = {
                context.getSharedPreferences("michi_whatsnew", android.content.Context.MODE_PRIVATE)
                    .edit().putString("last_seen_version", "2.0.0").apply()
                showWhatsNew = false
            }
        )
    }

    // Comprobación inteligente en segundo plano si está activado
    LaunchedEffect(Unit) {
        if (AppUpdateManager.isAutoCheckEnabled(context)) {
            val update = AppUpdateManager.checkForUpdates(context, force = false)
            if (update != null) {
                availableUpdate = update
            }
        }
    }

    if (playingStream != null) {
        BackHandler {
            playingStream = null
        }
        TvPlayerScreen(
            request = playingStream!!,
            onBack = { playingStream = null }
        )
    } else if (openedItem != null) {
        BackHandler {
            openedItem = null
        }
        DetailScreen(
            item = openedItem!!,
            onBack = { openedItem = null },
            onPlay = { request ->
                playingStream = request
            }
        )
    } else {
        if (currentScreen != ScreenNav.HOME) {
            BackHandler {
                currentScreen = ScreenNav.HOME
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isMobile = maxWidth < 650.dp

            if (isMobile) {
                // Modo Teléfono / Pantalla Vertical (Bottom Navigation)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    MichiTopBar(
                        onSearchClick = { currentScreen = ScreenNav.SEARCH }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            ScreenNav.HOME -> TvHomeScreen(onOpenItem = { openedItem = it })
                            ScreenNav.MOVIES -> TvMoviesScreen(onOpenItem = { openedItem = it })
                            ScreenNav.SERIES -> TvSeriesScreen(onOpenItem = { openedItem = it })
                            ScreenNav.LIVE_TV -> TvLiveScreen(onPlayChannel = { url, headers, name ->
                                playingStream = PlayRequest(url, headers, name)
                            })
                            ScreenNav.SEARCH -> TvSearchScreen(onOpenItem = { openedItem = it })
                            ScreenNav.PLUGINS -> TvPluginsScreen()
                            ScreenNav.SETTINGS -> TvSettingsScreen(onShowUpdate = { availableUpdate = it })
                        }
                    }

                    MichiBottomNav(
                        selectedScreen = currentScreen,
                        onSelectScreen = { screen -> currentScreen = screen }
                    )
                }
            } else {
                // Modo Smart TV / Pantalla Horizontal (TV Sidebar)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    TvSidebar(
                        selectedScreen = currentScreen,
                        onSelectScreen = { screen -> currentScreen = screen }
                    )

                    Box(modifier = Modifier.weight(1f)) {
                        when (currentScreen) {
                            ScreenNav.HOME -> TvHomeScreen(onOpenItem = { openedItem = it })
                            ScreenNav.MOVIES -> TvMoviesScreen(onOpenItem = { openedItem = it })
                            ScreenNav.SERIES -> TvSeriesScreen(onOpenItem = { openedItem = it })
                            ScreenNav.LIVE_TV -> TvLiveScreen(onPlayChannel = { url, headers, name ->
                                playingStream = PlayRequest(url, headers, name)
                            })
                            ScreenNav.SEARCH -> TvSearchScreen(onOpenItem = { openedItem = it })
                            ScreenNav.PLUGINS -> TvPluginsScreen()
                            ScreenNav.SETTINGS -> TvSettingsScreen(onShowUpdate = { availableUpdate = it })
                        }
                    }
                }
            }
        }
    }

    // Modal inteligente de actualización si hay una disponible
    if (availableUpdate != null) {
        MichiUpdateModal(
            releaseInfo = availableUpdate!!,
            downloadState = updateDownloadState,
            onStartDownload = {
                coroutineScope.launch {
                    val apk = AppUpdateManager.downloadUpdate(context, availableUpdate!!.apkUrl)
                    if (apk != null) {
                        AppUpdateManager.installApk(context, apk)
                    }
                }
            },
            onInstall = { apkFile ->
                AppUpdateManager.installApk(context, apkFile)
            },
            onDismiss = {
                availableUpdate = null
                AppUpdateManager.resetState()
            }
        )
    }
}

@Composable
fun TvSidebar(
    selectedScreen: ScreenNav,
    onSelectScreen: (ScreenNav) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MichiBorder)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Logo Michi TV
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MichiOrangeGradient),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🐾", fontSize = 22.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))

        SidebarIcon(
            icon = Icons.Default.Search,
            label = "Buscar",
            isSelected = selectedScreen == ScreenNav.SEARCH,
            onClick = { onSelectScreen(ScreenNav.SEARCH) }
        )
        SidebarIcon(
            icon = Icons.Default.Home,
            label = "Inicio",
            isSelected = selectedScreen == ScreenNav.HOME,
            onClick = { onSelectScreen(ScreenNav.HOME) }
        )
        SidebarIcon(
            icon = Icons.Default.Movie,
            label = "Películas",
            isSelected = selectedScreen == ScreenNav.MOVIES,
            onClick = { onSelectScreen(ScreenNav.MOVIES) }
        )
        SidebarIcon(
            icon = Icons.Default.Tv,
            label = "Series",
            isSelected = selectedScreen == ScreenNav.SERIES,
            onClick = { onSelectScreen(ScreenNav.SERIES) }
        )
        SidebarIcon(
            icon = Icons.Default.LiveTv,
            label = "En Vivo",
            isSelected = selectedScreen == ScreenNav.LIVE_TV,
            onClick = { onSelectScreen(ScreenNav.LIVE_TV) }
        )
        SidebarIcon(
            icon = Icons.Default.Extension,
            label = "Plugins",
            isSelected = selectedScreen == ScreenNav.PLUGINS,
            onClick = { onSelectScreen(ScreenNav.PLUGINS) }
        )
        Spacer(modifier = Modifier.weight(1f))
        SidebarIcon(
            icon = Icons.Default.Settings,
            label = "Ajustes",
            isSelected = selectedScreen == ScreenNav.SETTINGS,
            onClick = { onSelectScreen(ScreenNav.SETTINGS) }
        )
    }
}

@Composable
fun SidebarIcon(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val tint by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.onSurface
            isSelected -> MichiOrange
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }, label = "tint"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.surfaceVariant
            isSelected -> Color(0x33FF6D00)
            else -> Color.Transparent
        }, label = "icon_bg"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 1.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.onSurface else if (isSelected) MichiOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

object MediaCache {
    var homeRows: List<CatalogRow>? = null
    var movies: List<CatalogItem>? = null
    var series: List<CatalogItem>? = null

    fun clear() {
        homeRows = null
        movies = null
        series = null
    }
}

@Composable
fun TvHomeScreen(onOpenItem: (CatalogItem) -> Unit) {
    val dao = LocalMichiDao.current
    val watchlist by dao.getWatchlist().collectAsState(initial = emptyList<WatchlistItem>())
    val history by dao.getWatchHistory().collectAsState(initial = emptyList<WatchHistory>())

    var selectedItem by remember { mutableStateOf<CatalogItem?>(null) }
    var rows by remember { mutableStateOf(MediaCache.homeRows ?: emptyList()) }
    var isLoading by remember { mutableStateOf(MediaCache.homeRows == null) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (MediaCache.homeRows == null) {
            val allRows = mutableListOf<CatalogRow>()
            val plugins = PluginManager.getInstalledPlugins(context).filter { it.isEnabled }

            val sortedPlugins = plugins.sortedBy { 
                when (it.id) {
                    "fuegocine" -> 0
                    "latino" -> 1
                    "animeav1" -> 2
                    "iptv-org" -> 3
                    else -> 4
                }
            }

            // CARGA ULTRA RÁPIDA EN PARALELO
            val deferredList = sortedPlugins.map { plugin ->
                async(Dispatchers.IO) {
                    val pRows = mutableListOf<CatalogRow>()
                    if (plugin.id != "iptv-org") {
                        try {
                            val r = KinoPluginEngine.getHomeRows(context, plugin.id)
                            if (r.isNotEmpty()) pRows.addAll(r)
                            
                            if (plugin.id == "latino") {
                                val latinoMovies = KinoPluginEngine.browse(context, "latino", "latest:hackstore:movie")
                                if (latinoMovies.isNotEmpty()) {
                                    pRows.add(CatalogRow("Películas Latino", latinoMovies))
                                }
                                val latinoSeries = KinoPluginEngine.browse(context, "latino", "latest:hackstore:tv")
                                if (latinoSeries.isNotEmpty()) {
                                    pRows.add(CatalogRow("Series Latino", latinoSeries))
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error loading home rows for plugin ${plugin.id}", e)
                        }
                    }
                    pRows
                }
            }

            val results = deferredList.awaitAll()
            results.forEach { if (it.isNotEmpty()) allRows.addAll(it) }

            // Fallback popular catalogs if everything is empty
            if (allRows.isEmpty()) {
                val movies = CatalogService.getPopularMovies()
                if (movies.isNotEmpty()) {
                    allRows.add(CatalogRow("Películas en Tendencia Mundial", movies))
                }
                val series = CatalogService.getPopularSeries()
                if (series.isNotEmpty()) {
                    allRows.add(CatalogRow("Series Más Populares", series))
                }
            }

            MediaCache.homeRows = allRows
            rows = allRows
            isLoading = false
        }
        if (rows.isNotEmpty() && rows[0].items.isNotEmpty() && selectedItem == null) {
            selectedItem = rows[0].items.first()
        }
    }

    val combinedRows = remember(watchlist, history, rows) {
        val localRows = mutableListOf<CatalogRow>()
        if (history.isNotEmpty()) {
            localRows.add(
                CatalogRow(
                    title = "Continuar Viendo",
                    items = history.map {
                        CatalogItem(
                            id = it.id,
                            name = it.title,
                            type = it.type ?: "movie",
                            poster = it.poster,
                            pluginId = it.pluginId,
                            ref = it.ref
                        )
                    }
                )
            )
        }
        if (watchlist.isNotEmpty()) {
            localRows.add(
                CatalogRow(
                    title = "Mi Lista",
                    items = watchlist.map {
                        CatalogItem(
                            id = it.id,
                            name = it.title,
                            type = it.type ?: "movie",
                            poster = it.poster,
                            background = it.background,
                            description = it.description,
                            year = it.year,
                            pluginId = it.pluginId,
                            ref = it.ref
                        )
                    }
                )
            )
        }
        localRows + rows
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        selectedItem?.let { item ->
            val bgUrl = item.background ?: item.poster
            if (!bgUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = bgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                        .background(HeroBottomScrim)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            selectedItem?.let { item ->
                Column(
                    modifier = Modifier
                        .padding(top = 24.dp, bottom = 18.dp, end = 24.dp)
                        .fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MichiBadge(text = "DESTACADO EN MICHITV 🐾", isAccent = true)
                        Spacer(modifier = Modifier.width(8.dp))
                        item.rating?.let { r ->
                            MichiBadge(text = "★ ${"%.1f".format(r)} IMDb", isGold = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = item.name,
                        fontFamily = OutfitFontFamily,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        item.year?.let {
                            Text(
                                text = it,
                                fontFamily = OutfitFontFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (item.type == "series") "SERIE TV" else "PELÍCULA",
                            fontFamily = OutfitFontFamily,
                            fontSize = 12.sp,
                            color = MichiOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    item.description?.let { desc ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = desc,
                            fontFamily = OutfitFontFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.75f),
                            lineHeight = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MichiButton(
                            text = "▶ Ver Ahora",
                            isPrimary = true,
                            onClick = { onOpenItem(item) }
                        )
                        MichiButton(
                            text = "Detalles e Info",
                            isPrimary = false,
                            onClick = { /* Add to list */ }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MichiOrange)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(combinedRows) { row ->
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp, 16.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(MichiOrange)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = row.title,
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "Ver Todo >",
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 12.sp,
                                    color = MichiOrange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(row.items) { item ->
                                    MichiMovieCard(
                                        item = item,
                                        onFocus = { selectedItem = item },
                                        onClick = { onOpenItem(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvMoviesScreen(onOpenItem: (CatalogItem) -> Unit) {
    val context = LocalContext.current
    var movies by remember { mutableStateOf(MediaCache.movies ?: emptyList()) }
    var isLoading by remember { mutableStateOf(MediaCache.movies == null) }
    var selectedGenre by remember { mutableStateOf("Todos") }

    val genres = listOf("Todos", "Acción", "Comedia", "Terror", "Ciencia ficción", "Drama", "Animación")

    LaunchedEffect(Unit) {
        if (MediaCache.movies == null) {
            val list = mutableListOf<CatalogItem>()
            val plugins = PluginManager.getInstalledPlugins(context).filter { it.isEnabled }
            
            for (plugin in plugins) {
                if (plugin.id == "iptv-org") continue
                try {
                    val pRows = KinoPluginEngine.getHomeRows(context, plugin.id)
                    pRows.forEach { row ->
                        row.items.forEach { it ->
                            if (it.type == "movie") list.add(it)
                        }
                    }
                    if (plugin.id == "latino") {
                        val latinoMovies = KinoPluginEngine.browse(context, "latino", "latest:hackstore:movie")
                        list.addAll(latinoMovies.filter { it.type == "movie" })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error loading movies for plugin ${plugin.id}", e)
                }
            }

            if (list.isEmpty()) {
                list.addAll(CatalogService.getPopularMovies())
            }
            val distinct = list.distinctBy { it.name }
            MediaCache.movies = distinct
            movies = distinct
            isLoading = false
        }
    }

    val filteredMovies = remember(movies, selectedGenre) {
        if (selectedGenre == "Todos") movies
        else movies.filter { it.genres.any { g -> g.contains(selectedGenre, ignoreCase = true) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text(
            text = "Películas y Estrenos",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(genres) { g ->
                MichiChip(
                    text = g,
                    isSelected = g == selectedGenre,
                                onClick = { selectedGenre = g }
                            )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MichiOrange)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 100.dp else 135.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredMovies) { m ->
                    MichiMovieCard(
                        item = m,
                        onFocus = {},
                        onClick = { onOpenItem(m) }
                    )
                }
            }
        }
    }
}

@Composable
fun TvSeriesScreen(onOpenItem: (CatalogItem) -> Unit) {
    val context = LocalContext.current
    var seriesList by remember { mutableStateOf(MediaCache.series ?: emptyList()) }
    var isLoading by remember { mutableStateOf(MediaCache.series == null) }

    LaunchedEffect(Unit) {
        if (MediaCache.series == null) {
            val list = mutableListOf<CatalogItem>()
            val plugins = PluginManager.getInstalledPlugins(context).filter { it.isEnabled }
            
            for (plugin in plugins) {
                if (plugin.id == "iptv-org") continue
                try {
                    val pRows = KinoPluginEngine.getHomeRows(context, plugin.id)
                    pRows.forEach { row ->
                        row.items.forEach { it ->
                            if (it.type == "series") list.add(it)
                        }
                    }
                    if (plugin.id == "latino") {
                        val latinoSeries = KinoPluginEngine.browse(context, "latino", "latest:hackstore:tv")
                        list.addAll(latinoSeries.filter { it.type == "series" })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error loading series for plugin ${plugin.id}", e)
                }
            }

            if (list.isEmpty()) {
                list.addAll(CatalogService.getPopularSeries())
            }
            val distinct = list.distinctBy { it.name }
            MediaCache.series = distinct
            seriesList = distinct
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text(
            text = "Series de Televisión y Anime",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Temporadas completas, episodios y especiales para disfrutar en MichiTV 🐾",
            fontFamily = OutfitFontFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MichiOrange)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 100.dp else 135.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(seriesList) { s ->
                    MichiMovieCard(
                        item = s,
                        onFocus = {},
                        onClick = { onOpenItem(s) }
                    )
                }
            }
        }
    }
}

@Composable
fun TvLiveScreen(onPlayChannel: (url: String, headers: Map<String, String>, name: String) -> Unit) {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<CatalogRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val iptvRows = KinoPluginEngine.getHomeRows(context, "iptv-org")
        rows = iptvRows
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text(
            text = "Televisión En Vivo (IPTV)",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Canales de televisión en vivo transmitidos en tiempo real por streaming HLS.",
            fontFamily = OutfitFontFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MichiOrange)
            }
        } else if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay canales disponibles o el plugin iptv-org no está activo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = OutfitFontFamily)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(rows) { cat ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp, 14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MichiOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cat.title,
                                fontFamily = OutfitFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(cat.items) { ch ->
                                TvChannelCard(
                                    channel = ch,
                                    onClick = { onPlayChannel(ch.id, emptyMap(), ch.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvChannelCard(channel: CatalogItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MichiOrange else MichiBorder,
        label = "ch_border"
    )

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val logoUrl = channel.poster
        if (!logoUrl.isNullOrEmpty()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = MichiOrange)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.name,
            fontFamily = OutfitFontFamily,
            color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable

fun TvSearchScreen(onOpenItem: (CatalogItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            kotlinx.coroutines.delay(800) // debounce
            isSearching = true
            results = emptyList()
            val enabledPlugins = PluginManager.getInstalledPlugins(context).filter { it.isEnabled }
            val deferredResults = enabledPlugins.map { plugin -> async { try { KinoPluginEngine.search(context, plugin.id, query) } catch(e: Exception) { emptyList<CatalogItem>() } } }
            val cinemetaDeferred = async { try { CatalogService.search(query) } catch(e: Exception) { emptyList<CatalogItem>() } }
            val items = mutableListOf<CatalogItem>()
            items.addAll(deferredResults.map { it.await() }.flatten())
            items.addAll(cinemetaDeferred.await())
            results = items.sortedBy { when (it.pluginId) { "fuegocine" -> 0; "latino" -> 1; "cinemeta" -> 2; else -> 3 } }.distinctBy { it.name.lowercase().trim() }
            isSearching = false
        } else {
            results = emptyList()
            isSearching = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text(
            text = "Buscar en MichiTV 🐾",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Escribe una película, serie o anime...", fontFamily = OutfitFontFamily) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MichiOrange,
                    unfocusedBorderColor = MichiBorder,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MichiOrange,
                    focusedLabelColor = MichiOrange,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Spacer(modifier = Modifier.width(14.dp))
            MichiButton(
                text = "Buscar",
                isPrimary = true,
                icon = Icons.Default.Search,
                onClick = { /* Búsqueda automática ya activada por LaunchedEffect */ }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MichiOrange)
            }
        } else if (results.isEmpty() && query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No se encontraron resultados para '$query' en MichiTV.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = OutfitFontFamily)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600) 100.dp else 135.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(results) { item ->
                    MichiMovieCard(
                        item = item,
                        onFocus = {},
                        onClick = { onOpenItem(item) },
                        
                    )
                }
            }
        }
    }
}

@Composable
fun TvPluginsScreen() {
    val context = LocalContext.current
    var installedPlugins by remember { mutableStateOf<List<PluginInfo>>(emptyList()) }
    var catalogPlugins by remember { mutableStateOf<List<CatalogPluginItem>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Instalados, 1: Catálogo, 2: Registrar
    var customUrl by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            installedPlugins = PluginManager.getInstalledPlugins(context)
            catalogPlugins = PluginManager.getAvailableCatalog(context)
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Gestor y Registro de Plugins",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Administra, activa o instala extensiones de catálogo y reproducción de MichiTV.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MichiButton(text = "↻ Recargar", onClick = { reload() })
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MichiButton(
                text = "Instalados (${installedPlugins.size})",
                isPrimary = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            MichiButton(
                text = "Repositorio Oficial (${catalogPlugins.size})",
                isPrimary = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
            MichiButton(
                text = "+ Registrar Plugin Personalizado",
                isPrimary = selectedTab == 2,
                onClick = { selectedTab = 2 }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (selectedTab) {
            0 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(installedPlugins) { p ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0xFF242436), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Extension, contentDescription = null, tint = Color(0xFFE50914))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = p.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "v${p.version}", color = Color(0xFF888899), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val statusBg = if (p.isEnabled) Color(0xFF2E7D32) else Color(0xFF555566)
                                        Text(
                                            text = if (p.isEnabled) "ACTIVO" else "INACTIVO",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(statusBg, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (p.description.isNotBlank()) {
                                        Text(text = p.description, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 2)
                                    }
                                    Text(
                                        text = "Autor: ${p.author} • Capacidades: ${p.capabilities.joinToString(", ")}",
                                        color = Color(0xFF777788),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                MichiButton(
                                    text = if (p.isEnabled) "Desactivar" else "Activar",
                                    isPrimary = !p.isEnabled,
                                    onClick = { PluginManager.setPluginEnabled(context, p.id, !p.isEnabled); reload() }
                                )
                                if (p.isCustom) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    MichiButton(
                                        text = "Eliminar",
                                        onClick = { scope.launch { PluginManager.deleteCustomPlugin(context, p.id); reload() } }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(catalogPlugins) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = item.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (item.isInstalled) {
                                            Text(
                                                text = "INSTALADO",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(text = item.description, color = Color(0xFFCCCCCC), fontSize = 13.sp)
                                    Text(
                                        text = "Categorías: ${item.categories.joinToString(", ")} ${if (item.stremioUrl != null) "• Stremio Addon" else ""}",
                                        color = Color(0xFF888899),
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                MichiButton(
                                    text = if (item.isInstalled) "Configurado" else "Habilitar",
                                    isPrimary = !item.isInstalled,
                                    onClick = {
                                        val targetUrl = item.repo ?: item.stremioUrl ?: ""
                                        if (targetUrl.isNotEmpty()) {
                                            scope.launch {
                                                PluginManager.installPluginFromManifestUrl(context, targetUrl)
                                                reload()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Registrar Plugin por Enlace o Repositorio",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ingresa la URL de un manifiesto michitv-plugin.json o enlace directo de GitHub para descargar y registrar el plugin en la aplicación.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        label = { Text("URL del Manifiesto (https://.../michitv-plugin.json)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MichiOrange,
                            unfocusedBorderColor = Color(0xFF333344),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isInstalling) {
                        CircularProgressIndicator(color = MichiOrange)
                    } else {
                        MichiButton(
                            text = "Descargar e Instalar Plugin / Repo",
                            isPrimary = true,
                            onClick = {
                                if (customUrl.isNotBlank()) {
                                    isInstalling = true
                                    scope.launch {
                                        val res = PluginManager.installPluginFromManifestUrl(context, customUrl.trim())
                                        isInstalling = false
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Plugin instalado con éxito", Toast.LENGTH_SHORT).show()
                                            customUrl = ""
                                            reload()
                                        } else {
                                            Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvSettingsScreen(
    onShowUpdate: (ReleaseInfo) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var isDarkTheme by remember { AppState.isDarkTheme }
    var preferredServer by remember { mutableStateOf("Voe (Recomendado)") }
    var preferredLang by remember { mutableStateOf("Español Latino") }

    // Activación Telegram
    var activationInfo by remember { mutableStateOf(TelegramActivationManager.getActivationInfo(context)) }
    var inputVoucherKey by remember { mutableStateOf("") }
    var showCelebrationDialog by remember { mutableStateOf(false) }
    var isManualChecking by remember { mutableStateOf(false) }

    // Polling reactivo en tiempo real para auto-activación sin escribir nada
    LaunchedEffect(activationInfo.isActivated) {
        if (!activationInfo.isActivated) {
            while (!activationInfo.isActivated) {
                kotlinx.coroutines.delay(2500)
                val updated = withContext(Dispatchers.IO) {
                    TelegramActivationManager.checkRemoteStatus(context)
                }
                if (updated != null && updated.isActivated) {
                    activationInfo = updated
                    showCelebrationDialog = true
                    break
                }
            }
        }
    }

    // Actualizaciones OTA
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var autoCheckEnabled by remember { mutableStateOf(AppUpdateManager.isAutoCheckEnabled(context)) }
    var ghOwner by remember { mutableStateOf(AppUpdateManager.getGithubOwner(context)) }
    var ghRepo by remember { mutableStateOf(AppUpdateManager.getGithubRepo(context)) }
    var showGhConfigDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // --- CABECERA DE AJUSTES ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(MichiOrange, MichiOrangeDark))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column {
                    Text(
                        text = "Ajustes de MichiTV",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Configuración global, apariencia, streaming y actualizaciones",
                        fontSize = 13.sp,
                        fontFamily = OutfitFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Badge de Modo Activo
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isDarkTheme) MichiDarkSurfaceElevated else MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, if (isDarkTheme) MichiDarkBorder else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                        contentDescription = null,
                        tint = MichiOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isDarkTheme) "MODO CINE OSCURO" else "MODO CLARO",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = OutfitFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ================= SECCIÓN 1: APARIENCIA Y TEMA =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Palette, contentDescription = null, tint = MichiOrange, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Apariencia y Visualización",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFontFamily,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Elige la experiencia visual. El modo cine oscuro está optimizado para contraste OLED y visión nocturna.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Selector de Tema con Switch Elegante
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDarkTheme) Color(0xFF0F111A) else Color(0xFFFFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = null,
                                tint = MichiOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isDarkTheme) "Modo Cine Oscuro (Recomendado)" else "Modo Claro Activado",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = OutfitFontFamily
                            )
                            Text(
                                text = if (isDarkTheme) "Fondo obsidian ultra profundo con acentos neón" else "Fondo claro con textos oscuros",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = OutfitFontFamily
                            )
                        }
                    }

                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = { checked ->
                            isDarkTheme = checked
                            ThemePrefs.setDarkMode(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MichiOrange,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= SECCIÓN 2: ACTUALIZACIONES OTA (GITHUB RELEASES) =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.SystemUpdate, contentDescription = null, tint = MichiCyan, modifier = Modifier.size(22.dp))
                        Text(
                            text = "Actualizaciones OTA",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            fontSize = 18.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MichiCyan.copy(alpha = 0.15f))
                            .border(1.dp, MichiCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "v${AppUpdateManager.getCurrentVersionName(context)} • AL DÍA",
                            color = MichiCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "MichiTV descarga e instala parches, nuevos scrapers y mejoras directamente desde GitHub Releases.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isCheckingUpdate = true
                                val update = AppUpdateManager.checkForUpdates(context, force = true)
                                isCheckingUpdate = false
                                if (update != null) {
                                    onShowUpdate(update)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "¡MichiTV está actualizado a la última versión (v${AppUpdateManager.getCurrentVersionName(context)})!",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = MichiCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Buscando...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Comprobar Actualizaciones", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = { showGhConfigDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(0.7f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Repo GitHub",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buscar actualizaciones automáticamente al iniciar",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontFamily = OutfitFontFamily,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = autoCheckEnabled,
                        onCheckedChange = { checked ->
                            autoCheckEnabled = checked
                            AppUpdateManager.setAutoCheckEnabled(context, checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MichiCyan,
                            checkedTrackColor = MichiCyan.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= SECCIÓN 3: REPRODUCTOR Y STREAMING =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.PlayCircle, contentDescription = null, tint = MichiOrange, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Preferencias de Reproducción",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFontFamily,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Ajusta las opciones por defecto para pistas de audio, doblaje y servidores de extracción.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Idioma de Audio Preferido", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = OutfitFontFamily)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val langs = listOf("Español Latino", "Castellano", "Subtitulado")
                    items(langs) { l ->
                        MichiButton(
                            text = l,
                            isPrimary = preferredLang == l,
                            onClick = { preferredLang = l }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Text(text = "Servidor de Video Predilecto", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = OutfitFontFamily)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val servers = listOf("Voe (Recomendado)", "FuegoCine Direct", "StreamWish")
                    items(servers) { s ->
                        MichiButton(
                            text = s,
                            isPrimary = preferredServer == s,
                            onClick = { preferredServer = s }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(18.dp))
                    Text(
                        text = "Reanudación automática (Continuar Viendo) activa con base de datos local Room.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = OutfitFontFamily
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= SECCIÓN 4: ACTIVACIÓN Y LICENCIA TELEGRAM =================
        val isMobile = LocalConfiguration.current.screenWidthDp < 650
        val rawBotName = activationInfo.botUsername.removePrefix("@")
        val telegramDeepLink = "https://t.me/$rawBotName?start=activar_${activationInfo.deviceCode}"
        val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?size=360x360&data=" +
                java.net.URLEncoder.encode(telegramDeepLink, "UTF-8") + "&margin=12"
        var showQrOnMobile by remember { mutableStateOf(false) }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (activationInfo.isActivated) Color(0xFF00E676).copy(alpha = 0.5f) else MichiOrange.copy(alpha = 0.5f),
                    RoundedCornerShape(18.dp)
                )
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                // Cabecera de la sección
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (activationInfo.isActivated) Color(0xFF00E676).copy(alpha = 0.15f) else MichiOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VpnKey,
                                contentDescription = null,
                                tint = if (activationInfo.isActivated) Color(0xFF00E676) else MichiOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Licencia y Activación MichiTV 🐾",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OutfitFontFamily,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Asistente inteligente MichiBot en Telegram (@${rawBotName})",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                fontFamily = OutfitFontFamily
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (activationInfo.isActivated) Color(0xFF00E676).copy(alpha = 0.15f)
                                else Color(0xFFFF9100).copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (activationInfo.isActivated) Color(0xFF00E676) else Color(0xFFFF9100),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (activationInfo.isActivated) "ACTIVO • VIP 👑" else "MODO GRATUITO",
                            color = if (activationInfo.isActivated) Color(0xFF00E676) else Color(0xFFFF9100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Diálogo de Celebración VIP
                if (showCelebrationDialog) {
                    AlertDialog(
                        onDismissRequest = { showCelebrationDialog = false },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(text = "🎉", fontSize = 26.sp)
                                Text(
                                    text = "¡MichiTV VIP Activado!",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676),
                                    fontSize = 20.sp
                                )
                            }
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "🐾 ¡Miau! Tu dispositivo ha sido verificado con éxito por MichiBot.",
                                    fontFamily = OutfitFontFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "👑 Plan: ${activationInfo.planName}",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MichiOrange,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "¡Ya tienes acceso completo e ilimitado a todo el catálogo en 4K UHD, scrapers rápidos y sin cortes!",
                                    fontFamily = OutfitFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showCelebrationDialog = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(text = "¡Empezar a Disfrutar! 🚀", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = OutfitFontFamily)
                            }
                        },
                        containerColor = if (isDarkTheme) MichiDarkSurfaceElevated else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp)
                    )
                }

                // Indicador de Escaneo en Vivo
                if (!activationInfo.isActivated) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MichiCyan.copy(alpha = 0.12f))
                            .border(1.dp, MichiCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.25f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(700, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse_alpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E676).copy(alpha = alpha))
                            )
                            Text(
                                text = "ESPERANDO ACTIVACIÓN EN VIVO...",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MichiCyan,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    isManualChecking = true
                                    val res = withContext(Dispatchers.IO) {
                                        TelegramActivationManager.checkRemoteStatus(context)
                                    }
                                    isManualChecking = false
                                    if (res != null && res.isActivated) {
                                        activationInfo = res
                                        showCelebrationDialog = true
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Aún no activado en @$rawBotName. Escanea el QR o usa el botón.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isManualChecking,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isManualChecking) "Comprobando..." else "🔄 Comprobar Ahora",
                                fontFamily = OutfitFontFamily,
                                color = MichiOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Tarjetas de Identificación: Código de Dispositivo y PIN TV
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tarjeta Código de Dispositivo
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MichiCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "CÓDIGO DISPOSITIVO",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activationInfo.deviceCode,
                                color = MichiCyan,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = OutfitFontFamily,
                                letterSpacing = 1.5.sp
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(activationInfo.deviceCode))
                                    Toast.makeText(context, "¡Código copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copiar código",
                                    tint = MichiCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Tarjeta PIN Numérico ultracorto
                    Column(
                        modifier = Modifier
                            .weight(0.9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MichiOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "PIN RÁPIDO (TV)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activationInfo.numericPin,
                                color = MichiOrange,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = OutfitFontFamily,
                                letterSpacing = 3.sp
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(activationInfo.numericPin))
                                    Toast.makeText(context, "¡PIN copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copiar PIN",
                                    tint = MichiOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Botón Prueba VIP Gratis de 7 Días (1 Toque)
                if (!activationInfo.isActivated) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val ok = TelegramActivationManager.activateFreeTrial(context)
                            if (ok) {
                                activationInfo = TelegramActivationManager.getActivationInfo(context)
                                showCelebrationDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB300)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "🎁", fontSize = 16.sp)
                            Text(
                                text = "Activar Prueba VIP Gratis de 7 Días (1 Toque)",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = OutfitFontFamily,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ============ ADAPTACIÓN TV vs MÓVIL ============
                if (!isMobile) {
                    // MODO SMART TV: Tarjeta con Código QR para escanear con el móvil
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // QR Code renderizado
                        Box(
                            modifier = Modifier
                                .size(170.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = qrCodeUrl,
                                contentDescription = "Código QR de Activación con @$rawBotName",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Instrucciones paso a paso en TV
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MichiBadge(text = "📷 ESCANEO RÁPIDO PARA TV", isAccent = true)
                            }
                            Text(
                                text = "¡Tu TV se activará sola en tiempo real!",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = OutfitFontFamily
                            )
                            Text(
                                text = "1. Abre la cámara de tu celular y apunta al código QR.\n" +
                                        "2. Toca el enlace para abrir Telegram con @${rawBotName}.\n" +
                                        "3. ¡Listo! Esta pantalla se activará automáticamente al instante sin tener que escribir nada con el control remoto. 🍿🐾",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                fontFamily = OutfitFontFamily
                            )
                        }
                    }
                } else {
                    // MODO CELULAR: Acceso Directo de un toque al chat de Telegram
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MichiCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MichiBadge(text = "📱 ACCESO DIRECTO MÓVIL", isAccent = true)
                        }

                        Text(
                            text = "Activación instantánea en tu teléfono",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = OutfitFontFamily
                        )

                        Text(
                            text = "Toca el botón para abrir Telegram directamente. Al iniciar el bot con tu código, ¡la app se activará de inmediato!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = OutfitFontFamily
                        )

                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(telegramDeepLink))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "No se pudo abrir Telegram. Abre @$rawBotName manualmente.", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MichiCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🐾 Abrir en Telegram (@$rawBotName)",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = OutfitFontFamily
                            )
                        }

                        // Opción para mostrar QR en caso de querer escanearlo desde otro dispositivo
                        TextButton(
                            onClick = { showQrOnMobile = !showQrOnMobile },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                text = if (showQrOnMobile) "Ocultar Código QR" else "Mostrar Código QR (para escanear con otro equipo)",
                                color = MichiOrange,
                                fontSize = 12.sp,
                                fontFamily = OutfitFontFamily
                            )
                        }

                        if (showQrOnMobile) {
                            Box(
                                modifier = Modifier
                                    .size(180.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = qrCodeUrl,
                                    contentDescription = "Código QR",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canje de Clave / Voucher
                Text(
                    text = "O ingresa tu PIN, voucher o clave de activación:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputVoucherKey,
                        onValueChange = { inputVoucherKey = it },
                        placeholder = { Text("PIN (${activationInfo.numericPin}), Voucher o Clave", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MichiOrange,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Button(
                        onClick = {
                            if (inputVoucherKey.isNotBlank()) {
                                val success = TelegramActivationManager.activateWithKey(context, inputVoucherKey)
                                if (success) {
                                    activationInfo = TelegramActivationManager.getActivationInfo(context)
                                    inputVoucherKey = ""
                                    showCelebrationDialog = true
                                } else {
                                    Toast.makeText(context, "Clave o PIN no válido. Verifica con @$rawBotName", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MichiOrange),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(text = "Activar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (activationInfo.isActivated) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF00E676).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF00E676).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Suscripción: ${activationInfo.planName}",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = OutfitFontFamily
                            )
                            Text(
                                text = "Activado: ${activationInfo.activatedAt ?: "Reciente"}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = OutfitFontFamily
                            )
                        }

                        TextButton(
                            onClick = {
                                TelegramActivationManager.deactivate(context)
                                activationInfo = TelegramActivationManager.getActivationInfo(context)
                                Toast.makeText(context, "Dispositivo desvinculado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(text = "Desvincular", color = Color.Red.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= SECCIÓN 5: ALMACENAMIENTO Y MANTENIMIENTO =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null, tint = MichiCyan, modifier = Modifier.size(22.dp))
                    Text(
                        text = "Almacenamiento y Rendimiento",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFontFamily,
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Limpia la memoria caché temporal de video y carátulas si experimentas lentitud.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )

                Spacer(modifier = Modifier.height(16.dp))

                MichiButton(
                    text = "Limpiar Caché y Reiniciar Memoria",
                    icon = Icons.Filled.DeleteSweep,
                    onClick = {
                        try {
                            context.cacheDir.deleteRecursively()
                            MediaCache.clear()
                            Toast.makeText(context, "¡Caché liberada y memoria optimizada!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Caché limpiada", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Pie de Página Elegante
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "MichiTV Cinema OS • v${AppUpdateManager.getCurrentVersionName(context)}",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Desarrollado con Jetpack Compose • ExoPlayer Media3 • QuickJS Engine • Room 2.6",
                fontFamily = OutfitFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // Diálogo para personalizar dueño y repositorio de GitHub
    if (showGhConfigDialog) {
        var tempOwner by remember { mutableStateOf(ghOwner) }
        var tempRepo by remember { mutableStateOf(ghRepo) }

        AlertDialog(
            onDismissRequest = { showGhConfigDialog = false },
            containerColor = Color(0xFF181A26),
            title = {
                Text(text = "Configuración del Repositorio GitHub", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Especifica el usuario/organización y el nombre del repositorio donde publicas los releases:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = tempOwner,
                        onValueChange = { tempOwner = it },
                        label = { Text("Usuario / Organización (Owner)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MichiCyan,
                            unfocusedBorderColor = MichiBorder,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    OutlinedTextField(
                        value = tempRepo,
                        onValueChange = { tempRepo = it },
                        label = { Text("Nombre del Repositorio (Repo)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MichiCyan,
                            unfocusedBorderColor = MichiBorder,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ghOwner = tempOwner.trim()
                        ghRepo = tempRepo.trim()
                        AppUpdateManager.setGithubRepoConfig(context, ghOwner, ghRepo)
                        showGhConfigDialog = false
                        Toast.makeText(context, "Repositorio actualizado a $ghOwner/$ghRepo", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MichiCyan)
                ) {
                    Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGhConfigDialog = false }) {
                    Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
fun DetailScreen(
    item: CatalogItem,
    onBack: () -> Unit,
    onPlay: (request: PlayRequest) -> Unit
) {
    val dao = LocalMichiDao.current
    val history by dao.getWatchHistory().collectAsState(initial = emptyList<WatchHistory>())
    val isInList by dao.isInWatchlist(item.id).collectAsState(initial = false)
    val initialMeta = remember(item.id) {
        if (!item.pluginId.isNullOrEmpty() || !item.description.isNullOrEmpty()) {
            DetailMeta(
                id = item.id,
                name = item.name,
                description = item.description,
                poster = item.poster,
                background = item.background,
                year = item.year,
                type = item.type
            )
        } else null
    }

    var details by remember { mutableStateOf<DetailMeta?>(initialMeta) }
    var pluginEpisodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var episodePluginId by remember { mutableStateOf(item.pluginId ?: "") }
    var loading by remember { mutableStateOf(initialMeta == null) }
    var resolvingStream by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scope = coroutineScope

    LaunchedEffect(item.id) {
        // 1. Cargar episodios de serie desde el plugin
        if (item.type == "series" && !item.pluginId.isNullOrEmpty() && !item.ref.isNullOrEmpty()) {
            val eps = KinoPluginEngine.getEpisodes(context, item.pluginId, item.ref)
            if (eps.isNotEmpty()) {
                pluginEpisodes = eps
            }
        }

        // 1b. Series sin plugin (fichas Cinemeta): buscar capitulos por nombre
        if (item.type == "series" && pluginEpisodes.isEmpty()) {
            try {
                val (src, eps) = KinoPluginEngine.getEpisodesByName(context, item.name, item.year)
                if (eps.isNotEmpty()) {
                    pluginEpisodes = eps
                    episodePluginId = src
                }
            } catch (e: Exception) { }
        }

        // 2. Si no tiene metadatos completos y es un ID de IMDB (tt...), buscar en Cinemeta
        if (details == null && item.id.startsWith("tt")) {
            val meta = CatalogService.getDetails(item.type, item.id)
            if (meta != null) details = meta
        }
        loading = false
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isMobile = maxWidth < 650.dp
        val bg = details?.background ?: item.background ?: item.poster

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MichiOrange)
            }
        } else {
            val meta = details ?: DetailMeta(
                id = item.id,
                name = item.name,
                description = item.description,
                poster = item.poster,
                year = item.year,
                type = item.type
            )
            val episodesToShow = if (pluginEpisodes.isNotEmpty()) pluginEpisodes else meta.videos

            if (isMobile) {
                // MODO TELÉFONO / PANTALLA VERTICAL (Solución definitiva para pantallas estrechas / móvil)
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Cabecera Hero con Backdrop y degradado hacia negro
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(290.dp)
                                .background(MichiCardBackground)
                        ) {
                            if (!bg.isNullOrEmpty()) {
                                AsyncImage(
                                    model = bg,
                                    contentDescription = meta.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(HeroBottomScrim)
                            )
                        }

                        // 2. Bloque de Información Completo
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-24).dp)
                                .padding(horizontal = 20.dp)
                        ) {
                            // Título completo en Outfit Bold a todo el ancho (sin colapso)
                            Text(
                                text = meta.name,
                                fontFamily = OutfitFontFamily,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 30.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Fila de Badges
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                meta.year?.let {
                                    MichiBadge(text = it)
                                }
                                MichiBadge(
                                    text = if (meta.type == "series") "SERIE TV" else "PELÍCULA",
                                    isAccent = true
                                )
                                MichiBadge(text = "4K ULTRA HD")
                                MichiBadge(text = "🐾 MICHITV", isGold = true)
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (resolvingStream) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    CircularProgressIndicator(color = MichiOrange, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        "Conectando stream de MichiTV...",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 13.sp
                                    )
                                }
                            } else {
                                if (meta.type != "series" || pluginEpisodes.isEmpty()) {
                                    MichiButton(
                                        text = "▶ Reproducir Título",
                                        isPrimary = true,
                                        icon = Icons.Default.PlayArrow,
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = {
                                            scope.launch {
                                                resolvingStream = true
                                                var resolved = KinoPluginEngine.resolveStream(context, item.pluginId ?: "", item.ref ?: "")
                                                if (resolved == null) {
                                                    resolved = KinoPluginEngine.resolveByName(context, item.name)
                                                }
                                                resolvingStream = false
                                                if (resolved != null && resolved.url.isNotBlank()) {
                                                    val startMs = history.find { it.id == item.id }?.progressMs ?: 0L
                                                    onPlay(PlayRequest(resolved.url, resolved.headers, meta.name, item, null, null, startMs))
                                                } else {
                                                    Toast.makeText(context, "No se encontró un stream activo para este título.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        MichiButton(
                                            text = if (isInList) "En Mi Lista" else "+ Mi Lista",
                                            icon = if (isInList) Icons.Default.Check else Icons.Default.Add,
                                            isPrimary = isInList,
                                            onClick = {
                                                scope.launch {
                                                    if (isInList) {
                                                        dao.removeFromWatchlistById(item.id)
                                                        Toast.makeText(context, "Eliminado de Mi Lista", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        dao.addToWatchlist(
                                                            WatchlistItem(
                                                                id = item.id,
                                                                title = meta.name,
                                                                poster = meta.poster ?: item.poster,
                                                                type = meta.type,
                                                                pluginId = item.pluginId ?: "",
                                                                ref = item.ref ?: "",
                                                                background = meta.background ?: item.background,
                                                                year = meta.year,
                                                                description = meta.description
                                                            )
                                                        )
                                                        Toast.makeText(context, "Agregado a Mi Lista", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MichiButton(
                                    text = if (isInList) "En Mi Lista" else "Mi Lista",
                                    icon = if (isInList) Icons.Default.Check else Icons.Default.Add,
                                    isPrimary = isInList,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        scope.launch {
                                            if (isInList) {
                                                dao.removeFromWatchlistById(item.id)
                                                Toast.makeText(context, "Eliminado de Mi Lista", Toast.LENGTH_SHORT).show()
                                            } else {
                                                dao.addToWatchlist(
                                                    WatchlistItem(
                                                        id = item.id,
                                                        title = meta.name,
                                                        poster = meta.poster ?: item.poster,
                                                        type = meta.type,
                                                        pluginId = item.pluginId ?: "",
                                                        ref = item.ref ?: "",
                                                        background = meta.background ?: item.background,
                                                        year = meta.year,
                                                        description = meta.description
                                                    )
                                                )
                                                Toast.makeText(context, "Agregado a Mi Lista", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                                MichiButton(
                                    text = "Compartir",
                                    icon = Icons.Default.Share,
                                    modifier = Modifier.weight(1f),
                                    onClick = { }
                                )
                            }

                            meta.description?.let { desc ->
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = "Sinopsis",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = desc,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 19.sp
                                )
                            }

                            if (episodesToShow.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Episodios y Temporadas",
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    MichiBadge(text = "${episodesToShow.size} caps", isAccent = true)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    episodesToShow.forEach { ep ->
                                        MichiEpisodeCard(
                                            episode = ep,
                                            onClick = {
                                                scope.launch {
                                                    resolvingStream = true
                                                    var resolved = KinoPluginEngine.resolveStream(context, episodePluginId.ifEmpty { item.pluginId ?: "" }, ep.ref ?: ep.id ?: item.ref ?: "")
                                                    if (resolved == null) {
                                                        resolved = KinoPluginEngine.resolveByName(context, "${meta.name} ${ep.name ?: ""}")
                                                    }
                                                    resolvingStream = false
                                                    val epTitle = "${meta.name} - T${ep.season} E${ep.number}: ${ep.name ?: ""}"
                                                    if (resolved != null && resolved.url.isNotBlank()) {
                                                        val startMs = history.find { it.id == item.id && it.seasonNumber == ep.season && it.episodeNumber == ep.number }?.progressMs ?: 0L
                                                                onPlay(PlayRequest(resolved.url, resolved.headers, epTitle, item, ep.season, ep.number, startMs))
                                                    } else {
                                                        Toast.makeText(context, "No se encontró stream para este episodio.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }

                    // Botón flotante Volver estilo frosted glass
                    MichiIconButton(
                        icon = Icons.Default.ArrowBack,
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(top = 16.dp, start = 16.dp),
                        backgroundColor = Color(0x9908090D)
                    )
                }
            } else {
                // MODO SMART TV / PANTALLA ANCHA (Cinematic 16:9)
                Box(modifier = Modifier.fillMaxSize()) {
                    if (!bg.isNullOrEmpty()) {
                        AsyncImage(
                            model = bg,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(HeroLeftScrim)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        MichiButton(
                            text = "← Volver",
                            onClick = onBack,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Row(modifier = Modifier.fillMaxSize()) {
                            meta.poster?.let { posterUrl ->
                                AsyncImage(
                                    model = posterUrl,
                                    contentDescription = meta.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(300.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(1.5.dp, MichiBorder, RoundedCornerShape(14.dp))
                                )
                                Spacer(modifier = Modifier.width(32.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.name,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    meta.year?.let { MichiBadge(text = it) }
                                    MichiBadge(
                                        text = if (meta.type == "series") "SERIE TV" else "PELÍCULA",
                                        isAccent = true
                                    )
                                    MichiBadge(text = "4K ULTRA HD")
                                    MichiBadge(text = "🐾 MICHITV", isGold = true)
                                }

                                meta.description?.let { desc ->
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = desc,
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 20.sp,
                                        maxLines = 4,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(18.dp))

                                if (resolvingStream) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(color = MichiOrange, modifier = Modifier.size(26.dp))
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Text("Conectando stream de MichiTV...", color = MaterialTheme.colorScheme.onSurface, fontFamily = OutfitFontFamily)
                                    }
                                } else {
                                    if (meta.type != "series" || pluginEpisodes.isEmpty()) {
                                        MichiButton(
                                            text = "▶ Reproducir Título",
                                            isPrimary = true,
                                            icon = Icons.Default.PlayArrow,
                                            onClick = {
                                                scope.launch {
                                                    resolvingStream = true
                                                    var resolved = KinoPluginEngine.resolveStream(context, item.pluginId ?: "", item.ref ?: "")
                                                    if (resolved == null) {
                                                        resolved = KinoPluginEngine.resolveByName(context, item.name)
                                                    }
                                                    resolvingStream = false
                                                    if (resolved != null && resolved.url.isNotBlank()) {
                                                        val startMs = history.find { it.id == item.id }?.progressMs ?: 0L
                                                    onPlay(PlayRequest(resolved.url, resolved.headers, meta.name, item, null, null, startMs))
                                                    } else {
                                                        Toast.makeText(context, "No se encontró un stream activo para este título.", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            }
                                        )
                                    }

                                    if (episodesToShow.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(18.dp))
                                        Text(
                                            text = "Episodios Disponibles (${episodesToShow.size})",
                                            fontFamily = OutfitFontFamily,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            items(episodesToShow) { ep ->
                                                MichiEpisodeCard(
                                                    episode = ep,
                                                    onClick = {
                                                        scope.launch {
                                                            resolvingStream = true
                                                            var resolved = KinoPluginEngine.resolveStream(context, episodePluginId.ifEmpty { item.pluginId ?: "" }, ep.ref ?: ep.id ?: item.ref ?: "")
                                                            if (resolved == null) {
                                                                resolved = KinoPluginEngine.resolveByName(context, "${meta.name} ${ep.name ?: ""}")
                                                            }
                                                            resolvingStream = false
                                                            val epTitle = "${meta.name} - T${ep.season} E${ep.number}: ${ep.name ?: ""}"
                                                            if (resolved != null && resolved.url.isNotBlank()) {
                                                                val startMs = history.find { it.id == item.id && it.seasonNumber == ep.season && it.episodeNumber == ep.number }?.progressMs ?: 0L
                                                                onPlay(PlayRequest(resolved.url, resolved.headers, epTitle, item, ep.season, ep.number, startMs))
                                                            } else {
                                                                Toast.makeText(context, "No se encontró stream para este episodio.", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TvPlayerScreen(
    request: PlayRequest,
    onBack: () -> Unit
) {
    val videoUrl = request.url
    val headers = request.headers
    val title = request.title

    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val dao = LocalMichiDao.current

    val exoPlayer = remember(videoUrl) {
        val okHttpDataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(NetworkHelper.okHttpClient)

        val fullHeaders = headers.toMutableMap()
        val uri = android.net.Uri.parse(videoUrl)
        val host = uri.host
        if (!host.isNullOrEmpty()) {
            if (!fullHeaders.containsKey("Referer") && !fullHeaders.containsKey("referer")) {
                fullHeaders["Referer"] = "${uri.scheme ?: "https"}://$host/"
            }
        }
        if (!fullHeaders.containsKey("User-Agent") && !fullHeaders.containsKey("user-agent")) {
            fullHeaders["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }

        fullHeaders.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) {
                okHttpDataSourceFactory.setUserAgent(v)
            }
        }
        okHttpDataSourceFactory.setDefaultRequestProperties(fullHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                val mediaItem = MediaItem.fromUri(videoUrl)
                setMediaItem(mediaItem)
                if (request.startPositionMs > 0) {
                    seekTo(request.startPositionMs)
                }
                prepare()
                playWhenReady = true
            }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            if (exoPlayer.isPlaying && exoPlayer.duration > 0 && request.item != null) {
                val progress = exoPlayer.currentPosition
                val duration = exoPlayer.duration
                val percentage = progress.toFloat() / duration
                if (percentage > 0.02 && percentage < 0.98) {
                    dao.saveWatchHistory(
                        WatchHistory(
                            id = request.item.id,
                            title = request.item.name,
                            poster = request.item.poster,
                            type = request.item.type,
                            pluginId = request.item.pluginId ?: "",
                            ref = request.item.ref ?: "",
                            progressMs = progress,
                            durationMs = duration,
                            seasonNumber = request.season,
                            episodeNumber = request.episode,
                            episodeTitle = request.title
                        )
                    )
                }
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == androidx.media3.common.Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("TvPlayerScreen", "Error de reproducción ExoPlayer: ${error.errorCodeName} - ${error.message}", error)
                errorMessage = "Error de reproducción: ${error.errorCodeName}"
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && errorMessage == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(48.dp))
            }
        }

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xDD000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Aviso de Reproducción", color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, color = Color(0xFFFFAAAA), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    MichiButton(
                        text = "Reintentar ↻",
                        isPrimary = true,
                        onClick = {
                            errorMessage = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    )
                }
            }
        }

        // Overlay con botón Volver y Título
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MichiButton(
                text = "← Salir",
                onClick = onBack,
                icon = Icons.Default.ArrowBack
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MichiEpisodeCard(episode: EpisodeItem, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MichiOrange else MichiBorder,
        label = "ep_border"
    )

    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .focusable(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(105.dp)
                .background(MichiCardBackground),
            contentAlignment = Alignment.Center
        ) {
            episode.thumbnail?.let { thumb ->
                AsyncImage(
                    model = thumb,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MichiOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Text(
            text = "T${episode.season} E${episode.number}: ${episode.name ?: "Episodio"}",
            fontFamily = OutfitFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun MichiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    MichiButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        isPrimary = isPrimary,
        shapeRadius = 12.dp
    )
}

@Composable
fun TvMovieCard(
    item: CatalogItem,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    MichiMovieCard(
        item = item,
        onFocus = onFocus,
        onClick = onClick
    )
}

@Composable
fun TvEpisodeCard(
    episode: EpisodeItem,
    onClick: () -> Unit
) {
    MichiEpisodeCard(
        episode = episode,
        onClick = onClick
    )
}











@Composable
fun MichiMaintenanceScreen(
    message: String,
    timeLeftMs: Long?
) {
    var remainingMs by remember(timeLeftMs) { mutableStateOf(timeLeftMs) }
    LaunchedEffect(timeLeftMs) {
        while (true) {
            val r = remainingMs ?: break
            if (r <= 0) break
            kotlinx.coroutines.delay(1000)
            remainingMs = (remainingMs ?: 0) - 1000
        }
    }
    val countdownText = remainingMs?.let {
        val totalSec = (it.coerceAtLeast(0) / 1000).toInt()
        val mm = totalSec / 60
        val ss = totalSec % 60
        "%02d:%02d".format(mm, ss)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05060A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Construction,
                contentDescription = null,
                tint = MichiOrange,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "MODO MANTENIMIENTO",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 26.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = Color(0xFFB0B3C0),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            if (countdownText != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Volvemos en:",
                    color = Color(0xFF8A8FA0),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = countdownText,
                    color = Color(0xFF40E0D0),
                    fontWeight = FontWeight.Black,
                    fontSize = 44.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "La app volvera sola cuando termine. No toques nada.",
                    color = Color(0xFF8A8FA0),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Volvemos en unos minutos. Gracias por tu paciencia.",
                    color = Color(0xFF8A8FA0),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MichiWhatsNewDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MichiOrange),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Entendido", color = Color.White, fontWeight = FontWeight.Bold) }
        },
        title = { Text("Novedades MichiTV 2.0.0", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Activacion con Telegram: usa /activar MICHI-XXXX o escanea el QR.")
                Text("Modo mantenimiento con cuenta regresiva automatica.")
                Text("Soporte tecnico integrado desde el bot.")
                Text("Reproduccion mas estable: servidor FC prioritario.")
                Text("Correcciones de disenio y rendimiento.")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun MichiActivationWallScreen(
    activationInfo: TelegramActivationInfo,
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    var inputKey by remember { mutableStateOf("") }
    val isMobile = LocalConfiguration.current.screenWidthDp < 650

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F111A)) // Dark OLED background
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(if (isMobile) 1f else 0.75f)
                .border(2.dp, MichiOrange.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(imageVector = Icons.Filled.VpnKey, contentDescription = null, tint = MichiOrange, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Dispositivo no activado",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Para disfrutar de MichiTV, vincula esta pantalla a tu cuenta.",
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                val rawBotName = activationInfo.botUsername.removePrefix("@")
                val deepLink = "https://t.me/${rawBotName}?start=activar_${activationInfo.deviceCode}"
                val qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${deepLink}"
                val clipboard = LocalClipboardManager.current

                // QR visible tanto en TV como en celular: escanea y activa sin escribir
                AsyncImage(
                    model = qrUrl,
                    contentDescription = "Codigo QR de activacion",
                    modifier = Modifier
                        .size(if (isMobile) 200.dp else 180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isMobile) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=${rawBotName}&start=activar_${activationInfo.deviceCode}"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                                context.startActivity(webIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2CA5E0)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir en Telegram (auto-activacion)", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("O escanea el QR desde otro telefono", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                } else {
                    Text("Escanea el QR con tu telefono", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text("O busca @${rawBotName} en Telegram y envia el codigo:", color = Color.Gray, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            clipboard.setText(AnnotatedString(activationInfo.deviceCode))
                            Toast.makeText(context, "Codigo copiado", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CODIGO: ", color = Color.LightGray, fontSize = 14.sp)
                    Text(activationInfo.deviceCode, color = MichiOrange, fontWeight = FontWeight.Black, fontSize = 26.sp)
                }
                Text("Toca el codigo para copiarlo", color = Color.Gray, fontSize = 11.sp)

                Spacer(modifier = Modifier.height(24.dp))
                
                // Manual Entry
                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    placeholder = { Text("PIN numerico o voucher") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MichiOrange,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        Button(
                            onClick = {
                                val ok = TelegramActivationManager.activateWithKey(context, inputKey)
                                if (ok) onActivated() else Toast.makeText(context, "Clave invalida", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MichiOrange),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Validar")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (TelegramActivationManager.activateFreeTrial(context)) onActivated()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    modifier = Modifier.border(1.dp, MichiOrange, RoundedCornerShape(12.dp))
                ) {
                    Text("Probar 7 dias gratis", color = MichiOrange)
                }
            }
        }
    }
}
