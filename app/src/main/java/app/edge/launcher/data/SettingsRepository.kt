package app.edge.launcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "edge")

data class EdgeSettings(
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    val topEnabled: Boolean = true,
    val bottomEnabled: Boolean = true,
    /** Width of the left/right touch strips. */
    val sideWidthDp: Int = 12,
    /** Vertical span of the side strips, as fractions of screen height. */
    val leftStart: Float = 0.04f,
    val leftEnd: Float = 0.66f,
    val rightStart: Float = 0.04f,
    val rightEnd: Float = 0.66f,
    val topHeightDp: Int = 18,
    val bottomHeightDp: Int = 18,
    /** Lifts the bottom strip above the system's own home-gesture area. */
    val bottomOffsetDp: Int = 0,
    /** How far a finger must travel before a swipe counts. */
    val triggerDistanceDp: Int = 28,
    val dockIconDp: Int = 52,
    /** Right-edge swipes past this fraction of screen width open the spread. */
    val longSwipeFraction: Float = 0.4f,
    val hideInFullscreen: Boolean = true,
    val hideWithKeyboard: Boolean = true,
    val pinned: List<String> = emptyList(),
    val pinnedInitialized: Boolean = false,
    val onboardingDone: Boolean = false,
)

object Keys {
    val leftEnabled = booleanPreferencesKey("left_enabled")
    val rightEnabled = booleanPreferencesKey("right_enabled")
    val topEnabled = booleanPreferencesKey("top_enabled")
    val bottomEnabled = booleanPreferencesKey("bottom_enabled")
    val sideWidthDp = intPreferencesKey("side_width_dp")
    val leftStart = floatPreferencesKey("left_start")
    val leftEnd = floatPreferencesKey("left_end")
    val rightStart = floatPreferencesKey("right_start")
    val rightEnd = floatPreferencesKey("right_end")
    val topHeightDp = intPreferencesKey("top_height_dp")
    val bottomHeightDp = intPreferencesKey("bottom_height_dp")
    val bottomOffsetDp = intPreferencesKey("bottom_offset_dp")
    val triggerDistanceDp = intPreferencesKey("trigger_distance_dp")
    val dockIconDp = intPreferencesKey("dock_icon_dp")
    val longSwipeFraction = floatPreferencesKey("long_swipe_fraction")
    val hideInFullscreen = booleanPreferencesKey("hide_fullscreen")
    val hideWithKeyboard = booleanPreferencesKey("hide_keyboard")
    val pinned = stringPreferencesKey("pinned")
    val pinnedInitialized = booleanPreferencesKey("pinned_initialized")
    val onboardingDone = booleanPreferencesKey("onboarding_done")
}

class SettingsRepository(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    /** Null until the first read from disk completes. */
    val settings: StateFlow<EdgeSettings?> = store.data.map { p ->
        val d = EdgeSettings()
        EdgeSettings(
            leftEnabled = p[Keys.leftEnabled] ?: d.leftEnabled,
            rightEnabled = p[Keys.rightEnabled] ?: d.rightEnabled,
            topEnabled = p[Keys.topEnabled] ?: d.topEnabled,
            bottomEnabled = p[Keys.bottomEnabled] ?: d.bottomEnabled,
            sideWidthDp = p[Keys.sideWidthDp] ?: d.sideWidthDp,
            leftStart = p[Keys.leftStart] ?: d.leftStart,
            leftEnd = p[Keys.leftEnd] ?: d.leftEnd,
            rightStart = p[Keys.rightStart] ?: d.rightStart,
            rightEnd = p[Keys.rightEnd] ?: d.rightEnd,
            topHeightDp = p[Keys.topHeightDp] ?: d.topHeightDp,
            bottomHeightDp = p[Keys.bottomHeightDp] ?: d.bottomHeightDp,
            bottomOffsetDp = p[Keys.bottomOffsetDp] ?: d.bottomOffsetDp,
            triggerDistanceDp = p[Keys.triggerDistanceDp] ?: d.triggerDistanceDp,
            dockIconDp = p[Keys.dockIconDp] ?: d.dockIconDp,
            longSwipeFraction = p[Keys.longSwipeFraction] ?: d.longSwipeFraction,
            hideInFullscreen = p[Keys.hideInFullscreen] ?: d.hideInFullscreen,
            hideWithKeyboard = p[Keys.hideWithKeyboard] ?: d.hideWithKeyboard,
            pinned = p[Keys.pinned]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            pinnedInitialized = p[Keys.pinnedInitialized] ?: false,
            onboardingDone = p[Keys.onboardingDone] ?: false,
        )
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val current: EdgeSettings get() = settings.value ?: EdgeSettings()

    fun <T> set(key: Preferences.Key<T>, value: T) = edit { it[key] = value }

    fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch { store.edit { block(it) } }
    }

    fun resetEdges() = edit { p ->
        listOf(
            Keys.leftEnabled, Keys.rightEnabled, Keys.topEnabled, Keys.bottomEnabled,
            Keys.sideWidthDp, Keys.leftStart, Keys.leftEnd, Keys.rightStart, Keys.rightEnd,
            Keys.topHeightDp, Keys.bottomHeightDp, Keys.bottomOffsetDp, Keys.triggerDistanceDp,
            Keys.dockIconDp, Keys.longSwipeFraction, Keys.hideInFullscreen, Keys.hideWithKeyboard,
        ).forEach { p.remove(it) }
    }

    // --- Pinned dock apps ---------------------------------------------------

    private fun editPinned(transform: (MutableList<String>) -> Unit) = edit { p ->
        val list = p[Keys.pinned]?.split('\n')?.filter { it.isNotBlank() }?.toMutableList()
            ?: mutableListOf()
        transform(list)
        p[Keys.pinned] = list.distinct().joinToString("\n")
        p[Keys.pinnedInitialized] = true
    }

    fun setPinned(keys: List<String>) = editPinned { it.clear(); it.addAll(keys) }
    fun pin(key: String) = editPinned { if (key !in it) it.add(key) }
    fun unpin(key: String) = editPinned { it.remove(key) }
    fun move(key: String, delta: Int) = editPinned { list ->
        val i = list.indexOf(key)
        if (i >= 0) {
            val j = (i + delta).coerceIn(0, list.lastIndex)
            list.removeAt(i)
            list.add(j, key)
        }
    }
}
