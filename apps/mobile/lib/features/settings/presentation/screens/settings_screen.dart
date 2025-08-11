import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
      ),
      body: ListView(
        children: [
          // Playback Settings
          _buildSectionHeader(context, 'Playback'),
          ListTile(
            leading: const Icon(Icons.speed),
            title: const Text('Default Playback Speed'),
            subtitle: const Text('1.0x'),
            onTap: () {
              _showPlaybackSpeedDialog(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.repeat),
            title: const Text('Auto-play Next'),
            trailing: Switch(
              value: true,
              onChanged: (value) {},
            ),
          ),
          ListTile(
            leading: const Icon(Icons.screen_rotation),
            title: const Text('Auto-rotate'),
            trailing: Switch(
              value: true,
              onChanged: (value) {},
            ),
          ),
          ListTile(
            leading: const Icon(Icons.picture_in_picture),
            title: const Text('Picture-in-Picture'),
            trailing: Switch(
              value: true,
              onChanged: (value) {},
            ),
          ),
          
          const Divider(),
          
          // Display Settings
          _buildSectionHeader(context, 'Display'),
          ListTile(
            leading: const Icon(Icons.brightness_6),
            title: const Text('Theme'),
            subtitle: const Text('Dark'),
            onTap: () {
              _showThemeDialog(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.color_lens),
            title: const Text('Player Theme'),
            subtitle: const Text('Default'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.aspect_ratio),
            title: const Text('Default Aspect Ratio'),
            subtitle: const Text('Fit to Screen'),
            onTap: () {},
          ),
          
          const Divider(),
          
          // Subtitle Settings
          _buildSectionHeader(context, 'Subtitles'),
          ListTile(
            leading: const Icon(Icons.subtitles),
            title: const Text('Default Subtitle Language'),
            subtitle: const Text('English'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.text_fields),
            title: const Text('Subtitle Size'),
            subtitle: const Text('Medium'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.format_color_text),
            title: const Text('Subtitle Color'),
            subtitle: const Text('White'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.translate),
            title: const Text('Auto-generate Subtitles'),
            subtitle: const Text('Using on-device AI'),
            trailing: Switch(
              value: false,
              onChanged: (value) {},
            ),
          ),
          
          const Divider(),
          
          // Audio Settings
          _buildSectionHeader(context, 'Audio'),
          ListTile(
            leading: const Icon(Icons.audiotrack),
            title: const Text('Default Audio Track'),
            subtitle: const Text('Primary'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.equalizer),
            title: const Text('Equalizer'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.volume_up),
            title: const Text('Volume Boost'),
            trailing: Switch(
              value: false,
              onChanged: (value) {},
            ),
          ),
          
          const Divider(),
          
          // Gestures
          _buildSectionHeader(context, 'Gestures'),
          ListTile(
            leading: const Icon(Icons.gesture),
            title: const Text('Swipe to Seek'),
            trailing: Switch(
              value: true,
              onChanged: (value) {},
            ),
          ),
          ListTile(
            leading: const Icon(Icons.brightness_high),
            title: const Text('Swipe for Brightness'),
            trailing: Switch(
              value: true,
              onChanged: (value) {},
            ),
          ),
          ListTile(
            leading: const Icon(Icons.volume_up),
            title: const Text('Swipe for Volume'),
            trailing: Switch(
              value: true,
              onChanged: (value) {},
            ),
          ),
          ListTile(
            leading: const Icon(Icons.touch_app),
            title: const Text('Double Tap to Seek'),
            subtitle: const Text('10 seconds'),
            onTap: () {},
          ),
          
          const Divider(),
          
          // Storage
          _buildSectionHeader(context, 'Storage'),
          ListTile(
            leading: const Icon(Icons.folder),
            title: const Text('Default Download Location'),
            subtitle: const Text('/storage/emulated/0/Download'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.cleaning_services),
            title: const Text('Clear Cache'),
            subtitle: const Text('125 MB'),
            onTap: () {
              _showClearCacheDialog(context);
            },
          ),
          ListTile(
            leading: const Icon(Icons.history),
            title: const Text('Clear Watch History'),
            onTap: () {
              _showClearHistoryDialog(context);
            },
          ),
          
          const Divider(),
          
          // Privacy
          _buildSectionHeader(context, 'Privacy'),
          ListTile(
            leading: const Icon(Icons.lock),
            title: const Text('Private Folder'),
            subtitle: const Text('Password protected'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.fingerprint),
            title: const Text('Biometric Lock'),
            trailing: Switch(
              value: false,
              onChanged: (value) {},
            ),
          ),
          
          const Divider(),
          
          // About
          _buildSectionHeader(context, 'About'),
          ListTile(
            leading: const Icon(Icons.info),
            title: const Text('Version'),
            subtitle: const Text('1.0.0'),
          ),
          ListTile(
            leading: const Icon(Icons.code),
            title: const Text('Open Source Licenses'),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.privacy_tip),
            title: const Text('Privacy Policy'),
            onTap: () {},
          ),
          
          const SizedBox(height: 32),
        ],
      ),
    );
  }
  
  Widget _buildSectionHeader(BuildContext context, String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
      child: Text(
        title,
        style: TextStyle(
          color: Theme.of(context).primaryColor,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }
  
  void _showPlaybackSpeedDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Default Playback Speed'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            '0.25x', '0.5x', '0.75x', '1.0x', '1.25x', '1.5x', '1.75x', '2.0x'
          ].map((speed) {
            return RadioListTile<String>(
              title: Text(speed),
              value: speed,
              groupValue: '1.0x',
              onChanged: (value) {
                Navigator.of(context).pop();
              },
            );
          }).toList(),
        ),
      ),
    );
  }
  
  void _showThemeDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Theme'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            RadioListTile<String>(
              title: const Text('Light'),
              value: 'light',
              groupValue: 'dark',
              onChanged: (value) {
                Navigator.of(context).pop();
              },
            ),
            RadioListTile<String>(
              title: const Text('Dark'),
              value: 'dark',
              groupValue: 'dark',
              onChanged: (value) {
                Navigator.of(context).pop();
              },
            ),
            RadioListTile<String>(
              title: const Text('System'),
              value: 'system',
              groupValue: 'dark',
              onChanged: (value) {
                Navigator.of(context).pop();
              },
            ),
          ],
        ),
      ),
    );
  }
  
  void _showClearCacheDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Cache'),
        content: const Text('This will clear 125 MB of cached data. Continue?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Cache cleared')),
              );
            },
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }
  
  void _showClearHistoryDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Watch History'),
        content: const Text('This will clear all your watch history. This action cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              Navigator.of(context).pop();
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(content: Text('Watch history cleared')),
              );
            },
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }
}