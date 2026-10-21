package com.android.zilbio.ui.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.User
import com.android.zilbio.ui.theme.AccentAmberOrange
import com.android.zilbio.ui.theme.BackgroundAlabasterCream
import com.android.zilbio.ui.theme.BorderWarmSubtle
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.OutgoingBubblePeach
import com.android.zilbio.ui.theme.SurfaceCardWhite
import com.android.zilbio.ui.theme.TextPrimaryEspresso
import com.android.zilbio.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.launch
import okhttp3.Call

@Composable
fun HomeScreen(
    navController: NavController? = null,
    onNavigateToChatList: () -> Unit = { navController?.navigate("chatList") },
    onNavigateToFriends: () -> Unit = { navController?.navigate("friends") },
    onNavigateToSettings: () -> Unit = { navController?.navigate("settings") }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    var isOnline by remember { mutableStateOf(true) }

    // Update status to online and listen for incoming calls via SSE
    LaunchedEffect(currentUser?.userId) {
        val userId = currentUser?.userId
        if (!userId.isNullOrEmpty()) {
            CloudRealtimeService.updateUserStatus(userId, "online")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundAlabasterCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar with Logo & Online Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.android.zilbio.R.drawable.zilbio_logo_transparent),
                        contentDescription = "Zilbio Logo",
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Zilbio",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmberOrange
                    )
                }

                // Connection Status Indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceCardWhite,
                    border = BorderStroke(1.dp, BorderWarmSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) OnlineStatusGreen else TextSecondaryWarmGrey)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isOnline) "Çevrimiçi" else "Çevrimdışı",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondaryWarmGrey
                        )
                    }
                }
            }

            // Welcome Header Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
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
                    val displayName = currentUser?.displayName ?: "Kullanıcı"
                    val userLang = Language.fromCode(currentUser?.language ?: "tr")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = "Merhaba, $displayName",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryEspresso
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Anında konuş, canlı çevir.",
                                fontSize = 14.sp,
                                color = TextSecondaryWarmGrey
                            )
                        }

                        // Language Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = OutgoingBubblePeach
                        ) {
                            Text(
                                text = "${userLang.flag} ${userLang.displayName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentAmberOrange,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Copyable User ID
                    currentUser?.userId?.let { id ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BackgroundAlabasterCream,
                            border = BorderStroke(1.dp, BorderWarmSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Zilbio ID", id)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Kullanıcı ID panoya kopyalandı", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "ID: ",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextSecondaryWarmGrey
                                    )
                                    Text(
                                        text = id,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentAmberOrange
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Kopyala",
                                    tint = TextSecondaryWarmGrey,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Hızlı İşlemler",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryEspresso
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 2-Column Action Cards
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    QuickActionCard(
                        title = "Sohbetler",
                        subtitle = "Mesajlaşma ve ses",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onClick = onNavigateToChatList
                    )
                }

                item {
                    QuickActionCard(
                        title = "Arkadaşlar",
                        subtitle = "Kişiler ve istekler",
                        icon = Icons.Default.People,
                        onClick = onNavigateToFriends
                    )
                }

                item {
                    QuickActionCard(
                        title = "Arama Geçmişi",
                        subtitle = "Son görüşmeler",
                        icon = Icons.Default.History,
                        onClick = {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Arama geçmişi özelliği çok yakında!")
                            }
                        }
                    )
                }

                item {
                    QuickActionCard(
                        title = "Ayarlar",
                        subtitle = "Profil ve diller",
                        icon = Icons.Default.Settings,
                        onClick = onNavigateToSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
        border = BorderStroke(1.dp, BorderWarmSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OutgoingBubblePeach),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = AccentAmberOrange,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryEspresso
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextSecondaryWarmGrey,
                    maxLines = 1
                )
            }
        }
    }
}
