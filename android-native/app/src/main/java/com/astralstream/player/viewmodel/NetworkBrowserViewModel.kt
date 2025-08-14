package com.astralstream.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astralstream.player.network.SmbDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NetworkBrowserViewModel @Inject constructor(
    private val smbDataSource: SmbDataSource
) : ViewModel() {

    data class UiState(
        val servers: List<SmbDataSource.SmbServer> = emptyList(),
        val savedServers: List<SmbDataSource.SmbServer> = emptyList(),
        val currentServer: SmbDataSource.SmbServer? = null,
        val currentPath: String = "",
        val files: List<SmbDataSource.SmbFileInfo> = emptyList(),
        val navigationHistory: List<String> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadSavedServers()
        discoverServers()
    }

    fun discoverServers() {
        viewModelScope.launch {
            smbDataSource.discoverServers().collect { result ->
                when (result) {
                    is SmbDataSource.SmbResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                    }
                    is SmbDataSource.SmbResult.Success -> {
                        _uiState.update { 
                            it.copy(
                                servers = result.data,
                                isLoading = false,
                                error = null
                            )
                        }
                    }
                    is SmbDataSource.SmbResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = result.exception.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun connectToServer(server: SmbDataSource.SmbServer) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    currentServer = server,
                    currentPath = "",
                    navigationHistory = listOf(""),
                    isLoading = true,
                    error = null
                )
            }
            
            browseDirectory(server, "")
        }
    }

    fun navigateToPath(path: String) {
        val server = _uiState.value.currentServer ?: return
        
        viewModelScope.launch {
            val history = _uiState.value.navigationHistory.toMutableList()
            if (history.lastOrNull() != path) {
                history.add(path)
            }
            
            _uiState.update { 
                it.copy(
                    currentPath = path,
                    navigationHistory = history,
                    isLoading = true,
                    error = null
                )
            }
            
            browseDirectory(server, path)
        }
    }

    fun navigateBack() {
        val history = _uiState.value.navigationHistory
        if (history.size > 1) {
            val newHistory = history.dropLast(1)
            val previousPath = newHistory.last()
            
            _uiState.update { 
                it.copy(
                    currentPath = previousPath,
                    navigationHistory = newHistory
                )
            }
            
            _uiState.value.currentServer?.let { server ->
                viewModelScope.launch {
                    browseDirectory(server, previousPath)
                }
            }
        } else if (_uiState.value.currentServer != null) {
            // Go back to server list
            _uiState.update { 
                it.copy(
                    currentServer = null,
                    currentPath = "",
                    files = emptyList(),
                    navigationHistory = emptyList()
                )
            }
        }
    }

    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isNotEmpty()) {
            val parentPath = currentPath.substringBeforeLast('/', "")
            navigateToPath(parentPath)
        }
    }

    fun refresh() {
        if (_uiState.value.currentServer != null) {
            val server = _uiState.value.currentServer!!
            val path = _uiState.value.currentPath
            viewModelScope.launch {
                browseDirectory(server, path)
            }
        } else {
            discoverServers()
        }
    }

    fun addServer(server: SmbDataSource.SmbServer) {
        viewModelScope.launch {
            // Test connection first
            _uiState.update { it.copy(isLoading = true) }
            
            if (smbDataSource.testConnection(server)) {
                smbDataSource.saveServer(server)
                loadSavedServers()
                connectToServer(server)
            } else {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to connect to server"
                    )
                }
            }
        }
    }

    fun toggleServerSaved(server: SmbDataSource.SmbServer) {
        val savedServers = _uiState.value.savedServers
        if (savedServers.any { it.address == server.address }) {
            smbDataSource.removeServer(server.address)
        } else {
            smbDataSource.saveServer(server)
        }
        loadSavedServers()
    }

    fun getStreamingUrl(filePath: String): String {
        val server = _uiState.value.currentServer ?: return ""
        return smbDataSource.getStreamingUrl(server, filePath)
    }

    private fun loadSavedServers() {
        _uiState.update { 
            it.copy(savedServers = smbDataSource.getSavedServers())
        }
    }

    private suspend fun browseDirectory(server: SmbDataSource.SmbServer, path: String) {
        smbDataSource.browse(server, path).collect { result ->
            when (result) {
                is SmbDataSource.SmbResult.Loading -> {
                    _uiState.update { it.copy(isLoading = true, error = null) }
                }
                is SmbDataSource.SmbResult.Success -> {
                    _uiState.update { 
                        it.copy(
                            files = result.data,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                is SmbDataSource.SmbResult.Error -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = result.exception.message,
                            files = emptyList()
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        smbDataSource.clearCache()
    }
}