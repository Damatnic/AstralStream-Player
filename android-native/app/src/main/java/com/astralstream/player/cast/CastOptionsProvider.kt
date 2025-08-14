package com.astralstream.player.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions

/**
 * Provides Cast configuration options for the application
 * This class is referenced in AndroidManifest.xml
 */
class CastOptionsProvider : OptionsProvider {
    
    override fun getCastOptions(context: Context): CastOptions {
        val notificationOptions = NotificationOptions.Builder()
            .setActions(
                listOf(
                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    MediaIntentReceiver.ACTION_STOP_CASTING
                ),
                intArrayOf(0, 1)
            )
            .setTargetActivityClassName("com.astralstream.player.ui.player.PlayerActivity")
            .build()
        
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName("com.astralstream.player.cast.ExpandedControlsActivity")
            .build()
        
        return CastOptions.Builder()
            .setReceiverApplicationId(getReceiverApplicationId())
            .setCastMediaOptions(mediaOptions)
            .setEnableReconnectionService(true)
            .setResumeSavedSession(true)
            .build()
    }
    
    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
    
    private fun getReceiverApplicationId(): String {
        // Default Media Receiver app ID
        // For custom receiver app, replace with your app ID
        return "CC1AD845" // Default media receiver
    }
}