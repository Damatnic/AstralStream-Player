import 'package:hive/hive.dart';

part 'media_file.g.dart';

@HiveType(typeId: 0)
class MediaFile extends HiveObject {
  @HiveField(0)
  final String id;
  
  @HiveField(1)
  final String path;
  
  @HiveField(2)
  final String name;
  
  @HiveField(3)
  final String? thumbnailPath;
  
  @HiveField(4)
  final int size; // in bytes
  
  @HiveField(5)
  final Duration duration;
  
  @HiveField(6)
  final DateTime dateAdded;
  
  @HiveField(7)
  final DateTime lastModified;
  
  @HiveField(8)
  final MediaType type;
  
  @HiveField(9)
  final String? codec;
  
  @HiveField(10)
  final String? resolution;
  
  @HiveField(11)
  final int? bitrate;
  
  @HiveField(12)
  final double? frameRate;
  
  @HiveField(13)
  final List<String>? audioTracks;
  
  @HiveField(14)
  final List<String>? subtitleTracks;
  
  @HiveField(15)
  int playCount;
  
  @HiveField(16)
  Duration lastPosition;
  
  @HiveField(17)
  DateTime? lastPlayed;
  
  @HiveField(18)
  bool isFavorite;
  
  @HiveField(19)
  final String? folderPath;
  
  @HiveField(20)
  final String? albumArt;
  
  MediaFile({
    required this.id,
    required this.path,
    required this.name,
    this.thumbnailPath,
    required this.size,
    required this.duration,
    required this.dateAdded,
    required this.lastModified,
    required this.type,
    this.codec,
    this.resolution,
    this.bitrate,
    this.frameRate,
    this.audioTracks,
    this.subtitleTracks,
    this.playCount = 0,
    this.lastPosition = Duration.zero,
    this.lastPlayed,
    this.isFavorite = false,
    this.folderPath,
    this.albumArt,
  });
  
  String get sizeFormatted {
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(1)} KB';
    if (size < 1024 * 1024 * 1024) return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
  
  String get durationFormatted {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);
    
    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }
  
  double get watchProgress {
    if (duration.inSeconds == 0) return 0;
    return lastPosition.inSeconds / duration.inSeconds;
  }
  
  bool get isWatched => watchProgress > 0.9;
  bool get isInProgress => watchProgress > 0.05 && watchProgress < 0.9;
  
  MediaFile copyWith({
    String? id,
    String? path,
    String? name,
    String? thumbnailPath,
    int? size,
    Duration? duration,
    DateTime? dateAdded,
    DateTime? lastModified,
    MediaType? type,
    String? codec,
    String? resolution,
    int? bitrate,
    double? frameRate,
    List<String>? audioTracks,
    List<String>? subtitleTracks,
    int? playCount,
    Duration? lastPosition,
    DateTime? lastPlayed,
    bool? isFavorite,
    String? folderPath,
    String? albumArt,
  }) {
    return MediaFile(
      id: id ?? this.id,
      path: path ?? this.path,
      name: name ?? this.name,
      thumbnailPath: thumbnailPath ?? this.thumbnailPath,
      size: size ?? this.size,
      duration: duration ?? this.duration,
      dateAdded: dateAdded ?? this.dateAdded,
      lastModified: lastModified ?? this.lastModified,
      type: type ?? this.type,
      codec: codec ?? this.codec,
      resolution: resolution ?? this.resolution,
      bitrate: bitrate ?? this.bitrate,
      frameRate: frameRate ?? this.frameRate,
      audioTracks: audioTracks ?? this.audioTracks,
      subtitleTracks: subtitleTracks ?? this.subtitleTracks,
      playCount: playCount ?? this.playCount,
      lastPosition: lastPosition ?? this.lastPosition,
      lastPlayed: lastPlayed ?? this.lastPlayed,
      isFavorite: isFavorite ?? this.isFavorite,
      folderPath: folderPath ?? this.folderPath,
      albumArt: albumArt ?? this.albumArt,
    );
  }
}

@HiveType(typeId: 1)
enum MediaType {
  @HiveField(0)
  video,
  
  @HiveField(1)
  audio,
  
  @HiveField(2)
  subtitle,
}

@HiveType(typeId: 2)
class Playlist extends HiveObject {
  @HiveField(0)
  final String id;
  
  @HiveField(1)
  String name;
  
  @HiveField(2)
  String? description;
  
  @HiveField(3)
  List<String> mediaIds;
  
  @HiveField(4)
  final DateTime createdAt;
  
  @HiveField(5)
  DateTime modifiedAt;
  
  @HiveField(6)
  String? thumbnailPath;
  
  @HiveField(7)
  bool isPrivate;
  
  Playlist({
    required this.id,
    required this.name,
    this.description,
    required this.mediaIds,
    required this.createdAt,
    required this.modifiedAt,
    this.thumbnailPath,
    this.isPrivate = false,
  });
}

@HiveType(typeId: 3)
class WatchHistory extends HiveObject {
  @HiveField(0)
  final String mediaId;
  
  @HiveField(1)
  final DateTime watchedAt;
  
  @HiveField(2)
  final Duration position;
  
  @HiveField(3)
  final Duration totalDuration;
  
  WatchHistory({
    required this.mediaId,
    required this.watchedAt,
    required this.position,
    required this.totalDuration,
  });
}