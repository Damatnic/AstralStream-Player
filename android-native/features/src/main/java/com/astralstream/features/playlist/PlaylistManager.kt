package com.astralstream.features.playlist

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistManager @Inject constructor() {
    
    private val playlists = mutableListOf<Playlist>()
    private var currentPlaylistId: String? = null
    
    fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(
            id = System.currentTimeMillis().toString(),
            name = name,
            items = mutableListOf()
        )
        playlists.add(playlist)
        return playlist
    }
    
    fun getPlaylists(): List<Playlist> = playlists.toList()
    
    fun getPlaylist(id: String): Playlist? = playlists.find { it.id == id }
    
    fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
    }
    
    fun addToPlaylist(playlistId: String, mediaPath: String) {
        playlists.find { it.id == playlistId }?.apply {
            items.add(mediaPath)
        }
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val items: MutableList<String>
)