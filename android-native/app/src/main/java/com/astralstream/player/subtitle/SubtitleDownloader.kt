package com.astralstream.player.subtitle

import android.content.Context
import com.astralstream.player.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleDownloader @Inject constructor(
    private val context: Context,
    private val appConfig: AppConfig? = null
) {
    
    companion object {
        // OpenSubtitles API configuration
        private const val OPENSUBTITLES_API_URL = "https://api.opensubtitles.com/api/v1"
        
        // Alternative APIs
        private const val SUBSCENE_BASE_URL = "https://subscene.com"
        private const val ADDIC7ED_BASE_URL = "https://www.addic7ed.com"
        
        // Supported subtitle formats
        private val SUPPORTED_FORMATS = listOf("srt", "vtt", "ass", "ssa", "sub")
        
        // Language codes mapping
        private val LANGUAGE_CODES = mapOf(
            "English" to "en",
            "Spanish" to "es",
            "French" to "fr",
            "German" to "de",
            "Italian" to "it",
            "Portuguese" to "pt",
            "Russian" to "ru",
            "Japanese" to "ja",
            "Korean" to "ko",
            "Chinese" to "zh",
            "Arabic" to "ar",
            "Hindi" to "hi",
            "Dutch" to "nl",
            "Polish" to "pl",
            "Turkish" to "tr",
            "Swedish" to "sv",
            "Norwegian" to "no",
            "Danish" to "da",
            "Finnish" to "fi",
            "Greek" to "el",
            "Hebrew" to "he",
            "Thai" to "th",
            "Vietnamese" to "vi",
            "Indonesian" to "id",
            "Malay" to "ms"
        )
    }
    
    private val subtitleCache = File(context.cacheDir, "subtitles").apply {
        if (!exists()) mkdirs()
    }
    
    suspend fun searchSubtitles(
        query: String,
        languages: List<String> = listOf("en"),
        season: Int? = null,
        episode: Int? = null,
        movieHash: String? = null
    ): List<SubtitleInfo> {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<SubtitleInfo>()
            
            // Search OpenSubtitles
            results.addAll(searchOpenSubtitles(query, languages, season, episode, movieHash))
            
            // Search other sources if needed
            if (results.isEmpty()) {
                results.addAll(searchAlternativeSources(query, languages))
            }
            
            // Sort by relevance and rating
            results.sortedByDescending { it.score * it.rating }
        }
    }
    
    private suspend fun searchOpenSubtitles(
        query: String,
        languages: List<String>,
        season: Int?,
        episode: Int?,
        movieHash: String?
    ): List<SubtitleInfo> {
        val results = mutableListOf<SubtitleInfo>()
        
        try {
            val params = mutableMapOf<String, String>()
            params["query"] = query
            
            languages.forEach { lang ->
                val langCode = LANGUAGE_CODES[lang] ?: lang
                params["languages"] = langCode
            }
            
            season?.let { params["season_number"] = it.toString() }
            episode?.let { params["episode_number"] = it.toString() }
            movieHash?.let { params["moviehash"] = it }
            
            val url = buildUrl(OPENSUBTITLES_API_URL + "/subtitles", params)
            val response = makeApiRequest(url)
            
            val json = JSONObject(response)
            val data = json.optJSONArray("data") ?: JSONArray()
            
            for (i in 0 until data.length()) {
                val subtitle = data.getJSONObject(i)
                val attributes = subtitle.getJSONObject("attributes")
                
                results.add(
                    SubtitleInfo(
                        id = subtitle.getString("id"),
                        language = attributes.getString("language"),
                        languageName = getLanguageName(attributes.getString("language")),
                        fileName = attributes.getString("release"),
                        format = attributes.optString("format", "srt"),
                        downloadUrl = attributes.getJSONArray("files")
                            .getJSONObject(0)
                            .getString("file_id"),
                        score = attributes.optDouble("ratings", 0.0).toFloat(),
                        downloads = attributes.optInt("download_count", 0),
                        rating = attributes.optDouble("ratings", 0.0).toFloat() / 5f,
                        uploadDate = attributes.optString("upload_date"),
                        uploader = attributes.optString("uploader"),
                        source = SubtitleSource.OPENSUBTITLES,
                        fps = attributes.optDouble("fps", 0.0).toFloat(),
                        hearingImpaired = attributes.optBoolean("hearing_impaired", false),
                        machineTranslated = attributes.optBoolean("machine_translated", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return results
    }
    
    private suspend fun searchAlternativeSources(
        query: String,
        languages: List<String>
    ): List<SubtitleInfo> {
        // Placeholder for alternative subtitle sources
        // In production, implement web scraping or use other APIs
        return emptyList()
    }
    
    suspend fun downloadSubtitle(subtitle: SubtitleInfo): File? {
        return withContext(Dispatchers.IO) {
            try {
                // Check cache first
                val cachedFile = getCachedSubtitle(subtitle)
                if (cachedFile?.exists() == true) {
                    return@withContext cachedFile
                }
                
                // Download subtitle
                val content = when (subtitle.source) {
                    SubtitleSource.OPENSUBTITLES -> downloadFromOpenSubtitles(subtitle)
                    SubtitleSource.SUBSCENE -> downloadFromSubscene(subtitle)
                    SubtitleSource.ADDIC7ED -> downloadFromAddic7ed(subtitle)
                    SubtitleSource.LOCAL -> null
                }
                
                content?.let {
                    // Save to cache
                    val file = saveSubtitleToCache(subtitle, it)
                    
                    // Convert format if needed
                    if (subtitle.format != "srt" && subtitle.format != "vtt") {
                        convertSubtitleFormat(file, "srt")
                    } else {
                        file
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    private suspend fun downloadFromOpenSubtitles(subtitle: SubtitleInfo): String? {
        return try {
            val url = "$OPENSUBTITLES_API_URL/download"
            val params = mapOf("file_id" to subtitle.downloadUrl)
            
            val response = makeApiRequest(buildUrl(url, params), needsAuth = true)
            val json = JSONObject(response)
            
            val downloadUrl = json.getString("link")
            downloadFile(downloadUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private suspend fun downloadFromSubscene(subtitle: SubtitleInfo): String? {
        // Implement Subscene download logic
        return null
    }
    
    private suspend fun downloadFromAddic7ed(subtitle: SubtitleInfo): String? {
        // Implement Addic7ed download logic
        return null
    }
    
    private fun getCachedSubtitle(subtitle: SubtitleInfo): File? {
        val hash = generateHash("${subtitle.id}_${subtitle.language}_${subtitle.format}")
        val file = File(subtitleCache, "$hash.${subtitle.format}")
        return if (file.exists()) file else null
    }
    
    private fun saveSubtitleToCache(subtitle: SubtitleInfo, content: String): File {
        val hash = generateHash("${subtitle.id}_${subtitle.language}_${subtitle.format}")
        val file = File(subtitleCache, "$hash.${subtitle.format}")
        
        file.writeText(content)
        return file
    }
    
    private fun convertSubtitleFormat(file: File, targetFormat: String): File {
        // Simple conversion logic for common formats
        val content = file.readText()
        val convertedContent = when {
            file.extension == "vtt" && targetFormat == "srt" -> convertVttToSrt(content)
            file.extension == "ass" && targetFormat == "srt" -> convertAssToSrt(content)
            file.extension == "ssa" && targetFormat == "srt" -> convertSsaToSrt(content)
            else -> content
        }
        
        val newFile = File(file.parentFile, file.nameWithoutExtension + ".$targetFormat")
        newFile.writeText(convertedContent)
        return newFile
    }
    
    private fun convertVttToSrt(vtt: String): String {
        val lines = vtt.lines()
        val srtLines = mutableListOf<String>()
        var counter = 1
        var i = 0
        
        // Skip WEBVTT header
        while (i < lines.size && !lines[i].contains("-->")) {
            i++
        }
        
        while (i < lines.size) {
            if (lines[i].contains("-->")) {
                // Add counter
                srtLines.add(counter.toString())
                counter++
                
                // Convert timestamp format
                val timestamp = lines[i]
                    .replace(".", ",")
                    .replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})")) { 
                        "${it.groupValues[1]},${it.groupValues[2]}"
                    }
                srtLines.add(timestamp)
                
                // Add subtitle text
                i++
                while (i < lines.size && lines[i].isNotBlank()) {
                    srtLines.add(lines[i])
                    i++
                }
                srtLines.add("")
            }
            i++
        }
        
        return srtLines.joinToString("\n")
    }
    
    private fun convertAssToSrt(ass: String): String {
        // Basic ASS to SRT conversion
        val lines = ass.lines()
        val srtLines = mutableListOf<String>()
        var counter = 1
        
        for (line in lines) {
            if (line.startsWith("Dialogue:")) {
                val parts = line.split(",", limit = 10)
                if (parts.size >= 10) {
                    val start = convertAssTime(parts[1])
                    val end = convertAssTime(parts[2])
                    val text = parts[9]
                        .replace(Regex("\\{[^}]*\\}"), "") // Remove style tags
                        .replace("\\N", "\n") // Convert line breaks
                    
                    srtLines.add(counter.toString())
                    srtLines.add("$start --> $end")
                    srtLines.add(text)
                    srtLines.add("")
                    counter++
                }
            }
        }
        
        return srtLines.joinToString("\n")
    }
    
    private fun convertSsaToSrt(ssa: String): String {
        // SSA is similar to ASS
        return convertAssToSrt(ssa)
    }
    
    private fun convertAssTime(time: String): String {
        // Convert ASS time format (0:00:00.00) to SRT format (00:00:00,000)
        val parts = time.trim().split(":")
        if (parts.size == 3) {
            val hours = parts[0].padStart(2, '0')
            val minutes = parts[1].padStart(2, '0')
            val secondsParts = parts[2].split(".")
            val seconds = secondsParts[0].padStart(2, '0')
            val milliseconds = (secondsParts.getOrNull(1) ?: "00").padEnd(3, '0')
            return "$hours:$minutes:$seconds,$milliseconds"
        }
        return time
    }
    
    suspend fun detectLanguage(subtitleFile: File): String? {
        return withContext(Dispatchers.IO) {
            try {
                val content = subtitleFile.readText()
                // Simple language detection based on character sets
                when {
                    content.contains(Regex("[\\u4e00-\\u9fff]")) -> "Chinese"
                    content.contains(Regex("[\\u3040-\\u309f\\u30a0-\\u30ff]")) -> "Japanese"
                    content.contains(Regex("[\\uac00-\\ud7af]")) -> "Korean"
                    content.contains(Regex("[\\u0600-\\u06ff]")) -> "Arabic"
                    content.contains(Regex("[\\u0400-\\u04ff]")) -> "Russian"
                    content.contains(Regex("[\\u0370-\\u03ff]")) -> "Greek"
                    content.contains(Regex("[\\u0590-\\u05ff]")) -> "Hebrew"
                    else -> "English" // Default
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    fun clearCache() {
        subtitleCache.listFiles()?.forEach { it.delete() }
    }
    
    fun getCacheSize(): Long {
        return subtitleCache.listFiles()?.sumOf { it.length() } ?: 0L
    }
    
    private fun buildUrl(baseUrl: String, params: Map<String, String>): String {
        val queryString = params.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, "UTF-8")}"
        }
        return "$baseUrl?$queryString"
    }
    
    private suspend fun makeApiRequest(url: String, needsAuth: Boolean = false): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (needsAuth) {
                val apiKey = appConfig?.getOpenSubtitlesApiKey()
                if (apiKey != null) {
                    connection.setRequestProperty("Api-Key", apiKey)
                }
            }
            
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private suspend fun downloadFile(url: String): String {
        return withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            
            try {
                val inputStream = if (url.endsWith(".gz")) {
                    GZIPInputStream(connection.inputStream)
                } else {
                    connection.inputStream
                }
                
                inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
    }
    
    private fun generateHash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
    
    private fun getLanguageName(code: String): String {
        return LANGUAGE_CODES.entries.find { it.value == code }?.key ?: code
    }
    
    suspend fun getAvailableLanguages(): List<String> {
        return LANGUAGE_CODES.keys.toList()
    }
    
    suspend fun searchByHash(movieHash: String, fileSize: Long): List<SubtitleInfo> {
        // OpenSubtitles supports searching by movie hash for exact matches
        return searchSubtitles("", movieHash = movieHash)
    }
}

data class SubtitleInfo(
    val id: String,
    val language: String,
    val languageName: String,
    val fileName: String,
    val format: String,
    val downloadUrl: String,
    val score: Float,
    val downloads: Int,
    val rating: Float, // 0-1
    val uploadDate: String,
    val uploader: String,
    val source: SubtitleSource,
    val fps: Float = 0f,
    val hearingImpaired: Boolean = false,
    val machineTranslated: Boolean = false
) {
    val qualityScore: Float
        get() = (rating * 0.4f + (downloads.coerceAtMost(10000) / 10000f) * 0.3f + 
                if (!machineTranslated) 0.3f else 0f).coerceIn(0f, 1f)
}

enum class SubtitleSource {
    OPENSUBTITLES, SUBSCENE, ADDIC7ED, LOCAL
}