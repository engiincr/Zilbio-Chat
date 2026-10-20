package com.translive.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.ui.navigation.AppNavGraph
import com.translive.app.ui.navigation.BottomNavBar
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.TransLiveTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "translive_prefs")

class MainActivity : ComponentActivity() {

    private var currentUserId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle runtime permission results gracefully
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TransLiveTheme {
                val context = LocalContext.current
                val navController = rememberNavController()

                // Check login state from DataStore
                var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

                LaunchedEffect(Unit) {
                    try {
                        val prefs = context.dataStore.data.first()
                        val loggedIn = prefs[booleanPreferencesKey("is_logged_in")] ?: false
                        val userId = prefs[stringPreferencesKey("user_id")]
                        currentUserId = userId
                        isLoggedIn = loggedIn

                        // Update user online status on start if logged in
                        if (loggedIn && !userId.isNullOrEmpty()) {
                            try {
                                CloudRealtimeService.updateUserStatus(userId, "online")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isLoggedIn = false
                    }
                }

                val startDestination = when (isLoggedIn) {
                    true -> "home"
                    false -> "auth"
                    null -> return@TransLiveTheme // Loading state while reading DataStore
                }

                // Determine if bottom bar should show
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val showBottomBar = currentRoute in listOf("home", "chatList", "calls", "friends", "settings")

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController = navController)
                        }
                    },
                    containerColor = BackgroundAlabasterCream
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AppNavGraph(
                            navController = navController,
                            startDestination = startDestination
                        )
                    }
                }
            }
        }

        // Request runtime permissions
        requestAppPermissions()

        // Samsung/Xiaomi battery optimization
        requestBatteryExclusion()
    }

    override fun onResume() {
        super.onResume()
        currentUserId?.let { userId ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    CloudRealtimeService.updateUserStatus(userId, "online")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        currentUserId?.let { userId ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    CloudRealtimeService.updateUserStatus(userId, "offline")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // RECORD_AUDIO is required for voice calls and live speech translation
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // CAMERA for profile photos and video capabilities
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        // POST_NOTIFICATIONS for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // BLUETOOTH_CONNECT for Android 12+ (API 31+) for Bluetooth headsets during calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExclusion() {
        try {
            // Standard Android Battery Optimization exemption request
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general battery saver optimization settings
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }

            // Manufacturer-specific background autostart handling
            val manufacturer = Build.MANUFACTURER.lowercase()
            when {
                // Xiaomi, Redmi, POCO (MIUI / HyperOS)
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    handleXiaomiAutoStart()
                }
                // Samsung (One UI)
                manufacturer.contains("samsung") -> {
                    handleSamsungBattery()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleXiaomiAutoStart() {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },
            Intent().apply {
                component = ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
            }
        )

        for (intent in intents) {
            try {
                if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    startActivity(intent)
                    break
                }
            } catch (e: Exception) {
                // Try next intent fallback
            }
        }
    }

    private fun handleSamsungBattery() {
        val intents = listOf(
            Intent().apply {
                component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")
            },
            Intent().apply {
                component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            },
            Intent().apply {
                component = ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")
            },
            Intent().apply {
                component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")
            }
        )

        for (intent in intents) {
            try {
                if (packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    startActivity(intent)
                    break
                }
            } catch (e: Exception) {
                // Try next intent fallback
            }
        }
    }
}
