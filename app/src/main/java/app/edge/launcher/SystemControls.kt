package app.edge.launcher

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Snapshot of the things the indicator panel shows. */
data class SystemState(
    val battery: Int = 0,
    val charging: Boolean = false,
    val wifiOn: Boolean = false,
    val wifiConnected: Boolean = false,
    val mobileConnected: Boolean = false,
    val airplane: Boolean = false,
    val bluetoothOn: Boolean = false,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
    val mediaVolume: Float = 0f,
    val ringVolume: Float = 0f,
    val canWriteSettings: Boolean = false,
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    val rotationLocked: Boolean = false,
    val dndAccess: Boolean = false,
    val dnd: Boolean = false,
)

/** Reads and changes system state without root; every call tolerates failure. */
object SystemControls {
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch
    private var torchId: String? = null
    private var torchRegistered = false

    fun read(ctx: Context): SystemState {
        val battery = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.let {
            val l = it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val s = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            l * 100 / s
        } ?: 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val audio = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val resolver = ctx.contentResolver
        fun vol(stream: Int) = runCatching {
            audio.getStreamVolume(stream).toFloat() / audio.getStreamMaxVolume(stream).coerceAtLeast(1)
        }.getOrDefault(0f)
        return SystemState(
            battery = level,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            wifiOn = runCatching { ctx.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false),
            wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
            mobileConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
            airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            bluetoothOn = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }
                .getOrDefault(false),
            ringerMode = audio.ringerMode,
            mediaVolume = vol(AudioManager.STREAM_MUSIC),
            ringVolume = vol(AudioManager.STREAM_RING),
            canWriteSettings = Settings.System.canWrite(ctx),
            brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f,
            autoBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            rotationLocked = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            dndAccess = nm.isNotificationPolicyAccessGranted,
            dnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }

    fun setVolume(ctx: Context, stream: Int, fraction: Float) = runCatching {
        val audio = ctx.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(stream)
        audio.setStreamVolume(stream, (fraction * max).toInt().coerceIn(0, max), 0)
    }

    fun setSilent(ctx: Context, silent: Boolean) = runCatching {
        ctx.getSystemService(AudioManager::class.java).ringerMode =
            if (silent) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
    }

    fun setBrightness(ctx: Context, fraction: Float) = runCatching {
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (fraction * 255).toInt().coerceIn(1, 255))
    }

    fun setAutoBrightness(ctx: Context, on: Boolean) = runCatching {
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    fun setRotationLocked(ctx: Context, locked: Boolean) = runCatching {
        Settings.System.putInt(ctx.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 0 else 1)
    }

    fun setDnd(ctx: Context, on: Boolean) = runCatching {
        ctx.getSystemService(NotificationManager::class.java).setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
    }

    private fun torchCamera(ctx: Context): String? {
        torchId?.let { return it }
        val cm = ctx.getSystemService(CameraManager::class.java)
        torchId = runCatching {
            cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        if (!torchRegistered) {
            torchRegistered = true
            runCatching {
                cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == torchId) _torch.value = enabled
                    }
                }, Handler(Looper.getMainLooper()))
            }
        }
        return torchId
    }

    fun watchTorch(ctx: Context) {
        torchCamera(ctx)
    }

    fun setTorch(ctx: Context, on: Boolean) = runCatching {
        val id = torchCamera(ctx) ?: return@runCatching
        ctx.getSystemService(CameraManager::class.java).setTorchMode(id, on)
    }
}
