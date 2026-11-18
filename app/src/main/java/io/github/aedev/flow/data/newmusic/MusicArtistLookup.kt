package io.github.aedev.flow.data.newmusic

import io.github.aedev.flow.data.music.model.MusicArtist
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up artists for a song, caching results and filtering artists without IDs.
 * Empty results are not cached so they can be retried.
 * Lookups that take longer than 6 seconds return empty.
 */
@Singleton
class MusicArtistLookup @Inject constructor(
    private val lookup: suspend (String) -> List<MusicArtist>
) {
    private val cache = mutableMapOf<String, List<MusicArtist>>()
    private val mutex = Mutex()

    suspend fun artistsOf(videoId: String): List<MusicArtist> {
        mutex.withLock {
            cache[videoId]?.let { return it }
        }

        val result = withTimeoutOrNull(6_000) {
            lookup(videoId)
        } ?: emptyList()

        val filtered = result.filter { it.id != null }

        if (filtered.isNotEmpty()) {
            mutex.withLock {
                cache[videoId] = filtered
            }
        }

        return filtered
    }
}
