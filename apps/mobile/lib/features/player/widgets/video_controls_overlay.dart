import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';

class VideoControlsOverlay extends StatefulWidget {
  final bool isPlaying;
  final Duration position;
  final Duration duration;
  final Duration buffered;
  final VoidCallback? onPlayPause;
  final Function(Duration)? onSeek;
  final Function(double)? onSpeedChange;
  final VoidCallback? onFullScreen;
  final VoidCallback? onSettings;
  final VoidCallback? onSubtitles;
  final VoidCallback? onAudioTrack;
  final VoidCallback? onScreenshot;
  final VoidCallback? onCast;
  final VoidCallback? onRotate;
  final VoidCallback? onAspectRatio;
  final VoidCallback? onSleepTimer;
  final VoidCallback? onRepeatMode;
  final VoidCallback? onPlaylist;
  final VoidCallback? onBack;
  final VoidCallback? onNext;
  final VoidCallback? onPrevious;
  final String? title;
  final bool showControls;
  final bool isLocked;
  
  const VideoControlsOverlay({
    super.key,
    required this.isPlaying,
    required this.position,
    required this.duration,
    required this.buffered,
    this.onPlayPause,
    this.onSeek,
    this.onSpeedChange,
    this.onFullScreen,
    this.onSettings,
    this.onSubtitles,
    this.onAudioTrack,
    this.onScreenshot,
    this.onCast,
    this.onRotate,
    this.onAspectRatio,
    this.onSleepTimer,
    this.onRepeatMode,
    this.onPlaylist,
    this.onBack,
    this.onNext,
    this.onPrevious,
    this.title,
    this.showControls = true,
    this.isLocked = false,
  });

  @override
  State<VideoControlsOverlay> createState() => _VideoControlsOverlayState();
}

class _VideoControlsOverlayState extends State<VideoControlsOverlay> 
    with TickerProviderStateMixin {
  late AnimationController _controlsAnimationController;
  late Animation<double> _controlsAnimation;
  
  Timer? _hideTimer;
  bool _dragging = false;
  double _dragValue = 0.0;
  
  // Speed selection
  final List<double> _speeds = [0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 3.0, 4.0];
  double _currentSpeed = 1.0;
  
  // Repeat modes
  RepeatMode _repeatMode = RepeatMode.none;
  
  @override
  void initState() {
    super.initState();
    _controlsAnimationController = AnimationController(
      duration: const Duration(milliseconds: 200),
      vsync: this,
    );
    _controlsAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _controlsAnimationController,
      curve: Curves.easeInOut,
    ));
    
    if (widget.showControls) {
      _controlsAnimationController.forward();
      _startHideTimer();
    }
  }
  
  @override
  void didUpdateWidget(VideoControlsOverlay oldWidget) {
    super.didUpdateWidget(oldWidget);
    
    if (widget.showControls != oldWidget.showControls) {
      if (widget.showControls) {
        _controlsAnimationController.forward();
        _startHideTimer();
      } else {
        _controlsAnimationController.reverse();
      }
    }
  }
  
  @override
  void dispose() {
    _hideTimer?.cancel();
    _controlsAnimationController.dispose();
    super.dispose();
  }
  
  void _startHideTimer() {
    _hideTimer?.cancel();
    if (widget.isPlaying && !widget.isLocked) {
      _hideTimer = Timer(const Duration(seconds: 3), () {
        if (mounted && !_dragging) {
          _controlsAnimationController.reverse();
        }
      });
    }
  }
  
  void _cancelHideTimer() {
    _hideTimer?.cancel();
  }
  
  String _formatDuration(Duration duration) {
    final hours = duration.inHours;
    final minutes = duration.inMinutes.remainder(60);
    final seconds = duration.inSeconds.remainder(60);
    
    if (hours > 0) {
      return '${hours.toString().padLeft(2, '0')}:${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
    }
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }
  
  @override
  Widget build(BuildContext context) {
    if (widget.isLocked) {
      return const SizedBox.shrink();
    }
    
    return AnimatedBuilder(
      animation: _controlsAnimation,
      builder: (context, child) {
        return Opacity(
          opacity: _controlsAnimation.value,
          child: Container(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Colors.black.withOpacity(0.7 * _controlsAnimation.value),
                  Colors.transparent,
                  Colors.transparent,
                  Colors.black.withOpacity(0.7 * _controlsAnimation.value),
                ],
                stops: const [0.0, 0.3, 0.7, 1.0],
              ),
            ),
            child: Stack(
              children: [
                // Top bar
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  child: SafeArea(
                    child: Row(
                      children: [
                        IconButton(
                          icon: const Icon(Icons.arrow_back, color: Colors.white),
                          onPressed: widget.onBack,
                        ),
                        Expanded(
                          child: Text(
                            widget.title ?? '',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 16,
                              fontWeight: FontWeight.w500,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        // Top right buttons
                        IconButton(
                          icon: const Icon(Icons.cast, color: Colors.white),
                          onPressed: widget.onCast,
                        ),
                        IconButton(
                          icon: const Icon(Icons.timer, color: Colors.white),
                          onPressed: widget.onSleepTimer,
                        ),
                        IconButton(
                          icon: const Icon(Icons.more_vert, color: Colors.white),
                          onPressed: () => _showMoreOptions(context),
                        ),
                      ],
                    ),
                  ),
                ),
                
                // Center controls
                Positioned.fill(
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      IconButton(
                        icon: const Icon(Icons.skip_previous, color: Colors.white),
                        iconSize: 36,
                        onPressed: widget.onPrevious,
                      ),
                      const SizedBox(width: 20),
                      IconButton(
                        icon: const Icon(Icons.replay_10, color: Colors.white),
                        iconSize: 42,
                        onPressed: () {
                          final newPosition = widget.position - const Duration(seconds: 10);
                          widget.onSeek?.call(newPosition);
                        },
                      ),
                      const SizedBox(width: 20),
                      Container(
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          shape: BoxShape.circle,
                        ),
                        child: IconButton(
                          icon: Icon(
                            widget.isPlaying ? Icons.pause : Icons.play_arrow,
                            color: Colors.white,
                          ),
                          iconSize: 56,
                          onPressed: widget.onPlayPause,
                        ),
                      ),
                      const SizedBox(width: 20),
                      IconButton(
                        icon: const Icon(Icons.forward_10, color: Colors.white),
                        iconSize: 42,
                        onPressed: () {
                          final newPosition = widget.position + const Duration(seconds: 10);
                          widget.onSeek?.call(newPosition);
                        },
                      ),
                      const SizedBox(width: 20),
                      IconButton(
                        icon: const Icon(Icons.skip_next, color: Colors.white),
                        iconSize: 36,
                        onPressed: widget.onNext,
                      ),
                    ],
                  ),
                ),
                
                // Bottom controls
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: SafeArea(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        // Progress bar
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 16),
                          child: Row(
                            children: [
                              Text(
                                _formatDuration(_dragging 
                                  ? Duration(seconds: _dragValue.toInt())
                                  : widget.position),
                                style: const TextStyle(color: Colors.white, fontSize: 12),
                              ),
                              Expanded(
                                child: SliderTheme(
                                  data: SliderThemeData(
                                    trackHeight: 3,
                                    thumbShape: const RoundSliderThumbShape(
                                      enabledThumbRadius: 6,
                                    ),
                                    overlayShape: const RoundSliderOverlayShape(
                                      overlayRadius: 12,
                                    ),
                                    activeTrackColor: Theme.of(context).primaryColor,
                                    inactiveTrackColor: Colors.white.withOpacity(0.3),
                                    thumbColor: Theme.of(context).primaryColor,
                                    overlayColor: Theme.of(context).primaryColor.withOpacity(0.3),
                                  ),
                                  child: Slider(
                                    value: _dragging 
                                      ? _dragValue 
                                      : widget.position.inSeconds.toDouble(),
                                    min: 0,
                                    max: widget.duration.inSeconds.toDouble(),
                                    onChanged: (value) {
                                      setState(() {
                                        _dragging = true;
                                        _dragValue = value;
                                      });
                                      _cancelHideTimer();
                                    },
                                    onChangeEnd: (value) {
                                      setState(() {
                                        _dragging = false;
                                      });
                                      widget.onSeek?.call(Duration(seconds: value.toInt()));
                                      _startHideTimer();
                                    },
                                  ),
                                ),
                              ),
                              Text(
                                _formatDuration(widget.duration),
                                style: const TextStyle(color: Colors.white, fontSize: 12),
                              ),
                            ],
                          ),
                        ),
                        
                        // Bottom button row
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                            children: [
                              // Playlist button
                              IconButton(
                                icon: const Icon(Icons.queue_music, color: Colors.white),
                                onPressed: widget.onPlaylist,
                              ),
                              
                              // Subtitle button
                              IconButton(
                                icon: const Icon(Icons.closed_caption, color: Colors.white),
                                onPressed: widget.onSubtitles,
                              ),
                              
                              // Audio track button
                              IconButton(
                                icon: const Icon(Icons.audiotrack, color: Colors.white),
                                onPressed: widget.onAudioTrack,
                              ),
                              
                              // Speed button
                              TextButton(
                                onPressed: () => _showSpeedDialog(context),
                                child: Text(
                                  '${_currentSpeed}x',
                                  style: const TextStyle(color: Colors.white),
                                ),
                              ),
                              
                              // Repeat mode button
                              IconButton(
                                icon: Icon(
                                  _repeatMode == RepeatMode.none
                                    ? Icons.repeat
                                    : _repeatMode == RepeatMode.one
                                      ? Icons.repeat_one
                                      : Icons.repeat,
                                  color: _repeatMode == RepeatMode.none 
                                    ? Colors.white 
                                    : Theme.of(context).primaryColor,
                                ),
                                onPressed: () {
                                  setState(() {
                                    _repeatMode = RepeatMode.values[
                                      (_repeatMode.index + 1) % RepeatMode.values.length
                                    ];
                                  });
                                  widget.onRepeatMode?.call();
                                },
                              ),
                              
                              // Aspect ratio button
                              IconButton(
                                icon: const Icon(Icons.aspect_ratio, color: Colors.white),
                                onPressed: widget.onAspectRatio,
                              ),
                              
                              // Settings button
                              IconButton(
                                icon: const Icon(Icons.settings, color: Colors.white),
                                onPressed: widget.onSettings,
                              ),
                              
                              // Rotate button
                              IconButton(
                                icon: const Icon(Icons.screen_rotation, color: Colors.white),
                                onPressed: widget.onRotate,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                
                // Buffering indicator
                if (widget.buffered.inSeconds > 0 && 
                    widget.buffered < widget.duration)
                  const Center(
                    child: CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }
  
  void _showSpeedDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Playback Speed'),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: _speeds.map((speed) {
              return RadioListTile<double>(
                title: Text('${speed}x'),
                value: speed,
                groupValue: _currentSpeed,
                onChanged: (value) {
                  if (value != null) {
                    setState(() {
                      _currentSpeed = value;
                    });
                    widget.onSpeedChange?.call(value);
                    Navigator.of(context).pop();
                  }
                },
              );
            }).toList(),
          ),
        ),
      ),
    );
  }
  
  void _showMoreOptions(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => Container(
        decoration: BoxDecoration(
          color: Theme.of(context).scaffoldBackgroundColor,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
        ),
        child: SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.camera_alt),
                title: const Text('Screenshot'),
                onTap: () {
                  Navigator.pop(context);
                  widget.onScreenshot?.call();
                },
              ),
              ListTile(
                leading: const Icon(Icons.equalizer),
                title: const Text('Equalizer'),
                onTap: () {
                  Navigator.pop(context);
                  // Show equalizer
                },
              ),
              ListTile(
                leading: const Icon(Icons.picture_in_picture),
                title: const Text('Picture in Picture'),
                onTap: () {
                  Navigator.pop(context);
                  // Enter PiP mode
                },
              ),
              ListTile(
                leading: const Icon(Icons.info),
                title: const Text('Video Info'),
                onTap: () {
                  Navigator.pop(context);
                  // Show video info
                },
              ),
              ListTile(
                leading: const Icon(Icons.share),
                title: const Text('Share'),
                onTap: () {
                  Navigator.pop(context);
                  // Share video
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

enum RepeatMode {
  none,
  one,
  all,
}