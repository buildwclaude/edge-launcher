package app.edge.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edge.launcher.data.EdgeSettings
import app.edge.launcher.data.Keys
import app.edge.launcher.ui.ActionRow
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.Lomiri
import app.edge.launcher.ui.RangeRow
import app.edge.launcher.ui.SectionTitle
import app.edge.launcher.ui.SettingsCard
import app.edge.launcher.ui.SliderRow
import app.edge.launcher.ui.SwitchRow
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EdgeTheme { SettingsScreen() } }
        if (savedInstanceState == null) showOnboardingIfNeeded()
    }
}

@Composable
private fun SettingsScreen() {
    val context = LocalContext.current
    val repo = context.edge.settings
    val loaded by repo.settings.collectAsStateWithLifecycle()
    val s = loaded ?: EdgeSettings()
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val setupMissing = remember(tick) {
        listOf(
            Permissions.accessibilityEnabled(context),
            Permissions.usageAccess(context),
            Permissions.defaultHome(context),
        ).count { !it }
    }
    fun pct(f: Float) = "${(f * 100).roundToInt()}%"

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Edge",
                color = Lomiri.Text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp),
            )

            SectionTitle("Setup")
            SettingsCard {
                ActionRow(
                    if (setupMissing == 0) "All set" else "$setupMissing permission${if (setupMissing > 1) "s" else ""} missing",
                    "Walk through accessibility, usage access, overlay, battery and default home",
                ) { context.startActivity(Intent(context, OnboardingActivity::class.java)) }
            }

            SectionTitle("Edges")
            SettingsCard {
                SwitchRow("Left edge: dock", s.leftEnabled) { repo.set(Keys.leftEnabled, it) }
                SwitchRow("Right edge: app switcher", s.rightEnabled) { repo.set(Keys.rightEnabled, it) }
                SwitchRow("Top edge: notifications & quick settings", s.topEnabled) { repo.set(Keys.topEnabled, it) }
                SwitchRow("Bottom edge: app drawer", s.bottomEnabled) { repo.set(Keys.bottomEnabled, it) }
                HorizontalDivider(color = Lomiri.PanelLight)
                SwitchRow("Show strips", s.showStrips, "Tints the touch strips orange while you adjust them") {
                    repo.set(Keys.showStrips, it)
                }
            }

            SectionTitle("Strip size & position")
            SettingsCard {
                SliderRow("Side strip width", s.sideWidthDp.toFloat(), 4f..40f, { "${it.roundToInt()} dp" }) {
                    repo.set(Keys.sideWidthDp, it.roundToInt())
                }
                RangeRow("Left strip covers", s.leftStart..s.leftEnd, 0f..1f, { "${pct(it.start)} – ${pct(it.endInclusive)}" }) {
                    repo.edit { p -> p[Keys.leftStart] = it.start; p[Keys.leftEnd] = it.endInclusive }
                }
                RangeRow("Right strip covers", s.rightStart..s.rightEnd, 0f..1f, { "${pct(it.start)} – ${pct(it.endInclusive)}" }) {
                    repo.edit { p -> p[Keys.rightStart] = it.start; p[Keys.rightEnd] = it.endInclusive }
                }
                SliderRow("Top strip height", s.topHeightDp.toFloat(), 6f..48f, { "${it.roundToInt()} dp" }) {
                    repo.set(Keys.topHeightDp, it.roundToInt())
                }
                SliderRow("Bottom strip height", s.bottomHeightDp.toFloat(), 6f..48f, { "${it.roundToInt()} dp" }) {
                    repo.set(Keys.bottomHeightDp, it.roundToInt())
                }
                SliderRow("Bottom strip lift", s.bottomOffsetDp.toFloat(), 0f..64f, { "${it.roundToInt()} dp" }) {
                    repo.set(Keys.bottomOffsetDp, it.roundToInt())
                }
            }

            SectionTitle("Sensitivity")
            SettingsCard {
                SliderRow("Swipe distance to trigger", s.triggerDistanceDp.toFloat(), 8f..96f, { "${it.roundToInt()} dp" }) {
                    repo.set(Keys.triggerDistanceDp, it.roundToInt())
                }
                SliderRow("Long right swipe (spread) at", s.longSwipeFraction, 0.2f..0.7f, { "${pct(it)} of width" }) {
                    repo.set(Keys.longSwipeFraction, it)
                }
            }

            SectionTitle("Dock")
            SettingsCard {
                SliderRow("Icon size", s.dockIconDp.toFloat(), 36f..72f, { "${it.roundToInt()} dp" }) {
                    repo.set(Keys.dockIconDp, it.roundToInt())
                }
            }

            SectionTitle("Behaviour")
            SettingsCard {
                SwitchRow("Hide in full-screen apps", s.hideInFullscreen, "Video and games; swipe the system bars in first") {
                    repo.set(Keys.hideInFullscreen, it)
                }
                SwitchRow("Hide while the keyboard is open", s.hideWithKeyboard) { repo.set(Keys.hideWithKeyboard, it) }
                ActionRow("Reset edge settings", "Pinned apps are kept") { repo.resetEdges() }
            }

            SectionTitle("Tips")
            SettingsCard {
                Text(
                    "• Use gesture navigation (Settings › System › Navigation).\n" +
                        "• Lower the Back gesture sensitivity there, so it doesn't fight the side strips.\n" +
                        "• System Back still works on the lower third of each side; Edge covers the upper part.\n" +
                        "• Double-tap the home screen to lock. Long-press it for wallpaper and settings.\n" +
                        "• Long left swipe goes home. Long-press the orange dock button for home too.",
                    color = Lomiri.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }

            SectionTitle("About")
            SettingsCard {
                Text(
                    "Edge ${BuildConfig.VERSION_NAME}. Inspired by Ubuntu Touch / Lomiri.\n" +
                        "Ubuntu font © Canonical Ltd., used under the Ubuntu Font Licence 1.0.",
                    color = Lomiri.TextDim,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
