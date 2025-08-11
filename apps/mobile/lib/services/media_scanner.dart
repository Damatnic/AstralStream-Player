import 'dart:io';
import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
import '../models/media_file.dart';
import '../core/constants/app_constants.dart';
import 'package:uuid/uuid.dart';

class MediaScanner {
  static final MediaScanner _instance = MediaScanner._internal();
  factory MediaScanner() => _instance;
  MediaScanner._internal();

  final _uuid = const Uuid();
  final _scanController = StreamController<ScanProgress>.broadcast();
  Stream<ScanProgress> get scanProgress => _scanController.stream;
  
  bool _isScanning = false;
  bool get isScanning => _isScanning;
  
  final List<String> _defaultScanPaths = [
    '/storage/emulated/0/Movies',
    '/storage/emulated/0/Download',
    '/storage/emulated/0/DCIM',
    '/storage/emulated/0/WhatsApp/Media/WhatsApp Video',
    '/storage/emulated/0/WhatsApp/Media/.Statuses',
    '/storage/emulated/0/Telegram/Telegram Video',
    '/storage/emulated/0/Pictures',
    '/storage/emulated/0/Videos',
    '/storage/emulated/0/Music',
    '/storage/emulated/0/Recordings',
  ];
  
  Future<bool> requestPermissions() async {
    try {
      final deviceInfo = DeviceInfoPlugin();
      final androidInfo = await deviceInfo.androidInfo;
      
      if (androidInfo.version.sdkInt >= 33) {
        // Android 13+ requires different permissions
        final videoStatus = await Permission.videos.request();
        final audioStatus = await Permission.audio.request();
        final imagesStatus = await Permission.photos.request();
        
        return videoStatus.isGranted && 
               audioStatus.isGranted && 
               imagesStatus.isGranted;
      } else if (androidInfo.version.sdkInt >= 30) {
        // Android 11+ requires MANAGE_EXTERNAL_STORAGE
        final status = await Permission.manageExternalStorage.request();
        return status.isGranted;
      } else {
        // Android 10 and below
        final status = await Permission.storage.request();
        return status.isGranted;
      }
    } catch (e) {
      debugPrint('Error requesting permissions: $e');
      return false;
    }
  }
  
  Future<List<MediaFile>> scanDevice({
    List<String>? customPaths,
    bool scanFullDevice = false,
  }) async {
    if (_isScanning) {
      debugPrint('Scan already in progress');
      return [];
    }
    
    _isScanning = true;
    final mediaFiles = <MediaFile>[];
    
    try {
      // Request permissions first
      final hasPermission = await requestPermissions();
      if (!hasPermission) {
        _scanController.add(ScanProgress(
          status: ScanStatus.error,
          message: 'Storage permission denied',
        ));
        return [];
      }
      
      _scanController.add(ScanProgress(
        status: ScanStatus.scanning,
        message: 'Starting media scan...',
      ));
      
      List<String> pathsToScan;
      
      if (scanFullDevice) {
        pathsToScan = ['/storage/emulated/0'];
      } else {
        pathsToScan = customPaths ?? _defaultScanPaths;
      }
      
      for (final scanPath in pathsToScan) {
        final dir = Directory(scanPath);
        if (await dir.exists()) {
          await _scanDirectory(dir, mediaFiles);
        }
      }
      
      _scanController.add(ScanProgress(
        status: ScanStatus.completed,
        message: 'Scan completed',
        totalFiles: mediaFiles.length,
      ));
      
      return mediaFiles;
    } catch (e) {
      debugPrint('Error during scan: $e');
      _scanController.add(ScanProgress(
        status: ScanStatus.error,
        message: 'Scan failed: $e',
      ));
      return mediaFiles;
    } finally {
      _isScanning = false;
    }
  }
  
  Future<void> _scanDirectory(Directory dir, List<MediaFile> mediaFiles) async {
    try {
      final entities = await dir.list(recursive: true, followLinks: false).toList();
      
      for (final entity in entities) {
        if (entity is File) {
          final extension = path.extension(entity.path).toLowerCase().replaceAll('.', '');
          
          MediaType? type;
          if (AppConstants.supportedVideoExtensions.contains(extension)) {
            type = MediaType.video;
          } else if (AppConstants.supportedAudioExtensions.contains(extension)) {
            type = MediaType.audio;
          } else if (AppConstants.supportedSubtitleExtensions.contains(extension)) {
            type = MediaType.subtitle;
          }
          
          if (type != null && type != MediaType.subtitle) {
            final mediaFile = await _createMediaFile(entity, type);
            if (mediaFile != null) {
              mediaFiles.add(mediaFile);
              
              _scanController.add(ScanProgress(
                status: ScanStatus.scanning,
                message: 'Found: ${mediaFile.name}',
                currentFile: mediaFile.name,
                totalFiles: mediaFiles.length,
              ));
            }
          }
        }
      }
    } catch (e) {
      debugPrint('Error scanning directory ${dir.path}: $e');
    }
  }
  
  Future<MediaFile?> _createMediaFile(File file, MediaType type) async {
    try {
      final stat = await file.stat();
      final fileName = path.basename(file.path);
      final folderPath = path.dirname(file.path);
      
      // For now, we'll create basic media file info
      // In production, we'd extract metadata using ffmpeg or similar
      return MediaFile(
        id: _uuid.v4(),
        path: file.path,
        name: fileName,
        size: stat.size,
        duration: Duration.zero, // TODO: Extract actual duration
        dateAdded: DateTime.now(),
        lastModified: stat.modified,
        type: type,
        folderPath: folderPath,
      );
    } catch (e) {
      debugPrint('Error creating media file for ${file.path}: $e');
      return null;
    }
  }
  
  Future<List<MediaFile>> scanFolder(String folderPath) async {
    final mediaFiles = <MediaFile>[];
    final dir = Directory(folderPath);
    
    if (!await dir.exists()) {
      return mediaFiles;
    }
    
    await _scanDirectory(dir, mediaFiles);
    return mediaFiles;
  }
  
  Future<List<String>> getSubtitlesForVideo(String videoPath) async {
    final subtitles = <String>[];
    final videoDir = path.dirname(videoPath);
    final videoName = path.basenameWithoutExtension(videoPath);
    
    final dir = Directory(videoDir);
    if (!await dir.exists()) return subtitles;
    
    try {
      final entities = await dir.list().toList();
      for (final entity in entities) {
        if (entity is File) {
          final fileName = path.basename(entity.path);
          final extension = path.extension(entity.path).toLowerCase().replaceAll('.', '');
          
          // Check if it's a subtitle file with matching name
          if (AppConstants.supportedSubtitleExtensions.contains(extension)) {
            if (fileName.startsWith(videoName)) {
              subtitles.add(entity.path);
            }
          }
        }
      }
    } catch (e) {
      debugPrint('Error scanning for subtitles: $e');
    }
    
    return subtitles;
  }
  
  void dispose() {
    _scanController.close();
  }
}

class ScanProgress {
  final ScanStatus status;
  final String message;
  final String? currentFile;
  final int? totalFiles;
  final double? progress;
  
  ScanProgress({
    required this.status,
    required this.message,
    this.currentFile,
    this.totalFiles,
    this.progress,
  });
}

enum ScanStatus {
  idle,
  scanning,
  completed,
  error,
}