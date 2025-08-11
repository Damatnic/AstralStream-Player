import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/media_file.dart';
import '../services/database_service.dart';
import '../services/media_scanner.dart';

// Database Service Provider
final databaseServiceProvider = Provider<DatabaseService>((ref) {
  return DatabaseService();
});

// Media Scanner Provider
final mediaScannerProvider = Provider<MediaScanner>((ref) {
  return MediaScanner();
});

// Media Files State
class MediaFilesNotifier extends StateNotifier<AsyncValue<List<MediaFile>>> {
  final DatabaseService _dbService;
  final MediaScanner _scanner;
  
  MediaFilesNotifier(this._dbService, this._scanner) 
      : super(const AsyncValue.loading()) {
    loadMediaFiles();
  }
  
  Future<void> loadMediaFiles() async {
    try {
      state = const AsyncValue.loading();
      final files = _dbService.getAllMediaFiles();
      state = AsyncValue.data(files);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> scanAndRefresh() async {
    try {
      state = const AsyncValue.loading();
      
      // Scan device for new media
      final newFiles = await _scanner.scanDevice();
      
      // Add to database
      await _dbService.addMediaFiles(newFiles);
      
      // Reload from database
      final allFiles = _dbService.getAllMediaFiles();
      state = AsyncValue.data(allFiles);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> scanFolder(String path) async {
    try {
      final currentFiles = state.valueOrNull ?? [];
      
      // Scan specific folder
      final newFiles = await _scanner.scanFolder(path);
      
      // Add to database
      await _dbService.addMediaFiles(newFiles);
      
      // Update state
      state = AsyncValue.data([...currentFiles, ...newFiles]);
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
  
  Future<void> deleteMediaFile(String id) async {
    await _dbService.deleteMediaFile(id);
    final currentFiles = state.valueOrNull ?? [];
    state = AsyncValue.data(
      currentFiles.where((file) => file.id != id).toList()
    );
  }
  
  Future<void> toggleFavorite(String id) async {
    await _dbService.toggleFavorite(id);
    await loadMediaFiles();
  }
  
  Future<void> updatePlaybackPosition(String id, Duration position) async {
    await _dbService.updatePlaybackPosition(id, position);
  }
}

// Media Files Provider
final mediaFilesProvider = 
    StateNotifierProvider<MediaFilesNotifier, AsyncValue<List<MediaFile>>>((ref) {
  final dbService = ref.watch(databaseServiceProvider);
  final scanner = ref.watch(mediaScannerProvider);
  return MediaFilesNotifier(dbService, scanner);
});

// Filtered Providers
final videoFilesProvider = Provider<List<MediaFile>>((ref) {
  final mediaFiles = ref.watch(mediaFilesProvider);
  return mediaFiles.maybeWhen(
    data: (files) => files.where((f) => f.type == MediaType.video).toList(),
    orElse: () => [],
  );
});

final audioFilesProvider = Provider<List<MediaFile>>((ref) {
  final mediaFiles = ref.watch(mediaFilesProvider);
  return mediaFiles.maybeWhen(
    data: (files) => files.where((f) => f.type == MediaType.audio).toList(),
    orElse: () => [],
  );
});

final recentlyPlayedProvider = Provider<List<MediaFile>>((ref) {
  final dbService = ref.watch(databaseServiceProvider);
  return dbService.getRecentlyPlayed();
});

final favoritesProvider = Provider<List<MediaFile>>((ref) {
  final mediaFiles = ref.watch(mediaFilesProvider);
  return mediaFiles.maybeWhen(
    data: (files) => files.where((f) => f.isFavorite).toList(),
    orElse: () => [],
  );
});

// Search Provider
final searchQueryProvider = StateProvider<String>((ref) => '');

final searchResultsProvider = Provider<List<MediaFile>>((ref) {
  final query = ref.watch(searchQueryProvider);
  final mediaFiles = ref.watch(mediaFilesProvider);
  
  if (query.isEmpty) return [];
  
  return mediaFiles.maybeWhen(
    data: (files) {
      final lowercaseQuery = query.toLowerCase();
      return files.where((file) =>
        file.name.toLowerCase().contains(lowercaseQuery) ||
        (file.folderPath?.toLowerCase().contains(lowercaseQuery) ?? false)
      ).toList();
    },
    orElse: () => [],
  );
});

// Folder Structure Provider
final folderStructureProvider = Provider<Map<String, List<MediaFile>>>((ref) {
  final mediaFiles = ref.watch(mediaFilesProvider);
  
  return mediaFiles.maybeWhen(
    data: (files) {
      final folderMap = <String, List<MediaFile>>{};
      
      for (final file in files) {
        final folder = file.folderPath ?? 'Unknown';
        folderMap.putIfAbsent(folder, () => []).add(file);
      }
      
      return folderMap;
    },
    orElse: () => {},
  );
});

// Scan Progress Provider
final scanProgressProvider = StreamProvider<ScanProgress>((ref) {
  final scanner = ref.watch(mediaScannerProvider);
  return scanner.scanProgress;
});

// Sort Options
enum SortOption {
  nameAsc,
  nameDesc,
  dateAsc,
  dateDesc,
  sizeAsc,
  sizeDesc,
  durationAsc,
  durationDesc,
}

final sortOptionProvider = StateProvider<SortOption>((ref) => SortOption.dateDesc);

final sortedMediaFilesProvider = Provider<List<MediaFile>>((ref) {
  final mediaFiles = ref.watch(videoFilesProvider);
  final sortOption = ref.watch(sortOptionProvider);
  
  final sorted = List<MediaFile>.from(mediaFiles);
  
  switch (sortOption) {
    case SortOption.nameAsc:
      sorted.sort((a, b) => a.name.compareTo(b.name));
      break;
    case SortOption.nameDesc:
      sorted.sort((a, b) => b.name.compareTo(a.name));
      break;
    case SortOption.dateAsc:
      sorted.sort((a, b) => a.dateAdded.compareTo(b.dateAdded));
      break;
    case SortOption.dateDesc:
      sorted.sort((a, b) => b.dateAdded.compareTo(a.dateAdded));
      break;
    case SortOption.sizeAsc:
      sorted.sort((a, b) => a.size.compareTo(b.size));
      break;
    case SortOption.sizeDesc:
      sorted.sort((a, b) => b.size.compareTo(a.size));
      break;
    case SortOption.durationAsc:
      sorted.sort((a, b) => a.duration.compareTo(b.duration));
      break;
    case SortOption.durationDesc:
      sorted.sort((a, b) => b.duration.compareTo(a.duration));
      break;
  }
  
  return sorted;
});