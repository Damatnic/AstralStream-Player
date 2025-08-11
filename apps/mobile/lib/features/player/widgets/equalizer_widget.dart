import 'package:flutter/material.dart';

class EqualizerWidget extends StatefulWidget {
  final Function(List<double>)? onEqualizerChanged;
  final VoidCallback? onClose;
  
  const EqualizerWidget({
    super.key,
    this.onEqualizerChanged,
    this.onClose,
  });

  @override
  State<EqualizerWidget> createState() => _EqualizerWidgetState();
}

class _EqualizerWidgetState extends State<EqualizerWidget> {
  // Frequency bands in Hz
  static const List<String> _frequencyBands = [
    '60Hz',
    '230Hz',
    '910Hz',
    '3.6kHz',
    '14kHz',
  ];
  
  // Preset configurations
  static const Map<String, List<double>> _presets = {
    'Normal': [0, 0, 0, 0, 0],
    'Pop': [1, 3, 5, 3, 1],
    'Rock': [5, 3, 0, 3, 5],
    'Jazz': [3, 1, 0, 2, 4],
    'Classical': [4, 3, 0, 3, 4],
    'Dance': [6, 0, 2, 4, 5],
    'Bass Boost': [6, 4, 2, 0, 0],
    'Treble Boost': [0, 0, 2, 4, 6],
    'Vocal': [0, 3, 5, 3, 0],
    'Powerful': [5, 3, 0, 3, 5],
  };
  
  // Current equalizer values (-10 to +10)
  List<double> _bandValues = [0, 0, 0, 0, 0];
  String _selectedPreset = 'Normal';
  bool _bassBoost = false;
  bool _virtualizer = false;
  double _loudnessEnhancer = 0.0;
  
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
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
          
          // Title and close button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Equalizer',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: widget.onClose,
                ),
              ],
            ),
          ),
          
          // Preset selector
          SizedBox(
            height: 40,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: _presets.length,
              itemBuilder: (context, index) {
                final presetName = _presets.keys.elementAt(index);
                final isSelected = _selectedPreset == presetName;
                
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(presetName),
                    selected: isSelected,
                    onSelected: (selected) {
                      if (selected) {
                        setState(() {
                          _selectedPreset = presetName;
                          _bandValues = List.from(_presets[presetName]!);
                        });
                        widget.onEqualizerChanged?.call(_bandValues);
                      }
                    },
                  ),
                );
              },
            ),
          ),
          
          const SizedBox(height: 20),
          
          // Equalizer bands
          Container(
            height: 250,
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: List.generate(_frequencyBands.length, (index) {
                return Expanded(
                  child: Column(
                    children: [
                      Text(
                        '+10',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      Expanded(
                        child: RotatedBox(
                          quarterTurns: 3,
                          child: SliderTheme(
                            data: SliderThemeData(
                              trackHeight: 4,
                              thumbShape: const RoundSliderThumbShape(
                                enabledThumbRadius: 8,
                              ),
                              overlayShape: const RoundSliderOverlayShape(
                                overlayRadius: 16,
                              ),
                              activeTrackColor: Theme.of(context).primaryColor,
                              inactiveTrackColor: Colors.grey[700],
                              thumbColor: Theme.of(context).primaryColor,
                              overlayColor: Theme.of(context).primaryColor.withOpacity(0.2),
                            ),
                            child: Slider(
                              value: _bandValues[index],
                              min: -10,
                              max: 10,
                              onChanged: (value) {
                                setState(() {
                                  _bandValues[index] = value;
                                  _selectedPreset = 'Custom';
                                });
                                widget.onEqualizerChanged?.call(_bandValues);
                              },
                            ),
                          ),
                        ),
                      ),
                      Text(
                        '-10',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                      const SizedBox(height: 8),
                      Text(
                        _frequencyBands[index],
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                );
              }),
            ),
          ),
          
          const SizedBox(height: 20),
          
          // Additional audio effects
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(
              children: [
                // Bass Boost
                SwitchListTile(
                  title: const Text('Bass Boost'),
                  subtitle: const Text('Enhance low frequencies'),
                  value: _bassBoost,
                  onChanged: (value) {
                    setState(() {
                      _bassBoost = value;
                    });
                  },
                ),
                
                // Virtualizer
                SwitchListTile(
                  title: const Text('3D Surround'),
                  subtitle: const Text('Virtual surround sound'),
                  value: _virtualizer,
                  onChanged: (value) {
                    setState(() {
                      _virtualizer = value;
                    });
                  },
                ),
                
                // Loudness Enhancer
                ListTile(
                  title: const Text('Loudness Enhancer'),
                  subtitle: Slider(
                    value: _loudnessEnhancer,
                    min: 0,
                    max: 100,
                    divisions: 20,
                    label: '${_loudnessEnhancer.round()}%',
                    onChanged: (value) {
                      setState(() {
                        _loudnessEnhancer = value;
                      });
                    },
                  ),
                ),
              ],
            ),
          ),
          
          // Reset button
          Padding(
            padding: const EdgeInsets.all(16),
            child: OutlinedButton.icon(
              onPressed: () {
                setState(() {
                  _bandValues = [0, 0, 0, 0, 0];
                  _selectedPreset = 'Normal';
                  _bassBoost = false;
                  _virtualizer = false;
                  _loudnessEnhancer = 0;
                });
                widget.onEqualizerChanged?.call(_bandValues);
              },
              icon: const Icon(Icons.refresh),
              label: const Text('Reset'),
            ),
          ),
        ],
      ),
    );
  }
}

// Audio Effects Service
class AudioEffects {
  static final AudioEffects _instance = AudioEffects._internal();
  factory AudioEffects() => _instance;
  AudioEffects._internal();
  
  // Current effect values
  List<double> equalizerBands = [0, 0, 0, 0, 0];
  bool bassBoostEnabled = false;
  int bassBoostStrength = 0;
  bool virtualizerEnabled = false;
  int virtualizerStrength = 0;
  double loudnessEnhancer = 0;
  
  void applyEqualizer(List<double> bands) {
    equalizerBands = bands;
    // Apply to audio engine
    _applyEffects();
  }
  
  void setBassBoost(bool enabled, {int strength = 500}) {
    bassBoostEnabled = enabled;
    bassBoostStrength = strength;
    _applyEffects();
  }
  
  void setVirtualizer(bool enabled, {int strength = 500}) {
    virtualizerEnabled = enabled;
    virtualizerStrength = strength;
    _applyEffects();
  }
  
  void setLoudnessEnhancer(double gain) {
    loudnessEnhancer = gain;
    _applyEffects();
  }
  
  void _applyEffects() {
    // This would interface with the native audio engine
    // to apply the effects in real-time
  }
  
  void reset() {
    equalizerBands = [0, 0, 0, 0, 0];
    bassBoostEnabled = false;
    virtualizerEnabled = false;
    loudnessEnhancer = 0;
    _applyEffects();
  }
}