package io.github.aedev.flow.data.newmusic

import io.github.aedev.flow.data.music.model.MusicArtist

object InnertubeMusicService {
    suspend fun search(query: String): List<MusicArtist> = emptyList()
    
    suspend fun fetchQueue(videoId: String): List<String> = emptyList()
    
    fun convertToMusicTrack(data: Any): Any? = null
}
