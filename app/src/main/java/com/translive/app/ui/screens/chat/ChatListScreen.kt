package com.translive.app.ui.screens.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.translive.app.data.local.UserPreferences
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.domain.model.Friend
import com.translive.app.domain.model.Language
import com.translive.app.domain.model.Message
import com.translive.app.ui.screens.friends.AvatarInitials
import com.translive.app.ui.theme.AccentAmberOrange
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.BorderWarmSubtle
import com.translive.app.ui.theme.OnlineStatusGreen
import com.translive.app.ui.theme.OutgoingBubblePeach
import com.translive.app.ui.theme.SurfaceCardWhite
import com.translive.app.ui.theme.TextPrimaryEspresso
import com.translive.app.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.launch
import okhttp3.Call
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    navController: NavController? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() },
    onNavigateToChat: (friendId: String) -> Unit = { friendId -> navController?.navigate("chat/$friendId") },
    onNavigateToFriends: () -> Unit = { navController?.navigate("friends") }
) {
    val context = LocalContext.current
    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var friendsList by remember { mutableStateOf<List<Friend>>(emptyList()) }
    val lastMessages = remember { mutableStateMapOf<String, Message>() }
    var isLoading by remember { mutableStateOf(true) }

    val myUserId = currentUser?.userId ?: ""

    // 1. Arkadaşları ve son mesajları yükle
    LaunchedEffect(myUserId) {
        if (myUserId.isNotEmpty()) {
            isLoading = true
            try {
                val friends = CloudRealtimeService.getFriends(myUserId)
                friendsList = friends

                friends.forEach { friend ->
                    val chatId = CloudRealtimeService.generateChatId(myUserId, friend.userId)
                    val messages = CloudRealtimeService.getMessages(chatId)
                    val last = messages.lastOrNull()
                    if (last != null) {
                        lastMessages[friend.userId] = last
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                isLoading = false
            }
        }
    }

    // 2. ANLIK AKIŞ: Sohbetler listesindeyken tüm arkadaşların mesajlarını dinle
    DisposableEffect(myUserId, friendsList) {
        val calls = mutableListOf<Call>()
        if (myUserId.isNotEmpty() && friendsList.isNotEmpty()) {
            friendsList.forEach { friend ->
                val chatId = CloudRealtimeService.generateChatId(myUserId, friend.userId)
                val call = CloudRealtimeService.listenForMessages(chatId) { newMessage ->
                    coroutineScope.launch {
                        lastMessages[friend.userId] = newMessage
                    }
                }
                calls.add(call)
            }
        }
        onDispose {
            calls.forEach { it.cancel() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sohbetler",
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
        containerColor = BackgroundAlabasterCream
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AccentAmberOrange, strokeWidth = 2.dp)
                }
            } else if (friendsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(OutgoingBubblePeach),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = AccentAmberOrange,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Henüz sohbet yok",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryEspresso,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Henüz sohbet yok. Arkadaş ekleyerek başlayın!",
                            fontSize = 13.sp,
                            color = TextSecondaryWarmGrey,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onNavigateToFriends,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmberOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Arkadaşlara Git",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(friendsList, key = { it.userId }) { friend ->
                        val lastMsg = lastMessages[friend.userId]
                        val isUnread = lastMsg != null && !lastMsg.isRead && lastMsg.senderId != myUserId

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToChat(friend.userId) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
                            border = BorderStroke(1.dp, BorderWarmSubtle),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    AvatarInitials(name = friend.displayName, size = 48)
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (friend.status == "online") OnlineStatusGreen else Color.LightGray)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = friend.displayName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimaryEspresso,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val lang = Language.fromCode(friend.language)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = lang.flag, fontSize = 12.sp)
                                        }

                                        if (lastMsg != null) {
                                            val formattedTime = formatTimestamp(lastMsg.timestamp)
                                            Text(
                                                text = formattedTime,
                                                fontSize = 11.sp,
                                                color = TextSecondaryWarmGrey
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val previewText = when {
                                            lastMsg == null -> "Sohbet başlatmak için dokunun"
                                            lastMsg.type == "voice" -> "🎤 Sesli Mesaj"
                                            else -> lastMsg.translatedText.ifEmpty { lastMsg.originalText }
                                        }

                                        Text(
                                            text = previewText,
                                            fontSize = 13.sp,
                                            color = if (isUnread) TextPrimaryEspresso else TextSecondaryWarmGrey,
                                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        if (isUnread) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(AccentAmberOrange)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault())

    val diffDays = (now.time - date.time) / (1000 * 60 * 60 * 24)
    return if (diffDays == 0L) {
        timeFormat.format(date)
    } else {
        dateFormat.format(date)
    }
}
