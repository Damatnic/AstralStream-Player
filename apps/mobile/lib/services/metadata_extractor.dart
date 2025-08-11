import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as path;
import 'package:uuid/uuid.dart';
import '../models/media_file.dart';

class MetadataExtractor {
  static final MetadataExtractor _instance = MetadataExtractor._internal();
  factory MetadataExtractor() => _instance;
  MetadataExtractor._internal();
  
  final _uuid = const Uuid();
  
  Future<MediaMetadata?> extractMetadata(String filePath) async {
    try {
      final file = File(filePath);
      if (!await file.exists()) return null;
      
      // Get basic file info
      final stat = await file.stat();
      final fileName = path.basename(filePath);
      final extension = path.extension(filePath).toLowerCase();
      
      // For now, return basic metadata
      // In production, we'd use platform channels to call native FFmpeg
      return MediaMetadata(
        path: filePath,
        fileName: fileName,
        fileSize: stat.size,
        lastModified: stat.modified,
        extension: extension,
        // These would be extracted via FFmpeg
        duration: Duration.zero,
        width: 0,
        height: 0,
        codec: 'unknown',
        bitrate: 0,
        frameRate: 0,
        audioCodec: 'unknown',
        audioBitrate: 0,
        audioSampleRate: 0,
        audioChannels: 0,
      );
    } catch (e) {
      debugPrint('Error extracting metadata: $e');
      return null;
    }
  }
  
  Future<Uint8List?> generateThumbnail(
    String videoPath, {
    Duration? position,
    int width = 320,
    int height = 180,
  }) async {
    try {
      // In production, we'd use FFmpeg to generate thumbnails
      // For now, return null
      // Example FFmpeg command:
      // ffmpeg -i video.mp4 -ss 00:00:05 -vframes 1 -vf scale=320:180 thumbnail.jpg
      
      return null;
    } catch (e) {
      debugPrint('Error generating thumbnail: $e');
      return null;
    }
  }
  
  Future<String?> saveThumbnail(Uint8List thumbnailData, String videoPath) async {
    try {
      final appDir = await getApplicationDocumentsDirectory();
      final thumbnailsDir = Directory('${appDir.path}/thumbnails');
      
      if (!await thumbnailsDir.exists()) {
        await thumbnailsDir.create(recursive: true);
      }
      
      final videoName = path.basenameWithoutExtension(videoPath);
      final thumbnailPath = '${thumbnailsDir.path}/${videoName}_${_uuid.v4()}.jpg';
      
      final thumbnailFile = File(thumbnailPath);
      await thumbnailFile.writeAsBytes(thumbnailData);
      
      return thumbnailPath;
    } catch (e) {
      debugPrint('Error saving thumbnail: $e');
      return null;
    }
  }
  
  Future<List<Uint8List>?> generateVideoPreview(
    String videoPath, {
    int frameCount = 10,
    int width = 160,
    int height = 90,
  }) async {
    try {
      // Generate multiple thumbnails for video preview
      // This would use FFmpeg to extract frames at regular intervals
      
      return null;
    } catch (e) {
      debugPrint('Error generating video preview: $e');
      return null;
    }
  }
  
  Future<MediaInfo> analyzeMedia(String filePath) async {
    // This would use FFmpeg probe to get detailed media information
    return MediaInfo(
      streams: [],
      format: FormatInfo(
        filename: filePath,
        duration: 0,
        size: 0,
        bitrate: 0,
      ),
    );
  }
  
  Future<List<SubtitleTrack>> detectSubtitles(String videoPath) async {
    final subtitles = <SubtitleTrack>[];
    
    try {
      final videoDir = path.dirname(videoPath);
      final videoName = path.basenameWithoutExtension(videoPath);
      
      final dir = Directory(videoDir);
      if (!await dir.exists()) return subtitles;
      
      final files = await dir.list().toList();
      
      for (final file in files) {
        if (file is File) {
          final fileName = path.basename(file.path);
          final extension = path.extension(file.path).toLowerCase();
          
          // Check for subtitle files
          if (fileName.startsWith(videoName) && 
              ['.srt', '.ass', '.ssa', '.vtt', '.sub'].contains(extension)) {
            
            // Extract language from filename (e.g., movie.en.srt)
            final parts = fileName.split('.');
            String language = 'Unknown';
            
            if (parts.length > 2) {
              final langCode = parts[parts.length - 2];
              language = _getLanguageName(langCode);
            }
            
            subtitles.add(SubtitleTrack(
              path: file.path,
              language: language,
              format: extension.replaceAll('.', ''),
              isEmbedded: false,
            ));
          }
        }
      }
    } catch (e) {
      debugPrint('Error detecting subtitles: $e');
    }
    
    return subtitles;
  }
  
  String _getLanguageName(String code) {
    final languages = {
      'en': 'English',
      'es': 'Spanish',
      'fr': 'French',
      'de': 'German',
      'it': 'Italian',
      'pt': 'Portuguese',
      'ru': 'Russian',
      'ja': 'Japanese',
      'ko': 'Korean',
      'zh': 'Chinese',
      'ar': 'Arabic',
      'hi': 'Hindi',
      'bn': 'Bengali',
      'pa': 'Punjabi',
      'te': 'Telugu',
      'mr': 'Marathi',
      'ta': 'Tamil',
      'ur': 'Urdu',
      'gu': 'Gujarati',
      'kn': 'Kannada',
      'ml': 'Malayalam',
      'or': 'Odia',
      'as': 'Assamese',
    };
    
    return languages[code.toLowerCase()] ?? code.toUpperCase();
  }
}

class MediaMetadata {
  final String path;
  final String fileName;
  final int fileSize;
  final DateTime lastModified;
  final String extension;
  final Duration duration;
  final int width;
  final int height;
  final String codec;
  final int bitrate;
  final double frameRate;
  final String audioCodec;
  final int audioBitrate;
  final int audioSampleRate;
  final int audioChannels;
  
  MediaMetadata({
    required this.path,
    required this.fileName,
    required this.fileSize,
    required this.lastModified,
    required this.extension,
    required this.duration,
    required this.width,
    required this.height,
    required this.codec,
    required this.bitrate,
    required this.frameRate,
    required this.audioCodec,
    required this.audioBitrate,
    required this.audioSampleRate,
    required this.audioChannels,
  });
  
  String get resolution => '${width}x$height';
  
  String get aspectRatio {
    if (width == 0 || height == 0) return 'Unknown';
    final ratio = width / height;
    
    if ((ratio - 16/9).abs() < 0.1) return '16:9';
    if ((ratio - 4/3).abs() < 0.1) return '4:3';
    if ((ratio - 21/9).abs() < 0.1) return '21:9';
    if ((ratio - 1).abs() < 0.1) return '1:1';
    
    return '${width}:$height';
  }
}

class MediaInfo {
  final List<StreamInfo> streams;
  final FormatInfo format;
  
  MediaInfo({
    required this.streams,
    required this.format,
  });
}

class StreamInfo {
  final int index;
  final String codecType;
  final String codecName;
  final int? width;
  final int? height;
  final String? displayAspectRatio;
  final double? frameRate;
  final int? bitrate;
  final int? sampleRate;
  final int? channels;
  final String? language;
  
  StreamInfo({
    required this.index,
    required this.codecType,
    required this.codecName,
    this.width,
    this.height,
    this.displayAspectRatio,
    this.frameRate,
    this.bitrate,
    this.sampleRate,
    this.channels,
    this.language,
  });
}

class FormatInfo {
  final String filename;
  final double duration;
  final int size;
  final int bitrate;
  
  FormatInfo({
    required this.filename,
    required this.duration,
    required this.size,
    required this.bitrate,
  });
}

class SubtitleTrack {
  final String path;
  final String language;
  final String format;
  final bool isEmbedded;
  
  SubtitleTrack({
    required this.path,
    required this.language,
    required this.format,
    required this.isEmbedded,
  });
}