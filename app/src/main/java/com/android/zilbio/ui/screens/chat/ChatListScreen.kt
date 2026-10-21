package com.android.zilbio.ui.screens.chat

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.Message
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.components.VerifiedBadge
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.screens.friends.AvatarInitials
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
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
fun ChatListScreen(
    navController: NavController? = null,
    onNavigateToChat: (friendId: String) -> Unit = { friendId -> navController?.navigate("chat/$friendId") },
    onNavigateToFriends: () -> Unit = { navController?.navigate("friends") }
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    val pinnedChats by UserPreferences.getPinnedChats(context).collectAsState(initial = emptySet())
    val searchHistory by UserPreferences.getSearchHistory(context).collectAsState(initial = emptyList())
    val realtimeFriends by com.android.zilbio.service.GlobalRealtimeSyncManager.friendsFlow.collectAsState()
    val realtimeLastMessages by com.android.zilbio.service.GlobalRealtimeSyncManager.lastMessagesFlow.collectAsState()

    var friendsList by remember { mutableStateOf<List<Friend>>(emptyList()) }
    val lastMessages = remember { mutableStateMapOf<String, Message>() }
    var isLoading by remember { mutableStateOf(true) }

    val myUserId = currentUser?.userId ?: ""

    LaunchedEffect(realtimeFriends) {
        if (realtimeFriends.isNotEmpty() || friendsList.isEmpty()) {
            friendsList = realtimeFriends
            isLoading = false
        }
    }

    LaunchedEffect(realtimeLastMessages) {
        realtimeLastMessages.forEach { (_, msg) ->
            val friendId = if (msg.senderId == myUserId) msg.receiverId else msg.senderId
            lastMessages[friendId] = msg
        }
    }

    // Fetch initial friends & sync cache
    LaunchedEffect(myUserId) {
        if (myUserId.isNotEmpty()) {
            if (friendsList.isEmpty()) isLoading = true
            try {
                val friends = CloudRealtimeService.getFriends(myUserId)
                friendsList = friends
                com.android.zilbio.service.GlobalRealtimeSyncManager.friendsFlow.value = friends
            } catch (e: Exception) {
                // Keep state
            } finally {
                isLoading = false
            }
        }
    }

    // Realtime listen for last messages across all chats (Item #12)
    LaunchedEffect(myUserId) {
        if (myUserId.isNotEmpty()) {
            CloudRealtimeService.listenToLastMessages { _, msg ->
                val friendId = if (msg.senderId == myUserId) msg.receiverId else msg.senderId
                lastMessages[friendId] = msg
            }
        }
    }

    // Sort friends: Pinned chats first (Item #19), then by last message timestamp
    val sortedFriends = remember(friendsList, pinnedChats, lastMessages.toMap()) {
        friendsList.sortedWith(
            compareByDescending<Friend> { friend ->
                val chatId = CloudRealtimeService.generateChatId(myUserId, friend.userId)
                pinnedChats.contains(chatId)
            }.thenByDescending { friend ->
                lastMessages[friend.userId]?.timestamp ?: 0L
            }
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Filter by search query across contact name, username, and last messages
    val displayFriends = remember(sortedFriends, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedFriends
        } else {
            sortedFriends.filter { friend ->
                val nameMatch = friend.displayName.contains(searchQuery, ignoreCase = true) ||
                        friend.username.contains(searchQuery, ignoreCase = true)
                val msg = lastMessages[friend.userId]
                val msgMatch = msg?.originalText?.contains(searchQuery, ignoreCase = true) == true ||
                        msg?.translatedText?.contains(searchQuery, ignoreCase = true) == true
                nameMatch || msgMatch
            }
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(color = TextOffWhite, fontSize = 16.sp),
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    scope.launch { UserPreferences.addSearchQuery(context, searchQuery.trim()) }
                                }
                            }),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Sohbet veya mesajlarda ara...", color = TextMutedSlateGrey, fontSize = 15.sp)
                                }
                                innerTextField()
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kapat", tint = TextOffWhite)
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Temizle", tint = TextOffWhite)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceSlateNavyElevated)
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = com.android.zilbio.R.drawable.zilbio_logo_transparent),
                                contentDescription = "Zilbio Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sohbetler",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextOffWhite
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Ara",
                                tint = TextOffWhite
                            )
                        }
                        IconButton(onClick = onNavigateToFriends) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = strings.addFriend,
                                tint = AccentNeonLime
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundMidnightNavy
                    )
                )
            }
        },
        containerColor = BackgroundMidnightNavy
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
                    CircularProgressIndicator(color = AccentNeonLime, strokeWidth = 2.dp)
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
                                .background(SurfaceSlateNavyElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = AccentNeonLime,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = strings.noChatsYet,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOffWhite,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = strings.noChatsDesc,
                            fontSize = 13.sp,
                            color = TextMutedSlateGrey,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onNavigateToFriends,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentNeonLime, contentColor = BackgroundMidnightNavy),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.startChatWithFriends,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isSearchActive && searchQuery.isBlank() && searchHistory.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Son Aramalar",
                                        color = TextMutedSlateGrey,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Temizle",
                                        color = AccentNeonLime,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable {
                                            scope.launch { UserPreferences.clearSearchHistory(context) }
                                        }
                                    )
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(searchHistory) { item ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = SurfaceSlateNavyElevated,
                                            modifier = Modifier.clickable { searchQuery = item }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = item, fontSize = 12.sp, color = TextOffWhite)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Sil",
                                                    tint = TextMutedSlateGrey,
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clickable {
                                                            scope.launch {
                                                                UserPreferences.removeSearchQuery(context, item)
                                                            }
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (displayFriends.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = TextMutedSlateGrey,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Sonuç bulunamadı",
                                        color = TextOffWhite,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "\"$searchQuery\" ile eşleşen sohbet bulunamadı.",
                                        color = TextMutedSlateGrey,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    items(displayFriends, key = { it.userId }) { friend ->
                        val chatId = CloudRealtimeService.generateChatId(myUserId, friend.userId)
                        val isPinned = pinnedChats.contains(chatId)
                        val lastMsg = lastMessages[friend.userId]

                        ChatItemRow(
                            friend = friend,
                            lastMessage = lastMsg,
                            currentUserId = myUserId,
                            isPinned = isPinned,
                            onLongPress = {
                                scope.launch {
                                    UserPreferences.togglePinnedChat(context, chatId)
                                }
                            },
                            onClick = { onNavigateToChat(friend.userId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatItemRow(
    friend: Friend,
    lastMessage: Message?,
    currentUserId: String,
    isPinned: Boolean = false,
    onLongPress: () -> Unit = {},
    onClick: () -> Unit
) {
    val isUnread = lastMessage != null &&
            lastMessage.senderId != currentUserId &&
            !lastMessage.isRead &&
            lastMessage.deliveryStatus != "READ"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPinned) SurfaceSlateNavyElevated else SurfaceSlateNavy
        ),
        border = if (isPinned) BorderStroke(1.dp, AccentNeonLime.copy(alpha = 0.5f)) else BorderStroke(0.5.dp, BorderSlateNavy)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with Online Badge
            Box(contentAlignment = Alignment.BottomEnd) {
                val avatarBitmap = remember(friend.avatarBase64) {
                    friend.avatarBase64?.let { base64 ->
                        try {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Profil",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                } else {
                    AvatarInitials(name = friend.displayName.ifEmpty { friend.username }, size = 48)
                }

                if (friend.status == "online") {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(OnlineStatusGreen)
                            .border(2.dp, BackgroundMidnightNavy, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = friend.displayName.ifEmpty { friend.username },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextOffWhite
                        )
                        if (friend.isOwnerOrAdmin()) {
                            AdminBadge(customRole = friend.permissions.customRoleLabel, isCompact = true)
                        } else if (friend.permissions.isVerifiedBadge) {
                            VerifiedBadge(isCompact = true)
                        }
                        val lang = Language.fromCode(friend.language)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = lang.flag, fontSize = 12.sp)

                        if (isPinned) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.PushPin, contentDescription = "Sabitlendi", tint = AccentNeonLime, modifier = Modifier.size(14.dp))
                        }
                    }

                    if (lastMessage != null) {
                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(lastMessage.timestamp))
                        Text(
                            text = timeStr,
                            fontSize = 11.sp,
                            color = if (isUnread) AccentNeonLime else TextMutedSlateGrey,
                            fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
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
                        lastMessage == null -> "@${friend.username}"
                        lastMessage.type == "voice" -> "🎤 Sesli mesaj"
                        lastMessage.type == "image" -> "📷 Fotoğraf"
                        lastMessage.type == "document" -> "📄 Belge"
                        lastMessage.type == "call_log" -> "📞 Arama kaydı"
                        lastMessage.senderId == currentUserId -> "Sen: ${lastMessage.translatedText.ifEmpty { lastMessage.originalText }}"
                        else -> lastMessage.translatedText.ifEmpty { lastMessage.originalText }
                    }

                    Text(
                        text = previewText,
                        fontSize = 13.sp,
                        color = if (isUnread) TextOffWhite else TextMutedSlateGrey,
                        fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Unread badge dot (Item #11)
                    if (isUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(AccentNeonLime)
                        )
                    }
                }
            }
        }
    }
}
