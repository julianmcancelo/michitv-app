package com.kinotv.player.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey
    val id: String,
    val title: String,
    val poster: String?,
    val type: String?,
    val pluginId: String,
    val ref: String,
    val background: String? = null,
    val year: String? = null,
    val description: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
