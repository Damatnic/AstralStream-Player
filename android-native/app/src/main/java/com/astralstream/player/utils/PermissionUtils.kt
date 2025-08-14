package com.astralstream.player.utils

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Utility class for handling permissions
 */
object PermissionUtils {

    /**
     * Check if storage permissions are granted
     */
    fun hasStoragePermission(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(activity, Manifest.permission.READ_MEDIA_VIDEO) &&
            hasPermission(activity, Manifest.permission.READ_MEDIA_AUDIO) &&
            hasPermission(activity, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            hasPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is needed
     */
    fun needsManageExternalStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is granted
     */
    fun hasManageExternalStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Request MANAGE_EXTERNAL_STORAGE permission
     */
    fun requestManageExternalStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                activity.startActivity(intent)
            }
        }
    }

    /**
     * Check if camera permission is granted
     */
    fun hasCameraPermission(activity: Activity): Boolean {
        return hasPermission(activity, Manifest.permission.CAMERA)
    }

    /**
     * Check if microphone permission is granted
     */
    fun hasMicrophonePermission(activity: Activity): Boolean {
        return hasPermission(activity, Manifest.permission.RECORD_AUDIO)
    }

    /**
     * Get required storage permissions based on API level
     */
    fun getStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /**
     * Check if a specific permission is granted
     */
    private fun hasPermission(activity: Activity, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Request multiple permissions
     */
    fun requestPermissions(activity: Activity, permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    /**
     * Check if we should show rationale for a permission
     */
    fun shouldShowRequestPermissionRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}