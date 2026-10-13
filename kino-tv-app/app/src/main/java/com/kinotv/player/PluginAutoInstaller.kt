package com.kinotv.player

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PluginAutoInstaller {
    suspend fun autoInstallDefault(context: Context) = withContext(Dispatchers.IO) {
        try {
            val installed = PluginManager.getInstalledPlugins(context)
            if (installed.isEmpty()) {
                val catalog = PluginManager.getAvailableCatalog(context)
                for (item in catalog) {
                    if (!item.isInstalled) {
                        val targetUrl = item.repo ?: item.stremioUrl ?: ""
                        if (targetUrl.isNotEmpty()) {
                            PluginManager.installPluginFromManifestUrl(context, targetUrl)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
