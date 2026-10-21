package com.android.zilbio.data.webrtc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.android.zilbio.domain.model.AudioOutputRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioDeviceController(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "AudioDeviceController"
        private const val WAKE_LOCK_TAG = "zilbio:proximity_earpiece"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val _currentRoute = MutableStateFlow(AudioOutputRoute.SPEAKER)
    val currentRoute: StateFlow<AudioOutputRoute> = _currentRoute.asStateFlow()

    private val _isHeadsetPlugged = MutableStateFlow(false)
    val isHeadsetPlugged: StateFlow<Boolean> = _isHeadsetPlugged.asStateFlow()

    private val _isBluetoothConnected = MutableStateFlow(false)
    val isBluetoothConnected: StateFlow<Boolean> = _isBluetoothConnected.asStateFlow()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    _isHeadsetPlugged.value = (state == 1)
                    if (state == 1) {
                        setAudioRoute(AudioOutputRoute.BLUETOOTH_OR_HEADSET)
                    }
                }
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Headset unplugged -> Fallback to earpiece or speaker
                    setAudioRoute(AudioOutputRoute.EARPIECE)
                }
            }
        }
    }

    init {
        // Register Headset Plug and Becoming Noisy receivers
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        context.registerReceiver(noisyReceiver, filter)

        // Setup proximity wake lock for earpiece mode
        if (powerManager != null && powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG)
        }

        checkInitialAudioDevices()
    }

    private fun checkInitialAudioDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                ) {
                    _isHeadsetPlugged.value = true
                }
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                ) {
                    _isBluetoothConnected.value = true
                }
            }
        }
    }

    fun getConnectedAudioDevices(): List<com.android.zilbio.domain.model.ConnectedAudioDeviceInfo> {
        val list = mutableListOf<com.android.zilbio.domain.model.ConnectedAudioDeviceInfo>()
        list.add(com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.EARPIECE, "Dahili Ahize (Kulaklık)", true))
        list.add(com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.SPEAKER, "Dahili Hoparlör (Dış Ses)", true))

        var hasWired = false
        var hasBt = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (d in devices) {
                if (d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    d.type == AudioDeviceInfo.TYPE_USB_HEADSET
                ) {
                    hasWired = true
                    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d.productName.isNotBlank()) {
                        d.productName.toString()
                    } else {
                        "Kablolu Kulaklık"
                    }
                    list.add(com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.WIRED_HEADSET, "$name (Bağlı)", true))
                }
                if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                ) {
                    hasBt = true
                    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && d.productName.isNotBlank()) {
                        d.productName.toString()
                    } else {
                        "Bluetooth Kulaklık"
                    }
                    list.add(com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.BLUETOOTH, "$name (Bağlı)", true))
                }
            }
        }
        if (!hasWired) {
            list.add(com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.WIRED_HEADSET, "Kablolu Kulaklık (Bağlı Değil)", false))
        }
        if (!hasBt) {
            list.add(com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.BLUETOOTH, "Bluetooth Kulaklık (Bağlı Değil)", false))
        }
        return list
    }

    @Suppress("DEPRECATION")
    fun setAudioRoute(route: AudioOutputRoute) {
        Log.i(TAG, "Changing audio route to: $route")
        _currentRoute.value = route

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val targetDevice = when (route) {
                    AudioOutputRoute.EARPIECE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    AudioOutputRoute.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    AudioOutputRoute.WIRED_HEADSET -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    }
                    AudioOutputRoute.BLUETOOTH, AudioOutputRoute.BLUETOOTH_OR_HEADSET -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    else -> null
                }
                if (targetDevice != null) {
                    audioManager.setCommunicationDevice(targetDevice)
                }
            }

            when (route) {
                AudioOutputRoute.EARPIECE -> {
                    audioManager.isSpeakerphoneOn = false
                    try {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    } catch (e: Exception) {
                        Log.w(TAG, "Bluetooth SCO stop failed: ${e.message}")
                    }
                    enableProximitySensor(true)
                }
                AudioOutputRoute.SPEAKER -> {
                    enableProximitySensor(false)
                    try {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    } catch (e: Exception) {
                        Log.w(TAG, "Bluetooth SCO stop failed: ${e.message}")
                    }
                    audioManager.isSpeakerphoneOn = true
                }
                AudioOutputRoute.WIRED_HEADSET -> {
                    enableProximitySensor(false)
                    audioManager.isSpeakerphoneOn = false
                    try {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                    } catch (e: Exception) {
                        Log.w(TAG, "Bluetooth SCO stop failed: ${e.message}")
                    }
                }
                AudioOutputRoute.BLUETOOTH -> {
                    enableProximitySensor(false)
                    audioManager.isSpeakerphoneOn = false
                    try {
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Bluetooth SCO start failed: ${e.message}")
                    }
                }
                AudioOutputRoute.BLUETOOTH_OR_HEADSET -> {
                    enableProximitySensor(false)
                    if (_isBluetoothConnected.value) {
                        audioManager.isSpeakerphoneOn = false
                        try {
                            audioManager.startBluetoothSco()
                            audioManager.isBluetoothScoOn = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Bluetooth SCO start failed: ${e.message}")
                        }
                    } else {
                        audioManager.isSpeakerphoneOn = false
                        try {
                            audioManager.stopBluetoothSco()
                            audioManager.isBluetoothScoOn = false
                        } catch (e: Exception) {}
                    }
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error switching audio route: ${e.message}", e)
        }
    }

    private fun enableProximitySensor(enable: Boolean) {
        if (proximitySensor == null) return
        if (enable) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensorManager?.unregisterListener(this)
            releaseProximityWakeLock()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values.firstOrNull() ?: return
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isNear = distance < maxRange

            if (isNear && _currentRoute.value == AudioOutputRoute.EARPIECE) {
                acquireProximityWakeLock()
            } else {
                releaseProximityWakeLock()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun acquireProximityWakeLock() {
        if (proximityWakeLock?.isHeld == false) {
            proximityWakeLock?.acquire(10 * 60 * 1000L /* 10 minutes max */)
            Log.d(TAG, "Proximity wake lock acquired (screen will turn off at ear)")
        }
    }

    private fun releaseProximityWakeLock() {
        if (proximityWakeLock?.isHeld == true) {
            proximityWakeLock?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            Log.d(TAG, "Proximity wake lock released")
        }
    }

    fun release() {
        try {
            context.unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {
            // Ignored if not registered
        }
        enableProximitySensor(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
            } catch (e: Exception) {}
        }
        try {
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting audioManager on release: ${e.message}")
        }
    }
}
