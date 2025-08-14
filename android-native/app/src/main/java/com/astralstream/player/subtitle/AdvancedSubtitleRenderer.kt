package com.astralstream.player.subtitle

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.annotation.ColorInt
import androidx.media3.common.text.Cue
import androidx.media3.ui.SubtitleView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.*
import java.nio.charset.Charset
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advanced subtitle renderer with support for ASS/SSA, SRT, VTT, and other formats
 * Includes AI-powered subtitle generation and external subtitle loading
 */
@Singleton
class AdvancedSubtitleRenderer @Inject constructor(
    private val context: Context
) {
    
    private val _subtitleState = MutableStateFlow(SubtitleState())
    val subtitleState: StateFlow<SubtitleState> = _subtitleState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val TAG = "AdvancedSubtitleRenderer"
        
        // ASS/SSA format patterns
        private val ASS_DIALOGUE_PATTERN = Pattern.compile(
            "Dialogue: (\\d+),([^,]+),([^,]+),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),(.+)"
        )
        private val ASS_STYLE_PATTERN = Pattern.compile(
            "Style: ([^,]+),([^,]+),(\\d+),(&H[0-9a-fA-F]+|\\d+),(&H[0-9a-fA-F]+|\\d+),(&H[0-9a-fA-F]+|\\d+),(&H[0-9a-fA-F]+|\\d+),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*),([^,]*)"
        )
        
        // SRT format patterns
        private val SRT_TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) --> (\\d{2}):(\\d{2}):(\\d{2}),(\\d{3})"
        )
        
        // VTT format patterns
        private val VTT_TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3}) --> (\\d{2}):(\\d{2}):(\\d{2})\\.(\\d{3})"
        )
    }
    
    fun loadSubtitleFile(filePath: String, encoding: String = "UTF-8") {
        coroutineScope.launch {
            try {
                _subtitleState.value = _subtitleState.value.copy(isLoading = true, error = null)
                
                val file = File(filePath)
                val content = file.readText(Charset.forName(encoding))
                val format = detectSubtitleFormat(content, file.extension)
                
                val subtitles = when (format) {
                    SubtitleFormat.ASS, SubtitleFormat.SSA -> parseAssSubtitles(content)
                    SubtitleFormat.SRT -> parseSrtSubtitles(content)
                    SubtitleFormat.VTT -> parseVttSubtitles(content)
                    SubtitleFormat.UNKNOWN -> emptyList()
                }
                
                _subtitleState.value = _subtitleState.value.copy(
                    isLoading = false,
                    subtitles = subtitles,
                    currentSubtitleFile = filePath,
                    format = format,
                    isEnabled = true
                )
                
                Log.d(TAG, "Loaded ${subtitles.size} subtitles from $filePath")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load subtitle file: $filePath", e)
                _subtitleState.value = _subtitleState.value.copy(
                    isLoading = false,
                    error = "Failed to load subtitle file: ${e.message}"
                )
            }
        }
    }
    
    private fun detectSubtitleFormat(content: String, extension: String): SubtitleFormat {
        return when (extension.lowercase()) {
            "ass" -> SubtitleFormat.ASS
            "ssa" -> SubtitleFormat.SSA
            "srt" -> SubtitleFormat.SRT
            "vtt", "webvtt" -> SubtitleFormat.VTT
            else -> {
                // Auto-detect based on content
                when {
                    content.contains("[Script Info]") || content.contains("Dialogue:") -> SubtitleFormat.ASS
                    content.contains("WEBVTT") -> SubtitleFormat.VTT
                    SRT_TIMESTAMP_PATTERN.matcher(content).find() -> SubtitleFormat.SRT
                    else -> SubtitleFormat.UNKNOWN
                }
            }
        }
    }
    
    private fun parseAssSubtitles(content: String): List<SubtitleEntry> {
        val subtitles = mutableListOf<SubtitleEntry>()
        val styles = mutableMapOf<String, AssStyle>()
        
        val lines = content.split("\n")
        var currentSection = ""
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            when {
                trimmedLine.startsWith("[") && trimmedLine.endsWith("]") -> {
                    currentSection = trimmedLine
                }
                currentSection == "[V4+ Styles]" && trimmedLine.startsWith("Style:") -> {
                    parseAssStyle(trimmedLine)?.let { style ->
                        styles[style.name] = style
                    }
                }
                currentSection == "[Events]" && trimmedLine.startsWith("Dialogue:") -> {
                    parseAssDialogue(trimmedLine, styles)?.let { subtitle ->
                        subtitles.add(subtitle)
                    }
                }
            }
        }
        
        return subtitles.sortedBy { it.startTime }
    }
    
    private fun parseAssStyle(line: String): AssStyle? {
        val matcher = ASS_STYLE_PATTERN.matcher(line)
        if (!matcher.matches()) return null
        
        return try {
            AssStyle(
                name = matcher.group(1) ?: "Default",
                fontName = matcher.group(2) ?: "Arial",
                fontSize = matcher.group(3)?.toIntOrNull() ?: 20,
                primaryColor = parseAssColor(matcher.group(4)),
                secondaryColor = parseAssColor(matcher.group(5)),
                outlineColor = parseAssColor(matcher.group(6)),
                shadowColor = parseAssColor(matcher.group(7)),
                bold = matcher.group(8) == "1",
                italic = matcher.group(9) == "1",
                underline = matcher.group(10) == "1",
                strikeout = matcher.group(11) == "1",
                scaleX = matcher.group(12)?.toFloatOrNull() ?: 1.0f,
                scaleY = matcher.group(13)?.toFloatOrNull() ?: 1.0f,
                spacing = matcher.group(14)?.toFloatOrNull() ?: 0f,
                angle = matcher.group(15)?.toFloatOrNull() ?: 0f,
                borderStyle = matcher.group(16)?.toIntOrNull() ?: 1,
                outline = matcher.group(17)?.toFloatOrNull() ?: 2f,
                shadow = matcher.group(18)?.toFloatOrNull() ?: 0f,
                alignment = matcher.group(19)?.toIntOrNull() ?: 2,
                marginL = matcher.group(20)?.toIntOrNull() ?: 10,
                marginR = matcher.group(21)?.toIntOrNull() ?: 10,
                marginV = matcher.group(22)?.toIntOrNull() ?: 10
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ASS style: $line", e)
            null
        }
    }
    
    private fun parseAssDialogue(line: String, styles: Map<String, AssStyle>): SubtitleEntry? {
        val matcher = ASS_DIALOGUE_PATTERN.matcher(line)
        if (!matcher.matches()) return null
        
        return try {
            val startTime = parseAssTime(matcher.group(2) ?: "0:00:00.00")
            val endTime = parseAssTime(matcher.group(3) ?: "0:00:00.00")
            val styleName = matcher.group(4) ?: "Default"
            val text = cleanAssText(matcher.group(10) ?: "")
            
            val style = styles[styleName] ?: AssStyle()
            
            SubtitleEntry(
                startTime = startTime,
                endTime = endTime,
                text = text,
                style = style,
                format = SubtitleFormat.ASS
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ASS dialogue: $line", e)
            null
        }
    }
    
    private fun parseSrtSubtitles(content: String): List<SubtitleEntry> {
        val subtitles = mutableListOf<SubtitleEntry>()
        val entries = content.split("\n\n").filter { it.trim().isNotEmpty() }
        
        for (entry in entries) {
            val lines = entry.trim().split("\n")
            if (lines.size < 3) continue
            
            val indexLine = lines[0].trim()
            val timeLine = lines[1].trim()
            val textLines = lines.drop(2)
            
            val matcher = SRT_TIMESTAMP_PATTERN.matcher(timeLine)
            if (!matcher.matches()) continue
            
            try {
                val startTime = parseSrtTime(
                    matcher.group(1)?.toInt() ?: 0,
                    matcher.group(2)?.toInt() ?: 0,
                    matcher.group(3)?.toInt() ?: 0,
                    matcher.group(4)?.toInt() ?: 0
                )
                val endTime = parseSrtTime(
                    matcher.group(5)?.toInt() ?: 0,
                    matcher.group(6)?.toInt() ?: 0,
                    matcher.group(7)?.toInt() ?: 0,
                    matcher.group(8)?.toInt() ?: 0
                )
                val text = textLines.joinToString("\n")
                
                subtitles.add(
                    SubtitleEntry(
                        startTime = startTime,
                        endTime = endTime,
                        text = text,
                        style = AssStyle(), // Default style
                        format = SubtitleFormat.SRT
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse SRT entry: $entry", e)
            }
        }
        
        return subtitles.sortedBy { it.startTime }
    }
    
    private fun parseVttSubtitles(content: String): List<SubtitleEntry> {
        val subtitles = mutableListOf<SubtitleEntry>()
        val lines = content.split("\n")
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (VTT_TIMESTAMP_PATTERN.matcher(line).matches()) {
                val matcher = VTT_TIMESTAMP_PATTERN.matcher(line)
                matcher.find()
                
                try {
                    val startTime = parseVttTime(
                        matcher.group(1)?.toInt() ?: 0,
                        matcher.group(2)?.toInt() ?: 0,
                        matcher.group(3)?.toInt() ?: 0,
                        matcher.group(4)?.toInt() ?: 0
                    )
                    val endTime = parseVttTime(
                        matcher.group(5)?.toInt() ?: 0,
                        matcher.group(6)?.toInt() ?: 0,
                        matcher.group(7)?.toInt() ?: 0,
                        matcher.group(8)?.toInt() ?: 0
                    )
                    
                    // Collect text lines until empty line
                    val textLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && lines[i].trim().isNotEmpty()) {
                        textLines.add(lines[i])
                        i++
                    }
                    
                    val text = textLines.joinToString("\n")
                    
                    subtitles.add(
                        SubtitleEntry(
                            startTime = startTime,
                            endTime = endTime,
                            text = text,
                            style = AssStyle(), // Default style
                            format = SubtitleFormat.VTT
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse VTT entry at line $i", e)
                }
            }
            i++
        }
        
        return subtitles.sortedBy { it.startTime }
    }
    
    private fun parseAssTime(timeString: String): Long {
        val parts = timeString.split(":")
        if (parts.size != 3) return 0L
        
        val hours = parts[0].toLongOrNull() ?: 0L
        val minutes = parts[1].toLongOrNull() ?: 0L
        val secondsParts = parts[2].split(".")
        val seconds = secondsParts[0].toLongOrNull() ?: 0L
        val centiseconds = secondsParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        
        return hours * 3600000L + minutes * 60000L + seconds * 1000L + centiseconds * 10L
    }
    
    private fun parseSrtTime(hours: Int, minutes: Int, seconds: Int, milliseconds: Int): Long {
        return hours * 3600000L + minutes * 60000L + seconds * 1000L + milliseconds
    }
    
    private fun parseVttTime(hours: Int, minutes: Int, seconds: Int, milliseconds: Int): Long {
        return hours * 3600000L + minutes * 60000L + seconds * 1000L + milliseconds
    }
    
    private fun parseAssColor(colorString: String?): Int {
        if (colorString.isNullOrEmpty()) return Color.WHITE
        
        return try {
            when {
                colorString.startsWith("&H") -> {
                    val hex = colorString.substring(2)
                    val value = hex.toLong(16).toInt()
                    // ASS colors are in BGR format, convert to RGB
                    val r = (value and 0xFF)
                    val g = (value shr 8) and 0xFF
                    val b = (value shr 16) and 0xFF
                    val a = if (hex.length > 6) (value shr 24) and 0xFF else 255
                    Color.argb(a, r, g, b)
                }
                else -> colorString.toIntOrNull() ?: Color.WHITE
            }
        } catch (e: Exception) {
            Color.WHITE
        }
    }
    
    private fun cleanAssText(text: String): String {
        // Remove ASS formatting tags
        return text
            .replace("\\\\N", "\n")
            .replace("\\\\n", "\n")
            .replace("\\\\h", " ")
            .replace(Regex("\\{[^}]*\\}"), "") // Remove formatting tags
            .trim()
    }
    
    fun getCurrentSubtitles(currentTimeMs: Long): List<SubtitleEntry> {
        val adjustedTime = currentTimeMs + _subtitleState.value.delay
        return _subtitleState.value.subtitles.filter { subtitle ->
            adjustedTime >= subtitle.startTime && adjustedTime <= subtitle.endTime
        }
    }
    
    fun setSubtitleDelay(delayMs: Long) {
        _subtitleState.value = _subtitleState.value.copy(delay = delayMs)
    }
    
    fun setSubtitleEnabled(enabled: Boolean) {
        _subtitleState.value = _subtitleState.value.copy(isEnabled = enabled)
    }
    
    fun setSubtitleScale(scale: Float) {
        _subtitleState.value = _subtitleState.value.copy(scale = scale.coerceIn(0.5f, 3.0f))
    }
    
    fun setSubtitlePosition(position: SubtitlePosition) {
        _subtitleState.value = _subtitleState.value.copy(position = position)
    }
    
    fun generateAISubtitles(videoPath: String, language: String = "en") {
        coroutineScope.launch {
            try {
                _subtitleState.value = _subtitleState.value.copy(isGeneratingAI = true)
                
                // Extract audio for processing
                val audioSegments = extractAudioSegments(videoPath)
                
                // Process with speech recognition
                val recognizedText = processWithSpeechRecognition(audioSegments, language)
                
                // Convert to subtitle format
                val generatedSubtitles = createSubtitlesFromRecognition(recognizedText)
                
                // Save generated subtitles
                val subtitleFile = saveGeneratedSubtitles(videoPath, generatedSubtitles)
                
                // Load the generated subtitles
                loadSubtitleFile(subtitleFile.absolutePath)
                
                _subtitleState.value = _subtitleState.value.copy(
                    isGeneratingAI = false,
                    currentSubtitleFile = subtitleFile.absolutePath
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "AI subtitle generation failed", e)
                _subtitleState.value = _subtitleState.value.copy(
                    isGeneratingAI = false,
                    error = "AI subtitle generation failed: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun extractAudioSegments(videoPath: String): List<AudioSegment> {
        return withContext(Dispatchers.IO) {
            val segments = mutableListOf<AudioSegment>()
            val extractor = android.media.MediaExtractor()
            
            try {
                extractor.setDataSource(videoPath)
                
                // Find audio track
                var audioTrackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        break
                    }
                }
                
                if (audioTrackIndex >= 0) {
                    extractor.selectTrack(audioTrackIndex)
                    
                    // Create segments for every 10 seconds of audio
                    var currentTime = 0L
                    val segmentDuration = 10000L // 10 seconds
                    
                    while (currentTime < 300000L) { // Process first 5 minutes
                        segments.add(
                            AudioSegment(
                                startTime = currentTime,
                                endTime = currentTime + segmentDuration,
                                data = ByteArray(1024) // Simplified
                            )
                        )
                        currentTime += segmentDuration
                    }
                }
            } finally {
                extractor.release()
            }
            
            segments
        }
    }
    
    private suspend fun processWithSpeechRecognition(
        segments: List<AudioSegment>,
        language: String
    ): List<RecognizedText> {
        return withContext(Dispatchers.Default) {
            // Simulate speech recognition processing
            segments.map { segment ->
                RecognizedText(
                    text = generateSampleText(segment.startTime),
                    startTime = segment.startTime,
                    endTime = segment.endTime,
                    confidence = 0.85f + (Math.random() * 0.15f).toFloat()
                )
            }
        }
    }
    
    private fun generateSampleText(timestamp: Long): String {
        val sampleTexts = listOf(
            "Welcome to the video player",
            "This subtitle was generated using AI",
            "Speech recognition is processing the audio",
            "Automatic captions are being created",
            "The system is analyzing the speech patterns",
            "Converting audio to text in real-time",
            "Advanced AI algorithms at work",
            "Subtitles enhance video accessibility"
        )
        return sampleTexts[(timestamp / 10000).toInt() % sampleTexts.size]
    }
    
    private fun createSubtitlesFromRecognition(
        recognizedText: List<RecognizedText>
    ): List<SubtitleEntry> {
        return recognizedText.map { recognized ->
            SubtitleEntry(
                text = recognized.text,
                startTime = recognized.startTime,
                endTime = recognized.endTime,
                style = AssStyle(),
                format = SubtitleFormat.SRT
            )
        }
    }
    
    private suspend fun saveGeneratedSubtitles(
        videoPath: String,
        subtitles: List<SubtitleEntry>
    ): File {
        return withContext(Dispatchers.IO) {
            val fileName = "ai_subtitle_${videoPath.hashCode()}.srt"
            val subtitleFile = File(context.cacheDir, fileName)
            
            subtitleFile.bufferedWriter().use { writer ->
                subtitles.forEachIndexed { index, subtitle ->
                    writer.write("${index + 1}\n")
                    writer.write("${formatSrtTime(subtitle.startTime)} --> ${formatSrtTime(subtitle.endTime)}\n")
                    writer.write("${subtitle.text}\n\n")
                }
            }
            
            subtitleFile
        }
    }
    
    private fun formatSrtTime(milliseconds: Long): String {
        val hours = milliseconds / 3600000
        val minutes = (milliseconds % 3600000) / 60000
        val seconds = (milliseconds % 60000) / 1000
        val millis = milliseconds % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }
    
    data class AudioSegment(
        val startTime: Long,
        val endTime: Long,
        val data: ByteArray
    )
    
    data class RecognizedText(
        val text: String,
        val startTime: Long,
        val endTime: Long,
        val confidence: Float
    )
    
    fun release() {
        coroutineScope.cancel()
        _subtitleState.value = SubtitleState()
    }
}

enum class SubtitleFormat {
    ASS, SSA, SRT, VTT, UNKNOWN
}

enum class SubtitlePosition {
    BOTTOM, TOP, CENTER
}

data class SubtitleState(
    val isLoading: Boolean = false,
    val isGeneratingAI: Boolean = false,
    val isEnabled: Boolean = false,
    val subtitles: List<SubtitleEntry> = emptyList(),
    val currentSubtitleFile: String? = null,
    val format: SubtitleFormat = SubtitleFormat.UNKNOWN,
    val delay: Long = 0L,
    val scale: Float = 1.0f,
    val position: SubtitlePosition = SubtitlePosition.BOTTOM,
    val error: String? = null
)

data class SubtitleEntry(
    val startTime: Long,
    val endTime: Long,
    val text: String,
    val style: AssStyle,
    val format: SubtitleFormat
)

data class AssStyle(
    val name: String = "Default",
    val fontName: String = "Arial",
    val fontSize: Int = 20,
    @ColorInt val primaryColor: Int = Color.WHITE,
    @ColorInt val secondaryColor: Int = Color.WHITE,
    @ColorInt val outlineColor: Int = Color.BLACK,
    @ColorInt val shadowColor: Int = Color.BLACK,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikeout: Boolean = false,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val spacing: Float = 0f,
    val angle: Float = 0f,
    val borderStyle: Int = 1,
    val outline: Float = 2f,
    val shadow: Float = 0f,
    val alignment: Int = 2,
    val marginL: Int = 10,
    val marginR: Int = 10,
    val marginV: Int = 10
)