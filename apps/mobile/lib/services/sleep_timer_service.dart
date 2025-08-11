import 'dart:async';
import 'package:flutter/material.dart';

class SleepTimerService extends ChangeNotifier {
  static final SleepTimerService _instance = SleepTimerService._internal();
  factory SleepTimerService() => _instance;
  SleepTimerService._internal();
  
  Timer? _timer;
  DateTime? _endTime;
  Duration? _duration;
  bool _isActive = false;
  
  // Callbacks
  VoidCallback? onTimerEnd;
  Function(Duration)? onTimerTick;
  
  bool get isActive => _isActive;
  DateTime? get endTime => _endTime;
  Duration? get remainingTime {
    if (!_isActive || _endTime == null) return null;
    final remaining = _endTime!.difference(DateTime.now());
    return remaining.isNegative ? Duration.zero : remaining;
  }
  
  // Preset durations
  static const List<Duration> presets = [
    Duration(minutes: 15),
    Duration(minutes: 30),
    Duration(minutes: 45),
    Duration(hours: 1),
    Duration(hours: 1, minutes: 30),
    Duration(hours: 2),
  ];
  
  void startTimer(Duration duration) {
    cancelTimer();
    
    _duration = duration;
    _endTime = DateTime.now().add(duration);
    _isActive = true;
    
    // Update every second
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final remaining = remainingTime;
      
      if (remaining == null || remaining == Duration.zero) {
        _onTimerComplete();
      } else {
        onTimerTick?.call(remaining);
        notifyListeners();
      }
    });
    
    notifyListeners();
  }
  
  void startTimerUntil(TimeOfDay time) {
    final now = DateTime.now();
    var targetTime = DateTime(
      now.year,
      now.month,
      now.day,
      time.hour,
      time.minute,
    );
    
    // If the time is in the past, add a day
    if (targetTime.isBefore(now)) {
      targetTime = targetTime.add(const Duration(days: 1));
    }
    
    final duration = targetTime.difference(now);
    startTimer(duration);
  }
  
  void extendTimer(Duration additional) {
    if (_isActive && _endTime != null) {
      _endTime = _endTime!.add(additional);
      notifyListeners();
    }
  }
  
  void reduceTimer(Duration reduction) {
    if (_isActive && _endTime != null) {
      final newEndTime = _endTime!.subtract(reduction);
      if (newEndTime.isAfter(DateTime.now())) {
        _endTime = newEndTime;
        notifyListeners();
      } else {
        _onTimerComplete();
      }
    }
  }
  
  void cancelTimer() {
    _timer?.cancel();
    _timer = null;
    _endTime = null;
    _duration = null;
    _isActive = false;
    notifyListeners();
  }
  
  void _onTimerComplete() {
    cancelTimer();
    onTimerEnd?.call();
  }
  
  String formatRemainingTime() {
    final remaining = remainingTime;
    if (remaining == null) return '';
    
    final hours = remaining.inHours;
    final minutes = remaining.inMinutes.remainder(60);
    final seconds = remaining.inSeconds.remainder(60);
    
    if (hours > 0) {
      return '${hours}h ${minutes}m ${seconds}s';
    } else if (minutes > 0) {
      return '${minutes}m ${seconds}s';
    } else {
      return '${seconds}s';
    }
  }
  
  @override
  void dispose() {
    cancelTimer();
    super.dispose();
  }
}

// Sleep Timer Widget
class SleepTimerWidget extends StatefulWidget {
  final VoidCallback? onTimerSet;
  final VoidCallback? onClose;
  
  const SleepTimerWidget({
    super.key,
    this.onTimerSet,
    this.onClose,
  });

  @override
  State<SleepTimerWidget> createState() => _SleepTimerWidgetState();
}

class _SleepTimerWidgetState extends State<SleepTimerWidget> {
  final _timerService = SleepTimerService();
  TimeOfDay? _selectedTime;
  Duration? _customDuration;
  int _customHours = 0;
  int _customMinutes = 30;
  
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: Theme.of(context).scaffoldBackgroundColor,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
      ),
      child: SafeArea(
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
            
            // Header
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Sleep Timer',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: widget.onClose,
                  ),
                ],
              ),
            ),
            
            // Current timer status
            if (_timerService.isActive)
              Container(
                margin: const EdgeInsets.all(16),
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context).primaryColor.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    Icon(
                      Icons.timer,
                      color: Theme.of(context).primaryColor,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('Timer Active'),
                          AnimatedBuilder(
                            animation: _timerService,
                            builder: (context, child) {
                              return Text(
                                _timerService.formatRemainingTime(),
                                style: Theme.of(context).textTheme.headlineSmall,
                              );
                            },
                          ),
                        ],
                      ),
                    ),
                    TextButton(
                      onPressed: () {
                        _timerService.cancelTimer();
                        Navigator.pop(context);
                      },
                      child: const Text('Cancel'),
                    ),
                  ],
                ),
              ),
            
            // Preset options
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Quick Timers',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: SleepTimerService.presets.map((duration) {
                      final label = duration.inHours > 0
                        ? '${duration.inHours}h ${duration.inMinutes.remainder(60)}m'
                        : '${duration.inMinutes}m';
                      
                      return ChoiceChip(
                        label: Text(label),
                        selected: false,
                        onSelected: (_) {
                          _timerService.startTimer(duration);
                          widget.onTimerSet?.call();
                          Navigator.pop(context);
                        },
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
            
            const Divider(),
            
            // Custom duration
            ListTile(
              leading: const Icon(Icons.timer),
              title: const Text('Custom Duration'),
              subtitle: Text('${_customHours}h ${_customMinutes}m'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _showCustomDurationPicker(),
            ),
            
            // Sleep at specific time
            ListTile(
              leading: const Icon(Icons.bedtime),
              title: const Text('Sleep at Time'),
              subtitle: _selectedTime != null
                ? Text(_selectedTime!.format(context))
                : const Text('Select time'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => _selectTime(),
            ),
            
            // End of playlist
            if (!_timerService.isActive)
              ListTile(
                leading: const Icon(Icons.playlist_play),
                title: const Text('End of Playlist'),
                subtitle: const Text('Stop after current playlist ends'),
                onTap: () {
                  // Set flag to stop at end of playlist
                  Navigator.pop(context);
                },
              ),
            
            const SizedBox(height: 16),
          ],
        ),
      ),
    );
  }
  
  void _showCustomDurationPicker() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Set Duration'),
        content: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Hours picker
            Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Hours'),
                SizedBox(
                  width: 80,
                  height: 120,
                  child: ListWheelScrollView.useDelegate(
                    itemExtent: 40,
                    onSelectedItemChanged: (index) {
                      setState(() {
                        _customHours = index;
                      });
                    },
                    childDelegate: ListWheelChildBuilderDelegate(
                      builder: (context, index) {
                        return Center(
                          child: Text(
                            index.toString(),
                            style: const TextStyle(fontSize: 20),
                          ),
                        );
                      },
                      childCount: 24,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(width: 20),
            const Text(':', style: TextStyle(fontSize: 24)),
            const SizedBox(width: 20),
            // Minutes picker
            Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Minutes'),
                SizedBox(
                  width: 80,
                  height: 120,
                  child: ListWheelScrollView.useDelegate(
                    itemExtent: 40,
                    onSelectedItemChanged: (index) {
                      setState(() {
                        _customMinutes = index * 5;
                      });
                    },
                    childDelegate: ListWheelChildBuilderDelegate(
                      builder: (context, index) {
                        return Center(
                          child: Text(
                            (index * 5).toString().padLeft(2, '0'),
                            style: const TextStyle(fontSize: 20),
                          ),
                        );
                      },
                      childCount: 12,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              final duration = Duration(
                hours: _customHours,
                minutes: _customMinutes,
              );
              _timerService.startTimer(duration);
              widget.onTimerSet?.call();
              Navigator.pop(context);
              Navigator.pop(context);
            },
            child: const Text('Start'),
          ),
        ],
      ),
    );
  }
  
  void _selectTime() async {
    final time = await showTimePicker(
      context: context,
      initialTime: _selectedTime ?? TimeOfDay.now(),
    );
    
    if (time != null) {
      setState(() {
        _selectedTime = time;
      });
      
      _timerService.startTimerUntil(time);
      widget.onTimerSet?.call();
      Navigator.pop(context);
    }
  }
}