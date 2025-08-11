import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

class PlayerScreen extends ConsumerStatefulWidget {
  final String filePath;
  final bool isNetwork;

  const PlayerScreen({
    super.key,
    required this.filePath,
    this.isNetwork = false,
  });

  @override
  ConsumerState<PlayerScreen> createState() => _PlayerScreenState();
}

class _PlayerScreenState extends ConsumerState<PlayerScreen> {
  late final Player player;
  late final VideoController controller;
  bool _showControls = true;
  bool _isLocked = false;

  @override
  void initState() {
    super.initState();
    
    // Initialize player
    player = Player();
    controller = VideoController(player);
    
    // Load media
    if (widget.isNetwork) {
      player.open(Media(widget.filePath));
    } else {
      player.open(Media('file:///${widget.filePath}'));
    }
    
    // Set landscape orientation
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    
    // Hide system UI
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    
    // Auto-hide controls after 3 seconds
    Future.delayed(const Duration(seconds: 3), () {
      if (mounted) {
        setState(() {
          _showControls = false;
        });
      }
    });
  }

  @override
  void dispose() {
    player.dispose();
    // Restore orientation
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
      DeviceOrientation.landscapeLeft,
      DeviceOrientation.landscapeRight,
    ]);
    // Restore system UI
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    super.dispose();
  }

  void _toggleControls() {
    setState(() {
      _showControls = !_showControls;
    });
    
    if (_showControls) {
      // Auto-hide after 3 seconds
      Future.delayed(const Duration(seconds: 3), () {
        if (mounted && !_isLocked) {
          setState(() {
            _showControls = false;
          });
        }
      });
    }
  }

  void _toggleLock() {
    setState(() {
      _isLocked = !_isLocked;
      if (_isLocked) {
        _showControls = false;
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          // Video Player
          Center(
            child: Video(
              controller: controller,
              controls: NoVideoControls,
            ),
          ),
          
          // Gesture Detector
          GestureDetector(
            onTap: _isLocked ? null : _toggleControls,
            onDoubleTapDown: _isLocked ? null : (details) {
              final width = MediaQuery.of(context).size.width;
              final tapPosition = details.globalPosition.dx;
              
              if (tapPosition < width / 2) {
                // Seek backward 10 seconds
                player.seek(Duration(seconds: player.state.position.inSeconds - 10));
              } else {
                // Seek forward 10 seconds
                player.seek(Duration(seconds: player.state.position.inSeconds + 10));
              }
            },
            onVerticalDragUpdate: _isLocked ? null : (details) {
              final width = MediaQuery.of(context).size.width;
              final dragPosition = details.globalPosition.dx;
              
              if (dragPosition < width / 2) {
                // Adjust brightness
                // TODO: Implement brightness adjustment
              } else {
                // Adjust volume
                final delta = -details.delta.dy / 100;
                final newVolume = (player.state.volume + delta).clamp(0.0, 1.0);
                player.setVolume(newVolume * 100);
              }
            },
            onHorizontalDragUpdate: _isLocked ? null : (details) {
              // Seek
              final delta = details.delta.dx;
              final duration = player.state.duration;
              final position = player.state.position;
              final seekTo = position + Duration(seconds: (delta * 2).round());
              
              if (seekTo >= Duration.zero && seekTo <= duration) {
                player.seek(seekTo);
              }
            },
            child: Container(
              color: Colors.transparent,
            ),
          ),
          
          // Lock Button (Always visible)
          Positioned(
            right: 20,
            top: MediaQuery.of(context).padding.top + 20,
            child: IconButton(
              icon: Icon(
                _isLocked ? Icons.lock : Icons.lock_open,
                color: Colors.white,
              ),
              onPressed: _toggleLock,
            ),
          ),
          
          // Controls Overlay
          if (_showControls && !_isLocked)
            Container(
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.black.withOpacity(0.7),
                    Colors.transparent,
                    Colors.transparent,
                    Colors.black.withOpacity(0.7),
                  ],
                ),
              ),
              child: Column(
                children: [
                  // Top Bar
                  SafeArea(
                    child: Row(
                      children: [
                        IconButton(
                          icon: const Icon(Icons.arrow_back, color: Colors.white),
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                        Expanded(
                          child: Text(
                            widget.filePath.split('/').last,
                            style: const TextStyle(color: Colors.white, fontSize: 16),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        IconButton(
                          icon: const Icon(Icons.more_vert, color: Colors.white),
                          onPressed: () {
                            // Show options menu
                          },
                        ),
                      ],
                    ),
                  ),
                  
                  const Spacer(),
                  
                  // Center Controls
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.replay_10, color: Colors.white, size: 36),
                        onPressed: () {
                          player.seek(Duration(seconds: player.state.position.inSeconds - 10));
                        },
                      ),
                      const SizedBox(width: 40),
                      StreamBuilder<bool>(
                        stream: player.stream.playing,
                        builder: (context, snapshot) {
                          final isPlaying = snapshot.data ?? false;
                          return IconButton(
                            icon: Icon(
                              isPlaying ? Icons.pause : Icons.play_arrow,
                              color: Colors.white,
                              size: 56,
                            ),
                            onPressed: () {
                              player.playOrPause();
                            },
                          );
                        },
                      ),
                      const SizedBox(width: 40),
                      IconButton(
                        icon: const Icon(Icons.forward_10, color: Colors.white, size: 36),
                        onPressed: () {
                          player.seek(Duration(seconds: player.state.position.inSeconds + 10));
                        },
                      ),
                    ],
                  ),
                  
                  const Spacer(),
                  
                  // Bottom Controls
                  Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      children: [
                        // Progress Bar
                        StreamBuilder<Duration>(
                          stream: player.stream.position,
                          builder: (context, snapshot) {
                            final position = snapshot.data ?? Duration.zero;
                            final duration = player.state.duration;
                            
                            return Column(
                              children: [
                                Slider(
                                  value: position.inSeconds.toDouble(),
                                  max: duration.inSeconds.toDouble(),
                                  onChanged: (value) {
                                    player.seek(Duration(seconds: value.toInt()));
                                  },
                                ),
                                Padding(
                                  padding: const EdgeInsets.symmetric(horizontal: 16.0),
                                  child: Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        _formatDuration(position),
                                        style: const TextStyle(color: Colors.white),
                                      ),
                                      Text(
                                        _formatDuration(duration),
                                        style: const TextStyle(color: Colors.white),
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            );
                          },
                        ),
                        
                        // Bottom Buttons
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.list, color: Colors.white),
                              onPressed: () {
                                // Show playlist
                              },
                            ),
                            IconButton(
                              icon: const Icon(Icons.subtitles, color: Colors.white),
                              onPressed: () {
                                // Show subtitle options
                              },
                            ),
                            IconButton(
                              icon: const Icon(Icons.speed, color: Colors.white),
                              onPressed: () {
                                // Show speed options
                              },
                            ),
                            IconButton(
                              icon: const Icon(Icons.aspect_ratio, color: Colors.white),
                              onPressed: () {
                                // Change aspect ratio
                              },
                            ),
                            IconButton(
                              icon: const Icon(Icons.picture_in_picture, color: Colors.white),
                              onPressed: () {
                                // Enter PiP mode
                              },
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
  
  String _formatDuration(Duration duration) {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);
    
    if (hours > 0) {
      return '$hours:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    } else {
      return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
  }
}