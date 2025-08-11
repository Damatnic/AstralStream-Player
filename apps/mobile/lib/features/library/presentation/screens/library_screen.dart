import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

class LibraryScreen extends ConsumerWidget {
  const LibraryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Library'),
          bottom: const TabBar(
            tabs: [
              Tab(text: 'All'),
              Tab(text: 'Videos'),
              Tab(text: 'Audio'),
            ],
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.search),
              onPressed: () {
                // TODO: Implement search
              },
            ),
            IconButton(
              icon: const Icon(Icons.filter_list),
              onPressed: () {
                // TODO: Show filter options
              },
            ),
          ],
        ),
        body: const TabBarView(
          children: [
            AllMediaTab(),
            VideosTab(),
            AudioTab(),
          ],
        ),
      ),
    );
  }
}

class AllMediaTab extends StatelessWidget {
  const AllMediaTab({super.key});

  @override
  Widget build(BuildContext context) {
    return GridView.builder(
      padding: const EdgeInsets.all(8),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        childAspectRatio: 0.75,
        crossAxisSpacing: 8,
        mainAxisSpacing: 8,
      ),
      itemCount: 20,
      itemBuilder: (context, index) {
        return Card(
          child: InkWell(
            onTap: () {
              // Navigate to player
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
                          child: Icon(Icons.play_circle_outline, size: 48),
                        ),
                        Positioned(
                          bottom: 8,
                          right: 8,
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 6,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: Colors.black54,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: const Text(
                              '12:34',
                              style: TextStyle(
                                color: Colors.white,
                                fontSize: 12,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Media ${index + 1}',
                        style: Theme.of(context).textTheme.bodyMedium,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '1.2 GB • MP4',
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
    );
  }
}

class VideosTab extends StatelessWidget {
  const VideosTab({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: 15,
      itemBuilder: (context, index) {
        return ListTile(
          leading: Container(
            width: 80,
            height: 60,
            decoration: BoxDecoration(
              color: Colors.grey[800],
              borderRadius: BorderRadius.circular(8),
            ),
            child: const Icon(Icons.play_arrow),
          ),
          title: Text('Video ${index + 1}.mp4'),
          subtitle: const Text('1920x1080 • H.264 • 2.3 GB'),
          trailing: PopupMenuButton(
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'play',
                child: Text('Play'),
              ),
              const PopupMenuItem(
                value: 'info',
                child: Text('Details'),
              ),
              const PopupMenuItem(
                value: 'playlist',
                child: Text('Add to Playlist'),
              ),
              const PopupMenuItem(
                value: 'delete',
                child: Text('Delete'),
              ),
            ],
          ),
          onTap: () {
            // Navigate to player
          },
        );
      },
    );
  }
}

class AudioTab extends StatelessWidget {
  const AudioTab({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: 10,
      itemBuilder: (context, index) {
        return ListTile(
          leading: CircleAvatar(
            backgroundColor: Theme.of(context).primaryColor,
            child: const Icon(Icons.music_note, color: Colors.white),
          ),
          title: Text('Audio ${index + 1}.mp3'),
          subtitle: const Text('320 kbps • 5.2 MB'),
          trailing: IconButton(
            icon: const Icon(Icons.more_vert),
            onPressed: () {},
          ),
          onTap: () {
            // Play audio
          },
        );
      },
    );
  }
}