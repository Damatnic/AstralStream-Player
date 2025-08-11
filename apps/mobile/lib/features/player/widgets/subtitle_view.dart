import 'package:flutter/material.dart';
import 'dart:io';
import 'dart:async';

class SubtitleView extends StatefulWidget {
  final String? subtitlePath;
  final Duration position;
  final SubtitleStyle style;
  final VoidCallback? onStyleTap;
  
  const SubtitleView({
    super.key,
    this.subtitlePath,
    required this.position,
    required this.style,
    this.onStyleTap,
  });

  @override
  State<SubtitleView> createState() => _SubtitleViewState();
}

class _SubtitleViewState extends State<SubtitleView> {
  List<SubtitleEntry> _subtitles = [];
  SubtitleEntry? _currentSubtitle;
  
  @override
  void initState() {
    super.initState();
    if (widget.subtitlePath != null) {
      _loadSubtitles();
    }
  }
  
  @override
  void didUpdateWidget(SubtitleView oldWidget) {
    super.didUpdateWidget(oldWidget);
    
    if (widget.subtitlePath != oldWidget.subtitlePath) {
      _loadSubtitles();
    }
    
    if (widget.position != oldWidget.position) {
      _updateCurrentSubtitle();
    }
  }
  
  Future<void> _loadSubtitles() async {
    if (widget.subtitlePath == null) return;
    
    try {
      final file = File(widget.subtitlePath!);
      if (!await file.exists()) return;
      
      final content = await file.readAsString();
      final extension = widget.subtitlePath!.split('.').last.toLowerCase();
      
      switch (extension) {
        case 'srt':
          _subtitles = _parseSRT(content);
          break;
        case 'ass':
        case 'ssa':
          _subtitles = _parseASS(content);
          break;
        case 'vtt':
          _subtitles = _parseVTT(content);
          break;
        default:
          _subtitles = [];
      }
      
      _updateCurrentSubtitle();
    } catch (e) {
      debugPrint('Error loading subtitles: $e');
    }
  }
  
  List<SubtitleEntry> _parseSRT(String content) {
    final subtitles = <SubtitleEntry>[];
    final blocks = content.split('\n\n');
    
    for (final block in blocks) {
      final lines = block.trim().split('\n');
      if (lines.length < 3) continue;
      
      // Parse time
      final timeLine = lines[1];
      final times = timeLine.split(' --> ');
      if (times.length != 2) continue;
      
      final start = _parseTime(times[0].trim());
      final end = _parseTime(times[1].trim());
      
      // Parse text
      final text = lines.sublist(2).join('\n');
      
      subtitles.add(SubtitleEntry(
        start: start,
        end: end,
        text: text,
      ));
    }
    
    return subtitles;
  }
  
  List<SubtitleEntry> _parseASS(String content) {
    final subtitles = <SubtitleEntry>[];
    final lines = content.split('\n');
    
    bool inEvents = false;
    for (final line in lines) {
      if (line.startsWith('[Events]')) {
        inEvents = true;
        continue;
      }
      
      if (!inEvents) continue;
      
      if (line.startsWith('Dialogue:')) {
        final parts = line.substring(9).split(',');
        if (parts.length < 10) continue;
        
        final start = _parseASSTime(parts[1]);
        final end = _parseASSTime(parts[2]);
        final text = parts.sublist(9).join(',')
            .replaceAll(r'\N', '\n')
            .replaceAll(RegExp(r'\{[^}]*\}'), ''); // Remove style tags
        
        subtitles.add(SubtitleEntry(
          start: start,
          end: end,
          text: text,
        ));
      }
    }
    
    return subtitles;
  }
  
  List<SubtitleEntry> _parseVTT(String content) {
    final subtitles = <SubtitleEntry>[];
    final blocks = content.split('\n\n');
    
    for (final block in blocks) {
      if (block.startsWith('WEBVTT')) continue;
      
      final lines = block.trim().split('\n');
      if (lines.isEmpty) continue;
      
      // Find time line
      String? timeLine;
      int textStart = 0;
      
      for (int i = 0; i < lines.length; i++) {
        if (lines[i].contains('-->')) {
          timeLine = lines[i];
          textStart = i + 1;
          break;
        }
      }
      
      if (timeLine == null) continue;
      
      final times = timeLine.split(' --> ');
      if (times.length != 2) continue;
      
      final start = _parseTime(times[0].trim());
      final end = _parseTime(times[1].trim());
      
      final text = lines.sublist(textStart).join('\n')
          .replaceAll(RegExp(r'<[^>]*>'), ''); // Remove HTML tags
      
      subtitles.add(SubtitleEntry(
        start: start,
        end: end,
        text: text,
      ));
    }
    
    return subtitles;
  }
  
  Duration _parseTime(String time) {
    final parts = time.split(':');
    if (parts.length != 3) return Duration.zero;
    
    final hours = int.tryParse(parts[0]) ?? 0;
    final minutes = int.tryParse(parts[1]) ?? 0;
    
    final secondsParts = parts[2].split(',');
    final seconds = int.tryParse(secondsParts[0]) ?? 0;
    final milliseconds = secondsParts.length > 1
        ? int.tryParse(secondsParts[1]) ?? 0
        : 0;
    
    return Duration(
      hours: hours,
      minutes: minutes,
      seconds: seconds,
      milliseconds: milliseconds,
    );
  }
  
  Duration _parseASSTime(String time) {
    final parts = time.split(':');
    if (parts.length != 3) return Duration.zero;
    
    final hours = int.tryParse(parts[0]) ?? 0;
    final minutes = int.tryParse(parts[1]) ?? 0;
    
    final secondsParts = parts[2].split('.');
    final seconds = int.tryParse(secondsParts[0]) ?? 0;
    final centiseconds = secondsParts.length > 1
        ? int.tryParse(secondsParts[1]) ?? 0
        : 0;
    
    return Duration(
      hours: hours,
      minutes: minutes,
      seconds: seconds,
      milliseconds: centiseconds * 10,
    );
  }
  
  void _updateCurrentSubtitle() {
    SubtitleEntry? current;
    
    for (final subtitle in _subtitles) {
      if (widget.position >= subtitle.start && widget.position <= subtitle.end) {
        current = subtitle;
        break;
      }
    }
    
    if (current != _currentSubtitle) {
      setState(() {
        _currentSubtitle = current;
      });
    }
  }
  
  @override
  Widget build(BuildContext context) {
    if (_currentSubtitle == null || _currentSubtitle!.text.isEmpty) {
      return const SizedBox.shrink();
    }
    
    return Positioned(
      bottom: widget.style.bottomOffset,
      left: 16,
      right: 16,
      child: GestureDetector(
        onTap: widget.onStyleTap,
        child: Container(
          alignment: Alignment.center,
          child: Container(
            padding: EdgeInsets.symmetric(
              horizontal: widget.style.horizontalPadding,
              vertical: widget.style.verticalPadding,
            ),
            decoration: BoxDecoration(
              color: widget.style.backgroundColor,
              borderRadius: BorderRadius.circular(widget.style.borderRadius),
              border: widget.style.borderWidth > 0
                ? Border.all(
                    color: widget.style.borderColor,
                    width: widget.style.borderWidth,
                  )
                : null,
            ),
            child: Text(
              _currentSubtitle!.text,
              textAlign: widget.style.textAlign,
              style: TextStyle(
                color: widget.style.textColor,
                fontSize: widget.style.fontSize,
                fontWeight: widget.style.fontWeight,
                fontFamily: widget.style.fontFamily,
                shadows: widget.style.shadows,
                height: widget.style.lineHeight,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class SubtitleEntry {
  final Duration start;
  final Duration end;
  final String text;
  
  SubtitleEntry({
    required this.start,
    required this.end,
    required this.text,
  });
}

class SubtitleStyle {
  final Color textColor;
  final double fontSize;
  final FontWeight fontWeight;
  final String? fontFamily;
  final Color backgroundColor;
  final double borderRadius;
  final Color borderColor;
  final double borderWidth;
  final double horizontalPadding;
  final double verticalPadding;
  final TextAlign textAlign;
  final List<Shadow> shadows;
  final double lineHeight;
  final double bottomOffset;
  
  const SubtitleStyle({
    this.textColor = Colors.white,
    this.fontSize = 20,
    this.fontWeight = FontWeight.normal,
    this.fontFamily,
    this.backgroundColor = const Color(0xAA000000),
    this.borderRadius = 4,
    this.borderColor = Colors.black,
    this.borderWidth = 0,
    this.horizontalPadding = 12,
    this.verticalPadding = 8,
    this.textAlign = TextAlign.center,
    this.shadows = const [
      Shadow(
        offset: Offset(1, 1),
        blurRadius: 3,
        color: Colors.black,
      ),
    ],
    this.lineHeight = 1.4,
    this.bottomOffset = 50,
  });
  
  // Preset styles
  static const SubtitleStyle defaultStyle = SubtitleStyle();
  
  static const SubtitleStyle minimal = SubtitleStyle(
    backgroundColor: Colors.transparent,
    shadows: [
      Shadow(
        offset: Offset(2, 2),
        blurRadius: 4,
        color: Colors.black,
      ),
    ],
  );
  
  static const SubtitleStyle bold = SubtitleStyle(
    fontSize: 24,
    fontWeight: FontWeight.bold,
    backgroundColor: Colors.black87,
  );
  
  static const SubtitleStyle outlined = SubtitleStyle(
    backgroundColor: Colors.transparent,
    borderColor: Colors.black,
    borderWidth: 2,
    shadows: [],
  );
  
  static const SubtitleStyle yellowOnBlack = SubtitleStyle(
    textColor: Colors.yellow,
    backgroundColor: Colors.black87,
    fontSize: 22,
  );
  
  SubtitleStyle copyWith({
    Color? textColor,
    double? fontSize,
    FontWeight? fontWeight,
    String? fontFamily,
    Color? backgroundColor,
    double? borderRadius,
    Color? borderColor,
    double? borderWidth,
    double? horizontalPadding,
    double? verticalPadding,
    TextAlign? textAlign,
    List<Shadow>? shadows,
    double? lineHeight,
    double? bottomOffset,
  }) {
    return SubtitleStyle(
      textColor: textColor ?? this.textColor,
      fontSize: fontSize ?? this.fontSize,
      fontWeight: fontWeight ?? this.fontWeight,
      fontFamily: fontFamily ?? this.fontFamily,
      backgroundColor: backgroundColor ?? this.backgroundColor,
      borderRadius: borderRadius ?? this.borderRadius,
      borderColor: borderColor ?? this.borderColor,
      borderWidth: borderWidth ?? this.borderWidth,
      horizontalPadding: horizontalPadding ?? this.horizontalPadding,
      verticalPadding: verticalPadding ?? this.verticalPadding,
      textAlign: textAlign ?? this.textAlign,
      shadows: shadows ?? this.shadows,
      lineHeight: lineHeight ?? this.lineHeight,
      bottomOffset: bottomOffset ?? this.bottomOffset,
    );
  }
}

// Subtitle settings widget
class SubtitleSettingsWidget extends StatefulWidget {
  final SubtitleStyle currentStyle;
  final Function(SubtitleStyle) onStyleChanged;
  
  const SubtitleSettingsWidget({
    super.key,
    required this.currentStyle,
    required this.onStyleChanged,
  });

  @override
  State<SubtitleSettingsWidget> createState() => _SubtitleSettingsWidgetState();
}

class _SubtitleSettingsWidgetState extends State<SubtitleSettingsWidget> {
  late SubtitleStyle _style;
  
  @override
  void initState() {
    super.initState();
    _style = widget.currentStyle;
  }
  
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Subtitle Settings',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 20),
          
          // Preset styles
          Text(
            'Style Presets',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              ChoiceChip(
                label: const Text('Default'),
                selected: false,
                onSelected: (_) {
                  setState(() {
                    _style = SubtitleStyle.defaultStyle;
                  });
                  widget.onStyleChanged(_style);
                },
              ),
              ChoiceChip(
                label: const Text('Minimal'),
                selected: false,
                onSelected: (_) {
                  setState(() {
                    _style = SubtitleStyle.minimal;
                  });
                  widget.onStyleChanged(_style);
                },
              ),
              ChoiceChip(
                label: const Text('Bold'),
                selected: false,
                onSelected: (_) {
                  setState(() {
                    _style = SubtitleStyle.bold;
                  });
                  widget.onStyleChanged(_style);
                },
              ),
              ChoiceChip(
                label: const Text('Outlined'),
                selected: false,
                onSelected: (_) {
                  setState(() {
                    _style = SubtitleStyle.outlined;
                  });
                  widget.onStyleChanged(_style);
                },
              ),
            ],
          ),
          
          const SizedBox(height: 20),
          
          // Font size
          Text(
            'Font Size: ${_style.fontSize.round()}',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Slider(
            value: _style.fontSize,
            min: 12,
            max: 40,
            onChanged: (value) {
              setState(() {
                _style = _style.copyWith(fontSize: value);
              });
              widget.onStyleChanged(_style);
            },
          ),
          
          // Text color
          ListTile(
            title: const Text('Text Color'),
            trailing: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: _style.textColor,
                border: Border.all(color: Colors.grey),
                borderRadius: BorderRadius.circular(4),
              ),
            ),
            onTap: () => _showColorPicker(
              'Text Color',
              _style.textColor,
              (color) {
                setState(() {
                  _style = _style.copyWith(textColor: color);
                });
                widget.onStyleChanged(_style);
              },
            ),
          ),
          
          // Background color
          ListTile(
            title: const Text('Background Color'),
            trailing: Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: _style.backgroundColor,
                border: Border.all(color: Colors.grey),
                borderRadius: BorderRadius.circular(4),
              ),
            ),
            onTap: () => _showColorPicker(
              'Background Color',
              _style.backgroundColor,
              (color) {
                setState(() {
                  _style = _style.copyWith(backgroundColor: color);
                });
                widget.onStyleChanged(_style);
              },
            ),
          ),
          
          // Bottom offset
          Text(
            'Position: ${_style.bottomOffset.round()}px from bottom',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          Slider(
            value: _style.bottomOffset,
            min: 20,
            max: 200,
            onChanged: (value) {
              setState(() {
                _style = _style.copyWith(bottomOffset: value);
              });
              widget.onStyleChanged(_style);
            },
          ),
        ],
      ),
    );
  }
  
  void _showColorPicker(String title, Color currentColor, Function(Color) onColorChanged) {
    // Show color picker dialog
  }
}