package com.astralstream.player.ai.subtitle

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.*
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI-powered subtitle generation processor using on-device speech recognition
 * Handles speech-to-text, translation, speaker diarization, and formatting
 */
@Singleton
class SubtitleGenerationProcessor @Inject constructor() {
    
    companion object {
        private const val TAG = "SubtitleGenerationProcessor"
        private const val SPEECH_RECOGNITION_MODEL = "whisper_mobile.tflite"
        private const val SPEAKER_DIARIZATION_MODEL = "speaker_embeddings.tflite"
        private const val TRANSLATION_MODEL = "multilingual_translator.tflite"
        private const val PUNCTUATION_MODEL = "punctuation_restorer.tflite"
        
        // Audio processing parameters
        private const val SAMPLE_RATE = 16000 // Whisper expects 16kHz
        private const val AUDIO_CHUNK_SIZE = 16000 * 30 // 30 second chunks
        private const val OVERLAP_SIZE = 16000 * 2 // 2 second overlap
        private const val MIN_SPEECH_DURATION = 1000L // 1 second minimum
        private const val MAX_SUBTITLE_LENGTH = 84 // Max characters per subtitle line
        
        // Supported languages for translation
        private val SUPPORTED_LANGUAGES = mapOf(
            "en" to "English",
            "es" to "Spanish", 
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "ja" to "Japanese",
            "ko" to "Korean",
            "zh" to "Chinese",
            "ar" to "Arabic",
            "hi" to "Hindi"
        )
    }
    
    private var speechRecognitionInterpreter: Interpreter? = null
    private var speakerDiarizationInterpreter: Interpreter? = null
    private var translationInterpreter: Interpreter? = null
    private var punctuationInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val subtitleQueue = ConcurrentLinkedQueue<SubtitleSegment>()
    private val speakerProfiles = mutableMapOf<String, SpeakerProfile>()
    
    data class SubtitleGenerationOptions(
        val enableRealTimeGeneration: Boolean = true,
        val enableSpeakerDiarization: Boolean = true,
        val enableTranslation: Boolean = false,
        val targetLanguage: String = "en",
        val sourceLanguage: String = "auto", // Auto-detect
        val enablePunctuation: Boolean = true,
        val enableCapitalization: Boolean = true,
        val maxSubtitleDuration: Long = 6000L, // 6 seconds max per subtitle
        val wordsPerMinuteThreshold: Int = 200, // For subtitle timing
        val confidenceThreshold: Float = 0.6f
    )
    
    data class SubtitleSegment(
        val id: String,
        val text: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val speakerId: String?,
        val speakerName: String?,
        val confidence: Float,
        val language: String,
        val translatedText: String? = null,
        val wordTimings: List<WordTiming> = emptyList()
    )
    
    data class WordTiming(
        val word: String,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val confidence: Float
    )
    
    data class SpeakerProfile(
        val id: String,
        val name: String,
        val voiceEmbedding: FloatArray,
        val confidence: Float,
        val sampleCount: Int
    )
    
    data class SubtitleGenerationResult(
        val segments: List<SubtitleSegment>,
        val detectedLanguage: String,
        val totalProcessingTimeMs: Long,
        val averageConfidence: Float,
        val speakerCount: Int,
        val success: Boolean,
        val error: String? = null
    )
    
    /**
     * Initialize the subtitle generation processor
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Initializing subtitle generation processor...")
            
            val options = Interpreter.Options()
            
            // Check GPU compatibility
            val compatibilityList = CompatibilityList()
            if (compatibilityList.isDelegateSupportedOnThisDevice) {
                val delegateOptions = compatibilityList.bestOptionsForThisDevice
                gpuDelegate = GpuDelegate(delegateOptions)
                options.addDelegate(gpuDelegate)
                Log.d(TAG, "GPU acceleration enabled for subtitle generation")
            } else {
                options.setNumThreads(4)
                Log.d(TAG, "Using CPU with 4 threads for subtitle generation")
            }
            
            // Load models (placeholder - in real implementation, load from assets)
            // speechRecognitionInterpreter = loadModel(SPEECH_RECOGNITION_MODEL, options)
            // speakerDiarizationInterpreter = loadModel(SPEAKER_DIARIZATION_MODEL, options)
            // translationInterpreter = loadModel(TRANSLATION_MODEL, options)
            // punctuationInterpreter = loadModel(PUNCTUATION_MODEL, options)
            
            Log.d(TAG, "Subtitle generation processor initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize subtitle generation processor", e)
            false
        }
    }
    
    /**
     * Generate subtitles from audio file
     */
    suspend fun generateSubtitlesFromFile(
        audioFilePath: String,
        options: SubtitleGenerationOptions = SubtitleGenerationOptions()
    ): SubtitleGenerationResult = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        val segments = mutableListOf<SubtitleSegment>()
        
        try {
            Log.d(TAG, "Starting subtitle generation for: $audioFilePath")
            
            // Extract audio from file
            val audioData = extractAudioFromFile(audioFilePath)
            if (audioData.isEmpty()) {
                return@withContext SubtitleGenerationResult(
                    segments = emptyList(),
                    detectedLanguage = "unknown",
                    totalProcessingTimeMs = System.currentTimeMillis() - startTime,
                    averageConfidence = 0f,
                    speakerCount = 0,
                    success = false,
                    error = "Failed to extract audio data"
                )
            }
            
            // Process audio in chunks
            val chunkSize = AUDIO_CHUNK_SIZE
            val overlapSize = OVERLAP_SIZE
            var chunkStart = 0
            
            while (chunkStart < audioData.size) {
                val chunkEnd = kotlin.math.min(chunkStart + chunkSize, audioData.size)
                val chunk = audioData.sliceArray(chunkStart until chunkEnd)
                
                if (chunk.size < MIN_SPEECH_DURATION * SAMPLE_RATE / 1000) {
                    break // Skip chunks that are too small
                }
                
                val chunkTimeOffsetMs = (chunkStart.toDouble() / SAMPLE_RATE * 1000).toLong()
                val chunkSegments = processAudioChunk(chunk, chunkTimeOffsetMs, options)
                segments.addAll(chunkSegments)
                
                chunkStart += (chunkSize - overlapSize)
            }
            
            // Post-process segments
            val processedSegments = postProcessSegments(segments, options)
            val detectedLanguage = detectPrimaryLanguage(processedSegments)
            val averageConfidence = calculateAverageConfidence(processedSegments)
            val speakerCount = countUniqueSpeakers(processedSegments)
            
            val totalTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Subtitle generation completed in ${totalTime}ms. " +
                      "Generated ${processedSegments.size} segments.")
            
            SubtitleGenerationResult(
                segments = processedSegments,
                detectedLanguage = detectedLanguage,
                totalProcessingTimeMs = totalTime,
                averageConfidence = averageConfidence,
                speakerCount = speakerCount,
                success = true
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating subtitles", e)
            SubtitleGenerationResult(
                segments = segments,
                detectedLanguage = "unknown",
                totalProcessingTimeMs = System.currentTimeMillis() - startTime,
                averageConfidence = 0f,
                speakerCount = 0,
                success = false,
                error = e.message
            )
        }
    }
    
    /**
     * Real-time subtitle generation from audio stream
     */
    suspend fun generateRealTimeSubtitles(
        audioStream: ByteBuffer,
        options: SubtitleGenerationOptions = SubtitleGenerationOptions()
    ): List<SubtitleSegment> = withContext(Dispatchers.Default) {
        
        try {
            // Convert audio stream to float array
            val audioData = convertAudioStreamToFloatArray(audioStream)
            
            if (audioData.size < MIN_SPEECH_DURATION * SAMPLE_RATE / 1000) {
                return@withContext emptyList()
            }
            
            // Process in real-time mode
            val segments = processAudioChunk(audioData, System.currentTimeMillis(), options)
            
            // Add to queue for streaming
            segments.forEach { segment ->
                subtitleQueue.offer(segment)
            }
            
            segments
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating real-time subtitles", e)
            emptyList()
        }
    }
    
    /**
     * Process audio chunk for speech recognition and diarization
     */
    private suspend fun processAudioChunk(
        audioChunk: FloatArray,
        timeOffsetMs: Long,
        options: SubtitleGenerationOptions
    ): List<SubtitleSegment> = withContext(Dispatchers.Default) {
        
        val segments = mutableListOf<SubtitleSegment>()
        
        try {
            // Voice Activity Detection
            val voiceSegments = detectVoiceActivity(audioChunk, timeOffsetMs)
            
            for (voiceSegment in voiceSegments) {
                val segmentAudio = audioChunk.sliceArray(
                    voiceSegment.first until kotlin.math.min(voiceSegment.second, audioChunk.size)
                )
                
                // Speech recognition
                val recognitionResult = performSpeechRecognition(segmentAudio, options)
                if (recognitionResult.confidence < options.confidenceThreshold) {
                    continue
                }
                
                // Speaker identification
                val speakerId = if (options.enableSpeakerDiarization) {
                    identifySpeaker(segmentAudio)
                } else null
                
                // Create subtitle segment
                val startTime = timeOffsetMs + (voiceSegment.first * 1000L / SAMPLE_RATE)
                val endTime = timeOffsetMs + (voiceSegment.second * 1000L / SAMPLE_RATE)
                
                var text = recognitionResult.text
                
                // Apply punctuation and capitalization
                if (options.enablePunctuation) {
                    text = addPunctuation(text)
                }
                if (options.enableCapitalization) {
                    text = properCapitalization(text)
                }
                
                // Translation if needed
                val translatedText = if (options.enableTranslation && 
                    options.targetLanguage != recognitionResult.language) {
                    translateText(text, recognitionResult.language, options.targetLanguage)
                } else null
                
                val segment = SubtitleSegment(
                    id = generateSegmentId(),
                    text = text,
                    startTimeMs = startTime,
                    endTimeMs = endTime,
                    speakerId = speakerId,
                    speakerName = getSpeakerName(speakerId),
                    confidence = recognitionResult.confidence,
                    language = recognitionResult.language,
                    translatedText = translatedText,
                    wordTimings = recognitionResult.wordTimings
                )
                
                segments.add(segment)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing audio chunk", e)
        }
        
        segments
    }
    
    /**
     * Detect voice activity in audio
     */
    private fun detectVoiceActivity(
        audioData: FloatArray,
        timeOffsetMs: Long
    ): List<Pair<Int, Int>> {
        val segments = mutableListOf<Pair<Int, Int>>()
        
        // Simple energy-based VAD (Voice Activity Detection)
        val windowSize = SAMPLE_RATE / 100 // 10ms windows
        val energyThreshold = 0.01f
        val minSpeechLength = SAMPLE_RATE / 4 // 250ms minimum
        
        var speechStart = -1
        var i = 0
        
        while (i < audioData.size - windowSize) {
            val window = audioData.sliceArray(i until i + windowSize)
            val energy = window.map { it * it }.average().toFloat()
            
            if (energy > energyThreshold) {
                if (speechStart == -1) {
                    speechStart = i
                }
            } else {
                if (speechStart != -1 && (i - speechStart) > minSpeechLength) {
                    segments.add(Pair(speechStart, i))
                }
                speechStart = -1
            }
            
            i += windowSize / 2 // 50% overlap
        }
        
        // Handle final segment
        if (speechStart != -1 && (audioData.size - speechStart) > minSpeechLength) {
            segments.add(Pair(speechStart, audioData.size))
        }
        
        return segments
    }
    
    /**
     * Perform speech recognition on audio segment
     */
    private suspend fun performSpeechRecognition(
        audioSegment: FloatArray,
        options: SubtitleGenerationOptions
    ): SpeechRecognitionResult = withContext(Dispatchers.Default) {
        
        try {
            // Placeholder speech recognition implementation
            // In real implementation, this would use Whisper or similar model
            val text = generatePlaceholderText(audioSegment)
            val language = detectLanguage(audioSegment)
            val wordTimings = generateWordTimings(text, audioSegment.size)
            
            SpeechRecognitionResult(
                text = text,
                language = language,
                confidence = 0.85f, // Placeholder confidence
                wordTimings = wordTimings
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in speech recognition", e)
            SpeechRecognitionResult("", "en", 0f, emptyList())
        }
    }
    
    /**
     * Identify speaker from voice embedding
     */
    private suspend fun identifySpeaker(audioSegment: FloatArray): String? = 
        withContext(Dispatchers.Default) {
        
        try {
            // Extract voice embedding
            val embedding = extractVoiceEmbedding(audioSegment)
            
            // Compare with known speakers
            var bestMatchId: String? = null
            var bestSimilarity = 0f
            val similarityThreshold = 0.8f
            
            speakerProfiles.forEach { (speakerId, profile) ->
                val similarity = calculateEmbeddingSimilarity(embedding, profile.voiceEmbedding)
                if (similarity > bestSimilarity && similarity > similarityThreshold) {
                    bestSimilarity = similarity
                    bestMatchId = speakerId
                }
            }
            
            // Create new speaker if no match found
            if (bestMatchId == null) {
                val newSpeakerId = "speaker_${speakerProfiles.size + 1}"
                val newProfile = SpeakerProfile(
                    id = newSpeakerId,
                    name = "Speaker ${speakerProfiles.size + 1}",
                    voiceEmbedding = embedding,
                    confidence = 1.0f,
                    sampleCount = 1
                )
                speakerProfiles[newSpeakerId] = newProfile
                bestMatchId = newSpeakerId
            } else {
                // Update existing speaker profile
                speakerProfiles[bestMatchId]?.let { profile ->
                    updateSpeakerProfile(profile, embedding)
                }
            }
            
            bestMatchId
            
        } catch (e: Exception) {
            Log.e(TAG, "Error identifying speaker", e)
            null
        }
    }
    
    /**
     * Add punctuation to text using AI model
     */
    private suspend fun addPunctuation(text: String): String = withContext(Dispatchers.Default) {
        try {
            // Placeholder punctuation restoration
            // In real implementation, this would use a punctuation model
            var punctuatedText = text
            
            // Simple rule-based punctuation
            punctuatedText = punctuatedText.replace(Regex("\\s+"), " ")
            punctuatedText = punctuatedText.trim()
            
            // Add periods at natural breaks
            punctuatedText = punctuatedText.replace(Regex("(\\w+)\\s+(and|but|so|then|now)\\s+"), "$1. $2 ")
            
            // Ensure sentence ends with punctuation
            if (!punctuatedText.matches(Regex(".*[.!?]\\s*$"))) {
                punctuatedText += "."
            }
            
            punctuatedText
        } catch (e: Exception) {
            Log.e(TAG, "Error adding punctuation", e)
            text
        }
    }
    
    /**
     * Apply proper capitalization
     */
    private fun properCapitalization(text: String): String {
        return text.split(". ").joinToString(". ") { sentence ->
            sentence.trim().replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase() else it.toString() 
            }
        }
    }
    
    /**
     * Translate text to target language
     */
    private suspend fun translateText(
        text: String,
        sourceLanguage: String,
        targetLanguage: String
    ): String? = withContext(Dispatchers.Default) {
        
        try {
            if (sourceLanguage == targetLanguage) return@withContext null
            
            // Placeholder translation implementation
            // In real implementation, this would use a translation model
            Log.d(TAG, "Translating from $sourceLanguage to $targetLanguage: $text")
            
            // Simple placeholder translation
            when (targetLanguage) {
                "es" -> "[ES] $text"
                "fr" -> "[FR] $text"
                "de" -> "[DE] $text"
                else -> "[${targetLanguage.uppercase()}] $text"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error translating text", e)
            null
        }
    }
    
    /**
     * Post-process subtitle segments for better formatting and timing
     */
    private fun postProcessSegments(
        segments: List<SubtitleSegment>,
        options: SubtitleGenerationOptions
    ): List<SubtitleSegment> {
        val processed = mutableListOf<SubtitleSegment>()
        
        for (segment in segments) {
            var processedSegment = segment
            
            // Split long segments
            if (segment.text.length > MAX_SUBTITLE_LENGTH) {
                val splitSegments = splitLongSubtitle(segment)
                processed.addAll(splitSegments)
                continue
            }
            
            // Adjust timing based on reading speed
            val readingTimeMs = calculateReadingTime(segment.text, options.wordsPerMinuteThreshold)
            val actualDurationMs = segment.endTimeMs - segment.startTimeMs
            
            if (actualDurationMs < readingTimeMs) {
                // Extend subtitle duration if too short to read
                val extension = kotlin.math.min(
                    readingTimeMs - actualDurationMs,
                    options.maxSubtitleDuration - actualDurationMs
                )
                processedSegment = segment.copy(endTimeMs = segment.endTimeMs + extension)
            } else if (actualDurationMs > options.maxSubtitleDuration) {
                // Trim if too long
                processedSegment = segment.copy(endTimeMs = segment.startTimeMs + options.maxSubtitleDuration)
            }
            
            processed.add(processedSegment)
        }
        
        // Remove overlapping segments and merge adjacent ones
        return mergeAndCleanSegments(processed)
    }
    
    /**
     * Export subtitles in various formats
     */
    suspend fun exportSubtitles(
        segments: List<SubtitleSegment>,
        format: SubtitleFormat,
        filePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            val content = when (format) {
                SubtitleFormat.SRT -> generateSRTContent(segments)
                SubtitleFormat.VTT -> generateVTTContent(segments)
                SubtitleFormat.ASS -> generateASSContent(segments)
                SubtitleFormat.JSON -> generateJSONContent(segments)
            }
            
            // Write to file (placeholder implementation)
            Log.d(TAG, "Exporting ${segments.size} subtitles to $filePath in $format format")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting subtitles", e)
            false
        }
    }
    
    // Helper functions and data classes
    data class SpeechRecognitionResult(
        val text: String,
        val language: String,
        val confidence: Float,
        val wordTimings: List<WordTiming>
    )
    
    enum class SubtitleFormat { SRT, VTT, ASS, JSON }
    
    private fun extractAudioFromFile(filePath: String): FloatArray {
        // Placeholder audio extraction
        // In real implementation, use MediaExtractor to extract audio
        return FloatArray(SAMPLE_RATE * 10) // 10 seconds of silence as placeholder
    }
    
    private fun convertAudioStreamToFloatArray(stream: ByteBuffer): FloatArray {
        val floatBuffer = stream.asFloatBuffer()
        val result = FloatArray(floatBuffer.remaining())
        floatBuffer.get(result)
        return result
    }
    
    private fun generatePlaceholderText(audioSegment: FloatArray): String {
        // Placeholder text generation based on audio length
        val durationSeconds = audioSegment.size / SAMPLE_RATE
        return when {
            durationSeconds < 2 -> "Hello."
            durationSeconds < 5 -> "This is a sample subtitle."
            else -> "This is a longer sample subtitle that demonstrates the subtitle generation capability."
        }
    }
    
    private fun detectLanguage(audioSegment: FloatArray): String {
        // Placeholder language detection
        return "en" // Default to English
    }
    
    private fun generateWordTimings(text: String, audioLength: Int): List<WordTiming> {
        val words = text.split(" ")
        val totalDurationMs = (audioLength.toDouble() / SAMPLE_RATE * 1000).toLong()
        val timePerWord = totalDurationMs / words.size
        
        return words.mapIndexed { index, word ->
            WordTiming(
                word = word,
                startTimeMs = index * timePerWord,
                endTimeMs = (index + 1) * timePerWord,
                confidence = 0.85f
            )
        }
    }
    
    private fun extractVoiceEmbedding(audioSegment: FloatArray): FloatArray {
        // Placeholder voice embedding extraction
        return FloatArray(128) { kotlin.random.Random.nextFloat() }
    }
    
    private fun calculateEmbeddingSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        // Cosine similarity
        val dotProduct = embedding1.zip(embedding2).sumOf { (a, b) -> (a * b).toDouble() }.toFloat()
        val norm1 = kotlin.math.sqrt(embedding1.sumOf { (it * it).toDouble() }).toFloat()
        val norm2 = kotlin.math.sqrt(embedding2.sumOf { (it * it).toDouble() }).toFloat()
        
        return if (norm1 > 0 && norm2 > 0) dotProduct / (norm1 * norm2) else 0f
    }
    
    private fun updateSpeakerProfile(profile: SpeakerProfile, newEmbedding: FloatArray) {
        // Simple moving average update
        val alpha = 0.1f
        for (i in profile.voiceEmbedding.indices) {
            profile.voiceEmbedding[i] = profile.voiceEmbedding[i] * (1 - alpha) + 
                                      newEmbedding[i] * alpha
        }
    }
    
    private fun getSpeakerName(speakerId: String?): String? {
        return speakerId?.let { speakerProfiles[it]?.name }
    }
    
    private fun generateSegmentId(): String {
        return "sub_${System.currentTimeMillis()}_${kotlin.random.Random.nextInt(1000)}"
    }
    
    private fun splitLongSubtitle(segment: SubtitleSegment): List<SubtitleSegment> {
        val words = segment.text.split(" ")
        val segments = mutableListOf<SubtitleSegment>()
        val duration = segment.endTimeMs - segment.startTimeMs
        
        var currentText = ""
        var wordIndex = 0
        
        while (wordIndex < words.size) {
            while (wordIndex < words.size && 
                   (currentText + " " + words[wordIndex]).length <= MAX_SUBTITLE_LENGTH) {
                currentText += if (currentText.isEmpty()) words[wordIndex] else " " + words[wordIndex]
                wordIndex++
            }
            
            if (currentText.isNotEmpty()) {
                val segmentDuration = duration * currentText.split(" ").size / words.size
                val startTime = segment.startTimeMs + (duration * (wordIndex - currentText.split(" ").size) / words.size)
                
                segments.add(segment.copy(
                    id = generateSegmentId(),
                    text = currentText,
                    startTimeMs = startTime,
                    endTimeMs = startTime + segmentDuration
                ))
                
                currentText = ""
            }
        }
        
        return segments
    }
    
    private fun calculateReadingTime(text: String, wordsPerMinute: Int): Long {
        val wordCount = text.split(" ").size
        return (wordCount * 60000L / wordsPerMinute) // Convert to milliseconds
    }
    
    private fun mergeAndCleanSegments(segments: List<SubtitleSegment>): List<SubtitleSegment> {
        if (segments.isEmpty()) return segments
        
        val sorted = segments.sortedBy { it.startTimeMs }
        val merged = mutableListOf<SubtitleSegment>()
        
        var current = sorted[0]
        
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            
            // Check for overlap or very close segments
            if (next.startTimeMs <= current.endTimeMs + 500) { // 500ms gap tolerance
                // Merge segments
                current = current.copy(
                    text = current.text + " " + next.text,
                    endTimeMs = next.endTimeMs,
                    confidence = (current.confidence + next.confidence) / 2
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        
        merged.add(current)
        return merged
    }
    
    private fun detectPrimaryLanguage(segments: List<SubtitleSegment>): String {
        return segments.groupBy { it.language }
                      .maxByOrNull { it.value.size }
                      ?.key ?: "en"
    }
    
    private fun calculateAverageConfidence(segments: List<SubtitleSegment>): Float {
        return if (segments.isEmpty()) 0f else segments.map { it.confidence }.average().toFloat()
    }
    
    private fun countUniqueSpeakers(segments: List<SubtitleSegment>): Int {
        return segments.mapNotNull { it.speakerId }.distinct().size
    }
    
    private fun generateSRTContent(segments: List<SubtitleSegment>): String {
        return segments.mapIndexed { index, segment ->
            "${index + 1}\n" +
            "${formatSRTTime(segment.startTimeMs)} --> ${formatSRTTime(segment.endTimeMs)}\n" +
            "${segment.text}\n"
        }.joinToString("\n")
    }
    
    private fun generateVTTContent(segments: List<SubtitleSegment>): String {
        val header = "WEBVTT\n\n"
        val content = segments.map { segment ->
            "${formatVTTTime(segment.startTimeMs)} --> ${formatVTTTime(segment.endTimeMs)}\n" +
            "${segment.text}\n"
        }.joinToString("\n")
        return header + content
    }
    
    private fun generateASSContent(segments: List<SubtitleSegment>): String {
        // Simplified ASS format
        val header = """
            [Script Info]
            Title: AstralStream Generated Subtitles
            
            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Arial,16,&Hffffff,&Hffffff,&H0,&H0,0,0,0,0,100,100,0,0,1,2,0,2,10,10,10,1
            
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            
        """.trimIndent()
        
        val events = segments.map { segment ->
            "Dialogue: 0,${formatASSTime(segment.startTimeMs)},${formatASSTime(segment.endTimeMs)},Default,,0,0,0,,${segment.text}"
        }.joinToString("\n")
        
        return header + events
    }
    
    private fun generateJSONContent(segments: List<SubtitleSegment>): String {
        // Simple JSON representation
        return segments.joinToString(",\n", "[\n", "\n]") { segment ->
            """  {
    "id": "${segment.id}",
    "text": "${segment.text.replace("\"", "\\\"")}",
    "start": ${segment.startTimeMs},
    "end": ${segment.endTimeMs},
    "speaker": "${segment.speakerName ?: ""}",
    "confidence": ${segment.confidence},
    "language": "${segment.language}"
  }"""
        }
    }
    
    private fun formatSRTTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        val milliseconds = timeMs % 1000
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }
    
    private fun formatVTTTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        val milliseconds = timeMs % 1000
        return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, milliseconds)
    }
    
    private fun formatASSTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        val centiseconds = (timeMs % 1000) / 10
        return String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            speechRecognitionInterpreter?.close()
            speakerDiarizationInterpreter?.close()
            translationInterpreter?.close()
            punctuationInterpreter?.close()
            gpuDelegate?.close()
            processingScope.cancel()
            subtitleQueue.clear()
            speakerProfiles.clear()
            Log.d(TAG, "Subtitle generation processor cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up subtitle generation processor", e)
        }
    }
}