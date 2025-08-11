import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'dart:io';
import 'package:path/path.dart' as path;
import 'package:permission_handler/permission_handler.dart';
import '../../models/media_file.dart';
import '../../core/constants/app_constants.dart';

class FileBrowserScreen extends ConsumerStatefulWidget {
  final String? initialPath;
  
  const FileBrowserScreen({
    super.key,
    this.initialPath,
  });

  @override
  ConsumerState<FileBrowserScreen> createState() => _FileBrowserScreenState();
}

class _FileBrowserScreenState extends ConsumerState<FileBrowserScreen> {
  late String _currentPath;
  List<FileSystemEntity> _entities = [];
  List<String> _pathHistory = [];
  bool _isLoading = false;
  bool _showHiddenFiles = false;
  SortType _sortType = SortType.name;
  bool _sortAscending = true;
  
  // Multi-select
  bool _isSelectionMode = false;
  Set<String> _selectedPaths = {};
  
  @override
  void initState() {
    super.initState();
    _currentPath = widget.initialPath ?? '/storage/emulated/0';
    _loadDirectory();
  }
  
  Future<void> _loadDirectory() async {
    setState(() {
      _isLoading = true;
    });
    
    try {
      final dir = Directory(_currentPath);
      if (await dir.exists()) {
        final entities = await dir.list().toList();
        
        // Filter and sort
        _entities = entities.where((entity) {
          if (!_showHiddenFiles && path.basename(entity.path).startsWith('.')) {
            return false;
          }
          
          if (entity is File) {
            final extension = path.extension(entity.path).toLowerCase().replaceAll('.', '');
            return AppConstants.supportedVideoExtensions.contains(extension) ||
                   AppConstants.supportedAudioExtensions.contains(extension);
          }
          
          return entity is Directory;
        }).toList();
        
        _sortEntities();
      }
    } catch (e) {
      _showError('Failed to load directory: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }
  
  void _sortEntities() {
    _entities.sort((a, b) {
      // Directories first
      if (a is Directory && b is File) return -1;
      if (a is File && b is Directory) return 1;
      
      int result = 0;
      switch (_sortType) {
        case SortType.name:
          result = path.basename(a.path).toLowerCase()
              .compareTo(path.basename(b.path).toLowerCase());
          break;
        case SortType.date:
          final aStat = a.statSync();
          final bStat = b.statSync();
          result = aStat.modified.compareTo(bStat.modified);
          break;
        case SortType.size:
          if (a is File && b is File) {
            final aStat = a.statSync();
            final bStat = b.statSync();
            result = aStat.size.compareTo(bStat.size);
          }
          break;
        case SortType.type:
          final aExt = path.extension(a.path);
          final bExt = path.extension(b.path);
          result = aExt.compareTo(bExt);
          break;
      }
      
      return _sortAscending ? result : -result;
    });
  }
  
  void _navigateToDirectory(String newPath) {
    _pathHistory.add(_currentPath);
    _currentPath = newPath;
    _loadDirectory();
  }
  
  void _navigateBack() {
    if (_pathHistory.isNotEmpty) {
      _currentPath = _pathHistory.removeLast();
      _loadDirectory();
    } else if (_currentPath != '/storage/emulated/0') {
      _currentPath = path.dirname(_currentPath);
      _loadDirectory();
    }
  }
  
  void _toggleSelection(String entityPath) {
    setState(() {
      if (_selectedPaths.contains(entityPath)) {
        _selectedPaths.remove(entityPath);
        if (_selectedPaths.isEmpty) {
          _isSelectionMode = false;
        }
      } else {
        _selectedPaths.add(entityPath);
      }
    });
  }
  
  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: _navigateBack,
        ),
        title: Text(
          path.basename(_currentPath).isEmpty 
            ? 'Storage' 
            : path.basename(_currentPath),
          style: const TextStyle(fontSize: 18),
        ),
        actions: [
          if (_isSelectionMode) ...[
            Text('${_selectedPaths.length} selected'),
            IconButton(
              icon: const Icon(Icons.select_all),
              onPressed: () {
                setState(() {
                  _selectedPaths = _entities
                      .where((e) => e is File)
                      .map((e) => e.path)
                      .toSet();
                });
              },
            ),
            IconButton(
              icon: const Icon(Icons.close),
              onPressed: () {
                setState(() {
                  _isSelectionMode = false;
                  _selectedPaths.clear();
                });
              },
            ),
          ] else ...[
            IconButton(
              icon: const Icon(Icons.search),
              onPressed: () {
                // Show search
              },
            ),
            PopupMenuButton<String>(
              icon: const Icon(Icons.sort),
              onSelected: (value) {
                setState(() {
                  switch (value) {
                    case 'name':
                      _sortType = SortType.name;
                      break;
                    case 'date':
                      _sortType = SortType.date;
                      break;
                    case 'size':
                      _sortType = SortType.size;
                      break;
                    case 'type':
                      _sortType = SortType.type;
                      break;
                    case 'ascending':
                      _sortAscending = true;
                      break;
                    case 'descending':
                      _sortAscending = false;
                      break;
                    case 'hidden':
                      _showHiddenFiles = !_showHiddenFiles;
                      break;
                  }
                  _loadDirectory();
                });
              },
              itemBuilder: (context) => [
                const PopupMenuItem(
                  value: 'name',
                  child: Text('Sort by Name'),
                ),
                const PopupMenuItem(
                  value: 'date',
                  child: Text('Sort by Date'),
                ),
                const PopupMenuItem(
                  value: 'size',
                  child: Text('Sort by Size'),
                ),
                const PopupMenuItem(
                  value: 'type',
                  child: Text('Sort by Type'),
                ),
                const PopupMenuDivider(),
                PopupMenuItem(
                  value: 'ascending',
                  child: Row(
                    children: [
                      Icon(_sortAscending ? Icons.check : null, size: 20),
                      const SizedBox(width: 8),
                      const Text('Ascending'),
                    ],
                  ),
                ),
                PopupMenuItem(
                  value: 'descending',
                  child: Row(
                    children: [
                      Icon(!_sortAscending ? Icons.check : null, size: 20),
                      const SizedBox(width: 8),
                      const Text('Descending'),
                    ],
                  ),
                ),
                const PopupMenuDivider(),
                PopupMenuItem(
                  value: 'hidden',
                  child: Row(
                    children: [
                      Icon(_showHiddenFiles ? Icons.check : null, size: 20),
                      const SizedBox(width: 8),
                      const Text('Show Hidden Files'),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
      body: Column(
        children: [
          // Breadcrumb navigation
          Container(
            height: 40,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              color: Theme.of(context).primaryColor.withOpacity(0.1),
              border: Border(
                bottom: BorderSide(
                  color: Theme.of(context).dividerColor,
                ),
              ),
            ),
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _buildBreadcrumbs(),
              ),
            ),
          ),
          
          // File list
          Expanded(
            child: _isLoading
              ? const Center(child: CircularProgressIndicator())
              : _entities.isEmpty
                ? Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.folder_open,
                          size: 64,
                          color: Colors.grey[400],
                        ),
                        const SizedBox(height: 16),
                        Text(
                          'No media files found',
                          style: TextStyle(
                            color: Colors.grey[600],
                            fontSize: 16,
                          ),
                        ),
                      ],
                    ),
                  )
                : ListView.builder(
                    itemCount: _entities.length,
                    itemBuilder: (context, index) {
                      final entity = _entities[index];
                      final isDirectory = entity is Directory;
                      final name = path.basename(entity.path);
                      final isSelected = _selectedPaths.contains(entity.path);
                      
                      // Get file info
                      String? subtitle;
                      IconData icon;
                      
                      if (isDirectory) {
                        icon = Icons.folder;
                        try {
                          final itemCount = Directory(entity.path)
                              .listSync()
                              .where((e) => e is File)
                              .length;
                          subtitle = '$itemCount items';
                        } catch (_) {
                          subtitle = null;
                        }
                      } else {
                        final extension = path.extension(entity.path).toLowerCase();
                        final stat = entity.statSync();
                        
                        if (AppConstants.supportedVideoExtensions.contains(
                            extension.replaceAll('.', ''))) {
                          icon = Icons.movie;
                        } else {
                          icon = Icons.audiotrack;
                        }
                        
                        subtitle = '${_formatFileSize(stat.size)} • ${_formatDate(stat.modified)}';
                      }
                      
                      return ListTile(
                        leading: Container(
                          width: 48,
                          height: 48,
                          decoration: BoxDecoration(
                            color: isDirectory
                              ? Colors.amber.withOpacity(0.2)
                              : Theme.of(context).primaryColor.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Stack(
                            children: [
                              Center(
                                child: Icon(
                                  icon,
                                  color: isDirectory
                                    ? Colors.amber
                                    : Theme.of(context).primaryColor,
                                ),
                              ),
                              if (_isSelectionMode && !isDirectory)
                                Positioned(
                                  top: 2,
                                  right: 2,
                                  child: Container(
                                    width: 20,
                                    height: 20,
                                    decoration: BoxDecoration(
                                      color: isSelected
                                        ? Theme.of(context).primaryColor
                                        : Colors.white,
                                      shape: BoxShape.circle,
                                      border: Border.all(
                                        color: Theme.of(context).primaryColor,
                                        width: 2,
                                      ),
                                    ),
                                    child: isSelected
                                      ? const Icon(
                                          Icons.check,
                                          size: 12,
                                          color: Colors.white,
                                        )
                                      : null,
                                  ),
                                ),
                            ],
                          ),
                        ),
                        title: Text(
                          name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: subtitle != null ? Text(subtitle) : null,
                        trailing: isDirectory
                          ? const Icon(Icons.chevron_right)
                          : PopupMenuButton<String>(
                              onSelected: (value) {
                                switch (value) {
                                  case 'play':
                                    // Play file
                                    break;
                                  case 'playlist':
                                    // Add to playlist
                                    break;
                                  case 'info':
                                    // Show info
                                    break;
                                  case 'delete':
                                    // Delete file
                                    break;
                                }
                              },
                              itemBuilder: (context) => [
                                const PopupMenuItem(
                                  value: 'play',
                                  child: Text('Play'),
                                ),
                                const PopupMenuItem(
                                  value: 'playlist',
                                  child: Text('Add to Playlist'),
                                ),
                                const PopupMenuItem(
                                  value: 'info',
                                  child: Text('Properties'),
                                ),
                                const PopupMenuItem(
                                  value: 'delete',
                                  child: Text('Delete'),
                                ),
                              ],
                            ),
                        onTap: () {
                          if (_isSelectionMode && !isDirectory) {
                            _toggleSelection(entity.path);
                          } else if (isDirectory) {
                            _navigateToDirectory(entity.path);
                          } else {
                            // Play file
                            Navigator.pushNamed(
                              context,
                              '/player',
                              arguments: {'path': entity.path},
                            );
                          }
                        },
                        onLongPress: !isDirectory ? () {
                          setState(() {
                            _isSelectionMode = true;
                            _selectedPaths.add(entity.path);
                          });
                        } : null,
                      );
                    },
                  ),
          ),
        ],
      ),
      
      // FAB for multi-select actions
      floatingActionButton: _isSelectionMode
        ? FloatingActionButton.extended(
            onPressed: () {
              // Play selected files
            },
            icon: const Icon(Icons.play_arrow),
            label: Text('Play ${_selectedPaths.length} files'),
          )
        : null,
    );
  }
  
  List<Widget> _buildBreadcrumbs() {
    final parts = _currentPath.split('/').where((p) => p.isNotEmpty).toList();
    final widgets = <Widget>[];
    
    // Root
    widgets.add(
      TextButton(
        onPressed: () {
          _currentPath = '/storage/emulated/0';
          _pathHistory.clear();
          _loadDirectory();
        },
        child: const Text('Storage'),
      ),
    );
    
    // Build path buttons
    for (int i = 0; i < parts.length; i++) {
      widgets.add(const Icon(Icons.chevron_right, size: 16));
      
      final isLast = i == parts.length - 1;
      final targetPath = '/${parts.take(i + 1).join('/')}';
      
      widgets.add(
        TextButton(
          onPressed: isLast ? null : () {
            _currentPath = targetPath;
            _loadDirectory();
          },
          child: Text(
            parts[i],
            style: TextStyle(
              color: isLast ? Theme.of(context).primaryColor : null,
            ),
          ),
        ),
      );
    }
    
    return widgets;
  }
  
  String _formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) {
      return '${(bytes / 1024).toStringAsFixed(1)} KB';
    }
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
  
  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final difference = now.difference(date);
    
    if (difference.inDays == 0) {
      return 'Today';
    } else if (difference.inDays == 1) {
      return 'Yesterday';
    } else if (difference.inDays < 7) {
      return '${difference.inDays} days ago';
    } else {
      return '${date.day}/${date.month}/${date.year}';
    }
  }
}

enum SortType {
  name,
  date,
  size,
  type,
}