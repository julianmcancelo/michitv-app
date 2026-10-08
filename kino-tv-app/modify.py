import re

with open(r"app\src\main\java\com\kinotv\player\MainActivity.kt", "r", encoding="utf-8") as f:
    code = f.read()

# Add icon imports
imports = """
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lock
"""
if "import androidx.compose.material.icons.filled.Settings" not in code:
    code = code.replace("import androidx.compose.material.icons.filled.Search\n", "import androidx.compose.material.icons.filled.Search\n" + imports)


# Global ThemeState
if "object AppState" not in code:
    code = code.replace("class MainActivity : ComponentActivity() {", """
object AppState {
    var isDarkTheme = androidx.compose.runtime.mutableStateOf(true)
}

class MainActivity : ComponentActivity() {
""")

# Setup theme in setContent
setup = """
        setContent {
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                AppState.isDarkTheme.value = ThemePrefs.isDarkMode(context)
            }
            MichiTheme(darkTheme = AppState.isDarkTheme.value) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
"""

code = re.sub(r'setContent\s*\{\s*MichiTheme\s*\{\s*Surface\(modifier = Modifier\.fillMaxSize\(\), color = MichiBackground\)\s*\{', setup, code)

# In TvSettingsScreen, we need a toggle for Dark/Light mode!
# Find the end of Telegram Card
theme_toggle_ui = """
        // ================= TARJETA: APARIENCIA =================
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Apariencia de la App", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(text = "Cambiar entre Modo Oscuro y Modo Claro.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = if (AppState.isDarkTheme.value) "Oscuro" else "Claro", color = MaterialTheme.colorScheme.onSurface)
                    Switch(
                        checked = AppState.isDarkTheme.value,
                        onCheckedChange = { 
                            AppState.isDarkTheme.value = it
                            ThemePrefs.setDarkMode(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = MichiCyan, checkedTrackColor = MichiCyan.copy(alpha = 0.5f))
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
"""

# Inject it before OTA card
code = code.replace("// ================= TARJETA 2: ACTUALIZACIONES OTA =================", theme_toggle_ui + "\n        // ================= TARJETA 2: ACTUALIZACIONES OTA =================")


# Replace hardcoded colors with MaterialTheme colors in TvSettingsScreen
code = code.replace("Color(0xFF161622)", "MaterialTheme.colorScheme.surface")
code = code.replace("Color(0xFF161822)", "MaterialTheme.colorScheme.surfaceVariant")
code = code.replace("Color(0xFF101018)", "MaterialTheme.colorScheme.background")
code = code.replace("Color(0xFF262A3B)", "MaterialTheme.colorScheme.surfaceVariant")
code = code.replace("Color(0xFF0F1014)", "MaterialTheme.colorScheme.background")
code = code.replace("Color.White", "MaterialTheme.colorScheme.onSurface")
code = code.replace("Color(0xFFAAAAAA)", "MaterialTheme.colorScheme.onSurfaceVariant")
code = code.replace("MichiSurface", "MaterialTheme.colorScheme.surface")
code = code.replace("MichiBackground", "MaterialTheme.colorScheme.background")

# Remove emojis and use Icons!
code = code.replace('Text(text = "🐾", fontSize = 32.sp)', 'Icon(imageVector = Icons.Filled.Settings, contentDescription = null, tint = MichiOrange, modifier = Modifier.size(32.dp))')
code = code.replace('Text(text = "✈️", fontSize = 20.sp)', 'Icon(imageVector = Icons.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))')
code = code.replace('ACTIVO • VIP 👑', 'ACTIVO • VIP')
code = code.replace('Copiar 📋', 'Copiar')
code = code.replace('Activar ⚡', 'Activar')
code = code.replace('¡Dispositivo Activado con Éxito! 🐾', '¡Dispositivo Activado con Éxito!')
code = code.replace('Text(text = "🚀", fontSize = 20.sp)', 'Icon(imageVector = Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))')
code = code.replace('Text(text = "Buscar Actualizaciones 🔄"', 'Text(text = "Buscar Actualizaciones"')
code = code.replace('MichiTV ya está en su versión más reciente (v${AppUpdateManager.getCurrentVersionName(context)})! 🐾', '¡MichiTV está actualizado (v${AppUpdateManager.getCurrentVersionName(context)})!')
code = code.replace('MichiTV 🐾 v${', 'MichiTV v${')
code = code.replace('• Motor QuickJS & ExoPlayer Media3 ⚡', '• Motor QuickJS & ExoPlayer Media3')


# Remove Repo Configuration from OTA
repo_ui_pattern = r'MichiButton\(\s*text = "Repo: \$\{ghOwner\}",\s*onClick = \{ showGhConfigDialog = true \}\s*\)'
code = re.sub(repo_ui_pattern, '', code)

# Remove showGhConfigDialog entirely
dialog_pattern = r'if\s*\(showGhConfigDialog\)\s*\{.*?\}\s*\}'
code = re.sub(r'if \(!showGhConfigDialog.*?}\s*}', '', code, flags=re.DOTALL) # risky, let's do it manually

with open(r"app\src\main\java\com\kinotv\player\MainActivity.kt", "w", encoding="utf-8") as f:
    f.write(code)

