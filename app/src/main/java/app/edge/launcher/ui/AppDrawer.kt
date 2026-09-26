package app.edge.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.edge.launcher.data.AppInfo
import app.edge.launcher.edge

/**
 * Lomiri app drawer: search field on top, alphabetical grid. [reveal] is the
 * sheet it lives in; pulling down (bottom drawer) or swiping left (left
 * drawer) past the edge of the grid closes it.
 */
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    pinned: List<String>,
    visible: Boolean,
    reveal: RevealState,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    fromLeft: Boolean = false,
) {
    val edge = LocalContext.current.edge
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            menuFor = null
            focus.clearFocus()
            gridState.scrollToItem(0)
        }
    }

    val filtered = remember(apps, query) { filterApps(apps, query) }
    val pullToClose = remember(reveal, fromLeft) { PullToClose(reveal, enabled = !fromLeft) }

    fun launch(app: AppInfo) {
        keyboard?.hide()
        onLaunch(app)
    }

    Box(
        modifier
            .fillMaxSize()
            .then(
                if (fromLeft) {
                    Modifier.pointerInput(reveal) {
                        val tracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking(); reveal.beginDrag() },
                            onHorizontalDrag = { change, dx ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                reveal.dragBy(dx)
                            },
                            onDragEnd = { reveal.settle(tracker.calculateVelocity().x) },
                            onDragCancel = { reveal.settle(0f) },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        enabled = !fromLeft,
                        state = rememberDraggableState { reveal.dragBy(-it) },
                        onDragStarted = { reveal.beginDrag() },
                        onDragStopped = { reveal.settle(-it) },
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                SearchField(
                    query = query,
                    onQuery = { query = it },
                    onGo = { filtered.firstOrNull()?.let(::launch) },
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 24.dp),
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(pullToClose)
                    .navigationBarsPadding(),
            ) {
                items(filtered, key = { it.key }) { app ->
                    DrawerItem(app, onClick = { launch(app) }, onLongClick = { menuFor = app })
                }
            }
            if (filtered.isEmpty() && query.isNotEmpty()) {
                Text(
                    "No apps match “$query”",
                    color = Lomiri.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }

        val target = menuFor
        ActionSheet(
            visible = target != null,
            title = target?.label.orEmpty(),
            icon = target?.icon,
            actions = if (target == null) emptyList() else listOf(
                if (target.key in pinned) SheetAction("Unpin from launcher") { edge.settings.unpin(target.key) }
                else SheetAction("Pin to launcher") { edge.settings.pin(target.key) },
                SheetAction("App info") { edge.apps.openAppInfo(target) },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

/** Suru-style text field: dark, hairline border, magnifier on the left. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onGo: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color(0xFF1A1A1A), shape)
            .border(1.dp, Color(0xFF5A5A5A), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = Lomiri.Silk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search…", color = Lomiri.Ash, fontSize = 16.sp, fontFamily = Ubuntu)
            }
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Lomiri.Text, fontSize = 16.sp, fontFamily = Ubuntu),
                cursorBrush = SolidColor(Lomiri.Orange),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Default.Clear,
                contentDescription = "Clear",
                tint = Lomiri.Silk,
                modifier = Modifier.size(20.dp).clickable { onQuery("") },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Lomiri delegate: 10gu x 11gu cell, 6gu icon, small label.
    Column(
        Modifier
            .height(88.dp)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(top = 8.dp, start = 2.dp, end = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(
            app.icon,
            48.dp,
            contentDescription = app.label,
            modifier = Modifier.graphicsLayer {
                val sc = if (pressed) 0.9f else 1f
                scaleX = sc; scaleY = sc
            },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = Lomiri.Text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

fun filterApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    return apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        .sortedBy {
            when {
                it.label.startsWith(q, ignoreCase = true) -> 0
                it.label.split(' ').any { w -> w.startsWith(q, ignoreCase = true) } -> 1
                it.label.contains(q, ignoreCase = true) -> 2
                else -> 3
            }
        }
}

/** Pulling down past the top of the grid drags the drawer closed. */
private class PullToClose(private val reveal: RevealState, private val enabled: Boolean) : NestedScrollConnection {
    private var dragging = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (enabled && dragging && available.y < 0f) {
            reveal.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (enabled && source == NestedScrollSource.UserInput && available.y > 0f) {
            if (!dragging) {
                dragging = true
                reveal.beginDrag()
            }
            reveal.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (dragging) {
            dragging = false
            reveal.settle(-available.y)
            return available
        }
        return Velocity.Zero
    }
}
