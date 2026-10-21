package com.android.zilbio

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.service.GlobalCallState
import com.android.zilbio.service.GlobalRealtimeSyncManager
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.localization.getAppStrings
import com.android.zilbio.ui.navigation.AppNavGraph
import com.android.zilbio.ui.navigation.BottomNavBar
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.NeonLimeAccent
import com.android.zilbio.ui.theme.SurfaceDarkWarmElevated
import com.android.zilbio.ui.theme.SurfaceMidnightDark
import com.android.zilbio.ui.theme.TextMutedWarmGrey
import com.android.zilbio.ui.theme.TextOffWhite
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.data.updater.GitHubAutoUpdater
import com.android.zilbio.data.updater.UpdateState
import com.android.zilbio.data.rewards.BadgeRewardManager
import com.android.zilbio.ui.components.BadgeCelebrationDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.animation.AnimatedVisibility
import com.android.zilbio.ui.theme.ZilbioTheme
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zilbio_prefs")

class MainActivity : ComponentActivity() {

    private var currentUserId: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private var onNewIntentListener: ((Intent) -> Unit)? = null

    fun wakeUpScreenForCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Log.i("MainActivity", "Woke up screen and unlocked window flags for incoming call.")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error waking screen for call: ${e.message}")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("incoming_call_id")) {
            wakeUpScreenForCall()
        }
        onNewIntentListener?.invoke(intent)
    }

    private fun handleIntentNavigation(intent: Intent, navController: androidx.navigation.NavController) {
        try {
            if (intent.hasExtra("incoming_call_id")) {
                val callId = intent.getStringExtra("incoming_call_id") ?: ""
                val callerId = intent.getStringExtra("caller_id") ?: ""
                val isVideo = intent.getBooleanExtra("is_video", false)
                if (callId.isNotEmpty()) {
                    wakeUpScreenForCall()
                    val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                    if (!currentRoute.startsWith("call") && !currentRoute.startsWith("incomingCall")) {
                        navController.navigate("incomingCall/$callId/$callerId?isVideo=$isVideo")
                    }
                }
            } else if (intent.hasExtra("sender_id")) {
                val senderId = intent.getStringExtra("sender_id") ?: ""
                if (senderId.isNotEmpty()) {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                    if (currentRoute != "chat/$senderId") {
                        navController.navigate("chat/$senderId")
                    }
                }
            } else if (intent.getBooleanExtra("navigate_to_friends", false)) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                if (currentRoute != "friends") {
                    navController.navigate("friends")
                }
            } else if (intent.getBooleanExtra("navigate_to_settings", false) || intent.hasExtra("update_version_code")) {
                val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                if (currentRoute != "settings") {
                    navController.navigate("settings")
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "handleIntentNavigation error: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake screen if opened directly from incoming call notification
        if (intent?.hasExtra("incoming_call_id") == true) {
            wakeUpScreenForCall()
        }

        // Enable edge-to-edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val userPrefs by UserPreferences.getUser(context).collectAsState(initial = null)
            val appLanguage = userPrefs?.language ?: "tr"
            val appStrings = remember(appLanguage) { getAppStrings(appLanguage) }

            val appThemeMode by UserPreferences.getAppThemeMode(context).collectAsState(initial = "DARK")
            val darkTheme = when (appThemeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            val hasShownPowerOnboarding by UserPreferences.hasShownPowerOnboarding(context).collectAsState(initial = true)
            var showPowerDialog by remember { mutableStateOf(false) }

            // OTA Auto-Updater States
            val updateState by GitHubAutoUpdater.updateState.collectAsState()
            val isDownloading by GitHubAutoUpdater.isDownloading.collectAsState()
            val downloadProgress by GitHubAutoUpdater.downloadProgress.collectAsState()

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                ZilbioTheme(darkTheme = darkTheme) {
                    val navController = rememberNavController()
                    val currentUser by UserPreferences.getUser(this).collectAsState(initial = null)

                    var isLoggedIn by remember { mutableStateOf<Boolean?>(null) }

                    LaunchedEffect(Unit) {
                        try {
                            // Check for GitHub OTA updates on launch
                            GitHubAutoUpdater.checkForUpdates(context, manual = false, autoDownload = false)

                            val prefs = context.dataStore.data.first()
                            val loggedIn = prefs[booleanPreferencesKey("is_logged_in")] ?: false
                            val userId = prefs[stringPreferencesKey("user_id")]
                            currentUserId = userId
                            isLoggedIn = loggedIn

                            if (loggedIn && !userId.isNullOrEmpty()) {
                                try {
                                    CloudRealtimeService.updateUserPresence(userId, true)
                                    // Start Global SSE Realtime Background Sync (Item #1)
                                    GlobalRealtimeSyncManager.startSync(applicationContext, userId)

                                    val powerShown = prefs[booleanPreferencesKey("has_shown_power_onboarding")] ?: false
                                    if (!powerShown) {
                                        showPowerDialog = true
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            isLoggedIn = false
                        }
                    }

                    // Observe reactive incoming calls from GlobalRealtimeSyncManager
                    val incomingCallSession by GlobalRealtimeSyncManager.incomingCallState.collectAsState()
                    LaunchedEffect(incomingCallSession) {
                        val session = incomingCallSession ?: return@LaunchedEffect
                        val currentRoute = navController.currentBackStackEntry?.destination?.route ?: ""
                        if (!currentRoute.startsWith("call") && !currentRoute.startsWith("incomingCall")) {
                            wakeUpScreenForCall()
                            navController.navigate("incomingCall/${session.callId}/${session.callerId}?isVideo=${session.isVideo}")
                        }
                    }

                    // Handle intent-based navigation (e.g. notification taps)
                    DisposableEffect(navController) {
                        onNewIntentListener = { newIntent ->
                            handleIntentNavigation(newIntent, navController)
                        }
                        onDispose {
                            onNewIntentListener = null
                        }
                    }

                    LaunchedEffect(isLoggedIn) {
                        if (isLoggedIn == true && intent != null) {
                            handleIntentNavigation(intent, navController)
                        }
                    }

                    val startDestination = when (isLoggedIn) {
                        true -> "chatList"
                        false -> "auth"
                        null -> return@ZilbioTheme
                    }

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val showBottomBar = currentRoute in listOf("chatList", "calls", "friends", "settings", "home")

                    Scaffold(
                        bottomBar = {
                            if (showBottomBar) {
                                BottomNavBar(navController = navController)
                            }
                        },
                        containerColor = BackgroundMidnightNavy
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

                            // Suspension Enforcement Overlay
                            if (currentUser?.permissions?.isAccountSuspended == true && isLoggedIn == true) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(BackgroundMidnightNavy),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.padding(24.dp),
                                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = SurfaceMidnightDark),
                                        shape = RoundedCornerShape(16.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, DestructiveCoralRed)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = "Hesap Askıda",
                                                tint = DestructiveCoralRed,
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "Hesabınız Askıya Alındı",
                                                color = TextOffWhite,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Hesabınız sistem yöneticisi (@tugkancr) tarafından askıya alınmıştır veya dondurulmuştur. Bilgi almak için yönetici ile iletişime geçebilirsiniz.",
                                                color = TextMutedWarmGrey,
                                                fontSize = 14.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Button(
                                                onClick = {
                                                    lifecycleScope.launch {
                                                        currentUser?.userId?.let { uid ->
                                                            try {
                                                                CloudRealtimeService.updateUserPresence(uid, false)
                                                            } catch (e: Exception) {
                                                                // ignore
                                                            }
                                                        }
                                                        UserPreferences.clear(this@MainActivity)
                                                        navController.navigate("auth") {
                                                            popUpTo(0) { inclusive = true }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = DestructiveCoralRed),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Çıkış Yap", color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Floating Live OTA Update Banner
                            AnimatedVisibility(
                                visible = isDownloading || updateState is UpdateState.ReadyToInstall,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                androidx.compose.material3.Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SurfaceDarkWarmElevated,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isDownloading) "Güncelleme İndiriliyor... (%${(downloadProgress * 100).toInt()})" else "Yeni Güncelleme Hazır! ⚡",
                                                color = TextOffWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (updateState is UpdateState.ReadyToInstall) {
                                                Button(
                                                    onClick = {
                                                        (updateState as? UpdateState.ReadyToInstall)?.let { ready ->
                                                            GitHubAutoUpdater.installApk(context, ready.apkFile, ready.manifest.commitSha)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = NeonLimeAccent),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                                ) {
                                                    Text(text = "Yükle", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        if (isDownloading) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { downloadProgress / 100f },
                                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                                color = NeonLimeAccent,
                                                trackColor = SurfaceMidnightDark
                                            )
                                        }
                                    }
                                }
                            }

                            // Global Dynamic Island Floating Pill Overlay (Item #23)
                            com.android.zilbio.ui.components.ZilbioDynamicOverlay(
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    }

                    // One-Time Autostart & Power Onboarding Dialog (Item #7)
                    if (showPowerDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                showPowerDialog = false
                                coroutineScope.launch {
                                    UserPreferences.setPowerOnboardingShown(context)
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = NeonLimeAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Kesintisiz Bildirim & Arama",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextOffWhite
                                    )
                                }
                            },
                            text = {
                                Column {
                                    Text(
                                        text = "Zilbio'nun ekran kapalıyken veya arka plandayken anlık mesaj bildirimlerini ve canlı çevirili gelen aramaları kesintisiz iletebilmesi için 'Pil Tasarrufu Kısıtlamalarını Kaldır' ve 'Otomatik Başlatma' izinlerini yapılandırmanız önerilir.",
                                        fontSize = 13.sp,
                                        color = TextMutedWarmGrey,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Özellikle Samsung, Xiaomi, Redmi, Oppo ve Huawei cihazlarda bu ayar gelen çağrıların zamanında çalmasını sağlar.",
                                        fontSize = 12.sp,
                                        color = NeonLimeAccent
                                    )
                                }
                            },
                            containerColor = SurfaceDarkWarmElevated,
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showPowerDialog = false
                                        coroutineScope.launch {
                                            UserPreferences.setPowerOnboardingShown(context)
                                        }
                                        requestBatteryExclusion()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonLimeAccent),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Ayarları Yapılandır", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        showPowerDialog = false
                                        coroutineScope.launch {
                                            UserPreferences.setPowerOnboardingShown(context)
                                        }
                                    }
                                ) {
                                    Text("Daha Sonra", color = TextOffWhite)
                                }
                            }
                        )
                    }

                    // Real-time Celebratory Profile Badge Unlock Dialog
                    val celebrationEvent by BadgeRewardManager.celebrationFlow.collectAsState()
                    celebrationEvent?.let { event ->
                        BadgeCelebrationDialog(
                            event = event,
                            onDismiss = { BadgeRewardManager.dismissCelebration() }
                        )
                    }
                }
            }
        }

        // Request runtime permissions
        requestAppPermissions()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatic PiP entrance when Home/recents pressed during active video call (Item #1)
        if (GlobalCallState.isInActiveVideoCall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val aspectRatio = android.util.Rational(9, 16)
                val pipParams = android.app.PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .build()
                enterPictureInPictureMode(pipParams)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        GlobalCallState.setInPipMode(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            // Scenario A: In PiP window -> Keep camera active
            GlobalCallState.activeAudioEngine?.setVideoEnabled(true)
        } else {
            // Maximized / returning from PiP
            GlobalCallState.onAppForegrounded()
        }
    }

    override fun onResume() {
        super.onResume()
        // Scenario C: Resuming foreground -> restart camera
        GlobalCallState.onAppForegrounded()

        currentUserId?.let { userId ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    CloudRealtimeService.updateUserPresence(userId, true)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Scenario B: If fully minimized without PiP -> pause local camera for privacy & battery
        val inPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) isInPictureInPictureMode else false
        GlobalCallState.onAppBackgrounded(inPip)

        currentUserId?.let { userId ->
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    CloudRealtimeService.updateUserPresence(userId, false)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalRealtimeSyncManager.stopSync()
    }

    private fun requestAppPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(fallbackIntent)
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                    }
                }
            }

            val manufacturer = Build.MANUFACTURER.lowercase()
            when {
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                    handleXiaomiAutoStart()
                }
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
                // Try next
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
                // Try next
            }
        }
    }
}
