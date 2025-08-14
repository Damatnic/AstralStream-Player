package com.astralstream.player.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeEngine @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val THEMES_DIR = "themes"
        private const val CUSTOM_THEMES_FILE = "custom_themes.json"
        private const val THEME_VERSION = 1
        
        // Default theme colors
        private val DEFAULT_PRIMARY = Color.parseColor("#6200EE")
        private val DEFAULT_SECONDARY = Color.parseColor("#03DAC6")
        private val DEFAULT_TERTIARY = Color.parseColor("#3700B3")
        private val DEFAULT_BACKGROUND = Color.parseColor("#121212")
        private val DEFAULT_SURFACE = Color.parseColor("#1E1E1E")
    }
    
    private val _currentTheme = MutableStateFlow(getDefaultTheme())
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()
    
    private val _availableThemes = MutableStateFlow(loadBuiltInThemes())
    val availableThemes: StateFlow<List<AppTheme>> = _availableThemes.asStateFlow()
    
    private val _customThemes = MutableStateFlow<List<CustomTheme>>(emptyList())
    val customThemes: StateFlow<List<CustomTheme>> = _customThemes.asStateFlow()
    
    private val _dynamicColorEnabled = MutableStateFlow(false)
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()
    
    private val imageCache = mutableMapOf<String, Bitmap>()
    
    init {
        loadCustomThemes()
        loadSavedThemePreference()
    }
    
    // Initialization
    
    fun initialize() {
        // Initialize theme engine
        loadCustomThemes()
        loadSavedThemePreference()
    }
    
    fun clearImageCache() {
        imageCache.clear()
    }
    
    // Theme Application
    
    fun applyTheme(theme: AppTheme) {
        _currentTheme.value = theme
        saveThemePreference(theme.id)
    }
    
    fun applyThemeById(themeId: String) {
        val theme = findThemeById(themeId)
        theme?.let { applyTheme(it) }
    }
    
    fun applyDynamicTheme(bitmap: Bitmap) {
        val palette = Palette.from(bitmap).generate()
        
        val vibrantSwatch = palette.vibrantSwatch
        val darkVibrantSwatch = palette.darkVibrantSwatch
        val lightVibrantSwatch = palette.lightVibrantSwatch
        val mutedSwatch = palette.mutedSwatch
        val darkMutedSwatch = palette.darkMutedSwatch
        
        val dynamicTheme = AppTheme(
            id = "dynamic_${System.currentTimeMillis()}",
            name = "Dynamic",
            description = "Generated from video",
            isDark = true,
            colors = ThemeColors(
                primary = vibrantSwatch?.rgb ?: DEFAULT_PRIMARY,
                onPrimary = vibrantSwatch?.bodyTextColor ?: Color.WHITE,
                primaryContainer = darkVibrantSwatch?.rgb ?: DEFAULT_PRIMARY,
                onPrimaryContainer = darkVibrantSwatch?.bodyTextColor ?: Color.WHITE,
                
                secondary = lightVibrantSwatch?.rgb ?: DEFAULT_SECONDARY,
                onSecondary = lightVibrantSwatch?.bodyTextColor ?: Color.BLACK,
                secondaryContainer = mutedSwatch?.rgb ?: DEFAULT_SECONDARY,
                onSecondaryContainer = mutedSwatch?.bodyTextColor ?: Color.WHITE,
                
                tertiary = darkMutedSwatch?.rgb ?: DEFAULT_TERTIARY,
                onTertiary = darkMutedSwatch?.bodyTextColor ?: Color.WHITE,
                
                background = Color.parseColor("#121212"),
                onBackground = Color.WHITE,
                surface = darkMutedSwatch?.rgb ?: DEFAULT_SURFACE,
                onSurface = Color.WHITE,
                
                error = Color.parseColor("#CF6679"),
                onError = Color.BLACK
            ),
            isDynamic = true
        )
        
        applyTheme(dynamicTheme)
    }
    
    fun toggleDynamicColor(enabled: Boolean) {
        _dynamicColorEnabled.value = enabled
        if (!enabled) {
            // Revert to saved theme
            loadSavedThemePreference()
        }
    }
    
    // Custom Theme Creation
    
    suspend fun createCustomTheme(
        name: String,
        baseTheme: AppTheme? = null,
        colors: ThemeColors? = null
    ): CustomTheme {
        return withContext(Dispatchers.IO) {
            val customTheme = CustomTheme(
                id = "custom_${System.currentTimeMillis()}",
                name = name,
                baseThemeId = baseTheme?.id,
                colorOverrides = colors?.toMap() ?: emptyMap(),
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis()
            )
            
            val updatedList = _customThemes.value + customTheme
            _customThemes.value = updatedList
            saveCustomThemes(updatedList)
            
            customTheme
        }
    }
    
    suspend fun updateCustomTheme(theme: CustomTheme) {
        withContext(Dispatchers.IO) {
            val updatedList = _customThemes.value.map { 
                if (it.id == theme.id) {
                    theme.copy(modifiedAt = System.currentTimeMillis())
                } else it
            }
            _customThemes.value = updatedList
            saveCustomThemes(updatedList)
        }
    }
    
    suspend fun deleteCustomTheme(themeId: String) {
        withContext(Dispatchers.IO) {
            val updatedList = _customThemes.value.filter { it.id != themeId }
            _customThemes.value = updatedList
            saveCustomThemes(updatedList)
        }
    }
    
    // Theme Import/Export
    
    suspend fun exportTheme(theme: AppTheme): String {
        return withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("version", THEME_VERSION)
                put("id", theme.id)
                put("name", theme.name)
                put("description", theme.description)
                put("isDark", theme.isDark)
                put("colors", theme.colors.toJson())
                put("typography", theme.typography?.toJson())
                put("shapes", theme.shapes?.toJson())
            }
            json.toString(2)
        }
    }
    
    suspend fun importTheme(jsonString: String): AppTheme? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(jsonString)
                
                if (json.getInt("version") != THEME_VERSION) {
                    return@withContext null
                }
                
                val colors = ThemeColors.fromJson(json.getJSONObject("colors"))
                val typography = json.optJSONObject("typography")?.let {
                    ThemeTypography.fromJson(it)
                }
                val shapes = json.optJSONObject("shapes")?.let {
                    ThemeShapes.fromJson(it)
                }
                
                AppTheme(
                    id = "imported_${System.currentTimeMillis()}",
                    name = json.getString("name"),
                    description = json.optString("description"),
                    isDark = json.getBoolean("isDark"),
                    colors = colors,
                    typography = typography,
                    shapes = shapes
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    suspend fun exportAllThemes(): String {
        return withContext(Dispatchers.IO) {
            val allThemes = _availableThemes.value + _customThemes.value.map { it.toAppTheme() }
            val json = JSONObject().apply {
                put("version", THEME_VERSION)
                put("exportDate", System.currentTimeMillis())
                put("themes", allThemes.map { theme ->
                    JSONObject().apply {
                        put("id", theme.id)
                        put("name", theme.name)
                        put("data", exportTheme(theme))
                    }
                })
            }
            json.toString(2)
        }
    }
    
    // Color Generation
    
    fun generateColorScheme(baseColor: Int, isDark: Boolean = true): ThemeColors {
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(baseColor, hsl)
        
        return if (isDark) {
            generateDarkColorScheme(hsl)
        } else {
            generateLightColorScheme(hsl)
        }
    }
    
    private fun generateDarkColorScheme(hsl: FloatArray): ThemeColors {
        val primary = hslToColor(hsl[0], 0.6f, 0.5f)
        val secondary = hslToColor((hsl[0] + 120) % 360, 0.5f, 0.4f)
        val tertiary = hslToColor((hsl[0] + 240) % 360, 0.4f, 0.35f)
        
        return ThemeColors(
            primary = primary,
            onPrimary = Color.WHITE,
            primaryContainer = hslToColor(hsl[0], 0.5f, 0.3f),
            onPrimaryContainer = hslToColor(hsl[0], 0.2f, 0.9f),
            
            secondary = secondary,
            onSecondary = Color.WHITE,
            secondaryContainer = hslToColor((hsl[0] + 120) % 360, 0.4f, 0.25f),
            onSecondaryContainer = hslToColor((hsl[0] + 120) % 360, 0.2f, 0.85f),
            
            tertiary = tertiary,
            onTertiary = Color.WHITE,
            
            background = Color.parseColor("#121212"),
            onBackground = Color.parseColor("#E0E0E0"),
            surface = Color.parseColor("#1E1E1E"),
            onSurface = Color.parseColor("#E0E0E0"),
            
            error = Color.parseColor("#CF6679"),
            onError = Color.BLACK
        )
    }
    
    private fun generateLightColorScheme(hsl: FloatArray): ThemeColors {
        val primary = hslToColor(hsl[0], 0.8f, 0.4f)
        val secondary = hslToColor((hsl[0] + 120) % 360, 0.7f, 0.5f)
        val tertiary = hslToColor((hsl[0] + 240) % 360, 0.6f, 0.45f)
        
        return ThemeColors(
            primary = primary,
            onPrimary = Color.WHITE,
            primaryContainer = hslToColor(hsl[0], 0.3f, 0.9f),
            onPrimaryContainer = hslToColor(hsl[0], 0.9f, 0.2f),
            
            secondary = secondary,
            onSecondary = Color.WHITE,
            secondaryContainer = hslToColor((hsl[0] + 120) % 360, 0.3f, 0.85f),
            onSecondaryContainer = hslToColor((hsl[0] + 120) % 360, 0.8f, 0.25f),
            
            tertiary = tertiary,
            onTertiary = Color.WHITE,
            
            background = Color.WHITE,
            onBackground = Color.parseColor("#1C1C1C"),
            surface = Color.parseColor("#F5F5F5"),
            onSurface = Color.parseColor("#1C1C1C"),
            
            error = Color.parseColor("#BA1A1A"),
            onError = Color.WHITE
        )
    }
    
    private fun hslToColor(h: Float, s: Float, l: Float): Int {
        val c = (1 - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1 - kotlin.math.abs((h / 60) % 2 - 1))
        val m = l - c / 2
        
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        
        return Color.rgb(
            ((r + m) * 255).toInt(),
            ((g + m) * 255).toInt(),
            ((b + m) * 255).toInt()
        )
    }
    
    // Built-in Themes
    
    private fun loadBuiltInThemes(): List<AppTheme> {
        return listOf(
            getDefaultTheme(),
            getAmoledTheme(),
            getMaterialYouTheme(),
            getCyberpunkTheme(),
            getNatureTheme(),
            getRetroTheme(),
            getMinimalTheme(),
            getHighContrastTheme()
        )
    }
    
    private fun getDefaultTheme() = AppTheme(
        id = "default",
        name = "Default",
        description = "Material Design 3 default theme",
        isDark = true,
        colors = ThemeColors(
            primary = DEFAULT_PRIMARY,
            onPrimary = Color.WHITE,
            primaryContainer = Color.parseColor("#3700B3"),
            onPrimaryContainer = Color.WHITE,
            secondary = DEFAULT_SECONDARY,
            onSecondary = Color.BLACK,
            secondaryContainer = Color.parseColor("#018786"),
            onSecondaryContainer = Color.WHITE,
            tertiary = DEFAULT_TERTIARY,
            onTertiary = Color.WHITE,
            background = DEFAULT_BACKGROUND,
            onBackground = Color.WHITE,
            surface = DEFAULT_SURFACE,
            onSurface = Color.WHITE,
            error = Color.parseColor("#CF6679"),
            onError = Color.BLACK
        )
    )
    
    private fun getAmoledTheme() = AppTheme(
        id = "amoled",
        name = "AMOLED Black",
        description = "Pure black theme for AMOLED displays",
        isDark = true,
        colors = ThemeColors(
            primary = Color.parseColor("#BB86FC"),
            onPrimary = Color.BLACK,
            primaryContainer = Color.parseColor("#6200EE"),
            onPrimaryContainer = Color.WHITE,
            secondary = Color.parseColor("#03DAC6"),
            onSecondary = Color.BLACK,
            secondaryContainer = Color.parseColor("#018786"),
            onSecondaryContainer = Color.WHITE,
            tertiary = Color.parseColor("#FFB74D"),
            onTertiary = Color.BLACK,
            background = Color.BLACK,
            onBackground = Color.WHITE,
            surface = Color.BLACK,
            onSurface = Color.WHITE,
            error = Color.parseColor("#CF6679"),
            onError = Color.BLACK
        )
    )
    
    private fun getMaterialYouTheme() = AppTheme(
        id = "material_you",
        name = "Material You",
        description = "Dynamic color theme",
        isDark = true,
        colors = ThemeColors(
            primary = Color.parseColor("#D0BCFF"),
            onPrimary = Color.parseColor("#381E72"),
            primaryContainer = Color.parseColor("#4F378B"),
            onPrimaryContainer = Color.parseColor("#EADDFF"),
            secondary = Color.parseColor("#CCC2DC"),
            onSecondary = Color.parseColor("#332D41"),
            secondaryContainer = Color.parseColor("#4A4458"),
            onSecondaryContainer = Color.parseColor("#E8DEF8"),
            tertiary = Color.parseColor("#EFB8C8"),
            onTertiary = Color.parseColor("#492532"),
            background = Color.parseColor("#1C1B1F"),
            onBackground = Color.parseColor("#E6E1E5"),
            surface = Color.parseColor("#1C1B1F"),
            onSurface = Color.parseColor("#E6E1E5"),
            error = Color.parseColor("#F2B8B5"),
            onError = Color.parseColor("#601410")
        )
    )
    
    private fun getCyberpunkTheme() = AppTheme(
        id = "cyberpunk",
        name = "Cyberpunk",
        description = "Neon-lit futuristic theme",
        isDark = true,
        colors = ThemeColors(
            primary = Color.parseColor("#FF00FF"),
            onPrimary = Color.BLACK,
            primaryContainer = Color.parseColor("#CC00CC"),
            onPrimaryContainer = Color.WHITE,
            secondary = Color.parseColor("#00FFFF"),
            onSecondary = Color.BLACK,
            secondaryContainer = Color.parseColor("#00CCCC"),
            onSecondaryContainer = Color.WHITE,
            tertiary = Color.parseColor("#FFFF00"),
            onTertiary = Color.BLACK,
            background = Color.parseColor("#0A0A0A"),
            onBackground = Color.parseColor("#00FF00"),
            surface = Color.parseColor("#1A0A1A"),
            onSurface = Color.parseColor("#00FF00"),
            error = Color.parseColor("#FF0000"),
            onError = Color.WHITE
        )
    )
    
    private fun getNatureTheme() = AppTheme(
        id = "nature",
        name = "Nature",
        description = "Earthy green theme",
        isDark = true,
        colors = ThemeColors(
            primary = Color.parseColor("#4CAF50"),
            onPrimary = Color.WHITE,
            primaryContainer = Color.parseColor("#2E7D32"),
            onPrimaryContainer = Color.WHITE,
            secondary = Color.parseColor("#8BC34A"),
            onSecondary = Color.BLACK,
            secondaryContainer = Color.parseColor("#689F38"),
            onSecondaryContainer = Color.WHITE,
            tertiary = Color.parseColor("#FFC107"),
            onTertiary = Color.BLACK,
            background = Color.parseColor("#1B2E1B"),
            onBackground = Color.parseColor("#E8F5E9"),
            surface = Color.parseColor("#263826"),
            onSurface = Color.parseColor("#E8F5E9"),
            error = Color.parseColor("#FF5252"),
            onError = Color.WHITE
        )
    )
    
    private fun getRetroTheme() = AppTheme(
        id = "retro",
        name = "Retro",
        description = "80s inspired theme",
        isDark = true,
        colors = ThemeColors(
            primary = Color.parseColor("#FF6B6B"),
            onPrimary = Color.WHITE,
            primaryContainer = Color.parseColor("#C44569"),
            onPrimaryContainer = Color.WHITE,
            secondary = Color.parseColor("#4ECDC4"),
            onSecondary = Color.BLACK,
            secondaryContainer = Color.parseColor("#45B7B8"),
            onSecondaryContainer = Color.WHITE,
            tertiary = Color.parseColor("#FFE66D"),
            onTertiary = Color.BLACK,
            background = Color.parseColor("#2A1A3E"),
            onBackground = Color.parseColor("#F7F7F7"),
            surface = Color.parseColor("#44318D"),
            onSurface = Color.parseColor("#F7F7F7"),
            error = Color.parseColor("#FF1744"),
            onError = Color.WHITE
        )
    )
    
    private fun getMinimalTheme() = AppTheme(
        id = "minimal",
        name = "Minimal",
        description = "Clean and simple",
        isDark = false,
        colors = ThemeColors(
            primary = Color.parseColor("#000000"),
            onPrimary = Color.WHITE,
            primaryContainer = Color.parseColor("#333333"),
            onPrimaryContainer = Color.WHITE,
            secondary = Color.parseColor("#666666"),
            onSecondary = Color.WHITE,
            secondaryContainer = Color.parseColor("#999999"),
            onSecondaryContainer = Color.BLACK,
            tertiary = Color.parseColor("#CCCCCC"),
            onTertiary = Color.BLACK,
            background = Color.WHITE,
            onBackground = Color.BLACK,
            surface = Color.parseColor("#F5F5F5"),
            onSurface = Color.BLACK,
            error = Color.parseColor("#D32F2F"),
            onError = Color.WHITE
        )
    )
    
    private fun getHighContrastTheme() = AppTheme(
        id = "high_contrast",
        name = "High Contrast",
        description = "Maximum readability",
        isDark = true,
        colors = ThemeColors(
            primary = Color.WHITE,
            onPrimary = Color.BLACK,
            primaryContainer = Color.parseColor("#CCCCCC"),
            onPrimaryContainer = Color.BLACK,
            secondary = Color.YELLOW,
            onSecondary = Color.BLACK,
            secondaryContainer = Color.parseColor("#FFEB3B"),
            onSecondaryContainer = Color.BLACK,
            tertiary = Color.CYAN,
            onTertiary = Color.BLACK,
            background = Color.BLACK,
            onBackground = Color.WHITE,
            surface = Color.parseColor("#111111"),
            onSurface = Color.WHITE,
            error = Color.RED,
            onError = Color.WHITE
        )
    )
    
    // Persistence
    
    private fun loadCustomThemes() {
        val file = File(context.filesDir, CUSTOM_THEMES_FILE)
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                val themesArray = json.getJSONArray("themes")
                val themes = mutableListOf<CustomTheme>()
                
                for (i in 0 until themesArray.length()) {
                    val themeJson = themesArray.getJSONObject(i)
                    themes.add(CustomTheme.fromJson(themeJson))
                }
                
                _customThemes.value = themes
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun saveCustomThemes(themes: List<CustomTheme>) {
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("version", THEME_VERSION)
                    put("themes", themes.map { it.toJson() })
                }
                
                val file = File(context.filesDir, CUSTOM_THEMES_FILE)
                file.writeText(json.toString(2))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun saveThemePreference(themeId: String) {
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("current_theme", themeId)
            .apply()
    }
    
    private fun loadSavedThemePreference() {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val themeId = prefs.getString("current_theme", "default") ?: "default"
        
        val theme = findThemeById(themeId) ?: getDefaultTheme()
        _currentTheme.value = theme
    }
    
    private fun findThemeById(id: String): AppTheme? {
        return _availableThemes.value.find { it.id == id }
            ?: _customThemes.value.find { it.id == id }?.toAppTheme()
    }
    
    // Compose Integration
    
    fun toComposeColorScheme(theme: AppTheme): ColorScheme {
        return if (theme.isDark) {
            darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(theme.colors.primary),
                onPrimary = androidx.compose.ui.graphics.Color(theme.colors.onPrimary),
                primaryContainer = androidx.compose.ui.graphics.Color(theme.colors.primaryContainer),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(theme.colors.onPrimaryContainer),
                secondary = androidx.compose.ui.graphics.Color(theme.colors.secondary),
                onSecondary = androidx.compose.ui.graphics.Color(theme.colors.onSecondary),
                secondaryContainer = androidx.compose.ui.graphics.Color(theme.colors.secondaryContainer),
                onSecondaryContainer = androidx.compose.ui.graphics.Color(theme.colors.onSecondaryContainer),
                tertiary = androidx.compose.ui.graphics.Color(theme.colors.tertiary),
                onTertiary = androidx.compose.ui.graphics.Color(theme.colors.onTertiary),
                background = androidx.compose.ui.graphics.Color(theme.colors.background),
                onBackground = androidx.compose.ui.graphics.Color(theme.colors.onBackground),
                surface = androidx.compose.ui.graphics.Color(theme.colors.surface),
                onSurface = androidx.compose.ui.graphics.Color(theme.colors.onSurface),
                error = androidx.compose.ui.graphics.Color(theme.colors.error),
                onError = androidx.compose.ui.graphics.Color(theme.colors.onError)
            )
        } else {
            lightColorScheme(
                primary = androidx.compose.ui.graphics.Color(theme.colors.primary),
                onPrimary = androidx.compose.ui.graphics.Color(theme.colors.onPrimary),
                primaryContainer = androidx.compose.ui.graphics.Color(theme.colors.primaryContainer),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(theme.colors.onPrimaryContainer),
                secondary = androidx.compose.ui.graphics.Color(theme.colors.secondary),
                onSecondary = androidx.compose.ui.graphics.Color(theme.colors.onSecondary),
                secondaryContainer = androidx.compose.ui.graphics.Color(theme.colors.secondaryContainer),
                onSecondaryContainer = androidx.compose.ui.graphics.Color(theme.colors.onSecondaryContainer),
                tertiary = androidx.compose.ui.graphics.Color(theme.colors.tertiary),
                onTertiary = androidx.compose.ui.graphics.Color(theme.colors.onTertiary),
                background = androidx.compose.ui.graphics.Color(theme.colors.background),
                onBackground = androidx.compose.ui.graphics.Color(theme.colors.onBackground),
                surface = androidx.compose.ui.graphics.Color(theme.colors.surface),
                onSurface = androidx.compose.ui.graphics.Color(theme.colors.onSurface),
                error = androidx.compose.ui.graphics.Color(theme.colors.error),
                onError = androidx.compose.ui.graphics.Color(theme.colors.onError)
            )
        }
    }
}

// Data Classes

data class AppTheme(
    val id: String,
    val name: String,
    val description: String? = null,
    val isDark: Boolean,
    val colors: ThemeColors,
    val typography: ThemeTypography? = null,
    val shapes: ThemeShapes? = null,
    val isDynamic: Boolean = false
)

data class ThemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val error: Int,
    val onError: Int
) {
    fun toMap(): Map<String, Int> = mapOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "error" to error,
        "onError" to onError
    )
    
    fun toJson(): JSONObject = JSONObject(toMap())
    
    companion object {
        fun fromJson(json: JSONObject): ThemeColors = ThemeColors(
            primary = json.getInt("primary"),
            onPrimary = json.getInt("onPrimary"),
            primaryContainer = json.getInt("primaryContainer"),
            onPrimaryContainer = json.getInt("onPrimaryContainer"),
            secondary = json.getInt("secondary"),
            onSecondary = json.getInt("onSecondary"),
            secondaryContainer = json.getInt("secondaryContainer"),
            onSecondaryContainer = json.getInt("onSecondaryContainer"),
            tertiary = json.getInt("tertiary"),
            onTertiary = json.getInt("onTertiary"),
            background = json.getInt("background"),
            onBackground = json.getInt("onBackground"),
            surface = json.getInt("surface"),
            onSurface = json.getInt("onSurface"),
            error = json.getInt("error"),
            onError = json.getInt("onError")
        )
    }
}

data class ThemeTypography(
    val fontFamily: String? = null,
    val headingSize: Float = 1.0f,
    val bodySize: Float = 1.0f,
    val captionSize: Float = 1.0f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fontFamily", fontFamily)
        put("headingSize", headingSize)
        put("bodySize", bodySize)
        put("captionSize", captionSize)
    }
    
    companion object {
        fun fromJson(json: JSONObject): ThemeTypography = ThemeTypography(
            fontFamily = json.optString("fontFamily"),
            headingSize = json.optDouble("headingSize", 1.0).toFloat(),
            bodySize = json.optDouble("bodySize", 1.0).toFloat(),
            captionSize = json.optDouble("captionSize", 1.0).toFloat()
        )
    }
}

data class ThemeShapes(
    val cornerRadius: Float = 8f,
    val buttonRadius: Float = 4f,
    val cardRadius: Float = 12f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cornerRadius", cornerRadius)
        put("buttonRadius", buttonRadius)
        put("cardRadius", cardRadius)
    }
    
    companion object {
        fun fromJson(json: JSONObject): ThemeShapes = ThemeShapes(
            cornerRadius = json.optDouble("cornerRadius", 8.0).toFloat(),
            buttonRadius = json.optDouble("buttonRadius", 4.0).toFloat(),
            cardRadius = json.optDouble("cardRadius", 12.0).toFloat()
        )
    }
}

data class CustomTheme(
    val id: String,
    val name: String,
    val baseThemeId: String? = null,
    val colorOverrides: Map<String, Int> = emptyMap(),
    val createdAt: Long,
    val modifiedAt: Long
) {
    fun toAppTheme(): AppTheme {
        // Create theme from base + overrides
        return AppTheme(
            id = id,
            name = name,
            description = "Custom theme",
            isDark = true, // Determine from colors
            colors = ThemeColors(
                primary = colorOverrides["primary"] ?: DEFAULT_PRIMARY,
                onPrimary = colorOverrides["onPrimary"] ?: Color.WHITE,
                primaryContainer = colorOverrides["primaryContainer"] ?: DEFAULT_PRIMARY,
                onPrimaryContainer = colorOverrides["onPrimaryContainer"] ?: Color.WHITE,
                secondary = colorOverrides["secondary"] ?: DEFAULT_SECONDARY,
                onSecondary = colorOverrides["onSecondary"] ?: Color.BLACK,
                secondaryContainer = colorOverrides["secondaryContainer"] ?: DEFAULT_SECONDARY,
                onSecondaryContainer = colorOverrides["onSecondaryContainer"] ?: Color.WHITE,
                tertiary = colorOverrides["tertiary"] ?: DEFAULT_TERTIARY,
                onTertiary = colorOverrides["onTertiary"] ?: Color.WHITE,
                background = colorOverrides["background"] ?: DEFAULT_BACKGROUND,
                onBackground = colorOverrides["onBackground"] ?: Color.WHITE,
                surface = colorOverrides["surface"] ?: DEFAULT_SURFACE,
                onSurface = colorOverrides["onSurface"] ?: Color.WHITE,
                error = colorOverrides["error"] ?: Color.parseColor("#CF6679"),
                onError = colorOverrides["onError"] ?: Color.BLACK
            )
        )
    }
    
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseThemeId", baseThemeId)
        put("colorOverrides", JSONObject(colorOverrides))
        put("createdAt", createdAt)
        put("modifiedAt", modifiedAt)
    }
    
    companion object {
        fun fromJson(json: JSONObject): CustomTheme {
            val overridesJson = json.getJSONObject("colorOverrides")
            val overrides = mutableMapOf<String, Int>()
            overridesJson.keys().forEach { key ->
                overrides[key] = overridesJson.getInt(key)
            }
            
            return CustomTheme(
                id = json.getString("id"),
                name = json.getString("name"),
                baseThemeId = json.optString("baseThemeId"),
                colorOverrides = overrides,
                createdAt = json.getLong("createdAt"),
                modifiedAt = json.getLong("modifiedAt")
            )
        }
    }
}

// Companion objects for default colors
private const val DEFAULT_PRIMARY = -10185482
private const val DEFAULT_SECONDARY = -14575885
private const val DEFAULT_TERTIARY = -12627531
private const val DEFAULT_BACKGROUND = -15132391
private const val DEFAULT_SURFACE = -14605799