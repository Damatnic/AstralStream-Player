package com.astralstream.player.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudSyncManager @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val SYNC_COLLECTION = "user_sync_data"
        private const val BOOKMARKS_COLLECTION = "bookmarks"
        private const val WATCH_HISTORY_COLLECTION = "watch_history"
        private const val PLAYLISTS_COLLECTION = "playlists"
        private const val SETTINGS_COLLECTION = "settings"
        private const val SYNC_VERSION = 1
        private const val MAX_SYNC_ITEMS = 1000
        private const val BATCH_SIZE = 50
        private const val ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding"
    }
    
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val gson = Gson()
    
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _syncProgress = MutableStateFlow(SyncProgress())
    val syncProgress: StateFlow<SyncProgress> = _syncProgress.asStateFlow()
    
    private var syncListener: ListenerRegistration? = null
    private var encryptionKey: SecretKey? = null
    
    // Initialization
    
    fun initialize() {
        // Check if user is already signed in
        auth.currentUser?.let { user ->
            _syncState.value = _syncState.value.copy(
                isSignedIn = true,
                userId = user.uid,
                userEmail = user.email,
                isAnonymous = user.isAnonymous
            )
            initializeEncryption(user.uid)
        }
    }
    
    // Authentication
    
    suspend fun signInAnonymously(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = auth.signInAnonymously().await()
                _syncState.value = _syncState.value.copy(
                    isSignedIn = true,
                    userId = result.user?.uid,
                    isAnonymous = true
                )
                initializeEncryption(result.user?.uid)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun signInWithEmail(email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                _syncState.value = _syncState.value.copy(
                    isSignedIn = true,
                    userId = result.user?.uid,
                    userEmail = result.user?.email,
                    isAnonymous = false
                )
                initializeEncryption(result.user?.uid)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun createAccount(email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                _syncState.value = _syncState.value.copy(
                    isSignedIn = true,
                    userId = result.user?.uid,
                    userEmail = result.user?.email,
                    isAnonymous = false
                )
                initializeEncryption(result.user?.uid)
                initializeUserData()
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    fun signOut() {
        auth.signOut()
        syncListener?.remove()
        _syncState.value = SyncState()
        encryptionKey = null
    }
    
    // Sync Operations
    
    suspend fun syncBookmarks(bookmarks: List<SyncBookmark>): Boolean {
        if (!isNetworkAvailable() || !isSignedIn()) return false
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext false
                val batch = firestore.batch()
                
                _syncProgress.value = SyncProgress(
                    isSyncing = true,
                    currentOperation = "Syncing bookmarks",
                    totalItems = bookmarks.size
                )
                
                bookmarks.chunked(BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                    chunk.forEach { bookmark ->
                        val docRef = firestore
                            .collection(SYNC_COLLECTION)
                            .document(userId)
                            .collection(BOOKMARKS_COLLECTION)
                            .document(bookmark.id)
                        
                        val encryptedData = encryptData(gson.toJson(bookmark))
                        batch.set(docRef, mapOf(
                            "data" to encryptedData,
                            "timestamp" to bookmark.syncTimestamp,
                            "version" to SYNC_VERSION
                        ), SetOptions.merge())
                    }
                    
                    batch.commit().await()
                    
                    _syncProgress.value = _syncProgress.value.copy(
                        syncedItems = (chunkIndex + 1) * BATCH_SIZE
                    )
                }
                
                _syncState.value = _syncState.value.copy(
                    lastSyncTime = Date(),
                    bookmarksSynced = bookmarks.size
                )
                
                _syncProgress.value = SyncProgress()
                true
                
            } catch (e: Exception) {
                e.printStackTrace()
                _syncProgress.value = SyncProgress(error = e.message)
                false
            }
        }
    }
    
    suspend fun fetchBookmarks(): List<SyncBookmark> {
        if (!isNetworkAvailable() || !isSignedIn()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext emptyList()
                
                val documents = firestore
                    .collection(SYNC_COLLECTION)
                    .document(userId)
                    .collection(BOOKMARKS_COLLECTION)
                    .orderBy("timestamp")
                    .limit(MAX_SYNC_ITEMS.toLong())
                    .get()
                    .await()
                
                documents.documents.mapNotNull { doc ->
                    try {
                        val encryptedData = doc.getString("data") ?: return@mapNotNull null
                        val decryptedJson = decryptData(encryptedData)
                        gson.fromJson(decryptedJson, SyncBookmark::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    suspend fun syncWatchHistory(history: List<SyncWatchProgress>): Boolean {
        if (!isNetworkAvailable() || !isSignedIn()) return false
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext false
                
                _syncProgress.value = SyncProgress(
                    isSyncing = true,
                    currentOperation = "Syncing watch history",
                    totalItems = history.size
                )
                
                history.chunked(BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                    val batch = firestore.batch()
                    
                    chunk.forEach { progress ->
                        val docRef = firestore
                            .collection(SYNC_COLLECTION)
                            .document(userId)
                            .collection(WATCH_HISTORY_COLLECTION)
                            .document(progress.videoHash)
                        
                        val encryptedData = encryptData(gson.toJson(progress))
                        batch.set(docRef, mapOf(
                            "data" to encryptedData,
                            "timestamp" to progress.lastWatched,
                            "version" to SYNC_VERSION
                        ), SetOptions.merge())
                    }
                    
                    batch.commit().await()
                    
                    _syncProgress.value = _syncProgress.value.copy(
                        syncedItems = (chunkIndex + 1) * BATCH_SIZE
                    )
                }
                
                _syncState.value = _syncState.value.copy(
                    lastSyncTime = Date(),
                    watchHistorySynced = history.size
                )
                
                _syncProgress.value = SyncProgress()
                true
                
            } catch (e: Exception) {
                e.printStackTrace()
                _syncProgress.value = SyncProgress(error = e.message)
                false
            }
        }
    }
    
    suspend fun fetchWatchHistory(): List<SyncWatchProgress> {
        if (!isNetworkAvailable() || !isSignedIn()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext emptyList()
                
                val documents = firestore
                    .collection(SYNC_COLLECTION)
                    .document(userId)
                    .collection(WATCH_HISTORY_COLLECTION)
                    .orderBy("timestamp")
                    .limit(MAX_SYNC_ITEMS.toLong())
                    .get()
                    .await()
                
                documents.documents.mapNotNull { doc ->
                    try {
                        val encryptedData = doc.getString("data") ?: return@mapNotNull null
                        val decryptedJson = decryptData(encryptedData)
                        gson.fromJson(decryptedJson, SyncWatchProgress::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    suspend fun syncPlaylists(playlists: List<SyncPlaylist>): Boolean {
        if (!isNetworkAvailable() || !isSignedIn()) return false
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext false
                
                playlists.forEach { playlist ->
                    val docRef = firestore
                        .collection(SYNC_COLLECTION)
                        .document(userId)
                        .collection(PLAYLISTS_COLLECTION)
                        .document(playlist.id)
                    
                    val encryptedData = encryptData(gson.toJson(playlist))
                    docRef.set(mapOf(
                        "data" to encryptedData,
                        "timestamp" to playlist.modifiedAt,
                        "version" to SYNC_VERSION
                    ), SetOptions.merge()).await()
                }
                
                _syncState.value = _syncState.value.copy(
                    lastSyncTime = Date(),
                    playlistsSynced = playlists.size
                )
                
                true
                
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun syncSettings(settings: SyncSettings): Boolean {
        if (!isNetworkAvailable() || !isSignedIn()) return false
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext false
                
                val docRef = firestore
                    .collection(SYNC_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("user_settings")
                
                val encryptedData = encryptData(gson.toJson(settings))
                docRef.set(mapOf(
                    "data" to encryptedData,
                    "timestamp" to Date(),
                    "version" to SYNC_VERSION
                )).await()
                
                _syncState.value = _syncState.value.copy(
                    lastSyncTime = Date(),
                    settingsSynced = true
                )
                
                true
                
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    suspend fun fetchSettings(): SyncSettings? {
        if (!isNetworkAvailable() || !isSignedIn()) return null
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext null
                
                val doc = firestore
                    .collection(SYNC_COLLECTION)
                    .document(userId)
                    .collection(SETTINGS_COLLECTION)
                    .document("user_settings")
                    .get()
                    .await()
                
                val encryptedData = doc.getString("data") ?: return@withContext null
                val decryptedJson = decryptData(encryptedData)
                gson.fromJson(decryptedJson, SyncSettings::class.java)
                
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    // Real-time sync
    
    fun observeRemoteChanges(): Flow<SyncEvent> = callbackFlow {
        if (!isSignedIn()) {
            close()
            return@callbackFlow
        }
        
        val userId = auth.currentUser?.uid ?: run {
            close()
            return@callbackFlow
        }
        
        syncListener = firestore
            .collection(SYNC_COLLECTION)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(SyncEvent.Error(error.message ?: "Unknown error"))
                    return@addSnapshotListener
                }
                
                snapshot?.metadata?.let { metadata ->
                    if (!metadata.isFromCache && metadata.hasPendingWrites()) {
                        trySend(SyncEvent.RemoteChange)
                    }
                }
            }
        
        awaitClose { syncListener?.remove() }
    }
    
    // Conflict resolution
    
    fun resolveConflict(
        local: SyncableData,
        remote: SyncableData,
        strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.LATEST
    ): SyncableData {
        return when (strategy) {
            ConflictResolutionStrategy.LATEST -> {
                if (local.syncTimestamp > remote.syncTimestamp) local else remote
            }
            ConflictResolutionStrategy.LOCAL -> local
            ConflictResolutionStrategy.REMOTE -> remote
            ConflictResolutionStrategy.MERGE -> {
                // Implement custom merge logic based on data type
                mergeData(local, remote)
            }
        }
    }
    
    private fun mergeData(local: SyncableData, remote: SyncableData): SyncableData {
        // Custom merge logic for different data types
        return when {
            local is SyncWatchProgress && remote is SyncWatchProgress -> {
                // Take the one with more progress
                if (local.position > remote.position) local else remote
            }
            else -> {
                // Default to latest
                if (local.syncTimestamp > remote.syncTimestamp) local else remote
            }
        }
    }
    
    // Backup and Restore
    
    suspend fun createBackup(): BackupData? {
        return withContext(Dispatchers.IO) {
            try {
                BackupData(
                    version = SYNC_VERSION,
                    createdAt = Date(),
                    bookmarks = fetchBookmarks(),
                    watchHistory = fetchWatchHistory(),
                    playlists = emptyList(), // Fetch playlists
                    settings = fetchSettings()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    suspend fun restoreBackup(backup: BackupData): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var success = true
                
                // Restore bookmarks
                if (backup.bookmarks.isNotEmpty()) {
                    success = success && syncBookmarks(backup.bookmarks)
                }
                
                // Restore watch history
                if (backup.watchHistory.isNotEmpty()) {
                    success = success && syncWatchHistory(backup.watchHistory)
                }
                
                // Restore playlists
                if (backup.playlists.isNotEmpty()) {
                    success = success && syncPlaylists(backup.playlists)
                }
                
                // Restore settings
                backup.settings?.let {
                    success = success && syncSettings(it)
                }
                
                success
                
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    // Encryption
    
    private fun initializeEncryption(userId: String?) {
        userId?.let {
            val keySpec = generateKeyFromUserId(it)
            encryptionKey = SecretKeySpec(keySpec, "AES")
        }
    }
    
    private fun generateKeyFromUserId(userId: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(userId.toByteArray()).take(32).toByteArray()
    }
    
    private fun encryptData(data: String): String {
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val iv = ByteArray(16).apply { Random().nextBytes(this) }
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, ivSpec)
        val encrypted = cipher.doFinal(data.toByteArray())
        
        // Combine IV and encrypted data
        val combined = iv + encrypted
        return android.util.Base64.encodeToString(combined, android.util.Base64.DEFAULT)
    }
    
    private fun decryptData(encryptedData: String): String {
        val combined = android.util.Base64.decode(encryptedData, android.util.Base64.DEFAULT)
        
        // Extract IV and encrypted data
        val iv = combined.take(16).toByteArray()
        val encrypted = combined.drop(16).toByteArray()
        
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val ivSpec = IvParameterSpec(iv)
        
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, ivSpec)
        val decrypted = cipher.doFinal(encrypted)
        
        return String(decrypted)
    }
    
    // Utilities
    
    private fun isSignedIn(): Boolean = auth.currentUser != null
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private suspend fun initializeUserData() {
        // Initialize default user data structure in Firestore
        val userId = auth.currentUser?.uid ?: return
        
        withContext(Dispatchers.IO) {
            try {
                val userDoc = firestore
                    .collection(SYNC_COLLECTION)
                    .document(userId)
                
                userDoc.set(mapOf(
                    "createdAt" to Date(),
                    "version" to SYNC_VERSION,
                    "platform" to "Android"
                ), SetOptions.merge()).await()
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    suspend fun deleteAllSyncData(): Boolean {
        if (!isSignedIn()) return false
        
        return withContext(Dispatchers.IO) {
            try {
                val userId = auth.currentUser?.uid ?: return@withContext false
                
                // Delete all subcollections
                val collections = listOf(
                    BOOKMARKS_COLLECTION,
                    WATCH_HISTORY_COLLECTION,
                    PLAYLISTS_COLLECTION,
                    SETTINGS_COLLECTION
                )
                
                collections.forEach { collection ->
                    val docs = firestore
                        .collection(SYNC_COLLECTION)
                        .document(userId)
                        .collection(collection)
                        .get()
                        .await()
                    
                    val batch = firestore.batch()
                    docs.documents.forEach { doc ->
                        batch.delete(doc.reference)
                    }
                    batch.commit().await()
                }
                
                true
                
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    fun getSyncStatus(): SyncStatus {
        return SyncStatus(
            isEnabled = isSignedIn(),
            lastSync = _syncState.value.lastSyncTime,
            pendingChanges = 0, // Would need to track local changes
            totalSynced = with(_syncState.value) {
                bookmarksSynced + watchHistorySynced + playlistsSynced
            }
        )
    }
}

// Data classes

data class SyncState(
    val isSignedIn: Boolean = false,
    val userId: String? = null,
    val userEmail: String? = null,
    val isAnonymous: Boolean = false,
    val lastSyncTime: Date? = null,
    val bookmarksSynced: Int = 0,
    val watchHistorySynced: Int = 0,
    val playlistsSynced: Int = 0,
    val settingsSynced: Boolean = false
)

data class SyncProgress(
    val isSyncing: Boolean = false,
    val currentOperation: String? = null,
    val totalItems: Int = 0,
    val syncedItems: Int = 0,
    val error: String? = null
) {
    val progress: Float
        get() = if (totalItems > 0) syncedItems.toFloat() / totalItems else 0f
}

sealed class SyncEvent {
    object RemoteChange : SyncEvent()
    data class Error(val message: String) : SyncEvent()
}

interface SyncableData {
    val syncTimestamp: Long
}

data class SyncBookmark(
    val id: String,
    val videoHash: String,
    val position: Long,
    val title: String,
    val note: String?,
    override val syncTimestamp: Long
) : SyncableData

data class SyncWatchProgress(
    val videoHash: String,
    val videoTitle: String,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val completed: Boolean,
    override val syncTimestamp: Long
) : SyncableData

data class SyncPlaylist(
    val id: String,
    val name: String,
    val items: List<String>, // Video hashes
    val createdAt: Long,
    val modifiedAt: Long,
    override val syncTimestamp: Long
) : SyncableData

data class SyncSettings(
    val theme: String?,
    val subtitlePreferences: Map<String, Any>?,
    val playbackSettings: Map<String, Any>?,
    val gestures: Map<String, String>?,
    override val syncTimestamp: Long
) : SyncableData

data class BackupData(
    val version: Int,
    val createdAt: Date,
    val bookmarks: List<SyncBookmark>,
    val watchHistory: List<SyncWatchProgress>,
    val playlists: List<SyncPlaylist>,
    val settings: SyncSettings?
)

data class SyncStatus(
    val isEnabled: Boolean,
    val lastSync: Date?,
    val pendingChanges: Int,
    val totalSynced: Int
)

enum class ConflictResolutionStrategy {
    LATEST, LOCAL, REMOTE, MERGE
}