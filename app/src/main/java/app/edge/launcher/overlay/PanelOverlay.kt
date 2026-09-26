package app.edge.launcher.overlay

import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.NetworkCell
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeMute
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Battery3Bar
import androidx.compose.material.icons.outlined.Battery1Bar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.edge.launcher.SystemControls
import app.edge.launcher.SystemState

import app.edge.launcher.edge
import app.edge.launcher.service.EdgeAccessibilityService
import app.edge.launcher.service.EdgeNotificationListener
import app.edge.launcher.service.NotificationItem
import app.edge.launcher.service.OverlayWindow
import app.edge.launcher.ui.AppIcon
import app.edge.launcher.ui.EdgeTheme
import app.edge.launcher.ui.Lomiri
import app.edge.launcher.ui.MenuDivider
import app.edge.launcher.ui.MenuItem
import app.edge.launcher.ui.MenuSlider
import app.edge.launcher.ui.RevealState
import app.edge.launcher.ui.SuruSwitch
import app.edge.launcher.ui.Ubuntu
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

enum class Indicator(val title: String) {
    Messages("Notifications"),
    Network("Network"),
    Bluetooth("Bluetooth"),
    Sound("Sound"),
    Battery("Battery"),
    DateTime("Date and time"),
}

/**
 * Lomiri indicators: pull down from the top edge; the indicator under your
 * finger opens, and you can slide sideways while pulling to pick another.
 */
class PanelOverlay(private val service: EdgeAccessibilityService) {
    val reveal = RevealState(service.scope) { open ->
        if (open) window.setFocusable(true) else window.hide()
    }
    var selected by mutableIntStateOf(0)
    /** Bumped when state may have changed (panel opened, a control was used). */
    var tick by mutableIntStateOf(0)
    private var last = 0f

    private val window = OverlayWindow(service, service.wm, service.owner) {
        EdgeTheme { PanelContent(this) }
    }.apply { onBack = { close() } }

    val isShown get() = window.isShown
    val context get() = service

    private fun indexFor(x: Float): Int =
        (x / service.screenWidth() * Indicator.entries.size).toInt().coerceIn(0, Indicator.entries.lastIndex)

    fun begin(x: Float) {
        reveal.extentPx = service.screenHeight() * 0.8f
        selected = indexFor(x)
        tick++
        window.show(focusable = false)
        reveal.beginDrag()
        last = 0f
    }

    fun drag(distance: Float, x: Float) {
        reveal.dragBy(distance - last)
        last = distance
        selected = indexFor(x)
    }

    fun release(velocity: Float) { reveal.settle(velocity) }
    fun cancel() { reveal.animateTo(false) }
    fun close() { reveal.animateTo(false) }

    fun hideNow() {
        reveal.snapClosed()
        window.hide()
    }

    fun detach() = window.detach()

    fun open(intent: Intent) = service.startFromOverlay(intent)

    fun systemShade(quickSettings: Boolean) {
        hideNow()
        service.openShade(quickSettings)
    }
}

@Composable
private fun PanelContent(panel: PanelOverlay) {
    val ctx = LocalContext.current
    val reveal = panel.reveal
    var state by remember { mutableStateOf(SystemState()) }
    val torch by SystemControls.torch.collectAsState()
    val notifications by EdgeNotificationListener.items.collectAsState()
    LaunchedEffect(panel.tick) {
        state = SystemControls.read(ctx)
        SystemControls.watchTorch(ctx)
    }
    fun refresh() { panel.tick++ }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = constraints.maxHeight.toFloat()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = reveal.value * 0.4f }
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { panel.close() } },
        )
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = (reveal.value - 1f) * h }
                .background(Lomiri.LauncherBg)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Spacer(Modifier.statusBarsPadding())
            IndicatorBar(panel, state, torch, notifications.isNotEmpty())
            MenuDivider()

            val pager = rememberPagerState(initialPage = panel.selected) { Indicator.entries.size }
            LaunchedEffect(panel.selected) {
                if (pager.currentPage != panel.selected) pager.scrollToPage(panel.selected)
            }
            LaunchedEffect(pager) {
                snapshotFlow { pager.currentPage }.collect { panel.selected = it }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                when (Indicator.entries[page]) {
                    Indicator.Messages -> MessagesPage(panel, notifications)
                    Indicator.Network -> NetworkPage(panel, state)
                    Indicator.Bluetooth -> BluetoothPage(panel, state)
                    Indicator.Sound -> SoundPage(panel, state, ::refresh)
                    Indicator.Battery -> BatteryPage(panel, state, torch, ::refresh)
                    Indicator.DateTime -> DateTimePage(panel)
                }
            }
            // Handle: drag up to close.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { reveal.dragBy(it) },
                        onDragStarted = { reveal.beginDrag() },
                        onDragStopped = { reveal.settle(it) },
                    )
                    .clickable(remember { MutableInteractionSource() }, indication = null) { panel.close() },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 36.dp, height = 4.dp).background(Lomiri.Ash, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun IndicatorBar(panel: PanelOverlay, s: SystemState, torch: Boolean, hasMessages: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(panel.tick) { now = System.currentTimeMillis() }
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Indicator.entries.forEachIndexed { i, ind ->
            val sel = panel.selected == i
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { panel.selected = i },
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (ind) {
                        Indicator.Messages -> Icon(
                            if (hasMessages) Icons.Outlined.MarkEmailUnread else Icons.Outlined.Email, null,
                            tint = if (hasMessages) Lomiri.Orange else Color.White, modifier = Modifier.size(22.dp),
                        )
                        Indicator.Network -> Icon(
                            when {
                                s.airplane -> Icons.Outlined.AirplanemodeActive
                                s.wifiOn -> Icons.Outlined.Wifi
                                s.mobileConnected -> Icons.Outlined.NetworkCell
                                else -> Icons.Outlined.WifiOff
                            }, null, tint = Color.White, modifier = Modifier.size(22.dp),
                        )
                        Indicator.Bluetooth -> Icon(
                            if (s.bluetoothOn) Icons.Outlined.Bluetooth else Icons.Outlined.BluetoothDisabled, null,
                            tint = Color.White, modifier = Modifier.size(22.dp),
                        )
                        Indicator.Sound -> Icon(
                            when (s.ringerMode) {
                                AudioManager.RINGER_MODE_SILENT -> Icons.Outlined.VolumeOff
                                AudioManager.RINGER_MODE_VIBRATE -> Icons.Outlined.Vibration
                                else -> Icons.Outlined.VolumeUp
                            }, null, tint = Color.White, modifier = Modifier.size(22.dp),
                        )
                        Indicator.Battery -> {
                            Icon(batteryIcon(s), null, tint = if (torch) Lomiri.Orange else Color.White, modifier = Modifier.size(22.dp))
                            Text("${s.battery}%", color = Color.White, fontSize = 13.sp)
                        }
                        Indicator.DateTime -> Text(
                            DateFormat.getTimeFormat(ctx).format(Date(now)),
                            color = Color.White, fontSize = 15.sp,
                        )
                    }
                }
                // Selected indicator: a white bar under it.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(width = 28.dp, height = 2.dp)
                        .graphicsLayer { alpha = if (sel) 0.9f else 0f }
                        .background(Color.White),
                )
            }
        }
    }
}

private fun batteryIcon(s: SystemState): ImageVector = when {
    s.charging -> Icons.Outlined.BatteryChargingFull
    s.battery > 85 -> Icons.Outlined.BatteryFull
    s.battery > 50 -> Icons.Outlined.Battery5Bar
    s.battery > 20 -> Icons.Outlined.Battery3Bar
    else -> Icons.Outlined.Battery1Bar
}

@Composable
private fun PageTitle(text: String) {
    Text(
        text,
        color = Lomiri.TextDim,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun Chevron() = Icon(Icons.Outlined.ChevronRight, null, tint = Lomiri.Ash)

// --- Pages ------------------------------------------------------------------

@Composable
private fun MessagesPage(panel: PanelOverlay, items: List<NotificationItem>) {
    val ctx = LocalContext.current
    val edge = ctx.edge
    if (!EdgeNotificationListener.isEnabled(ctx)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PageTitle("Notifications")
            Text(
                "Allow Edge to read notifications to see them here.",
                color = Lomiri.TextDim, fontSize = 15.sp,
                modifier = Modifier.padding(16.dp),
            )
            MenuItem("Allow notification access", icon = Icons.Outlined.NotificationsNone, onClick = {
                panel.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }) { Chevron() }
            MenuItem("Show system notifications", icon = Icons.Outlined.Tune, onClick = { panel.systemShade(false) })
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { PageTitle("Notifications") }
        if (items.isEmpty()) {
            item {
                Text(
                    "No notifications", color = Lomiri.TextDim, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        items(items, key = { it.key }) { n ->
            NotificationRow(n, edge.apps.forPackage(n.packageName)) {
                panel.hideNow()
                EdgeNotificationListener.open(ctx, n)
            }
        }
        if (items.any { it.clearable }) {
            item {
                Text(
                    "Clear all",
                    color = Lomiri.Text,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { EdgeNotificationListener.clearAll() }.padding(18.dp),
                )
            }
        }
        item {
            MenuItem("Show system notifications", icon = Icons.Outlined.Tune, onClick = { panel.systemShade(false) })
        }
    }
}

@Composable
private fun NotificationRow(n: NotificationItem, app: app.edge.launcher.data.AppInfo?, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = constraints.maxWidth.toFloat()
        Column(
            Modifier
                .offset { IntOffset(offset.floatValue.roundToInt(), 0) }
                .graphicsLayer { alpha = 1f - (kotlin.math.abs(offset.floatValue) / w).coerceIn(0f, 1f) }
                .then(
                    if (n.clearable) {
                        Modifier.pointerInput(n.key) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, dx -> offset.floatValue += dx },
                                onDragEnd = {
                                    val from = offset.floatValue
                                    scope.launch {
                                        if (kotlin.math.abs(from) > w * 0.35f) {
                                            animate(from, if (from > 0) w else -w, animationSpec = tween(150)) { v, _ -> offset.floatValue = v }
                                            EdgeNotificationListener.dismiss(n.key)
                                        } else {
                                            animate(from, 0f, animationSpec = tween(150)) { v, _ -> offset.floatValue = v }
                                        }
                                    }
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(onClick = onOpen),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (app != null) AppIcon(app.icon, 40.dp) else Box(Modifier.size(40.dp).background(Lomiri.Inkstone, CircleShape))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            n.title.ifBlank { app?.label.orEmpty() },
                            color = Lomiri.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text(
                            DateUtils.getRelativeTimeSpanString(n.postTime, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE).toString(),
                            color = Lomiri.Ash, fontSize = 12.sp,
                        )
                    }
                    if (n.text.isNotBlank()) {
                        Text(n.text, color = Lomiri.TextDim, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            MenuDivider()
        }
    }
}

@Composable
private fun NetworkPage(panel: PanelOverlay, s: SystemState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("Network")
        MenuItem("Flight mode", icon = Icons.Outlined.AirplanemodeActive, onClick = {
            panel.open(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
        }) { SuruSwitch(s.airplane, enabled = false) {} }
        MenuItem(
            "Wi-Fi",
            subtitle = when { !s.wifiOn -> "Off"; s.wifiConnected -> "Connected"; else -> "On, not connected" },
            icon = if (s.wifiOn) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
            onClick = {
                panel.open(
                    if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS),
                )
            },
        ) { Chevron() }
        MenuItem(
            "Mobile data",
            subtitle = if (s.mobileConnected) "In use" else null,
            icon = Icons.Outlined.NetworkCell,
            onClick = {
                panel.open(
                    if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                    else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS),
                )
            },
        ) { Chevron() }
        MenuItem("Network settings…", icon = Icons.Outlined.Settings, onClick = {
            panel.open(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        })
        MenuItem("System quick settings", icon = Icons.Outlined.Tune, onClick = { panel.systemShade(true) })
    }
}

@Composable
private fun BluetoothPage(panel: PanelOverlay, s: SystemState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("Bluetooth")
        MenuItem(
            "Bluetooth",
            subtitle = if (s.bluetoothOn) "On" else "Off",
            icon = if (s.bluetoothOn) Icons.Outlined.Bluetooth else Icons.Outlined.BluetoothDisabled,
            onClick = { panel.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        ) { Chevron() }
        MenuItem("Bluetooth settings…", icon = Icons.Outlined.Settings, onClick = {
            panel.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        })
    }
}

@Composable
private fun SoundPage(panel: PanelOverlay, s: SystemState, refresh: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("Sound")
        MenuItem("Silent mode", icon = Icons.Outlined.Vibration) {
            SuruSwitch(s.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                SystemControls.setSilent(ctx, it); refresh()
            }
        }
        Text("Volume", color = Lomiri.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
        MenuSlider(s.mediaVolume, Icons.Outlined.VolumeMute, Icons.Outlined.VolumeUp) {
            SystemControls.setVolume(ctx, AudioManager.STREAM_MUSIC, it)
        }
        Text("Ringer", color = Lomiri.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
        MenuSlider(s.ringVolume, Icons.Outlined.VolumeDown, Icons.Outlined.VolumeUp) {
            SystemControls.setVolume(ctx, AudioManager.STREAM_RING, it)
        }
        if (s.dndAccess) {
            MenuItem("Do not disturb", icon = Icons.Outlined.DoNotDisturbOn) {
                SuruSwitch(s.dnd) { SystemControls.setDnd(ctx, it); refresh() }
            }
        } else {
            MenuItem("Do not disturb", subtitle = "Allow Edge to control it", icon = Icons.Outlined.DoNotDisturbOn, onClick = {
                panel.open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }) { Chevron() }
        }
        MenuItem("Sound settings…", icon = Icons.Outlined.Settings, onClick = {
            panel.open(Intent(Settings.ACTION_SOUND_SETTINGS))
        })
    }
}

@Composable
private fun BatteryPage(panel: PanelOverlay, s: SystemState, torch: Boolean, refresh: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageTitle("Battery")
        MenuItem(
            "Charge level ${s.battery}%",
            subtitle = if (s.charging) "Charging" else null,
            icon = batteryIcon(s),
        )
        if (s.canWriteSettings) {
            Text("Brightness", color = Lomiri.TextDim, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
            MenuSlider(s.brightness, Icons.Outlined.BrightnessLow, Icons.Outlined.BrightnessHigh) {
                SystemControls.setBrightness(ctx, it)
            }
            MenuItem("Adjust brightness automatically") {
                SuruSwitch(s.autoBrightness) { SystemControls.setAutoBrightness(ctx, it); refresh() }
            }
            MenuItem("Rotation lock", icon = Icons.Outlined.ScreenLockRotation) {
                SuruSwitch(s.rotationLocked) { SystemControls.setRotationLocked(ctx, it); refresh() }
            }
        } else {
            MenuItem(
                "Brightness & rotation",
                subtitle = "Allow Edge to modify system settings",
                icon = Icons.Outlined.BrightnessHigh,
                onClick = {
                    panel.open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                },
            ) { Chevron() }
        }
        MenuItem("Flashlight", icon = Icons.Outlined.FlashlightOn) {
            SuruSwitch(torch) { SystemControls.setTorch(ctx, it) }
        }
        MenuItem("Battery settings…", icon = Icons.Outlined.Settings, onClick = {
            panel.open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
        })
        MenuItem("System quick settings", icon = Icons.Outlined.Tune, onClick = { panel.systemShade(true) })
    }
}

@Composable
private fun DateTimePage(panel: PanelOverlay) {
    val ctx = LocalContext.current
    val now = remember(panel.tick) { Date() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            DateFormat.getTimeFormat(ctx).format(now),
            color = Lomiri.Text, fontSize = 44.sp, fontFamily = Ubuntu, fontWeight = FontWeight.Light,
            modifier = Modifier.padding(start = 16.dp, top = 18.dp),
        )
        Text(
            DateFormat.format(DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "EEEEdMMMMyyyy"), now).toString(),
            color = Lomiri.TextDim, fontSize = 16.sp,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
        MenuDivider()
        MonthCalendar(now)
        MenuDivider()
        MenuItem("Alarms…", icon = Icons.Outlined.Alarm, onClick = { panel.open(Intent(AlarmClock.ACTION_SHOW_ALARMS)) })
        MenuItem("Time and date settings…", icon = Icons.Outlined.Settings, onClick = {
            panel.open(Intent(Settings.ACTION_DATE_SETTINGS))
        })
    }
}

@Composable
private fun MonthCalendar(now: Date) {
    val cal = remember(now) { Calendar.getInstance().apply { time = now } }
    val today = cal.get(Calendar.DAY_OF_MONTH)
    val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val firstDow = cal.firstDayOfWeek
    val lead = (first.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val names = remember {
        val sym = java.text.DateFormatSymbols.getInstance().shortWeekdays
        (0 until 7).map { sym[(firstDow - 1 + it) % 7 + 1].take(2) }
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            DateFormat.format("MMMM yyyy", now).toString(),
            color = Lomiri.Text, fontSize = 16.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Row { names.forEach { Text(it, color = Lomiri.Ash, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        val cells = lead + days
        for (row in 0 until (cells + 6) / 7) {
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (col in 0 until 7) {
                    val d = row * 7 + col - lead + 1
                    Box(Modifier.weight(1f).height(34.dp), contentAlignment = Alignment.Center) {
                        if (d in 1..days) {
                            if (d == today) Box(Modifier.size(30.dp).background(Lomiri.Orange, CircleShape))
                            Text("$d", color = Lomiri.Text, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

