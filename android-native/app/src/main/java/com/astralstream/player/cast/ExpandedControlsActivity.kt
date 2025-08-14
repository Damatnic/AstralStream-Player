package com.astralstream.player.cast

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity

/**
 * Expanded controls for Cast playback
 * This activity provides full-screen controls when casting
 */
class ExpandedControlsActivity : ExpandedControllerActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // The ExpandedControllerActivity base class handles all the UI
        // and controls automatically. We just need to extend it.
        // Button configuration is done via the CastOptionsProvider
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        // Let the base class handle the menu
        return true
    }
}