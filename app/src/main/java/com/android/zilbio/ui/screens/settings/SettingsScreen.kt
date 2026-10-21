package com.android.zilbio.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.LinearProgressIndicator
import com.android.zilbio.BuildConfig
import com.android.zilbio.data.updater.AppUpdateManager
import com.android.zilbio.data.updater.UpdateState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.screens.friends.AvatarInitials
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.CallEndCoralRed
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.NeonLimeAccent
import com.android.zilbio.ui.theme.SurfaceDarkWarmElevated
import com.android.zilbio.ui.theme.SurfaceMidnightDark
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.TextMutedWarmGrey
import com.android.zilbio.ui.theme.TextOffWhite
import com.android.zilbio.data.rewards.BadgeRewardManager
import com.android.zilbio.ui.components.ProfileBadgesCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() },
    onLogout: () -> Unit = {
        navController?.navigate("auth") {
            popUpTo(0) { inclusive = true }
        }
    }
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    val appThemeMode by UserPreferences.getAppThemeMode(context).collectAsState(initial = "DARK")
    val unlockedBadges by BadgeRewardManager.unlockedBadgesFlow.collectAsState()

    var editableDisplayName by remember { mutableStateOf("") }
    var selectedLanguageCode by remember { mutableStateOf("tr") }
    var isSavingName by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val updateState by AppUpdateManager.updateState.collectAsState()
    val isDownloading by AppUpdateManager.isDownloading.collectAsState()
    val downloadProgress by AppUpdateManager.downloadProgress.collectAsState()
    val latestManifest by AppUpdateManager.latestManifest.collectAsState()
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        val user = currentUser ?: return@LaunchedEffect
        if (!hasInitialized) {
            hasInitialized = true
            editableDisplayName = user.displayName
            selectedLanguageCode = user.language.ifEmpty { "tr" }
            BadgeRewardManager.init(context, user.userId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.android.zilbio.R.drawable.zilbio_logo_transparent),
                                contentDescription = "Zilbio Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.settingsTitle,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOffWhite
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceSlateNavy,
                            border = BorderStroke(1.dp, BorderSlateNavy)
                        ) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonLimeAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundMidnightNavy
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundMidnightNavy
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Card (Avatar & Display Name)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarInitials(
                        name = editableDisplayName.ifEmpty { "User" },
                        size = 76,
                        avatarBase64 = currentUser?.avatarBase64
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editableDisplayName,
                            onValueChange = { editableDisplayName = it },
                            label = { Text(strings.displayName, fontSize = 12.sp, color = TextMutedWarmGrey) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceMidnightDark,
                                unfocusedContainerColor = SurfaceMidnightDark,
                                focusedIndicatorColor = NeonLimeAccent,
                                unfocusedIndicatorColor = BorderSlateNavy,
                                focusedTextColor = TextOffWhite,
                                unfocusedTextColor = TextOffWhite
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val trimmedName = editableDisplayName.trim()
                                isSavingName = true
                                coroutineScope.launch {
                                    try {
                                        UserPreferences.updateDisplayName(context, trimmedName)
                                        currentUser?.let { u ->
                                            CloudRealtimeService.updateUserProfile(u.userId, mapOf("displayName" to trimmedName))
                                        }
                                        snackbarHostState.showSnackbar(strings.changesSaved)
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Hata: ${e.message}")
                                    } finally {
                                        isSavingName = false
                                    }
                                }
                            },
                            enabled = !isSavingName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeonLimeAccent)
                        ) {
                            if (isSavingName) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = strings.saveChanges,
                                    tint = Color.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Account ID Row
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentUser?.userId?.let { id ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("User ID", id)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "${strings.username}: $id", Toast.LENGTH_SHORT).show()
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = SurfaceMidnightDark
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "@${currentUser?.username ?: ""} (ID: ${currentUser?.userId ?: "-"})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonLimeAccent
                                )
                                if (currentUser?.isOwnerOrAdmin() == true) {
                                    AdminBadge(customRole = currentUser?.permissions?.customRoleLabel, isCompact = true)
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = TextMutedWarmGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            ProfileBadgesCard(
                badges = BadgeRewardManager.getBadgesWithStatus(unlockedBadges),
                title = "Başarı Rozetlerim 🏆"
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Exclusive Owner / Admin Control Panel (for @tugkancr)
            if (currentUser?.isOwnerOrAdmin() == true) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController?.navigate("admin_panel") },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                    border = BorderStroke(1.dp, NeonLimeAccent.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(NeonLimeAccent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = NeonLimeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Yönetici Paneli (Admin)",
                                    color = TextOffWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                AdminBadge(customRole = "Owner", isCompact = true)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Kullanıcı arama, rol yönetimi ve yetki yapılandırması",
                                color = TextMutedWarmGrey,
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = NeonLimeAccent
                        )
                    }
                }
            }

            // Link to PersonalProfileScreen (Item #13)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController?.navigate("personal_profile") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NeonLimeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kişisel Profil & Fotoğraf",
                            color = TextOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Biyografi, yaş, doğum tarihi, fotoğraf ve gizlilik",
                            color = TextMutedWarmGrey,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMutedWarmGrey
                    )
                }
            }

            // Global Theme Mode Selector (Item #9 & #24)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Uygulama Teması",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOffWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val themeOptions = listOf(
                        "DARK" to "Karanlık (Midnight Navy OLED)",
                        "LIGHT" to "Aydınlık Mod",
                        "SYSTEM" to "Sistem Temasıyla Eşle"
                    )

                    themeOptions.forEach { (mode, label) ->
                        val isSelected = appThemeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        UserPreferences.setAppThemeMode(context, mode)
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    coroutineScope.launch {
                                        UserPreferences.setAppThemeMode(context, mode)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonLimeAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextOffWhite
                            )
                        }
                    }
                }
            }

            // Privacy & Security Card Link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController?.navigate("privacy") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NeonLimeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.privacyAndSecurity,
                            color = TextOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.blockedAccountsDesc,
                            color = TextMutedWarmGrey,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMutedWarmGrey
                    )
                }
            }

            // Storage & Data Card Link (Storage Manager)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController?.navigate("storage_manager") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NeonLimeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Depolama ve Veri",
                            color = TextOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Önbellek boyutu, ses kayıtları ve medya temizleme",
                            color = TextMutedWarmGrey,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMutedWarmGrey
                    )
                }
            }

            // Language Selector Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.appLanguage,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOffWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Language.entries.forEach { lang ->
                        val isSelected = selectedLanguageCode == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SurfaceMidnightDark else Color.Transparent)
                                .clickable {
                                    if (selectedLanguageCode != lang.code) {
                                        selectedLanguageCode = lang.code
                                        coroutineScope.launch {
                                            try {
                                                UserPreferences.updateLanguage(context, lang.code)
                                                currentUser?.let { u ->
                                                    val updated = u.copy(language = lang.code)
                                                    CloudRealtimeService.registerUser(updated)
                                                }
                                                snackbarHostState.showSnackbar(strings.changesSaved)
                                            } catch (e: Exception) {
                                                // ignore
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    if (selectedLanguageCode != lang.code) {
                                        selectedLanguageCode = lang.code
                                        coroutineScope.launch {
                                            UserPreferences.updateLanguage(context, lang.code)
                                            currentUser?.let { u ->
                                                val updated = u.copy(language = lang.code)
                                                CloudRealtimeService.registerUser(updated)
                                            }
                                            snackbarHostState.showSnackbar(strings.changesSaved)
                                        }
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonLimeAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = lang.flag, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = lang.displayName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextOffWhite
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }

            // ==========================================
            // APPLICATION UPDATES & OTA CARD (Section 9)
            // ==========================================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(NeonLimeAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = NeonLimeAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Uygulama Güncellemeleri",
                                color = TextOffWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val statusSubtitle = when (val state = updateState) {
                                is UpdateState.Checking -> "Güncellemeler denetleniyor..."
                                is UpdateState.Downloading -> "İndiriliyor: %${state.progressPercent} (${state.bytesDownloaded / (1024 * 1024)}MB / ${if (state.totalBytes > 0) "${state.totalBytes / (1024 * 1024)}MB" else "..."})"
                                is UpdateState.ReadyToInstall -> "Yeni sürüm (v${state.manifest.versionName}) kuruluma hazır"
                                is UpdateState.UpdateAvailable -> "Yeni sürüm mevcut: v${state.manifest.versionName} (Kod ${state.manifest.versionCode})"
                                is UpdateState.UpToDate -> "En güncel sürüm yüklü: v${BuildConfig.VERSION_NAME}"
                                is UpdateState.Error -> state.message
                                UpdateState.Idle -> "Mevcut sürüm: v${BuildConfig.VERSION_NAME} (Kod ${BuildConfig.VERSION_CODE})"
                            }
                            Text(
                                text = statusSubtitle,
                                color = when (updateState) {
                                    is UpdateState.UpdateAvailable, is UpdateState.ReadyToInstall -> NeonLimeAccent
                                    is UpdateState.Error -> DestructiveCoralRed
                                    else -> TextMutedWarmGrey
                                },
                                fontSize = 12.sp,
                                maxLines = 2
                            )
                        }
                    }

                    // Download Progress Bar
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = NeonLimeAccent,
                            trackColor = SurfaceMidnightDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Paket indiriliyor...", color = NeonLimeAccent, fontSize = 11.sp)
                            Text(text = "%$downloadProgress", color = TextOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Action Buttons
                    when (val state = updateState) {
                        is UpdateState.Checking -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMidnightDark)
                            ) {
                                CircularProgressIndicator(
                                    color = NeonLimeAccent,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Kontrol Ediliyor...", color = TextOffWhite, fontSize = 13.sp)
                            }
                        }
                        is UpdateState.UpdateAvailable -> {
                            Button(
                                onClick = { AppUpdateManager.startDownload(context, state.manifest) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonLimeAccent)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = SurfaceMidnightDark, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Güncellemeyi İndir (v${state.manifest.versionName})",
                                    color = SurfaceMidnightDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = { AppUpdateManager.installApk(context, state.apkFile, state.manifest) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E))
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Güncellemeyi Yükle",
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        is UpdateState.Error -> {
                            Button(
                                onClick = { AppUpdateManager.checkForUpdates(context, manual = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMidnightDark),
                                border = BorderStroke(1.dp, BorderSlateNavy)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonLimeAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tekrar Dene", color = TextOffWhite, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Button(
                                onClick = { AppUpdateManager.checkForUpdates(context, manual = true) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceMidnightDark),
                                border = BorderStroke(1.dp, BorderSlateNavy)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonLimeAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Güncellemeleri Denetle", color = TextOffWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // About & Updates Card Link
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController?.navigate("about") },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, BorderSlateNavy)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NeonLimeAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.aboutTitle,
                            color = TextOffWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.aboutDesc,
                            color = TextMutedWarmGrey,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = TextMutedWarmGrey
                    )
                }
            }

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceSlateNavy),
                border = BorderStroke(1.dp, CallEndCoralRed)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = strings.logout,
                        tint = CallEndCoralRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.logout,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CallEndCoralRed
                    )
                }
            }

            // Footer Version Info (Item #14)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextMutedWarmGrey,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Zilbio v3.0.1 (WebRTC + Mesh Live Call)",
                    fontSize = 12.sp,
                    color = TextMutedWarmGrey,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = strings.logout,
                    color = TextOffWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${strings.logout}?",
                    color = TextMutedWarmGrey
                )
            },
            containerColor = SurfaceDarkWarmElevated,
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            currentUser?.userId?.let { uid ->
                                try {
                                    CloudRealtimeService.updateUserPresence(uid, false)
                                    CloudRealtimeService.updateUserStatus(uid, "offline")
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                            UserPreferences.clear(context)
                            showLogoutDialog = false
                            onLogout()
                        }
                    }
                ) {
                    Text(text = strings.logout, color = CallEndCoralRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = strings.cancel, color = TextOffWhite)
                }
            }
        )
    }
}
