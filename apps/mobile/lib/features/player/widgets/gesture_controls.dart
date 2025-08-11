import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:math' as math;

class GestureControls extends StatefulWidget {
  final Widget child;
  final VoidCallback? onTap;
  final Function(double)? onVolumeChange;
  final Function(double)? onBrightnessChange;
  final Function(Duration)? onSeek;
  final Function(int)? onDoubleTapSeek;
  final VoidCallback? onLongPress;
  final Function(double)? onScaleUpdate;
  final bool isLocked;
  
  const GestureControls({
    super.key,
    required this.child,
    this.onTap,
    this.onVolumeChange,
    this.onBrightnessChange,
    this.onSeek,
    this.onDoubleTapSeek,
    this.onLongPress,
    this.onScaleUpdate,
    this.isLocked = false,
  });

  @override
  State<GestureControls> createState() => _GestureControlsState();
}

class _GestureControlsState extends State<GestureControls> 
    with TickerProviderStateMixin {
  // Gesture detection variables
  bool _isDragging = false;
  Offset? _startDragPosition;
  double? _startValue;
  GestureType? _currentGesture;
  
  // Visual feedback
  late AnimationController _feedbackController;
  late Animation<double> _feedbackAnimation;
  String _feedbackText = '';
  IconData? _feedbackIcon;
  
  // Double tap
  DateTime? _lastTapTime;
  Offset? _lastTapPosition;
  late AnimationController _doubleTapController;
  late Animation<double> _doubleTapAnimation;
  bool _showDoubleTapFeedback = false;
  int _doubleTapDirection = 0; // -1 for left, 1 for right
  
  // Scale gesture
  double _baseScale = 1.0;
  double _currentScale = 1.0;
  
  @override
  void initState() {
    super.initState();
    _feedbackController = AnimationController(
      duration: const Duration(milliseconds: 200),
      vsync: this,
    );
    _feedbackAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _feedbackController,
      curve: Curves.easeOut,
    ));
    
    _doubleTapController = AnimationController(
      duration: const Duration(milliseconds: 400),
      vsync: this,
    );
    _doubleTapAnimation = Tween<double>(
      begin: 0.0,
      end: 1.0,
    ).animate(CurvedAnimation(
      parent: _doubleTapController,
      curve: Curves.easeOut,
    ));
  }
  
  @override
  void dispose() {
    _feedbackController.dispose();
    _doubleTapController.dispose();
    super.dispose();
  }
  
  void _handleTapDown(TapDownDetails details) {
    if (widget.isLocked) return;
    
    final now = DateTime.now();
    final position = details.globalPosition;
    
    // Check for double tap
    if (_lastTapTime != null && _lastTapPosition != null) {
      final timeDiff = now.difference(_lastTapTime!);
      final spaceDiff = (position - _lastTapPosition!).distance;
      
      if (timeDiff.inMilliseconds < 300 && spaceDiff < 50) {
        _handleDoubleTap(position);
        _lastTapTime = null;
        _lastTapPosition = null;
        return;
      }
    }
    
    _lastTapTime = now;
    _lastTapPosition = position;
  }
  
  void _handleDoubleTap(Offset position) {
    final width = MediaQuery.of(context).size.width;
    final isLeftSide = position.dx < width / 2;
    
    setState(() {
      _showDoubleTapFeedback = true;
      _doubleTapDirection = isLeftSide ? -1 : 1;
    });
    
    _doubleTapController.forward().then((_) {
      setState(() {
        _showDoubleTapFeedback = false;
      });
      _doubleTapController.reset();
    });
    
    widget.onDoubleTapSeek?.call(isLeftSide ? -10 : 10);
  }
  
  void _handleVerticalDragStart(DragStartDetails details) {
    if (widget.isLocked) return;
    
    final width = MediaQuery.of(context).size.width;
    final isLeftSide = details.globalPosition.dx < width / 2;
    
    setState(() {
      _isDragging = true;
      _startDragPosition = details.globalPosition;
      _currentGesture = isLeftSide ? GestureType.brightness : GestureType.volume;
      
      if (_currentGesture == GestureType.brightness) {
        _startValue = 0.5; // TODO: Get actual brightness
        _feedbackIcon = Icons.brightness_6;
      } else {
        _startValue = 0.5; // TODO: Get actual volume
        _feedbackIcon = Icons.volume_up;
      }
    });
    
    _feedbackController.forward();
  }
  
  void _handleVerticalDragUpdate(DragUpdateDetails details) {
    if (!_isDragging || _startDragPosition == null || _startValue == null) return;
    
    final dragDistance = _startDragPosition!.dy - details.globalPosition.dy;
    final screenHeight = MediaQuery.of(context).size.height;
    final delta = dragDistance / screenHeight;
    final newValue = (_startValue! + delta).clamp(0.0, 1.0);
    
    setState(() {
      if (_currentGesture == GestureType.brightness) {
        _feedbackText = 'Brightness: ${(newValue * 100).toInt()}%';
        widget.onBrightnessChange?.call(newValue);
      } else if (_currentGesture == GestureType.volume) {
        _feedbackText = 'Volume: ${(newValue * 100).toInt()}%';
        widget.onVolumeChange?.call(newValue);
        
        // Update icon based on volume level
        if (newValue == 0) {
          _feedbackIcon = Icons.volume_off;
        } else if (newValue < 0.5) {
          _feedbackIcon = Icons.volume_down;
        } else {
          _feedbackIcon = Icons.volume_up;
        }
      }
    });
  }
  
  void _handleHorizontalDragStart(DragStartDetails details) {
    if (widget.isLocked) return;
    
    setState(() {
      _isDragging = true;
      _startDragPosition = details.globalPosition;
      _currentGesture = GestureType.seek;
      _feedbackIcon = Icons.fast_forward;
    });
    
    _feedbackController.forward();
  }
  
  void _handleHorizontalDragUpdate(DragUpdateDetails details) {
    if (!_isDragging || _startDragPosition == null) return;
    
    final dragDistance = details.globalPosition.dx - _startDragPosition!.dx;
    final screenWidth = MediaQuery.of(context).size.width;
    final seekSeconds = (dragDistance / screenWidth * 60).round(); // 60 seconds max seek
    
    setState(() {
      if (seekSeconds > 0) {
        _feedbackIcon = Icons.fast_forward;
        _feedbackText = '+${seekSeconds}s';
      } else {
        _feedbackIcon = Icons.fast_rewind;
        _feedbackText = '${seekSeconds}s';
      }
    });
    
    widget.onSeek?.call(Duration(seconds: seekSeconds));
  }
  
  void _handleDragEnd(DragEndDetails details) {
    setState(() {
      _isDragging = false;
      _startDragPosition = null;
      _startValue = null;
      _currentGesture = null;
    });
    
    _feedbackController.reverse();
  }
  
  void _handleScaleStart(ScaleStartDetails details) {
    if (widget.isLocked) return;
    _baseScale = _currentScale;
  }
  
  void _handleScaleUpdate(ScaleUpdateDetails details) {
    if (widget.isLocked) return;
    
    setState(() {
      _currentScale = (_baseScale * details.scale).clamp(0.5, 3.0);
    });
    
    widget.onScaleUpdate?.call(_currentScale);
  }
  
  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        Transform.scale(
          scale: _currentScale,
          child: GestureDetector(
            onTapDown: _handleTapDown,
            onTap: widget.onTap,
            onLongPress: widget.isLocked ? null : widget.onLongPress,
            onVerticalDragStart: _handleVerticalDragStart,
            onVerticalDragUpdate: _handleVerticalDragUpdate,
            onVerticalDragEnd: _handleDragEnd,
            onHorizontalDragStart: _handleHorizontalDragStart,
            onHorizontalDragUpdate: _handleHorizontalDragUpdate,
            onHorizontalDragEnd: _handleDragEnd,
            onScaleStart: _handleScaleStart,
            onScaleUpdate: _handleScaleUpdate,
            child: widget.child,
          ),
        ),
        
        // Visual feedback overlay
        if (_isDragging && _currentGesture != null)
          Positioned.fill(
            child: IgnorePointer(
              child: AnimatedBuilder(
                animation: _feedbackAnimation,
                builder: (context, child) {
                  return Opacity(
                    opacity: _feedbackAnimation.value,
                    child: Center(
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 24,
                          vertical: 16,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withOpacity(0.7),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (_feedbackIcon != null)
                              Icon(
                                _feedbackIcon,
                                color: Colors.white,
                                size: 32,
                              ),
                            if (_feedbackText.isNotEmpty) ...[
                              const SizedBox(width: 12),
                              Text(
                                _feedbackText,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 18,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
        
        // Double tap feedback
        if (_showDoubleTapFeedback)
          Positioned(
            left: _doubleTapDirection < 0 ? 50 : null,
            right: _doubleTapDirection > 0 ? 50 : null,
            top: 0,
            bottom: 0,
            child: IgnorePointer(
              child: AnimatedBuilder(
                animation: _doubleTapAnimation,
                builder: (context, child) {
                  return Opacity(
                    opacity: math.sin(_doubleTapAnimation.value * math.pi),
                    child: Container(
                      width: 100,
                      height: 100,
                      alignment: Alignment.center,
                      child: Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: Colors.white.withOpacity(0.3),
                        ),
                        child: Icon(
                          _doubleTapDirection < 0
                              ? Icons.replay_10
                              : Icons.forward_10,
                          color: Colors.white,
                          size: 40,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
          ),
      ],
    );
  }
}

enum GestureType {
  volume,
  brightness,
  seek,
}