package app.edge.launcher

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.edge.launcher.data.Keys
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.Lomiri

class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EdgeTheme { Onboarding(onFinish = ::finishSetup) } }
    }

    private fun finishSetup() {
        edge.settings.set(Keys.onboardingDone, true)
        finish()
    }
}

private class Step(
    val title: String,
    val body: String,
    val done: Boolean,
    val optional: Boolean = false,
    val action: String,
    val onAction: () -> Unit,
    val extraAction: Pair<String, () -> Unit>? = null,
)

@Composable
private fun Onboarding(onFinish: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    val steps = remember(tick) {
        listOf(
            Step(
                title = "Edge gestures",
                body = "Turn on the Edge gestures accessibility service. It draws thin invisible strips on the " +
                    "screen edges so swipes work over every app, and locks the screen when you double-tap. " +
                    "It doesn't read what's on screen.\n\n" +
                    "If the switch is greyed out (“Restricted setting”), it's because the APK was sideloaded. " +
                    "Open App info, tap ⋮ in the top corner, choose “Allow restricted settings”, then come back and try again.",
                done = Permissions.accessibilityEnabled(context),
                action = "Open accessibility",
                onAction = { Permissions.open(context, Permissions.accessibilitySettings()) },
                extraAction = "App info" to { Permissions.open(context, Permissions.appDetails(context)) },
            ),
            Step(
                title = "Usage access",
                body = "Lets Edge see which apps you've used recently, to fill in the app switcher and the " +
                    "running-app dots in the dock straight after a restart.",
                done = Permissions.usageAccess(context),
                action = "Grant usage access",
                onAction = {
                    Permissions.open(
                        context, Permissions.usageAccessSettings(context),
                        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS),
                    )
                },
            ),
            Step(
                title = "Display over other apps",
                body = "Optional. Edge's panels are drawn by the accessibility service and don't need this. " +
                    "Granting it can help keep Edge alive on phones that are strict about background apps.",
                done = Permissions.overlay(context),
                optional = true,
                action = "Allow",
                onAction = { Permissions.open(context, Permissions.overlaySettings(context)) },
            ),
            Step(
                title = "Battery",
                body = "Set Edge to “Unrestricted” so the system doesn't stop the gesture service to save power. " +
                    "If it keeps dying, also lock Edge in the recent-apps screen of your old launcher.",
                done = Permissions.batteryUnrestricted(context),
                optional = true,
                action = "Don't optimise",
                onAction = {
                    Permissions.open(context, Permissions.batterySettings(context), Permissions.appDetails(context))
                },
            ),
            Step(
                title = "Default home app",
                body = "Make Edge your home screen. Pressing Home or swiping up from the very bottom will then " +
                    "bring you back to the Edge clock.",
                done = Permissions.defaultHome(context),
                action = "Set as home",
                onAction = {
                    runCatching { roleLauncher.launch(Permissions.homeRequest(context)) }
                        .onFailure { Permissions.open(context, android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) }
                },
            ),
        )
    }
    val requiredDone = steps.filter { !it.optional }.all { it.done }
    val current = steps.indexOfFirst { !it.done }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text("Welcome to Edge", color = Lomiri.Text, fontSize = 32.sp, fontWeight = FontWeight.Light)
            Text(
                "Everything is driven from the screen edges. A few permissions first.",
                color = Lomiri.TextDim,
                fontSize = 16.sp,
            )
            Spacer(Modifier.height(8.dp))
            steps.forEachIndexed { i, step -> StepCard(i + 1, step, expanded = i == current) }

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF232323)).padding(16.dp),
            ) {
                Text("Navigation", color = Lomiri.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                val navPath = if (Build.MANUFACTURER.equals("Nothing", true)) {
                    "Settings › System › Gestures › Navigation mode"
                } else {
                    "Settings › System › Navigation"
                }
                Text(
                    "Switch the phone to gesture navigation ($navPath), then lower the Back gesture " +
                        "sensitivity for both edges. Edge's side strips cover the upper two-thirds of the " +
                        "screen; system Back still works below them.",
                    color = Lomiri.TextDim,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Left edge: dock  •  Right edge: switch apps (drag further for all)\n" +
                        "Bottom edge: app drawer  •  Top edge: notifications (left) / quick settings (right)",
                    color = Lomiri.Text,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onFinish,
                enabled = requiredDone,
                colors = ButtonDefaults.buttonColors(containerColor = Lomiri.Orange),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (requiredDone) "Done" else "Finish the required steps", fontSize = 16.sp) }
            if (!requiredDone) {
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Skip for now", color = Lomiri.TextDim)
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, step: Step, expanded: Boolean) {
    val badge by animateColorAsState(if (step.done) Lomiri.Orange else Color(0xFF3A3A3A), label = "badge")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF232323))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(badge), contentAlignment = Alignment.Center) {
                if (step.done) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Text("$number", color = Color.White, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(step.title, color = Lomiri.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (step.optional && !step.done) Text("Optional", color = Lomiri.TextDim, fontSize = 12.sp)
        }
        if (expanded || !step.done) {
            Spacer(Modifier.height(8.dp))
            Text(step.body, color = Lomiri.TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (!step.done) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = step.onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (expanded) Lomiri.Orange else Color(0xFF3A3A3A),
                    ),
                ) { Text(step.action) }
                step.extraAction?.let { (label, action) ->
                    OutlinedButton(onClick = action) { Text(label, color = Lomiri.Text) }
                }
            }
        }
    }
}

/** Opens onboarding once, the first time Edge is started. */
fun ComponentActivity.showOnboardingIfNeeded() {
    lifecycleScope.launch {
        val s = edge.settings.settings.filterNotNull().first()
        if (!s.onboardingDone) startActivity(android.content.Intent(this@showOnboardingIfNeeded, OnboardingActivity::class.java))
    }
}
