package com.kinotv.player

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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

data class PlayRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val title: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MichiTheme {
                MainAppNavigation()
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
            videoUrl = playingStream!!.url,
            headers = playingStream!!.headers,
            title = playingStream!!.title,
            onBack = { playingStream = null }
        )
    } else if (openedItem != null) {
        BackHandler {
            openedItem = null
        }
        DetailScreen(
            item = openedItem!!,
            onBack = { openedItem = null },
            onPlay = { url, headers, title ->
                playingStream = PlayRequest(url, headers, title)
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
                .background(MichiBackground)
        ) {
            val isMobile = maxWidth < 650.dp

            if (isMobile) {
                // Modo Teléfono / Pantalla Vertical (Bottom Navigation)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MichiBackground)
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
                        .background(MichiBackground)
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
            .background(MichiSurface)
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
            isFocused -> Color.White
            isSelected -> MichiOrange
            else -> MichiTextMuted
        }, label = "tint"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> MichiSurfaceElevated
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
                color = if (isFocused) Color.White else if (isSelected) MichiOrange else Color.Transparent,
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
    var selectedItem by remember { mutableStateOf<CatalogItem?>(null) }
    var rows by remember { mutableStateOf(MediaCache.homeRows ?: emptyList()) }
    var isLoading by remember { mutableStateOf(MediaCache.homeRows == null) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (MediaCache.homeRows == null) {
            val allRows = mutableListOf<CatalogRow>()
            val plugins = PluginManager.getInstalledPlugins(context).filter { it.isEnabled }

            // Prioritize real movies/series (latino, fuegocine, etc.) and append others
            val sortedPlugins = plugins.sortedBy { 
                when (it.id) {
                    "fuegocine" -> 0
                    "latino" -> 1
                    "animeav1" -> 2
                    "iptv-org" -> 3
                    else -> 4
                }
            }

            for (plugin in sortedPlugins) {
                if (plugin.id == "iptv-org") continue // iptv handled in Live screen
                try {
                    val pRows = KinoPluginEngine.getHomeRows(context, plugin.id)
                    if (pRows.isNotEmpty()) allRows.addAll(pRows)
                    
                    if (plugin.id == "latino") {
                        val latinoMovies = KinoPluginEngine.browse(context, "latino", "latest:hackstore:movie")
                        if (latinoMovies.isNotEmpty()) {
                            allRows.add(CatalogRow("Películas Latino", latinoMovies))
                        }
                        val latinoSeries = KinoPluginEngine.browse(context, "latino", "latest:hackstore:tv")
                        if (latinoSeries.isNotEmpty()) {
                            allRows.add(CatalogRow("Series Latino", latinoSeries))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error loading home rows for plugin ${plugin.id}", e)
                }
            }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MichiBackground)
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
                        color = Color.White,
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
                                color = MichiTextSecondary,
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
                            color = MichiTextSecondary,
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
                    items(rows) { row ->
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
                                        color = Color.White
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
            .background(MichiBackground)
            .padding(24.dp)
    ) {
        Text(
            text = "Películas y Estrenos",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
            .background(MichiBackground)
            .padding(24.dp)
    ) {
        Text(
            text = "Series de Televisión y Anime",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Temporadas completas, episodios y especiales para disfrutar en MichiTV 🐾",
            fontFamily = OutfitFontFamily,
            fontSize = 13.sp,
            color = MichiTextSecondary,
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
            .background(MichiBackground)
            .padding(24.dp)
    ) {
        Text(
            text = "Televisión En Vivo (IPTV)",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Canales de televisión en vivo transmitidos en tiempo real por streaming HLS.",
            fontFamily = OutfitFontFamily,
            fontSize = 13.sp,
            color = MichiTextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MichiOrange)
            }
        } else if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No hay canales disponibles o el plugin iptv-org no está activo.", color = MichiTextMuted, fontFamily = OutfitFontFamily)
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
                                color = Color.White
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
            .background(if (isFocused) MichiSurfaceElevated else MichiSurface)
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
                    .background(MichiSurfaceElevated, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LiveTv, contentDescription = null, tint = MichiOrange)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = channel.name,
            fontFamily = OutfitFontFamily,
            color = if (isFocused) Color.White else MichiTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@androidx.compose.foundation.ExperimentalFoundationApi
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
            .background(MichiBackground)
            .padding(24.dp)
    ) {
        Text(
            text = "Buscar en MichiTV 🐾",
            fontFamily = OutfitFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = MichiOrange,
                    focusedLabelColor = MichiOrange,
                    unfocusedLabelColor = MichiTextMuted
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
                Text("No se encontraron resultados para '$query' en MichiTV.", color = MichiTextMuted, fontFamily = OutfitFontFamily)
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
                        modifier = Modifier.animateItemPlacement()
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
                    color = Color.White
                )
                Text(
                    text = "Administra, activa o instala extensiones de catálogo y reproducción de MichiTV.",
                    fontSize = 14.sp,
                    color = Color(0xFFAAAAAA)
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
                text = "Cat�logo MichiTV & Stremio (${catalogPlugins.size})",
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
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
                                        Text(text = p.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = "v${p.version}", color = Color(0xFF888899), fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        val statusBg = if (p.isEnabled) Color(0xFF2E7D32) else Color(0xFF555566)
                                        Text(
                                            text = if (p.isEnabled) "ACTIVO" else "INACTIVO",
                                            color = Color.White,
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
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
                                        Text(text = item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                        .background(Color(0xFF161622), RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Registrar Plugin por Enlace o Repositorio",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ingresa la URL de un manifiesto michitv-plugin.json o enlace directo de GitHub para descargar y registrar el plugin en la aplicación.",
                        color = Color(0xFFAAAAAA),
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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

    var preferredServer by remember { mutableStateOf("Voe (Recomendado)") }
    var preferredLang by remember { mutableStateOf("Español Latino") }

    // Activación Telegram
    var activationInfo by remember { mutableStateOf(TelegramActivationManager.getActivationInfo(context)) }
    var inputVoucherKey by remember { mutableStateOf("") }

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "🐾", fontSize = 32.sp)
            Column {
                Text(
                    text = "Ajustes y Configuración",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = OutfitFontFamily,
                    color = Color.White
                )
                Text(
                    text = "Personaliza tu experiencia, vincula con Telegram y mantén MichiTV al día",
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily,
                    color = MichiTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= TARJETA 1: ACTIVACIÓN TELEGRAM BOT =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MichiSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (activationInfo.isActivated) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF9100).copy(alpha = 0.5f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "✈️", fontSize = 20.sp)
                        Text(
                            text = "Licencia Telegram",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Badge de Estado
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (activationInfo.isActivated)
                                    Color(0xFF00E676).copy(alpha = 0.15f)
                                else
                                    Color(0xFFFF9100).copy(alpha = 0.15f)
                            )
                            .border(
                                1.dp,
                                if (activationInfo.isActivated) Color(0xFF00E676) else Color(0xFFFF9100),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (activationInfo.isActivated) "ACTIVO • VIP 👑" else "MODO GRATUITO",
                            color = if (activationInfo.isActivated) Color(0xFF00E676) else Color(0xFFFF9100),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Código del Dispositivo
                Text(
                    text = "Código Único de tu Dispositivo:",
                    color = MichiTextMuted,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101018))
                        .border(1.dp, MichiCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activationInfo.deviceCode,
                        color = MichiCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = OutfitFontFamily,
                        letterSpacing = 2.sp
                    )

                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(activationInfo.deviceCode))
                            Toast.makeText(context, "¡Código ${activationInfo.deviceCode} copiado!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MichiCyan.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Copiar 📋", color = MichiCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pasos de activación con Telegram
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF161822))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Cómo activar con el Bot oficial:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = OutfitFontFamily
                    )
                    Text(
                        text = "1. Abre Telegram y busca ${activationInfo.botUsername}",
                        color = MichiTextMuted,
                        fontSize = 12.sp,
                        fontFamily = OutfitFontFamily
                    )
                    Text(
                        text = "2. Envía el comando: /activar ${activationInfo.deviceCode}",
                        color = MichiOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = OutfitFontFamily
                    )
                    Text(
                        text = "3. O ingresa abajo la clave provista por el bot:",
                        color = MichiTextMuted,
                        fontSize = 12.sp,
                        fontFamily = OutfitFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Input para voucher o clave manual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputVoucherKey,
                        onValueChange = { inputVoucherKey = it },
                        placeholder = { Text("Clave / Voucher (ej. MICHI-VIP-2026)", fontSize = 13.sp, color = MichiTextMuted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MichiOrange,
                            unfocusedBorderColor = MichiBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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
                                    Toast.makeText(context, "¡Dispositivo Activado con Éxito! 🐾", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Clave no válida. Verifica con el bot.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MichiOrange),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Text(text = "Activar ⚡", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (activationInfo.isActivated) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Plan: ${activationInfo.planName} • Activado: ${activationInfo.activatedAt ?: "Reciente"}",
                            color = Color(0xFF00E676),
                            fontSize = 12.sp,
                            fontFamily = OutfitFontFamily
                        )

                        TextButton(
                            onClick = {
                                TelegramActivationManager.deactivate(context)
                                activationInfo = TelegramActivationManager.getActivationInfo(context)
                                Toast.makeText(context, "Dispositivo desvinculado", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(text = "Desvincular", color = Color.Red.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ================= TARJETA 2: ACTUALIZACIONES GITHUB RELEASES OTA =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MichiSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MichiBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "🚀", fontSize = 20.sp)
                        Text(
                            text = "Actualizaciones OTA",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF262A3B))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "v${AppUpdateManager.getCurrentVersionName(context)}",
                            color = MichiCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = OutfitFontFamily,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Comprueba e instala las últimas mejoras, parches de seguridad y nuevos plugins automáticamente desde el repositorio de GitHub.",
                    color = MichiTextMuted,
                    fontSize = 13.sp,
                    fontFamily = OutfitFontFamily
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                                        "¡MichiTV ya está en su versión más reciente (v${AppUpdateManager.getCurrentVersionName(context)})! 🐾",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        },
                        enabled = !isCheckingUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = MichiCyan),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "Buscando...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        } else {
                            Text(text = "Buscar Actualizaciones 🔄", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = { showGhConfigDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MichiBorder),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Repo: $ghOwner/$ghRepo",
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buscar actualizaciones automáticamente al iniciar",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontFamily = OutfitFontFamily
                    )
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

        // ================= TARJETA 3: PREFERENCIAS DE REPRODUCTOR =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MichiSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MichiBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = "Preferencias de Audio y Subtítulos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = OutfitFontFamily)
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

                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Servidor de Video Preferido", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = OutfitFontFamily)
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

                Spacer(modifier = Modifier.height(20.dp))
                MichiButton(
                    text = "Limpiar Caché de Plugins y Reproductor",
                    onClick = {
                        try {
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "Caché limpiada con éxito", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Caché limpiada", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "MichiTV 🐾 v${AppUpdateManager.getCurrentVersionName(context)} • Sistema de Streaming Inteligente • Motor QuickJS & ExoPlayer Media3",
            fontFamily = OutfitFontFamily,
            color = MichiTextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
    }

    // Diálogo para personalizar dueño y repositorio de GitHub
    if (showGhConfigDialog) {
        var tempOwner by remember { mutableStateOf(ghOwner) }
        var tempRepo by remember { mutableStateOf(ghRepo) }

        AlertDialog(
            onDismissRequest = { showGhConfigDialog = false },
            containerColor = Color(0xFF181A26),
            title = {
                Text(text = "Configuración del Repositorio GitHub", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Especifica el usuario/organización y el nombre del repositorio donde publicas los releases:",
                        color = MichiTextMuted,
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
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
                    Text("Cancelar", color = MichiTextMuted)
                }
            }
        )
    }
}

@Composable
fun DetailScreen(
    item: CatalogItem,
    onBack: () -> Unit,
    onPlay: (url: String, headers: Map<String, String>, title: String) -> Unit
) {
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
            .background(MichiBackground)
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
                                color = Color.White,
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
                                        .background(MichiSurface, RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    CircularProgressIndicator(color = MichiOrange, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        "Conectando stream de MichiTV...",
                                        color = Color.White,
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
                                                    onPlay(resolved.url, resolved.headers, meta.name)
                                                } else {
                                                    Toast.makeText(context, "No se encontró un stream activo para este título.", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                MichiButton(
                                    text = "🐾 Mi Lista",
                                    icon = Icons.Default.Add,
                                    modifier = Modifier.weight(1f),
                                    onClick = { }
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
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = desc,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 13.sp,
                                    color = MichiTextSecondary,
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
                                        color = Color.White
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
                                                    var resolved = KinoPluginEngine.resolveStream(context, item.pluginId ?: "", ep.id ?: item.ref ?: "")
                                                    if (resolved == null) {
                                                        resolved = KinoPluginEngine.resolveByName(context, "${meta.name} ${ep.name ?: ""}")
                                                    }
                                                    resolvingStream = false
                                                    val epTitle = "${meta.name} - T${ep.season} E${ep.number}: ${ep.name ?: ""}"
                                                    if (resolved != null && resolved.url.isNotBlank()) {
                                                        onPlay(resolved.url, resolved.headers, epTitle)
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
                                    color = Color.White
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
                                        color = MichiTextSecondary,
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
                                        Text("Conectando stream de MichiTV...", color = Color.White, fontFamily = OutfitFontFamily)
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
                                                        onPlay(resolved.url, resolved.headers, meta.name)
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
                                            color = Color.White,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                            items(episodesToShow) { ep ->
                                                MichiEpisodeCard(
                                                    episode = ep,
                                                    onClick = {
                                                        scope.launch {
                                                            resolvingStream = true
                                                            var resolved = KinoPluginEngine.resolveStream(context, item.pluginId ?: "", ep.id ?: item.ref ?: "")
                                                            if (resolved == null) {
                                                                resolved = KinoPluginEngine.resolveByName(context, "${meta.name} ${ep.name ?: ""}")
                                                            }
                                                            resolvingStream = false
                                                            val epTitle = "${meta.name} - T${ep.season} E${ep.number}: ${ep.name ?: ""}"
                                                            if (resolved != null && resolved.url.isNotBlank()) {
                                                                onPlay(resolved.url, resolved.headers, epTitle)
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
    videoUrl: String,
    headers: Map<String, String> = emptyMap(),
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                prepare()
                playWhenReady = true
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
                    Text(text = "Aviso de Reproducción", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                color = Color.White,
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
            .background(if (isFocused) MichiSurfaceElevated else MichiSurface)
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
            color = if (isFocused) Color.White else MichiTextSecondary,
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





