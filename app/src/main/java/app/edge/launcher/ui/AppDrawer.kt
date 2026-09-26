package app.edge.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
 * Full-screen, searchable, alphabetical app grid. Used by the home screen and
 * by the bottom-edge overlay. [reveal] is the sheet this drawer lives in, so a
 * pull-down at the top of the grid closes it.
 */
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    pinned: List<String>,
    visible: Boolean,
    reveal: RevealState,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val edge = context.edge
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }
    val gridState = rememberLazyGridState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            menuFor = null
            focus.clearFocus()
            gridState.scrollToItem(0)
        }
    }

    val filtered = remember(apps, query) { filterApps(apps, query) }
    val pullToClose = remember(reveal) { PullToClose(reveal) }

    fun launch(app: AppInfo) {
        keyboard?.hide()
        onLaunch(app)
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // Header: search pill. Dragging it down also closes the drawer.
            Box(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { reveal.dragBy(-it) },
                        onDragStarted = { reveal.beginDrag() },
                        onDragStopped = { reveal.settle(-it) },
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Lomiri.PanelLight)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Lomiri.TextDim)
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search apps", color = Lomiri.TextDim, fontSize = 17.sp, fontFamily = Ubuntu)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Lomiri.Text, fontSize = 17.sp, fontFamily = Ubuntu),
                            cursorBrush = SolidColor(Lomiri.Orange),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(::launch) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Lomiri.TextDim,
                            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(11.dp))
                                .clickable { query = "" },
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 80.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                if (target.key in pinned) SheetAction("Unpin from dock") { edge.settings.unpin(target.key) }
                else SheetAction("Pin to dock") { edge.settings.pin(target.key) },
                SheetAction("App info") { edge.apps.openAppInfo(target) },
            ),
            onDismiss = { menuFor = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(app: AppInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.icon, 54.dp, contentDescription = app.label)
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
private class PullToClose(private val reveal: RevealState) : NestedScrollConnection {
    private var dragging = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (dragging && available.y < 0f) {
            reveal.dragBy(-available.y)
            return Offset(0f, available.y)
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y > 0f) {
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
