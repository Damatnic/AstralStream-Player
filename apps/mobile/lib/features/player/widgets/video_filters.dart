import 'package:flutter/material.dart';
import 'dart:ui' as ui;

class VideoFilters extends StatefulWidget {
  final Function(VideoFilterSettings)? onFilterChanged;
  final VoidCallback? onClose;
  
  const VideoFilters({
    super.key,
    this.onFilterChanged,
    this.onClose,
  });

  @override
  State<VideoFilters> createState() => _VideoFiltersState();
}

class _VideoFiltersState extends State<VideoFilters> with TickerProviderStateMixin {
  late TabController _tabController;
  late VideoFilterSettings _settings;
  
  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _settings = VideoFilterSettings();
  }
  
  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }
  
  void _updateSettings(VideoFilterSettings Function(VideoFilterSettings) updater) {
    setState(() {
      _settings = updater(_settings);
    });
    widget.onFilterChanged?.call(_settings);
  }
  
  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.6,
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        children: [
          // Handle bar
          Container(
            width: 40,
            height: 4,
            margin: const EdgeInsets.symmetric(vertical: 12),
            decoration: BoxDecoration(
              color: Colors.grey[600],
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          
          // Header
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Video Adjustments',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                Row(
                  children: [
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _settings = VideoFilterSettings();
                        });
                        widget.onFilterChanged?.call(_settings);
                      },
                      child: const Text('Reset'),
                    ),
                    IconButton(
                      icon: const Icon(Icons.close),
                      onPressed: widget.onClose,
                    ),
                  ],
                ),
              ],
            ),
          ),
          
          // Tabs
          TabBar(
            controller: _tabController,
            tabs: const [
              Tab(text: 'Basic'),
              Tab(text: 'Color'),
              Tab(text: 'Effects'),
            ],
          ),
          
          // Tab content
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                // Basic adjustments
                _buildBasicTab(),
                
                // Color adjustments
                _buildColorTab(),
                
                // Effects
                _buildEffectsTab(),
              ],
            ),
          ),
        ],
      ),
    );
  }
  
  Widget _buildBasicTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          _buildSlider(
            'Brightness',
            Icons.brightness_6,
            _settings.brightness,
            -1.0,
            1.0,
            (value) => _updateSettings((s) => s.copyWith(brightness: value)),
          ),
          _buildSlider(
            'Contrast',
            Icons.contrast,
            _settings.contrast,
            0.5,
            2.0,
            (value) => _updateSettings((s) => s.copyWith(contrast: value)),
          ),
          _buildSlider(
            'Saturation',
            Icons.palette,
            _settings.saturation,
            0.0,
            2.0,
            (value) => _updateSettings((s) => s.copyWith(saturation: value)),
          ),
          _buildSlider(
            'Gamma',
            Icons.gradient,
            _settings.gamma,
            0.5,
            2.0,
            (value) => _updateSettings((s) => s.copyWith(gamma: value)),
          ),
          _buildSlider(
            'Exposure',
            Icons.exposure,
            _settings.exposure,
            -2.0,
            2.0,
            (value) => _updateSettings((s) => s.copyWith(exposure: value)),
          ),
          _buildSlider(
            'Sharpness',
            Icons.details,
            _settings.sharpness,
            -1.0,
            1.0,
            (value) => _updateSettings((s) => s.copyWith(sharpness: value)),
          ),
        ],
      ),
    );
  }
  
  Widget _buildColorTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          _buildSlider(
            'Hue',
            Icons.color_lens,
            _settings.hue,
            -180.0,
            180.0,
            (value) => _updateSettings((s) => s.copyWith(hue: value)),
          ),
          _buildSlider(
            'Temperature',
            Icons.thermostat,
            _settings.temperature,
            -1.0,
            1.0,
            (value) => _updateSettings((s) => s.copyWith(temperature: value)),
          ),
          _buildSlider(
            'Tint',
            Icons.format_color_fill,
            _settings.tint,
            -1.0,
            1.0,
            (value) => _updateSettings((s) => s.copyWith(tint: value)),
          ),
          
          const SizedBox(height: 20),
          
          // Color presets
          Text(
            'Color Presets',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildPresetChip('Normal', () {
                _updateSettings((s) => VideoFilterSettings());
              }),
              _buildPresetChip('Vivid', () {
                _updateSettings((s) => s.copyWith(
                  saturation: 1.3,
                  contrast: 1.1,
                ));
              }),
              _buildPresetChip('Cinema', () {
                _updateSettings((s) => s.copyWith(
                  saturation: 0.9,
                  contrast: 1.2,
                  temperature: -0.1,
                ));
              }),
              _buildPresetChip('Warm', () {
                _updateSettings((s) => s.copyWith(
                  temperature: 0.3,
                ));
              }),
              _buildPresetChip('Cool', () {
                _updateSettings((s) => s.copyWith(
                  temperature: -0.3,
                ));
              }),
              _buildPresetChip('B&W', () {
                _updateSettings((s) => s.copyWith(
                  saturation: 0.0,
                ));
              }),
            ],
          ),
        ],
      ),
    );
  }
  
  Widget _buildEffectsTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Blur
          _buildSlider(
            'Blur',
            Icons.blur_on,
            _settings.blur,
            0.0,
            10.0,
            (value) => _updateSettings((s) => s.copyWith(blur: value)),
          ),
          
          // Vignette
          _buildSlider(
            'Vignette',
            Icons.vignette,
            _settings.vignette,
            0.0,
            1.0,
            (value) => _updateSettings((s) => s.copyWith(vignette: value)),
          ),
          
          // Film grain
          _buildSlider(
            'Film Grain',
            Icons.grain,
            _settings.filmGrain,
            0.0,
            1.0,
            (value) => _updateSettings((s) => s.copyWith(filmGrain: value)),
          ),
          
          const SizedBox(height: 20),
          
          // Effect toggles
          SwitchListTile(
            title: const Text('Flip Horizontal'),
            value: _settings.flipHorizontal,
            onChanged: (value) {
              _updateSettings((s) => s.copyWith(flipHorizontal: value));
            },
          ),
          SwitchListTile(
            title: const Text('Flip Vertical'),
            value: _settings.flipVertical,
            onChanged: (value) {
              _updateSettings((s) => s.copyWith(flipVertical: value));
            },
          ),
          SwitchListTile(
            title: const Text('Grayscale'),
            value: _settings.grayscale,
            onChanged: (value) {
              _updateSettings((s) => s.copyWith(grayscale: value));
            },
          ),
          SwitchListTile(
            title: const Text('Invert Colors'),
            value: _settings.invertColors,
            onChanged: (value) {
              _updateSettings((s) => s.copyWith(invertColors: value));
            },
          ),
          
          const SizedBox(height: 20),
          
          // Filter presets
          Text(
            'Filter Presets',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: 12),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              _buildPresetChip('Vintage', () {
                _updateSettings((s) => s.copyWith(
                  saturation: 0.7,
                  temperature: 0.2,
                  vignette: 0.3,
                  filmGrain: 0.2,
                ));
              }),
              _buildPresetChip('Dramatic', () {
                _updateSettings((s) => s.copyWith(
                  contrast: 1.4,
                  saturation: 0.8,
                  vignette: 0.5,
                ));
              }),
              _buildPresetChip('Soft', () {
                _updateSettings((s) => s.copyWith(
                  blur: 0.5,
                  contrast: 0.9,
                ));
              }),
              _buildPresetChip('Sharp', () {
                _updateSettings((s) => s.copyWith(
                  sharpness: 0.8,
                  contrast: 1.2,
                ));
              }),
            ],
          ),
        ],
      ),
    );
  }
  
  Widget _buildSlider(
    String label,
    IconData icon,
    double value,
    double min,
    double max,
    Function(double) onChanged,
  ) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        children: [
          Row(
            children: [
              Icon(icon, size: 20),
              const SizedBox(width: 12),
              Text(label),
              const Spacer(),
              Text(
                value.toStringAsFixed(1),
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ],
          ),
          Slider(
            value: value,
            min: min,
            max: max,
            onChanged: onChanged,
          ),
        ],
      ),
    );
  }
  
  Widget _buildPresetChip(String label, VoidCallback onTap) {
    return ActionChip(
      label: Text(label),
      onPressed: onTap,
    );
  }
}

class VideoFilterSettings {
  // Basic adjustments
  final double brightness;
  final double contrast;
  final double saturation;
  final double gamma;
  final double exposure;
  final double sharpness;
  
  // Color adjustments
  final double hue;
  final double temperature;
  final double tint;
  
  // Effects
  final double blur;
  final double vignette;
  final double filmGrain;
  final bool flipHorizontal;
  final bool flipVertical;
  final bool grayscale;
  final bool invertColors;
  
  const VideoFilterSettings({
    this.brightness = 0.0,
    this.contrast = 1.0,
    this.saturation = 1.0,
    this.gamma = 1.0,
    this.exposure = 0.0,
    this.sharpness = 0.0,
    this.hue = 0.0,
    this.temperature = 0.0,
    this.tint = 0.0,
    this.blur = 0.0,
    this.vignette = 0.0,
    this.filmGrain = 0.0,
    this.flipHorizontal = false,
    this.flipVertical = false,
    this.grayscale = false,
    this.invertColors = false,
  });
  
  VideoFilterSettings copyWith({
    double? brightness,
    double? contrast,
    double? saturation,
    double? gamma,
    double? exposure,
    double? sharpness,
    double? hue,
    double? temperature,
    double? tint,
    double? blur,
    double? vignette,
    double? filmGrain,
    bool? flipHorizontal,
    bool? flipVertical,
    bool? grayscale,
    bool? invertColors,
  }) {
    return VideoFilterSettings(
      brightness: brightness ?? this.brightness,
      contrast: contrast ?? this.contrast,
      saturation: saturation ?? this.saturation,
      gamma: gamma ?? this.gamma,
      exposure: exposure ?? this.exposure,
      sharpness: sharpness ?? this.sharpness,
      hue: hue ?? this.hue,
      temperature: temperature ?? this.temperature,
      tint: tint ?? this.tint,
      blur: blur ?? this.blur,
      vignette: vignette ?? this.vignette,
      filmGrain: filmGrain ?? this.filmGrain,
      flipHorizontal: flipHorizontal ?? this.flipHorizontal,
      flipVertical: flipVertical ?? this.flipVertical,
      grayscale: grayscale ?? this.grayscale,
      invertColors: invertColors ?? this.invertColors,
    );
  }
  
  // Convert to shader parameters
  Map<String, dynamic> toShaderParams() {
    return {
      'brightness': brightness,
      'contrast': contrast,
      'saturation': saturation,
      'gamma': gamma,
      'exposure': exposure,
      'sharpness': sharpness,
      'hue': hue,
      'temperature': temperature,
      'tint': tint,
      'blur': blur,
      'vignette': vignette,
      'filmGrain': filmGrain,
      'flipH': flipHorizontal ? 1.0 : 0.0,
      'flipV': flipVertical ? 1.0 : 0.0,
      'grayscale': grayscale ? 1.0 : 0.0,
      'invert': invertColors ? 1.0 : 0.0,
    };
  }
  
  bool get hasChanges {
    return brightness != 0.0 ||
           contrast != 1.0 ||
           saturation != 1.0 ||
           gamma != 1.0 ||
           exposure != 0.0 ||
           sharpness != 0.0 ||
           hue != 0.0 ||
           temperature != 0.0 ||
           tint != 0.0 ||
           blur != 0.0 ||
           vignette != 0.0 ||
           filmGrain != 0.0 ||
           flipHorizontal ||
           flipVertical ||
           grayscale ||
           invertColors;
  }
}