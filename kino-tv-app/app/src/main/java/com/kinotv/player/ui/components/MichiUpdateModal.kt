package com.kinotv.player.ui.components

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kinotv.player.ReleaseInfo
import com.kinotv.player.UpdateDownloadState
import com.kinotv.player.ui.theme.*
import java.io.File

@Composable
fun MichiUpdateModal(
    releaseInfo: ReleaseInfo,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstall: (File) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState !is UpdateDownloadState.Downloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC050608))
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(MichiOrange, Color(0xFF552200), MichiBorder)
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .shadow(24.dp, RoundedCornerShape(22.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10121A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    // Encabezado
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        Brush.linearGradient(listOf(MichiOrange, Color(0xFFFF8800))),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Nueva Versión Disponible",
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Actualización oficial desde GitHub",
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        MichiBadge(
                            text = releaseInfo.tagName,
                            isGold = true
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Información de Release
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF171924), RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = releaseInfo.releaseTitle.ifBlank { "Lanzamiento MichiTV ${releaseInfo.tagName}" },
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (releaseInfo.apkSize > 0) {
                            val mbSize = "%.1f MB".format(releaseInfo.apkSize / (1024.0 * 1024.0))
                            Text(
                                text = mbSize,
                                fontFamily = OutfitFontFamily,
                                fontSize = 12.sp,
                                color = MichiOrange
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Changelog / Notas de la versión
                    Text(
                        text = "Novedades y Mejoras:",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 160.dp)
                            .background(Color(0xFF0C0E14), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF222533), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = releaseInfo.changelog.ifBlank { "• Correcciones de estabilidad y rendimiento general.\n• Optimización en reproducción de canales y series." },
                            fontFamily = OutfitFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xFFDDDDDD),
                            lineHeight = 18.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Estado de descarga
                    when (downloadState) {
                        is UpdateDownloadState.Idle -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                MichiButton(
                                    text = "Más tarde",
                                    modifier = Modifier.weight(1f),
                                    onClick = onDismiss
                                )
                                MichiButton(
                                    text = "Actualizar Ahora 🚀",
                                    isPrimary = true,
                                    modifier = Modifier.weight(1.3f),
                                    onClick = onStartDownload
                                )
                            }
                        }

                        is UpdateDownloadState.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Descargando actualización...",
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 13.sp,
                                        color = MichiOrange,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${downloadState.progressPercent}%",
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                val animatedProgress by animateFloatAsState(
                                    targetValue = downloadState.progressPercent / 100f,
                                    label = "progress"
                                )
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MichiOrange,
                                    trackColor = Color(0xFF202333)
                                )
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "¡Descarga completada! Listo para instalar.",
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 14.sp,
                                        color = Color(0xFF81C784),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                MichiButton(
                                    text = "Instalar Actualización 📦",
                                    isPrimary = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { onInstall(downloadState.apkFile) }
                                )
                            }
                        }

                        is UpdateDownloadState.Error -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = downloadState.message,
                                    color = Color(0xFFFF6B6B),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(bottom = 10.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    MichiButton(
                                        text = "Cerrar",
                                        modifier = Modifier.weight(1f),
                                        onClick = onDismiss
                                    )
                                    MichiButton(
                                        text = "Reintentar",
                                        isPrimary = true,
                                        modifier = Modifier.weight(1f),
                                        onClick = onStartDownload
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


