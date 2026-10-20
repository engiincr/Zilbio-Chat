package com.translive.app.ui.screens.settings

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
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
import com.translive.app.data.local.UserPreferences
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.domain.model.Language
import com.translive.app.domain.model.User
import com.translive.app.ui.screens.friends.AvatarInitials
import com.translive.app.ui.theme.AccentAmberOrange
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.BorderWarmSubtle
import com.translive.app.ui.theme.CallEndCoralRed
import com.translive.app.ui.theme.InputFieldBackground
import com.translive.app.ui.theme.OutgoingBubblePeach
import com.translive.app.ui.theme.SurfaceCardWhite
import com.translive.app.ui.theme.TextPrimaryEspresso
import com.translive.app.ui.theme.TextSecondaryWarmGrey
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
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)

    var editableDisplayName by remember { mutableStateOf("") }
    var selectedLanguageCode by remember { mutableStateOf("tr") }
    var isSavingName by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            editableDisplayName = user.displayName
            selectedLanguageCode = user.language.ifEmpty { "tr" }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ayarlar",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryEspresso
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimaryEspresso
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundAlabasterCream
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundAlabasterCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Section Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                border = BorderStroke(1.dp, BorderWarmSubtle),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Avatar Circle with Edit badge
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AvatarInitials(name = editableDisplayName.ifEmpty { "Kullanıcı" }, size = 76)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(AccentAmberOrange)
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Profili Düzenle",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Editable Display Name Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = editableDisplayName,
                            onValueChange = { editableDisplayName = it },
                            label = { Text("Görünen İsim", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = InputFieldBackground,
                                unfocusedContainerColor = InputFieldBackground,
                                focusedIndicatorColor = AccentAmberOrange,
                                unfocusedIndicatorColor = BorderWarmSubtle,
                                focusedTextColor = TextPrimaryEspresso,
                                unfocusedTextColor = TextPrimaryEspresso
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                val trimmed = editableDisplayName.trim()
                                val user = currentUser
                                if (trimmed.isNotEmpty() && user != null) {
                                    coroutineScope.launch {
                                        isSavingName = true
                                        try {
                                            UserPreferences.updateDisplayName(context, trimmed)
                                            val updated = user.copy(displayName = trimmed)
                                            CloudRealtimeService.registerUser(updated)
                                            snackbarHostState.showSnackbar("İsim güncellendi ✓")
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Hata: ${e.message}")
                                        } finally {
                                            isSavingName = false
                                        }
                                    }
                                }
                            },
                            enabled = !isSavingName && editableDisplayName.trim() != currentUser?.displayName,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (editableDisplayName.trim() != currentUser?.displayName) AccentAmberOrange
                                    else InputFieldBackground
                                )
                        ) {
                            if (isSavingName) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Kaydet",
                                    tint = if (editableDisplayName.trim() != currentUser?.displayName) Color.White else TextSecondaryWarmGrey
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // @username (Read-Only)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = InputFieldBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kullanıcı Adı: ",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondaryWarmGrey
                            )
                            Text(
                                text = "@${currentUser?.username ?: "-"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryEspresso
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // User ID (Read-Only with Copy button)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val uid = currentUser?.userId ?: ""
                                if (uid.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("User ID", uid)
                                    clipboard.setPrimaryClip(clip)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Kullanıcı ID kopyalandı ✓")
                                    }
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = InputFieldBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Hesap ID: ",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryWarmGrey
                                )
                                Text(
                                    text = currentUser?.userId ?: "-",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentAmberOrange
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "ID Kopyala",
                                tint = TextSecondaryWarmGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Language Selector Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                border = BorderStroke(1.dp, BorderWarmSubtle),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = AccentAmberOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Varsayılan Dil / Çeviri Tercihi",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryEspresso
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Language.entries.forEach { lang ->
                        val isSelected = selectedLanguageCode == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) OutgoingBubblePeach else Color.Transparent)
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
                                                snackbarHostState.showSnackbar("Dil ${lang.displayName} olarak değiştirildi ✓")
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                            snackbarHostState.showSnackbar("Dil ${lang.displayName} olarak değiştirildi ✓")
                                        }
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = AccentAmberOrange)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = lang.flag, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = lang.displayName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = TextPrimaryEspresso
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Logout Button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceCardWhite),
                border = BorderStroke(1.dp, CallEndCoralRed)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Çıkış Yap",
                        tint = CallEndCoralRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Çıkış Yap (Logout)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CallEndCoralRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Version
            Text(
                text = "TransLive v1.0.0",
                fontSize = 12.sp,
                color = TextSecondaryWarmGrey,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Çıkış Yap",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryEspresso
                )
            },
            text = {
                Text(
                    text = "Hesabınızdan çıkış yapmak istediğinize emin misiniz?",
                    fontSize = 14.sp,
                    color = TextPrimaryEspresso
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = currentUser?.userId
                        coroutineScope.launch {
                            if (!uid.isNullOrEmpty()) {
                                try {
                                    CloudRealtimeService.updateUserStatus(uid, "offline")
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                            UserPreferences.clear(context)
                            showLogoutDialog = false
                            onLogout()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CallEndCoralRed)
                ) {
                    Text("Evet, Çıkış Yap", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("İptal", color = TextSecondaryWarmGrey)
                }
            },
            containerColor = SurfaceCardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
