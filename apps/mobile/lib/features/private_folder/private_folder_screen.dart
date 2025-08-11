import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';
import 'package:crypto/crypto.dart';
import 'dart:convert';
import '../../services/database_service.dart';
import '../../models/media_file.dart';

class PrivateFolderScreen extends ConsumerStatefulWidget {
  const PrivateFolderScreen({super.key});

  @override
  ConsumerState<PrivateFolderScreen> createState() => _PrivateFolderScreenState();
}

class _PrivateFolderScreenState extends ConsumerState<PrivateFolderScreen> {
  final LocalAuthentication _localAuth = LocalAuthentication();
  final DatabaseService _dbService = DatabaseService();
  
  bool _isAuthenticated = false;
  bool _isLoading = false;
  bool _biometricsAvailable = false;
  List<MediaFile> _privateFiles = [];
  
  @override
  void initState() {
    super.initState();
    _checkBiometrics();
    _authenticate();
  }
  
  Future<void> _checkBiometrics() async {
    try {
      final isAvailable = await _localAuth.canCheckBiometrics;
      final isDeviceSupported = await _localAuth.isDeviceSupported();
      
      setState(() {
        _biometricsAvailable = isAvailable && isDeviceSupported;
      });
    } catch (e) {
      debugPrint('Error checking biometrics: $e');
    }
  }
  
  Future<void> _authenticate() async {
    // Check if password is set
    final hasPassword = _dbService.getSetting<bool>('private_folder_enabled') ?? false;
    
    if (!hasPassword) {
      // First time setup
      _showSetupDialog();
      return;
    }
    
    // Try biometric authentication first
    if (_biometricsAvailable) {
      final useBiometrics = _dbService.getSetting<bool>('private_folder_biometrics') ?? false;
      
      if (useBiometrics) {
        try {
          final authenticated = await _localAuth.authenticate(
            localizedReason: 'Authenticate to access private folder',
            options: const AuthenticationOptions(
              biometricOnly: false,
              stickyAuth: true,
            ),
          );
          
          if (authenticated) {
            setState(() {
              _isAuthenticated = true;
            });
            _loadPrivateFiles();
            return;
          }
        } catch (e) {
          debugPrint('Biometric authentication error: $e');
        }
      }
    }
    
    // Fall back to password
    _showPasswordDialog();
  }
  
  void _showSetupDialog() {
    final passwordController = TextEditingController();
    final confirmController = TextEditingController();
    bool obscurePassword = true;
    
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Setup Private Folder'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Text(
                'Create a password to protect your private media files.',
                style: TextStyle(fontSize: 14),
              ),
              const SizedBox(height: 20),
              TextField(
                controller: passwordController,
                obscureText: obscurePassword,
                decoration: InputDecoration(
                  labelText: 'Password',
                  border: const OutlineInputBorder(),
                  suffixIcon: IconButton(
                    icon: Icon(
                      obscurePassword ? Icons.visibility : Icons.visibility_off,
                    ),
                    onPressed: () {
                      setState(() {
                        obscurePassword = !obscurePassword;
                      });
                    },
                  ),
                ),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: confirmController,
                obscureText: obscurePassword,
                decoration: const InputDecoration(
                  labelText: 'Confirm Password',
                  border: OutlineInputBorder(),
                ),
              ),
              if (_biometricsAvailable) ...[
                const SizedBox(height: 16),
                CheckboxListTile(
                  title: const Text('Enable biometric unlock'),
                  subtitle: const Text('Use fingerprint or face unlock'),
                  value: true,
                  onChanged: (value) {},
                ),
              ],
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                if (passwordController.text.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Password cannot be empty')),
                  );
                  return;
                }
                
                if (passwordController.text != confirmController.text) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Passwords do not match')),
                  );
                  return;
                }
                
                // Hash and save password
                final hash = _hashPassword(passwordController.text);
                _dbService.setSetting('private_folder_password', hash);
                _dbService.setSetting('private_folder_enabled', true);
                
                if (_biometricsAvailable) {
                  _dbService.setSetting('private_folder_biometrics', true);
                }
                
                Navigator.of(context).pop();
                
                setState(() {
                  _isAuthenticated = true;
                });
                
                _loadPrivateFiles();
              },
              child: const Text('Create'),
            ),
          ],
        ),
      ),
    );
  }
  
  void _showPasswordDialog() {
    final passwordController = TextEditingController();
    bool obscurePassword = true;
    int attempts = 0;
    
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => StatefulBuilder(
        builder: (context, setState) => AlertDialog(
          title: const Text('Enter Password'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: passwordController,
                obscureText: obscurePassword,
                autofocus: true,
                decoration: InputDecoration(
                  labelText: 'Password',
                  border: const OutlineInputBorder(),
                  suffixIcon: IconButton(
                    icon: Icon(
                      obscurePassword ? Icons.visibility : Icons.visibility_off,
                    ),
                    onPressed: () {
                      setState(() {
                        obscurePassword = !obscurePassword;
                      });
                    },
                  ),
                ),
                onSubmitted: (_) => _verifyPassword(passwordController.text),
              ),
              if (attempts > 2)
                const Padding(
                  padding: EdgeInsets.only(top: 8),
                  child: Text(
                    'Forgot password? You can reset by clearing app data.',
                    style: TextStyle(fontSize: 12, color: Colors.red),
                  ),
                ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
                Navigator.of(context).pop();
              },
              child: const Text('Cancel'),
            ),
            TextButton(
              onPressed: () {
                final success = _verifyPassword(passwordController.text);
                if (success) {
                  Navigator.of(context).pop();
                  setState(() {
                    _isAuthenticated = true;
                  });
                  _loadPrivateFiles();
                } else {
                  setState(() {
                    attempts++;
                  });
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Incorrect password')),
                  );
                }
              },
              child: const Text('Unlock'),
            ),
          ],
        ),
      ),
    );
  }
  
  bool _verifyPassword(String password) {
    final savedHash = _dbService.getSetting<String>('private_folder_password');
    if (savedHash == null) return false;
    
    final inputHash = _hashPassword(password);
    return inputHash == savedHash;
  }
  
  String _hashPassword(String password) {
    final bytes = utf8.encode(password);
    final digest = sha256.convert(bytes);
    return digest.toString();
  }
  
  Future<void> _loadPrivateFiles() async {
    setState(() {
      _isLoading = true;
    });
    
    try {
      // Load private files from database
      final allFiles = _dbService.getAllMediaFiles();
      _privateFiles = allFiles.where((file) {
        final isPrivate = _dbService.getSetting<bool>('private_${file.id}') ?? false;
        return isPrivate;
      }).toList();
    } catch (e) {
      debugPrint('Error loading private files: $e');
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }
  
  void _addToPrivateFolder(MediaFile file) {
    _dbService.setSetting('private_${file.id}', true);
    setState(() {
      _privateFiles.add(file);
    });
  }
  
  void _removeFromPrivateFolder(MediaFile file) {
    _dbService.setSetting('private_${file.id}', false);
    setState(() {
      _privateFiles.remove(file);
    });
  }
  
  @override
  Widget build(BuildContext context) {
    if (!_isAuthenticated) {
      return Scaffold(
        appBar: AppBar(
          title: const Text('Private Folder'),
        ),
        body: const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.lock,
                size: 64,
                color: Colors.grey,
              ),
              SizedBox(height: 16),
              Text(
                'Authentication Required',
                style: TextStyle(fontSize: 18),
              ),
            ],
          ),
        ),
      );
    }
    
    return Scaffold(
      appBar: AppBar(
        title: const Text('Private Folder'),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () => _showAddFilesDialog(),
          ),
          PopupMenuButton<String>(
            onSelected: (value) {
              switch (value) {
                case 'change_password':
                  _showChangePasswordDialog();
                  break;
                case 'biometrics':
                  _toggleBiometrics();
                  break;
                case 'clear':
                  _clearPrivateFolder();
                  break;
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'change_password',
                child: Text('Change Password'),
              ),
              if (_biometricsAvailable)
                PopupMenuItem(
                  value: 'biometrics',
                  child: Text(
                    _dbService.getSetting<bool>('private_folder_biometrics') ?? false
                      ? 'Disable Biometrics'
                      : 'Enable Biometrics',
                  ),
                ),
              const PopupMenuItem(
                value: 'clear',
                child: Text('Clear All'),
              ),
            ],
          ),
        ],
      ),
      body: _isLoading
        ? const Center(child: CircularProgressIndicator())
        : _privateFiles.isEmpty
          ? Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(
                    Icons.folder_special,
                    size: 64,
                    color: Colors.grey[400],
                  ),
                  const SizedBox(height: 16),
                  Text(
                    'No private files',
                    style: TextStyle(
                      fontSize: 18,
                      color: Colors.grey[600],
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    'Add files to keep them secure',
                    style: TextStyle(
                      fontSize: 14,
                      color: Colors.grey[500],
                    ),
                  ),
                  const SizedBox(height: 24),
                  ElevatedButton.icon(
                    onPressed: () => _showAddFilesDialog(),
                    icon: const Icon(Icons.add),
                    label: const Text('Add Files'),
                  ),
                ],
              ),
            )
          : GridView.builder(
              padding: const EdgeInsets.all(8),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                childAspectRatio: 0.75,
                crossAxisSpacing: 8,
                mainAxisSpacing: 8,
              ),
              itemCount: _privateFiles.length,
              itemBuilder: (context, index) {
                final file = _privateFiles[index];
                
                return Card(
                  child: InkWell(
                    onTap: () {
                      // Play file
                      Navigator.pushNamed(
                        context,
                        '/player',
                        arguments: {'path': file.path},
                      );
                    },
                    onLongPress: () {
                      _showFileOptions(file);
                    },
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Container(
                            decoration: BoxDecoration(
                              color: Colors.grey[800],
                              borderRadius: const BorderRadius.vertical(
                                top: Radius.circular(12),
                              ),
                            ),
                            child: Stack(
                              children: [
                                const Center(
                                  child: Icon(
                                    Icons.lock_outline,
                                    size: 32,
                                    color: Colors.white54,
                                  ),
                                ),
                                Positioned(
                                  top: 8,
                                  right: 8,
                                  child: Container(
                                    padding: const EdgeInsets.all(4),
                                    decoration: BoxDecoration(
                                      color: Colors.black54,
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                    child: Icon(
                                      file.type == MediaType.video
                                        ? Icons.movie
                                        : Icons.audiotrack,
                                      size: 16,
                                      color: Colors.white,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        Padding(
                          padding: const EdgeInsets.all(8),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                file.name,
                                style: Theme.of(context).textTheme.bodyMedium,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 4),
                              Text(
                                file.sizeFormatted,
                                style: Theme.of(context).textTheme.bodySmall,
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
  
  void _showAddFilesDialog() {
    // Show file selector to add files to private folder
  }
  
  void _showFileOptions(MediaFile file) {
    showModalBottomSheet(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.play_arrow),
              title: const Text('Play'),
              onTap: () {
                Navigator.pop(context);
                Navigator.pushNamed(
                  context,
                  '/player',
                  arguments: {'path': file.path},
                );
              },
            ),
            ListTile(
              leading: const Icon(Icons.folder_off),
              title: const Text('Remove from Private'),
              onTap: () {
                Navigator.pop(context);
                _removeFromPrivateFolder(file);
              },
            ),
            ListTile(
              leading: const Icon(Icons.info),
              title: const Text('Properties'),
              onTap: () {
                Navigator.pop(context);
                // Show file info
              },
            ),
          ],
        ),
      ),
    );
  }
  
  void _showChangePasswordDialog() {
    // Show dialog to change password
  }
  
  void _toggleBiometrics() {
    final current = _dbService.getSetting<bool>('private_folder_biometrics') ?? false;
    _dbService.setSetting('private_folder_biometrics', !current);
    
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          !current ? 'Biometrics enabled' : 'Biometrics disabled',
        ),
      ),
    );
  }
  
  void _clearPrivateFolder() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Private Folder'),
        content: const Text('Remove all files from private folder?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              for (final file in _privateFiles) {
                _dbService.setSetting('private_${file.id}', false);
              }
              setState(() {
                _privateFiles.clear();
              });
              Navigator.pop(context);
            },
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }
}