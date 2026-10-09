package com.kinotv.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey
    val id: String,
    val title: String,
    val poster: String?,
    val type: String?,
    val pluginId: String,
    val ref: String,
    val progressMs: Long,
    val durationMs: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val lastWatchedAt: Long = System.currentTimeMillis()
)
