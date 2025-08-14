package com.astralstream.player.metadata

import android.content.Context
import com.astralstream.player.config.AppConfig
import com.astralstream.player.config.DefaultApiKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataFetcher @Inject constructor(
    private val context: Context,
    private val appConfig: AppConfig? = null
) {
    
    companion object {
        
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/"
        private const val OMDB_BASE_URL = "https://www.omdbapi.com"
        
        // Regex patterns for parsing filenames
        private val MOVIE_PATTERN = Pattern.compile(
            "^(.+?)[\\.\\s_-]*(\\d{4})?[\\.\\s_-]*(?:1080p|720p|480p|2160p|4K|BluRay|WEBRip|HDRip|DVDRip)?.*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$",
            Pattern.CASE_INSENSITIVE
        )
        
        private val TV_SHOW_PATTERN = Pattern.compile(
            "^(.+?)[\\.\\s_-]*[Ss](\\d{1,2})[Ee](\\d{1,2}).*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$",
            Pattern.CASE_INSENSITIVE
        )
        
        private val TV_SHOW_ALT_PATTERN = Pattern.compile(
            "^(.+?)[\\.\\s_-]*(\\d{1,2})x(\\d{1,2}).*\\.(mp4|mkv|avi|mov|wmv|flv|webm)$",
            Pattern.CASE_INSENSITIVE
        )
    }
    
    suspend fun fetchMetadata(filename: String): VideoMetadata? {
        return withContext(Dispatchers.IO) {
            try {
                // Check if we have API keys configured
                if (appConfig == null || !appConfig.hasRequiredApiKeys()) {
                    // Return basic metadata from filename only
                    val parsed = parseFilename(filename)
                    return@withContext VideoMetadata(
                        title = parsed.title,
                        mediaType = parsed.type,
                        year = parsed.year,
                        season = parsed.season,
                        episode = parsed.episode,
                        plot = "Metadata service not configured. Please add API keys in settings.",
                        poster = null,
                        backdrop = null,
                        rating = 0f
                    )
                }
                
                val parsed = parseFilename(filename)
                
                when (parsed.type) {
                    MediaType.MOVIE -> fetchMovieMetadata(parsed.title, parsed.year)
                    MediaType.TV_SHOW -> fetchTVShowMetadata(
                        parsed.title, 
                        parsed.season ?: 1, 
                        parsed.episode ?: 1
                    )
                    MediaType.UNKNOWN -> fetchGeneralMetadata(parsed.title)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    private fun parseFilename(filename: String): ParsedFilename {
        // Remove path if present
        val name = filename.substringAfterLast('/')
            .substringAfterLast('\\')
        
        // Try TV show patterns first
        var matcher = TV_SHOW_PATTERN.matcher(name)
        if (matcher.find()) {
            return ParsedFilename(
                title = cleanTitle(matcher.group(1) ?: ""),
                type = MediaType.TV_SHOW,
                season = matcher.group(2)?.toIntOrNull(),
                episode = matcher.group(3)?.toIntOrNull()
            )
        }
        
        matcher = TV_SHOW_ALT_PATTERN.matcher(name)
        if (matcher.find()) {
            return ParsedFilename(
                title = cleanTitle(matcher.group(1) ?: ""),
                type = MediaType.TV_SHOW,
                season = matcher.group(2)?.toIntOrNull(),
                episode = matcher.group(3)?.toIntOrNull()
            )
        }
        
        // Try movie pattern
        matcher = MOVIE_PATTERN.matcher(name)
        if (matcher.find()) {
            return ParsedFilename(
                title = cleanTitle(matcher.group(1) ?: ""),
                type = MediaType.MOVIE,
                year = matcher.group(2)?.toIntOrNull()
            )
        }
        
        // Default to unknown
        val cleanName = name.substringBeforeLast('.')
        return ParsedFilename(
            title = cleanTitle(cleanName),
            type = MediaType.UNKNOWN
        )
    }
    
    private fun cleanTitle(title: String): String {
        return title
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .split(' ')
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }
    }
    
    private suspend fun fetchMovieMetadata(title: String, year: Int?): VideoMetadata? {
        // Try TMDb first
        val tmdbResult = searchTMDbMovie(title, year)
        if (tmdbResult != null) return tmdbResult
        
        // Fallback to OMDb
        return searchOMDbMovie(title, year)
    }
    
    private suspend fun fetchTVShowMetadata(
        title: String, 
        season: Int, 
        episode: Int
    ): VideoMetadata? {
        // Try TMDb first
        val tmdbResult = searchTMDbTVShow(title, season, episode)
        if (tmdbResult != null) return tmdbResult
        
        // Fallback to OMDb
        return searchOMDbTVShow(title, season, episode)
    }
    
    private suspend fun fetchGeneralMetadata(title: String): VideoMetadata? {
        // Try as movie first
        val movieResult = fetchMovieMetadata(title, null)
        if (movieResult != null) return movieResult
        
        // Try as TV show
        return fetchTVShowMetadata(title, 1, 1)
    }
    
    private suspend fun searchTMDbMovie(title: String, year: Int?): VideoMetadata? {
        val apiKey = appConfig?.getTmdbApiKey() ?: DefaultApiKeys.TMDB_API_KEY
        try {
            val query = URLEncoder.encode(title, "UTF-8")
            var url = "$TMDB_BASE_URL/search/movie?api_key=$apiKey&query=$query"
            year?.let { url += "&year=$it" }
            
            val response = makeHttpRequest(url)
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            if (results.length() > 0) {
                val movie = results.getJSONObject(0)
                val movieId = movie.getInt("id")
                
                // Get detailed info
                return getTMDbMovieDetails(movieId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private suspend fun getTMDbMovieDetails(movieId: Int): VideoMetadata? {
        val apiKey = appConfig?.getTmdbApiKey() ?: DefaultApiKeys.TMDB_API_KEY
        try {
            val url = "$TMDB_BASE_URL/movie/$movieId?api_key=$apiKey&append_to_response=credits"
            val response = makeHttpRequest(url)
            val movie = JSONObject(response)
            
            return VideoMetadata(
                title = movie.getString("title"),
                originalTitle = movie.optString("original_title"),
                year = movie.optString("release_date").take(4).toIntOrNull(),
                plot = movie.optString("overview"),
                poster = movie.optString("poster_path")?.let { 
                    "${TMDB_IMAGE_BASE}w500$it" 
                },
                backdrop = movie.optString("backdrop_path")?.let { 
                    "${TMDB_IMAGE_BASE}w1280$it" 
                },
                rating = movie.optDouble("vote_average", 0.0).toFloat(),
                runtime = movie.optInt("runtime"),
                genres = parseGenres(movie.optJSONArray("genres")),
                cast = parseCast(movie.optJSONObject("credits")?.optJSONArray("cast")),
                director = parseDirector(movie.optJSONObject("credits")?.optJSONArray("crew")),
                mediaType = MediaType.MOVIE,
                tmdbId = movieId.toString(),
                imdbId = movie.optString("imdb_id")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private suspend fun searchTMDbTVShow(
        title: String, 
        season: Int, 
        episode: Int
    ): VideoMetadata? {
        val apiKey = appConfig?.getTmdbApiKey() ?: DefaultApiKeys.TMDB_API_KEY
        try {
            val query = URLEncoder.encode(title, "UTF-8")
            val url = "$TMDB_BASE_URL/search/tv?api_key=$apiKey&query=$query"
            
            val response = makeHttpRequest(url)
            val json = JSONObject(response)
            val results = json.getJSONArray("results")
            
            if (results.length() > 0) {
                val show = results.getJSONObject(0)
                val showId = show.getInt("id")
                
                // Get episode details
                return getTMDbEpisodeDetails(showId, season, episode)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private suspend fun getTMDbEpisodeDetails(
        showId: Int, 
        season: Int, 
        episode: Int
    ): VideoMetadata? {
        val apiKey = appConfig?.getTmdbApiKey() ?: DefaultApiKeys.TMDB_API_KEY
        try {
            // Get show details
            val showUrl = "$TMDB_BASE_URL/tv/$showId?api_key=$apiKey"
            val showResponse = makeHttpRequest(showUrl)
            val show = JSONObject(showResponse)
            
            // Get episode details
            val episodeUrl = "$TMDB_BASE_URL/tv/$showId/season/$season/episode/$episode?api_key=$apiKey"
            val episodeResponse = makeHttpRequest(episodeUrl)
            val ep = JSONObject(episodeResponse)
            
            return VideoMetadata(
                title = show.getString("name"),
                episodeTitle = ep.optString("name"),
                year = show.optString("first_air_date").take(4).toIntOrNull(),
                plot = ep.optString("overview").ifEmpty { 
                    show.optString("overview") 
                },
                poster = show.optString("poster_path")?.let { 
                    "${TMDB_IMAGE_BASE}w500$it" 
                },
                backdrop = ep.optString("still_path")?.let { 
                    "${TMDB_IMAGE_BASE}w1280$it" 
                } ?: show.optString("backdrop_path")?.let { 
                    "${TMDB_IMAGE_BASE}w1280$it" 
                },
                rating = ep.optDouble("vote_average", 0.0).toFloat(),
                runtime = ep.optInt("runtime"),
                genres = parseGenres(show.optJSONArray("genres")),
                cast = parseCast(ep.optJSONArray("guest_stars")),
                director = ep.optJSONArray("crew")?.let { parseDirector(it) },
                mediaType = MediaType.TV_SHOW,
                season = season,
                episode = episode,
                tmdbId = showId.toString(),
                airDate = ep.optString("air_date")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private suspend fun searchOMDbMovie(title: String, year: Int?): VideoMetadata? {
        val apiKey = appConfig?.getOmdbApiKey() ?: DefaultApiKeys.OMDB_API_KEY
        try {
            val query = URLEncoder.encode(title, "UTF-8")
            var url = "$OMDB_BASE_URL/?apikey=$apiKey&t=$query&type=movie"
            year?.let { url += "&y=$it" }
            
            val response = makeHttpRequest(url)
            val json = JSONObject(response)
            
            if (json.optString("Response") == "True") {
                return parseOMDbResponse(json, MediaType.MOVIE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private suspend fun searchOMDbTVShow(
        title: String, 
        season: Int, 
        episode: Int
    ): VideoMetadata? {
        val apiKey = appConfig?.getOmdbApiKey() ?: DefaultApiKeys.OMDB_API_KEY
        try {
            val query = URLEncoder.encode(title, "UTF-8")
            val url = "$OMDB_BASE_URL/?apikey=$apiKey&t=$query&Season=$season&Episode=$episode"
            
            val response = makeHttpRequest(url)
            val json = JSONObject(response)
            
            if (json.optString("Response") == "True") {
                val metadata = parseOMDbResponse(json, MediaType.TV_SHOW)
                return metadata.copy(
                    season = season,
                    episode = episode
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
    
    private fun parseOMDbResponse(json: JSONObject, type: MediaType): VideoMetadata {
        return VideoMetadata(
            title = json.optString("Title"),
            year = json.optString("Year").take(4).toIntOrNull(),
            plot = json.optString("Plot"),
            poster = json.optString("Poster").takeIf { it != "N/A" },
            rating = json.optString("imdbRating").toFloatOrNull() ?: 0f,
            runtime = json.optString("Runtime").replace(" min", "").toIntOrNull(),
            genres = json.optString("Genre").split(", "),
            cast = json.optString("Actors").split(", ").map { 
                CastMember(it, null, null) 
            },
            director = json.optString("Director"),
            mediaType = type,
            imdbId = json.optString("imdbID"),
            rated = json.optString("Rated"),
            released = json.optString("Released")
        )
    }
    
    private fun parseGenres(genres: org.json.JSONArray?): List<String> {
        val list = mutableListOf<String>()
        genres?.let {
            for (i in 0 until it.length()) {
                list.add(it.getJSONObject(i).getString("name"))
            }
        }
        return list
    }
    
    private fun parseCast(cast: org.json.JSONArray?): List<CastMember> {
        val list = mutableListOf<CastMember>()
        cast?.let {
            for (i in 0 until minOf(it.length(), 10)) {
                val actor = it.getJSONObject(i)
                list.add(
                    CastMember(
                        name = actor.getString("name"),
                        character = actor.optString("character"),
                        profilePath = actor.optString("profile_path")?.let { path ->
                            "${TMDB_IMAGE_BASE}w185$path"
                        }
                    )
                )
            }
        }
        return list
    }
    
    private fun parseDirector(crew: org.json.JSONArray?): String? {
        crew?.let {
            for (i in 0 until it.length()) {
                val member = it.getJSONObject(i)
                if (member.optString("job") == "Director") {
                    return member.getString("name")
                }
            }
        }
        return null
    }
    
    private suspend fun makeHttpRequest(url: String): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
}

data class ParsedFilename(
    val title: String,
    val type: MediaType,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
)

enum class MediaType {
    MOVIE, TV_SHOW, UNKNOWN
}

data class VideoMetadata(
    val title: String,
    val originalTitle: String? = null,
    val episodeTitle: String? = null,
    val year: Int? = null,
    val plot: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val rating: Float = 0f,
    val runtime: Int? = null, // in minutes
    val genres: List<String> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val director: String? = null,
    val mediaType: MediaType,
    val season: Int? = null,
    val episode: Int? = null,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val rated: String? = null,
    val released: String? = null,
    val airDate: String? = null
) {
    val displayTitle: String
        get() = when (mediaType) {
            MediaType.TV_SHOW -> {
                if (season != null && episode != null) {
                    "$title S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
                } else title
            }
            else -> year?.let { "$title ($it)" } ?: title
        }
    
    val formattedRuntime: String?
        get() = runtime?.let {
            val hours = it / 60
            val minutes = it % 60
            if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

data class CastMember(
    val name: String,
    val character: String? = null,
    val profilePath: String? = null
)