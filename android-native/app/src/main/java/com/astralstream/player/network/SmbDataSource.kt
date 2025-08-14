package com.astralstream.player.network

import android.net.Uri
import jcifs.CIFSContext
import jcifs.CIFSException
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMB/CIFS data source for accessing network shares
 * Supports SMB1, SMB2, and SMB3 protocols
 */
@Singleton
class SmbDataSource @Inject constructor() {

    data class SmbCredentials(
        val domain: String = "",
        val username: String,
        val password: String
    )

    data class SmbServer(
        val name: String,
        val address: String,
        val credentials: SmbCredentials? = null,
        val lastConnected: Long = 0
    )

    data class SmbFileInfo(
        val name: String,
        val path: String,
        val isDirectory: Boolean,
        val size: Long = 0,
        val lastModified: Long = 0,
        val isHidden: Boolean = false,
        val canRead: Boolean = true,
        val canWrite: Boolean = false,
        val mimeType: String? = null
    )

    sealed class SmbResult<out T> {
        data class Success<T>(val data: T) : SmbResult<T>()
        data class Error(val exception: Exception) : SmbResult<Nothing>()
        object Loading : SmbResult<Nothing>()
    }

    private val contexts = mutableMapOf<String, CIFSContext>()
    private val savedServers = mutableListOf<SmbServer>()

    /**
     * Create SMB context with authentication
     */
    private fun createContext(server: SmbServer): CIFSContext {
        val key = "${server.address}_${server.credentials?.username}"
        
        return contexts.getOrPut(key) {
            val props = Properties().apply {
                // SMB configuration
                setProperty("jcifs.smb.client.minVersion", "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.responseTimeout", "30000")
                setProperty("jcifs.smb.client.soTimeout", "35000")
                setProperty("jcifs.smb.client.connTimeout", "10000")
                setProperty("jcifs.smb.client.sessionTimeout", "60000")
                setProperty("jcifs.smb.client.dfs.disabled", "true")
                setProperty("jcifs.smb.client.useExtendedSecurity", "true")
                setProperty("jcifs.smb.client.useSMB2Negotiation", "true")
                setProperty("jcifs.smb.client.enableSMB2", "true")
                setProperty("jcifs.smb.client.signingPreferred", "true")
                
                // Performance tuning
                setProperty("jcifs.smb.client.rcv_buf_size", "65536")
                setProperty("jcifs.smb.client.snd_buf_size", "65536")
                setProperty("jcifs.smb.client.transaction_buf_size", "65536")
                
                // Compatibility settings
                setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
                setProperty("jcifs.smb.client.encryptionEnabled", "false")
            }
            
            val baseContext = BaseContext(PropertyConfiguration(props))
            
            if (server.credentials != null) {
                val auth = NtlmPasswordAuthenticator(
                    server.credentials.domain.ifEmpty { null },
                    server.credentials.username,
                    server.credentials.password
                )
                baseContext.withCredentials(auth)
            } else {
                baseContext.withGuestCrendentials()
            }
        }
    }

    /**
     * Browse SMB server or directory
     */
    suspend fun browse(
        server: SmbServer,
        path: String = ""
    ): Flow<SmbResult<List<SmbFileInfo>>> = flow {
        emit(SmbResult.Loading)
        
        try {
            val files = withContext(Dispatchers.IO) {
                val context = createContext(server)
                val smbPath = buildSmbPath(server.address, path)
                val smbFile = SmbFile(smbPath, context)
                
                if (!smbFile.exists()) {
                    throw IOException("Path does not exist: $smbPath")
                }
                
                if (!smbFile.isDirectory) {
                    throw IOException("Path is not a directory: $smbPath")
                }
                
                smbFile.listFiles()?.map { file ->
                    SmbFileInfo(
                        name = file.name.removeSuffix("/"),
                        path = file.path,
                        isDirectory = file.isDirectory,
                        size = if (file.isFile) file.length() else 0,
                        lastModified = file.lastModified,
                        isHidden = file.isHidden,
                        canRead = file.canRead(),
                        canWrite = file.canWrite(),
                        mimeType = if (file.isFile) getMimeType(file.name) else null
                    )
                }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                    ?: emptyList()
            }
            
            emit(SmbResult.Success(files))
        } catch (e: CIFSException) {
            emit(SmbResult.Error(IOException("SMB error: ${e.message}", e)))
        } catch (e: Exception) {
            emit(SmbResult.Error(e))
        }
    }

    /**
     * Discover SMB servers on the network
     */
    suspend fun discoverServers(): Flow<SmbResult<List<SmbServer>>> = flow {
        emit(SmbResult.Loading)
        
        try {
            val servers = withContext(Dispatchers.IO) {
                val context = BaseContext(PropertyConfiguration(Properties())).withGuestCrendentials()
                
                // Browse workgroup/domain
                val workgroups = try {
                    SmbFile("smb://", context).listFiles()
                } catch (e: Exception) {
                    emptyArray()
                }
                
                val serverList = mutableListOf<SmbServer>()
                
                workgroups?.forEach { workgroup ->
                    try {
                        workgroup.listFiles()?.forEach { server ->
                            serverList.add(
                                SmbServer(
                                    name = server.name.removeSuffix("/"),
                                    address = server.canonicalPath.removePrefix("smb://").removeSuffix("/")
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Skip inaccessible workgroups
                    }
                }
                
                // Add saved servers
                serverList.addAll(savedServers)
                
                serverList.distinctBy { it.address }
            }
            
            emit(SmbResult.Success(servers))
        } catch (e: Exception) {
            emit(SmbResult.Error(e))
        }
    }

    /**
     * Test SMB connection
     */
    suspend fun testConnection(server: SmbServer): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val context = createContext(server)
                val smbPath = buildSmbPath(server.address, "")
                val smbFile = SmbFile(smbPath, context)
                smbFile.exists()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Open SMB file as input stream
     */
    suspend fun openFile(
        server: SmbServer,
        filePath: String
    ): InputStream? {
        return withContext(Dispatchers.IO) {
            try {
                val context = createContext(server)
                val smbFile = SmbFile(filePath, context)
                
                if (!smbFile.exists() || !smbFile.isFile) {
                    null
                } else {
                    smbFile.inputStream
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Copy SMB file to local storage for playback
     */
    suspend fun cacheFile(
        server: SmbServer,
        smbFilePath: String,
        localFile: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val context = createContext(server)
                val smbFile = SmbFile(smbFilePath, context)
                
                if (!smbFile.exists() || !smbFile.isFile) {
                    return@withContext false
                }
                
                val totalSize = smbFile.length()
                var downloadedSize = 0L
                
                smbFile.inputStream.use { input ->
                    FileOutputStream(localFile).use { output ->
                        val buffer = ByteArray(65536) // 64KB buffer
                        var bytesRead: Int
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedSize += bytesRead
                            onProgress(downloadedSize, totalSize)
                        }
                    }
                }
                
                true
            } catch (e: Exception) {
                localFile.delete()
                false
            }
        }
    }

    /**
     * Get SMB streaming URL for direct playback
     */
    fun getStreamingUrl(
        server: SmbServer,
        filePath: String
    ): String {
        val credentials = server.credentials
        return if (credentials != null) {
            val auth = "${credentials.username}:${credentials.password}"
            val domain = if (credentials.domain.isNotEmpty()) "${credentials.domain};" else ""
            "smb://$domain$auth@${server.address}${filePath.removePrefix("smb://")}"
        } else {
            buildSmbPath(server.address, filePath)
        }
    }

    /**
     * Build SMB path from components
     */
    private fun buildSmbPath(serverAddress: String, path: String): String {
        val cleanAddress = serverAddress.removePrefix("smb://").removeSuffix("/")
        val cleanPath = path.removePrefix("/").removePrefix("smb://")
        
        return when {
            cleanPath.isEmpty() -> "smb://$cleanAddress/"
            cleanPath.startsWith(cleanAddress) -> "smb://$cleanPath"
            else -> "smb://$cleanAddress/$cleanPath"
        }
    }

    /**
     * Get MIME type from file extension
     */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "mpg", "mpeg" -> "video/mpeg"
            "3gp" -> "video/3gpp"
            "ts", "m2ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "srt", "ass", "vtt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    /**
     * Save server to favorites
     */
    fun saveServer(server: SmbServer) {
        savedServers.removeAll { it.address == server.address }
        savedServers.add(0, server.copy(lastConnected = System.currentTimeMillis()))
    }

    /**
     * Remove server from favorites
     */
    fun removeServer(address: String) {
        savedServers.removeAll { it.address == address }
    }

    /**
     * Get saved servers
     */
    fun getSavedServers(): List<SmbServer> = savedServers.toList()

    /**
     * Clear cached contexts
     */
    fun clearCache() {
        contexts.clear()
    }
}