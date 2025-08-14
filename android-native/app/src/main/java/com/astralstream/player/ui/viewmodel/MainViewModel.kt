package com.astralstream.player.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main view model for the application
 */
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Initialize app state
        viewModelScope.launch {
            // Check permissions and setup initial state
            checkPermissions()
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun handlePermissionResults(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        _uiState.value = _uiState.value.copy(
            needsPermissions = !allGranted,
            hasStoragePermission = permissions.any { it.key.contains("STORAGE") || it.key.contains("MEDIA") } == true
        )
    }

    private fun checkPermissions() {
        // Check if permissions are needed
        _uiState.value = _uiState.value.copy(needsPermissions = true)
    }
}

data class MainUiState(
    val isLoading: Boolean = true,
    val needsPermissions: Boolean = false,
    val hasStoragePermission: Boolean = false,
    val error: String? = null
)