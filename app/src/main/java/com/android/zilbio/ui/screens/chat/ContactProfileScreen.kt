package com.android.zilbio.ui.screens.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.ChatCustomization
import com.android.zilbio.domain.model.User
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.components.VerifiedBadge
import com.android.zilbio.data.rewards.BadgeRewardManager
import com.android.zilbio.data.rewards.Badge
import com.android.zilbio.ui.components.ProfileBadgesCard
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.ChatThemeOptions
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactProfileScreen(
    navController: NavController? = null,
    friendId: String = "",
    contactUserId: String = friendId,
    chatId: String = "",
    onNavigateBack: () -> Unit = { navController?.popBackStack() },
    onStartAudioCall: () -> Unit = { navController?.navigate("call/$contactUserId") },
    onStartVideoCall: () -> Unit = { navController?.navigate("call/$contactUserId?isVideo=true") },
    onOpenChat: () -> Unit = { navController?.navigate("chat/$contactUserId") }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contactUser by remember { mutableStateOf<User?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }

    val chatCustomization by UserPreferences.getChatCustomization(context, chatId)
        .collectAsState(initial = ChatCustomization())

    var selectedThemeId by remember { mutableStateOf(chatCustomization.themeId) }
    var brightnessSlider by remember { mutableFloatStateOf(chatCustomization.brightness) }

    LaunchedEffect(chatCustomization) {
        selectedThemeId = chatCustomization.themeId
        brightnessSlider = chatCustomization.brightness
    }

    var contactBadges by remember { mutableStateOf<List<Badge>>(emptyList()) }

    LaunchedEffect(contactUserId) {
        val user = CloudRealtimeService.searchUserById(contactUserId)
        contactUser = user
        if (contactUserId.isNotBlank()) {
            contactBadges = BadgeRewardManager.fetchContactBadges(contactUserId)
        }
    }

    Scaffold(
        containerColor = BackgroundMidnightNavy,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kişi Bilgisi",
                        color = TextOffWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextOffWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* More actions */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Diğer",
                            tint = TextOffWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundMidnightNavy
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Hero Header (88dp Avatar, Name, Handle, Status)
            val avatarBitmap = remember(contactUser?.avatarBase64) {
                contactUser?.avatarBase64?.let { base64 ->
                    try {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            Box(contentAlignment = Alignment.BottomEnd) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Profil",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .border(2.dp, AccentNeonLime, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(SurfaceSlateNavyElevated)
                            .border(2.dp, BorderSlateNavy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (contactUser?.displayName?.take(1) ?: contactUser?.username?.take(1) ?: "?").uppercase(),
                            color = AccentNeonLime,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (contactUser?.status == "online") {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(OnlineStatusGreen)
                            .border(2.dp, BackgroundMidnightNavy, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = contactUser?.displayName?.ifBlank { contactUser?.username } ?: "Kullanıcı",
                    color = TextOffWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (contactUser?.isOwnerOrAdmin() == true) {
                    AdminBadge(customRole = contactUser?.permissions?.customRoleLabel)
                } else if (contactUser?.permissions?.isVerifiedBadge == true) {
                    VerifiedBadge()
                }
            }


            Text(
                text = "@${contactUser?.username ?: ""}",
                color = TextMutedSlateGrey,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            val statusText = if (contactUser?.status == "online") {
                "Çevrimiçi"
            } else if ((contactUser?.lastSeen ?: 0L) > 0L) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(contactUser!!.lastSeen))
                "Son çevrimiçi $timeStr"
            } else {
                "Çevrimdışı"
            }

            Text(
                text = statusText,
                color = if (contactUser?.status == "online") OnlineStatusGreen else TextMutedSlateGrey,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 4 Quick-Action Pills in a Row (Sustur, Ara, Görüntülü, Sohbet)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionPill(
                    icon = Icons.Default.NotificationsOff,
                    label = if (isMuted) "Susturuldu" else "Sustur",
                    isActive = isMuted,
                    onClick = { isMuted = !isMuted }
                )
                ActionPill(
                    icon = Icons.Default.Call,
                    label = "Ara",
                    onClick = onStartAudioCall
                )
                ActionPill(
                    icon = Icons.Default.Videocam,
                    label = "Görüntülü",
                    onClick = onStartVideoCall
                )
                ActionPill(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    label = "Sohbet",
                    onClick = onOpenChat
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Details Card (Bio, Age, Birth Date)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hakkında ve Detaylar",
                        color = AccentNeonLime,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val bioText = contactUser?.bio?.ifBlank { "Henüz bir durum bilgisi eklenmedi." } ?: "..."
                    Text(
                        text = bioText,
                        color = TextOffWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    if ((contactUser?.age ?: 0) > 0 || !contactUser?.birthDate.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderSlateNavy, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if ((contactUser?.age ?: 0) > 0) {
                                Text(text = "Yaş", color = TextMutedSlateGrey, fontSize = 13.sp)
                                Text(text = "${contactUser?.age}", color = TextOffWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                            if (!contactUser?.birthDate.isNullOrBlank()) {
                                Text(text = "Doğum Tarihi", color = TextMutedSlateGrey, fontSize = 13.sp)
                                Text(text = contactUser?.birthDate ?: "", color = TextOffWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProfileBadgesCard(
                badges = contactBadges,
                title = "${contactUser?.displayName?.ifBlank { contactUser?.username } ?: "Kullanıcı"} Başarı Rozetleri 🏆"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Per-Chat Theme & Brightness Customization
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDialog = !showThemeDialog },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = AccentNeonLime)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = "Sohbet Teması & Parlaklık", color = TextOffWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = ChatThemeOptions.find { it.id == selectedThemeId }?.name?.take(15) ?: "Varsayılan",
                            color = AccentNeonLime,
                            fontSize = 13.sp
                        )
                    }

                    if (showThemeDialog) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = "Renk Paleti Seçin (8 Tema)", color = TextMutedSlateGrey, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        ChatThemeOptions.chunked(4).forEach { rowList ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                rowList.forEach { theme ->
                                    val isSelected = theme.id == selectedThemeId
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(theme.background)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) AccentNeonLime else BorderSlateNavy,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedThemeId = theme.id
                                                scope.launch {
                                                    UserPreferences.saveChatCustomization(
                                                        context,
                                                        chatId,
                                                        selectedThemeId,
                                                        brightnessSlider
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(theme.accent)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Duvar Kağıdı Parlaklığı: ${(brightnessSlider * 100).toInt()}%", color = TextMutedSlateGrey, fontSize = 12.sp)
                        Slider(
                            value = brightnessSlider,
                            onValueChange = { newVal ->
                                brightnessSlider = newVal
                                scope.launch {
                                    UserPreferences.saveChatCustomization(
                                        context,
                                        chatId,
                                        selectedThemeId,
                                        newVal
                                    )
                                }
                            },
                            valueRange = 0.1f..1.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentNeonLime,
                                activeTrackColor = AccentNeonLime,
                                inactiveTrackColor = BorderSlateNavy
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Disappearing Messages Card (Süreli Mesajlar)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDisappearingDialog = !showDisappearingDialog },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentNeonLime)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = "Süreli Mesajlar", color = TextOffWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "Yeni mesajların belirlenen sürede kaybolması",
                                    color = TextMutedSlateGrey,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        val currentLabel = when (chatCustomization.disappearingDurationMs) {
                            86_400_000L -> "24 Saat"
                            604_800_000L -> "7 Gün"
                            else -> "Kapalı"
                        }
                        Text(
                            text = currentLabel,
                            color = AccentNeonLime,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (showDisappearingDialog) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = BorderSlateNavy, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        val durationOptions = listOf(
                            0L to "Kapalı (Süresiz)",
                            86_400_000L to "24 Saat",
                            604_800_000L to "7 Gün"
                        )

                        durationOptions.forEach { (durationMs, label) ->
                            val isSelected = chatCustomization.disappearingDurationMs == durationMs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch {
                                            UserPreferences.setDisappearingMessagesDuration(context, chatId, durationMs)
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            UserPreferences.setDisappearingMessagesDuration(context, chatId, durationMs)
                                        }
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AccentNeonLime)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentNeonLime else TextOffWhite
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Actions Card (Starred messages, Block, Report)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ContactActionRow(
                        icon = Icons.Default.Star,
                        title = "Yıldızlı Mesajlar",
                        textColor = TextOffWhite,
                        iconColor = AccentNeonLime,
                        onClick = { /* Starred messages */ }
                    )
                    HorizontalDivider(color = BorderSlateNavy, thickness = 0.5.dp)
                    ContactActionRow(
                        icon = Icons.Default.Block,
                        title = "Kişiyi Engelle",
                        textColor = DestructiveCoralRed,
                        iconColor = DestructiveCoralRed,
                        onClick = { /* Block */ }
                    )
                    HorizontalDivider(color = BorderSlateNavy, thickness = 0.5.dp)
                    ContactActionRow(
                        icon = Icons.Default.Warning,
                        title = "Şikayet Et",
                        textColor = DestructiveCoralRed,
                        iconColor = DestructiveCoralRed,
                        onClick = { /* Report */ }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isActive) AccentNeonLime else SurfaceSlateNavyElevated)
                .border(1.dp, if (isActive) AccentNeonLime else BorderSlateNavy, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) BackgroundMidnightNavy else AccentNeonLime,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) AccentNeonLime else TextOffWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ContactActionRow(
    icon: ImageVector,
    title: String,
    textColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
