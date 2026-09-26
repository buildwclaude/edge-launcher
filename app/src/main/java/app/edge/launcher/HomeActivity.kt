package app.edge.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edge.launcher.ui.ActionSheet
import app.edge.launcher.ui.AppDrawer
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.HomeClock
import app.edge.launcher.ui.Lomiri
import app.edge.launcher.ui.RevealState
import app.edge.launcher.ui.SheetAction

class HomeActivity : ComponentActivity() {
    private var drawer: RevealState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            EdgeTheme {
                HomeRoot(onDrawerState = { drawer = it })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already home: close the drawer.
        drawer?.animateTo(false)
    }
}

@Composable
private fun HomeRoot(onDrawerState: (RevealState) -> Unit) {
    val context = LocalContext.current
    val edge = context.edge
    val scope = rememberCoroutineScope()
    val drawer = remember { RevealState(scope) }.also(onDrawerState)
    val apps by edge.apps.apps.collectAsStateWithLifecycle()
    val settings by edge.settings.settings.collectAsStateWithLifecycle()
    var homeMenu by remember { mutableStateOf(false) }
    val drawerVisible by remember { derivedStateOf { drawer.progress.value > 0f || drawer.isShown } }

    BackHandler(enabled = drawer.isShown) { drawer.animateTo(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = constraints.maxHeight.toFloat()
        drawer.extentPx = height

        // Home layer: wallpaper shows through; clock in the middle.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { SystemActions.lockScreen(context) },
                        onLongPress = { homeMenu = true },
                    )
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking(); drawer.beginDrag() },
                        onVerticalDrag = { change, dy ->
                            tracker.addPosition(change.uptimeMillis, change.position)
                            drawer.dragBy(-dy)
                        },
                        onDragEnd = { drawer.settle(-tracker.calculateVelocity().y) },
                        onDragCancel = { drawer.settle(0f) },
                    )
                }
                .graphicsLayer { alpha = 1f - drawer.progress.value },
            contentAlignment = Alignment.Center,
        ) {
            HomeClock()
        }

        if (drawerVisible) {
            AppDrawer(
                apps = apps.orEmpty(),
                pinned = settings?.pinned.orEmpty(),
                visible = drawer.isShown,
                reveal = drawer,
                onLaunch = { app ->
                    edge.apps.launch(app)
                    drawer.snapClosed()
                },
                modifier = Modifier
                    .graphicsLayer { translationY = (1f - drawer.progress.value) * height }
                    .background(Lomiri.Panel),
            )
        }

        ActionSheet(
            visible = homeMenu,
            title = "Edge",
            icon = null,
            actions = listOf(
                SheetAction("Change wallpaper") {
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"))
                    }
                },
            ),
            onDismiss = { homeMenu = false },
        )
    }
}
