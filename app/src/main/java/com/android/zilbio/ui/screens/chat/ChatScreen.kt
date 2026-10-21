package com.android.zilbio.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.audio.AudioPlayerManager
import com.android.zilbio.data.audio.VoiceNoteManager
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.data.remote.GeminiTranslationEngine
import com.android.zilbio.domain.model.ChatCustomization
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.Message
import com.android.zilbio.domain.model.User
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.components.VerifiedBadge
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.service.GlobalRealtimeSyncManager
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.screens.friends.AvatarInitials
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.EmeraldReactionBg
import com.android.zilbio.ui.theme.EmeraldReactionBorder
import com.android.zilbio.ui.theme.EmeraldReactionText
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.QuotaWarningAmber
import com.android.zilbio.ui.theme.ScrimDarkBlur
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import com.android.zilbio.ui.theme.TickGrey
import com.android.zilbio.ui.theme.TickReadLime
import com.android.zilbio.ui.theme.getChatTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Call
import java.io.File
import com.android.zilbio.data.rewards.BadgeRewardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

private val chatIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController? = null,
    friendId: String,
    onNavigateBack: () -> Unit = { navController?.popBackStack() },
    onNavigateToCall: (friendId: String) -> Unit = { fId -> navController?.navigate("call/$fId") },
    onNavigateToVideoCall: (friendId: String) -> Unit = { fId -> navController?.navigate("call/$fId?video=true") }
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    var friendUser by remember { mutableStateOf<Friend?>(null) }
    val messages = remember { mutableStateListOf<Message>() }

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // In-chat Search State
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Quoted reply state & Swipe-to-reply
    var replyingToMessage by remember { mutableStateOf<Message?>(null) }

    // Edit message state
    var editingMessage by remember { mutableStateOf<Message?>(null) }

    // Voice recording state & controls
    val isRecordingVoice by VoiceNoteManager.isRecording.collectAsState()
    val isRecordingPaused by VoiceNoteManager.isPaused.collectAsState()
    val recordingDurationSec by VoiceNoteManager.recordingDurationSeconds.collectAsState()
    var isHandsFreeLocked by remember { mutableStateOf(false) }
    var isTranscribingVoice by remember { mutableStateOf(false) }

    // Bottom Sheets & Overlays
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showEmojiStickerSheet by remember { mutableStateOf(false) }
    var selectedMessageForMenu by remember { mutableStateOf<Message?>(null) }
    var translationDetailMessage by remember { mutableStateOf<Message?>(null) }

    // Quota warning banner
    var quotaWarningText by remember { mutableStateOf<String?>(null) }
    var showQuotaBanner by remember { mutableStateOf(false) }

    val myUserId = currentUser?.userId ?: ""
    val chatId = remember(myUserId, friendId) {
        if (myUserId.isNotEmpty() && friendId.isNotEmpty()) {
            CloudRealtimeService.generateChatId(myUserId, friendId)
        } else ""
    }

    // Per-Chat Custom Theme & Disappearing Duration
    val chatCustomization by UserPreferences.getChatCustomization(context, chatId)
        .collectAsState(initial = ChatCustomization())
    val currentTheme = remember(chatCustomization.themeId) {
        getChatTheme(chatCustomization.themeId)
    }

    // Media file pickers
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) sendMediaAttachment(uri, "image", currentUser, friendUser, chatId, friendId, chatCustomization.disappearingDurationMs, context) { messages.add(it) }
    }
    val documentPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) sendMediaAttachment(uri, "document", currentUser, friendUser, chatId, friendId, chatCustomization.disappearingDurationMs, context) { messages.add(it) }
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) sendMediaAttachment(uri, "video", currentUser, friendUser, chatId, friendId, chatCustomization.disappearingDurationMs, context) { messages.add(it) }
    }
    val audioPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) sendMediaAttachment(uri, "audio", currentUser, friendUser, chatId, friendId, chatCustomization.disappearingDurationMs, context) { messages.add(it) }
    }

    // 1. Fetch friend details and initial messages
    LaunchedEffect(friendId, myUserId) {
        if (friendId.isNotEmpty() && myUserId.isNotEmpty()) {
            try {
                val myFriends = CloudRealtimeService.getFriends(myUserId)
                val foundFriend = myFriends.find { it.userId == friendId }
                val liveUser = CloudRealtimeService.searchUserById(friendId)
                if (foundFriend != null) {
                    val resolvedLang = liveUser?.language?.ifBlank { foundFriend.language } ?: foundFriend.language.ifBlank { "tr" }
                    friendUser = foundFriend.copy(
                        language = resolvedLang,
                        displayName = liveUser?.displayName?.ifBlank { foundFriend.displayName } ?: foundFriend.displayName,
                        status = liveUser?.status ?: foundFriend.status,
                        lastSeen = liveUser?.lastSeen ?: foundFriend.lastSeen
                    )
                } else if (liveUser != null) {
                    friendUser = Friend(
                        userId = liveUser.userId,
                        username = liveUser.username,
                        displayName = liveUser.displayName,
                        avatarBase64 = liveUser.avatarBase64,
                        status = liveUser.status,
                        lastSeen = liveUser.lastSeen,
                        bio = liveUser.bio,
                        language = liveUser.language.ifBlank { "tr" }
                    )
                }

                val initialMessages = CloudRealtimeService.getMessages(chatId)
                messages.clear()
                messages.addAll(initialMessages)
            } catch (e: Exception) {
                // Ignore load error
            }
        }
    }

    // 2. Realtime SSE listener, Smart Suppression tracking & Read Status
    DisposableEffect(chatId) {
        GlobalRealtimeSyncManager.currentlyActiveChatId = chatId

        if (chatId.isNotEmpty() && myUserId.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                CloudRealtimeService.markChatAsRead(chatId, myUserId)
            }
        }

        var messageCall: Call? = null
        if (chatId.isNotEmpty()) {
            messageCall = CloudRealtimeService.listenForMessages(chatId) { newMessage ->
                coroutineScope.launch {
                    val index = messages.indexOfFirst { it.messageId == newMessage.messageId }
                    if (index >= 0) {
                        messages[index] = newMessage
                    } else {
                        messages.add(newMessage)
                        listState.animateScrollToItem(messages.size - 1)
                        if (newMessage.receiverId == myUserId) {
                            CloudRealtimeService.markChatAsRead(chatId, myUserId)
                        }
                    }
                }
            }
        }
        onDispose {
            GlobalRealtimeSyncManager.currentlyActiveChatId = null
            messageCall?.cancel()
            VoiceNoteManager.release()
            AudioPlayerManager.stop()
        }
    }

    // 3. 60-Second Hard Auto-Send Trigger for voice
    LaunchedEffect(recordingDurationSec, isRecordingVoice) {
        if (isRecordingVoice && recordingDurationSec >= VoiceNoteManager.AUTO_SEND_SECONDS) {
            val audioFile = VoiceNoteManager.stopRecording()
            isHandsFreeLocked = false
            if (audioFile != null && friendUser != null) {
                processAndSendVoiceNote(
                    audioFile = audioFile,
                    currentUser = currentUser,
                    friendUser = friendUser,
                    chatId = chatId,
                    friendId = friendId,
                    disappearingDurationMs = chatCustomization.disappearingDurationMs,
                    onSendingState = { isTranscribingVoice = it },
                    onMessageCreated = { messages.add(it) }
                )
            }
        }
    }

    // 4. Collect Quota Warning from Gemini Translation Engine
    LaunchedEffect(Unit) {
        GeminiTranslationEngine.quotaWarning.collectLatest { warningMessage ->
            quotaWarningText = warningMessage
            showQuotaBanner = true
            delay(5000L)
            showQuotaBanner = false
        }
    }

    // Filter messages for search query and disappearing messages
    val displayMessages = remember(messages.toList(), isSearching, searchQuery) {
        val now = System.currentTimeMillis()
        val nonExpired = messages.filter { msg ->
            msg.expiresAt == null || msg.expiresAt!! > now
        }
        if (isSearching && searchQuery.isNotBlank()) {
            nonExpired.filter {
                it.originalText.contains(searchQuery, ignoreCase = true) ||
                        it.translatedText.contains(searchQuery, ignoreCase = true)
            }
        } else {
            nonExpired
        }
    }

    // Pinned Message banner
    val pinnedMessage = remember(messages.toList()) {
        messages.lastOrNull { it.isPinned }
    }

    // Floating scroll to bottom
    val showScrollToBottom by remember {
        derivedStateOf {
            val total = displayMessages.size
            total > 4 && listState.firstVisibleItemIndex < total - 4
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(currentTheme.background)
    ) {
        // Brightness dimming overlay
        if (chatCustomization.brightness < 1.0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 1.0f - chatCustomization.brightness))
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (isSearching) {
                    TopAppBar(
                        title = {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(color = TextOffWhite, fontSize = 16.sp),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text("Mesajlarda ara...", color = TextMutedSlateGrey, fontSize = 15.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { isSearching = false; searchQuery = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kapat", tint = TextOffWhite)
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                Text(
                                    text = "${displayMessages.size} sonuç",
                                    color = AccentNeonLime,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle", tint = TextOffWhite)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceSlateNavy)
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    navController?.navigate("contact_profile/$friendId/$chatId")
                                }
                            ) {
                                Box(contentAlignment = Alignment.BottomEnd) {
                                    AvatarInitials(name = friendUser?.displayName ?: "User", size = 40)
                                    if (friendUser?.status == "online") {
                                        Box(
                                            modifier = Modifier
                                                .size(11.dp)
                                                .clip(CircleShape)
                                                .background(OnlineStatusGreen)
                                                .border(2.dp, BackgroundMidnightNavy, CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = friendUser?.displayName ?: "...",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextOffWhite
                                        )
                                        if (friendUser?.isOwnerOrAdmin() == true) {
                                            AdminBadge(customRole = friendUser?.permissions?.customRoleLabel, isCompact = true)
                                        } else if (friendUser?.permissions?.isVerifiedBadge == true) {
                                            VerifiedBadge(isCompact = true)
                                        }
                                        val lang = Language.fromCode(friendUser?.language ?: "tr")
                                        Text(text = lang.flag, fontSize = 12.sp)
                                    }
                                    val statusText = if (friendUser?.status == "online") {
                                        "Çevrimiçi"
                                    } else if ((friendUser?.lastSeen ?: 0L) > 0L) {
                                        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(friendUser!!.lastSeen))
                                        "Son çevrimiçi $timeStr"
                                    } else {
                                        "Çevrimdışı"
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 11.sp,
                                        color = if (friendUser?.status == "online") OnlineStatusGreen else TextMutedSlateGrey
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.cancel, tint = TextOffWhite)
                            }
                        },
                        actions = {
                            // Search toggle
                            IconButton(onClick = { isSearching = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Ara", tint = TextOffWhite)
                            }
                            val canCall = currentUser?.permissions?.canStartCalls ?: true
                            if (canCall) {
                                // Video call
                                IconButton(onClick = { onNavigateToVideoCall(friendId) }) {
                                    Icon(Icons.Default.Videocam, contentDescription = "Görüntülü Ara", tint = AccentNeonLime)
                                }
                                // Audio call
                                IconButton(onClick = { onNavigateToCall(friendId) }) {
                                    Icon(Icons.Default.Call, contentDescription = "Sesli Ara", tint = AccentNeonLime)
                                }
                            }
                        },

                        colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceSlateNavy.copy(alpha = 0.95f))
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Sticky Pinned Message Banner
                pinnedMessage?.let { pinned ->
                    Surface(
                        color = SurfaceSlateNavyElevated,
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val idx = displayMessages.indexOfFirst { it.messageId == pinned.messageId }
                                if (idx >= 0) {
                                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = "Sabitlendi", tint = AccentNeonLime, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Sabitlenen Mesaj", color = AccentNeonLime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = pinned.translatedText.ifBlank { pinned.originalText },
                                    color = TextOffWhite,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        CloudRealtimeService.togglePinMessage(chatId, pinned.messageId, false)
                                    }
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Kaldır", tint = TextMutedSlateGrey, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Disappearing Messages Banner (if active)
                if (chatCustomization.disappearingDurationMs > 0L) {
                    val durationText = when (chatCustomization.disappearingDurationMs) {
                        86_400_000L -> "24 Saat"
                        604_800_000L -> "7 Gün"
                        else -> "Süreli"
                    }
                    Surface(
                        color = SurfaceSlateNavyElevated.copy(alpha = 0.7f),
                        border = BorderStroke(0.5.dp, BorderSlateNavy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentNeonLime, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Süreli mesajlar etkin: Yeni mesajlar $durationText sonra kaybolacak.",
                                color = TextMutedSlateGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Quota Warning Banner
                AnimatedVisibility(
                    visible = showQuotaBanner,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = QuotaWarningAmber.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, QuotaWarningAmber.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = quotaWarningText ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextOffWhite,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Message List
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayMessages, key = { it.messageId }) { msg ->
                            val isOutgoing = msg.senderId == myUserId
                            SwipeableMessageContainer(
                                onSwipeReply = { replyingToMessage = msg }
                            ) {
                                MessageBubble(
                                    message = msg,
                                    isOutgoing = isOutgoing,
                                    friendName = friendUser?.displayName ?: "Arkadaş",
                                    friendAvatar = friendUser?.avatarBase64,
                                    friendIsAdmin = friendUser?.isOwnerOrAdmin() == true,
                                    myIsAdmin = currentUser?.isOwnerOrAdmin() == true,
                                    currentUserId = myUserId,
                                    bubbleOutgoingColor = currentTheme.bubbleOutgoing,
                                    bubbleIncomingColor = currentTheme.bubbleIncoming,
                                    accentColor = currentTheme.accent,
                                    onDoubleTap = {
                                        coroutineScope.launch {
                                            CloudRealtimeService.toggleReaction(chatId, msg.messageId, myUserId, "❤️")
                                        }
                                    },
                                    onLongPress = {
                                        selectedMessageForMenu = msg
                                    },
                                    onQuickReplyCall = { replyText ->
                                        sendMessage(
                                            text = replyText,
                                            currentUser = currentUser,
                                            friendUser = friendUser,
                                            chatId = chatId,
                                            friendId = friendId,
                                            disappearingDurationMs = chatCustomization.disappearingDurationMs,
                                            context = context,
                                            onClear = { inputText = "" },
                                            onSendState = { isSending = it },
                                            onMessageCreated = { messages.add(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Floating scroll-to-bottom FAB
                    if (showScrollToBottom) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (displayMessages.isNotEmpty()) {
                                        listState.animateScrollToItem(displayMessages.size - 1)
                                    }
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                                .size(42.dp),
                            containerColor = SurfaceSlateNavyElevated,
                            contentColor = AccentNeonLime,
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Aşağı kaydır")
                        }
                    }
                }

                // Voice Transcribing / Uploading Banner
                if (isTranscribingVoice) {
                    Surface(
                        color = SurfaceSlateNavyElevated,
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AccentNeonLime,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = strings.translatingLive,
                                color = AccentNeonLime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Edit Message Banner
                editingMessage?.let { editTarget ->
                    Surface(
                        color = SurfaceSlateNavyElevated,
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = AccentNeonLime, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Mesajı Düzenle",
                                    color = AccentNeonLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = editTarget.originalText,
                                    color = TextMutedSlateGrey,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingMessage = null
                                    inputText = ""
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = TextMutedSlateGrey, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Quoted Reply Preview Bar
                replyingToMessage?.let { replyMsg ->
                    if (editingMessage == null) {
                        Surface(
                            color = SurfaceSlateNavyElevated,
                            border = BorderStroke(1.dp, BorderSlateNavy),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(36.dp)
                                        .background(AccentNeonLime, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (replyMsg.senderId == myUserId) "Sen" else (friendUser?.displayName ?: "Arkadaş"),
                                        color = AccentNeonLime,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = replyMsg.translatedText.ifBlank { replyMsg.originalText },
                                        color = TextMutedSlateGrey,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { replyingToMessage = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = strings.cancel, tint = TextMutedSlateGrey, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Active Voice Recording Bar (with Pause/Resume and Cancel/Send)
                if (isRecordingVoice) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .background(BackgroundMidnightNavy)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(27.dp),
                        color = SurfaceSlateNavy,
                        border = BorderStroke(1.dp, DestructiveCoralRed.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Pulsing / Paused Status Indicator
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecordingPaused) QuotaWarningAmber else DestructiveCoralRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            val mins = recordingDurationSec / 60
                            val secs = recordingDurationSec % 60
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                color = if (isRecordingPaused) QuotaWarningAmber else TextOffWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (isRecordingPaused) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Duraklatıldı",
                                    color = QuotaWarningAmber,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Pause / Resume Toggle
                            IconButton(
                                onClick = {
                                    if (isRecordingPaused) {
                                        VoiceNoteManager.resumeRecording()
                                    } else {
                                        VoiceNoteManager.pauseRecording()
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceSlateNavyElevated)
                            ) {
                                Icon(
                                    imageVector = if (isRecordingPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = if (isRecordingPaused) "Devam Et" else "Duraklat",
                                    tint = if (isRecordingPaused) AccentNeonLime else QuotaWarningAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Delete / Cancel Recording
                            IconButton(
                                onClick = {
                                    VoiceNoteManager.cancelRecording()
                                    isHandsFreeLocked = false
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(DestructiveCoralRed.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "İptal",
                                    tint = DestructiveCoralRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Stop & Send
                            IconButton(
                                onClick = {
                                    val audioFile = VoiceNoteManager.stopRecording()
                                    isHandsFreeLocked = false
                                    if (audioFile != null && friendUser != null) {
                                        processAndSendVoiceNote(
                                            audioFile = audioFile,
                                            currentUser = currentUser,
                                            friendUser = friendUser,
                                            chatId = chatId,
                                            friendId = friendId,
                                            disappearingDurationMs = chatCustomization.disappearingDurationMs,
                                            onSendingState = { isTranscribingVoice = it },
                                            onMessageCreated = { messages.add(it) }
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(AccentNeonLime)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Gönder",
                                    tint = BackgroundMidnightNavy,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // 54dp Pill-Style Input Bar with .imePadding()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .background(BackgroundMidnightNavy)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                    val canSendMessages = currentUser?.permissions?.canSendMessages ?: true
                    val canSendMedia = currentUser?.permissions?.canSendMedia ?: true

                    if (!canSendMessages) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            color = SurfaceSlateNavy,
                            border = BorderStroke(1.dp, DestructiveCoralRed.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Yönetici tarafından mesaj gönderme yetkiniz kısıtlanmıştır.",
                                    color = DestructiveCoralRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(27.dp),
                            color = SurfaceSlateNavy,
                            border = BorderStroke(1.dp, BorderSlateNavy)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (canSendMedia) {
                                    // Circular "+" Attachment Button
                                    IconButton(
                                        onClick = { showAttachmentMenu = true },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(SurfaceSlateNavyElevated)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Attachment",
                                            tint = AccentNeonLime,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                // Text Input Field
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {

                                    if (inputText.isEmpty()) {
                                        Text(
                                            text = strings.typeMessagePlaceholder,
                                            fontSize = 14.sp,
                                            color = TextMutedSlateGrey
                                        )
                                    }
                                    BasicTextField(
                                        value = inputText,
                                        onValueChange = { inputText = it },
                                        textStyle = TextStyle(
                                            color = TextOffWhite,
                                            fontSize = 15.sp
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 4
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Send or Mic Button
                                if (inputText.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            if (editingMessage != null) {
                                                // Save Edited Message
                                                val editTarget = editingMessage!!
                                                val newText = inputText.trim()
                                                editingMessage = null
                                                inputText = ""
                                                if (newText.isNotEmpty()) {
                                                    val targetIndex = messages.indexOfFirst { it.messageId == editTarget.messageId }
                                                    if (targetIndex >= 0) {
                                                        messages[targetIndex] = messages[targetIndex].copy(
                                                            originalText = newText,
                                                            translatedText = newText,
                                                            isEdited = true
                                                        )
                                                    }
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        try {
                                                            val srcLang = currentUser?.language?.ifBlank { "tr" } ?: "tr"
                                                            val targetLang = friendUser?.language?.ifBlank { "en" } ?: "en"
                                                            val translated = if (srcLang.equals(targetLang, ignoreCase = true)) {
                                                                newText
                                                            } else {
                                                                GeminiTranslationEngine.translateText(newText, srcLang, targetLang).translatedText
                                                            }
                                                            val targetIndex2 = messages.indexOfFirst { it.messageId == editTarget.messageId }
                                                            if (targetIndex2 >= 0) {
                                                                messages[targetIndex2] = messages[targetIndex2].copy(translatedText = translated)
                                                            }
                                                            CloudRealtimeService.editMessage(chatId, editTarget.messageId, newText, translated)
                                                        } catch (e: Exception) {
                                                            // Ignore
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Send New Message
                                                val textToSend = inputText
                                                val reply = replyingToMessage
                                                replyingToMessage = null
                                                sendMessage(
                                                    text = textToSend,
                                                    currentUser = currentUser,
                                                    friendUser = friendUser,
                                                    chatId = chatId,
                                                    friendId = friendId,
                                                    disappearingDurationMs = chatCustomization.disappearingDurationMs,
                                                    replyTo = reply,
                                                    context = context,
                                                    onClear = { inputText = "" },
                                                    onSendState = { isSending = it },
                                                    onMessageCreated = { messages.add(it) }
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(AccentNeonLime)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = strings.send, tint = BackgroundMidnightNavy, modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    // Mic voice button with push-to-talk / handsfree drag
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(AccentNeonLime)
                                            .pointerInput(Unit) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        if (!isRecordingVoice) {
                                                            VoiceNoteManager.startRecording(context, coroutineScope)
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        if (isRecordingVoice && !isHandsFreeLocked) {
                                                            val audioFile = VoiceNoteManager.stopRecording()
                                                            if (audioFile != null && friendUser != null) {
                                                                processAndSendVoiceNote(
                                                                    audioFile = audioFile,
                                                                    currentUser = currentUser,
                                                                    friendUser = friendUser,
                                                                    chatId = chatId,
                                                                    friendId = friendId,
                                                                    disappearingDurationMs = chatCustomization.disappearingDurationMs,
                                                                    onSendingState = { isTranscribingVoice = it },
                                                                    onMessageCreated = { messages.add(it) }
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        if (isRecordingVoice && !isHandsFreeLocked) {
                                                            VoiceNoteManager.cancelRecording()
                                                        }
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (dragAmount.y < -30f || dragAmount.x < -30f) {
                                                            isHandsFreeLocked = true
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Hold to record",
                                            tint = BackgroundMidnightNavy,
                                            modifier = Modifier.size(22.dp)
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

        // Long-Press Blurred Overlay & Context Menu
        selectedMessageForMenu?.let { selectedMsg ->
            val isOutgoing = selectedMsg.senderId == myUserId
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScrimDarkBlur)
                    .clickable { selectedMessageForMenu = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clickable(enabled = false) {},
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Floating Reaction Pill Bar
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = SurfaceSlateNavyElevated,
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val reactionEmojis = listOf("❤️", "👍", "🙏", "🤣", "😮", "😢")
                            reactionEmojis.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            coroutineScope.launch {
                                                CloudRealtimeService.toggleReaction(chatId, selectedMsg.messageId, myUserId, emoji)
                                            }
                                            selectedMessageForMenu = null
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    // Action Menu Card
                    Card(
                        modifier = Modifier.fillMaxWidth(0.85f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavyElevated)
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            // Reply
                            ContextMenuActionRow(
                                icon = Icons.Default.Reply,
                                text = strings.reply,
                                onClick = {
                                    replyingToMessage = selectedMsg
                                    selectedMessageForMenu = null
                                }
                            )

                            // Edit Message (Outgoing Text Messages only)
                            if (isOutgoing && selectedMsg.type == "text") {
                                ContextMenuActionRow(
                                    icon = Icons.Default.Edit,
                                    text = "Düzenle",
                                    tint = AccentNeonLime,
                                    onClick = {
                                        editingMessage = selectedMsg
                                        inputText = selectedMsg.originalText
                                        replyingToMessage = null
                                        selectedMessageForMenu = null
                                    }
                                )
                            }

                            // Pin / Unpin
                            ContextMenuActionRow(
                                icon = if (selectedMsg.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                text = if (selectedMsg.isPinned) "Sabitlemeyi Kaldır" else "Mesajı Sabitle",
                                onClick = {
                                    coroutineScope.launch {
                                        CloudRealtimeService.togglePinMessage(chatId, selectedMsg.messageId, !selectedMsg.isPinned)
                                    }
                                    selectedMessageForMenu = null
                                }
                            )

                            // Star / Unstar
                            ContextMenuActionRow(
                                icon = if (selectedMsg.isStarred) Icons.Outlined.StarBorder else Icons.Default.Star,
                                text = if (selectedMsg.isStarred) "Yıldızı Kaldır" else "Yıldızla",
                                onClick = {
                                    coroutineScope.launch {
                                        CloudRealtimeService.toggleStarMessage(chatId, selectedMsg.messageId, !selectedMsg.isStarred)
                                    }
                                    selectedMessageForMenu = null
                                }
                            )

                            // Copy
                            ContextMenuActionRow(
                                icon = Icons.Default.ContentCopy,
                                text = strings.copy,
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Zilbio Message", selectedMsg.translatedText.ifBlank { selectedMsg.originalText })
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, strings.messageCopied, Toast.LENGTH_SHORT).show()
                                    selectedMessageForMenu = null
                                }
                            )

                            // Translation Details
                            ContextMenuActionRow(
                                icon = Icons.Default.Info,
                                text = strings.translationDetails,
                                onClick = {
                                    translationDetailMessage = selectedMsg
                                    selectedMessageForMenu = null
                                }
                            )

                            // Delete
                            ContextMenuActionRow(
                                icon = Icons.Default.Delete,
                                text = strings.deleteForEveryone,
                                tint = DestructiveCoralRed,
                                onClick = {
                                    coroutineScope.launch {
                                        CloudRealtimeService.deleteMessage(chatId, selectedMsg.messageId)
                                    }
                                    messages.remove(selectedMsg)
                                    selectedMessageForMenu = null
                                }
                            )
                        }
                    }
                }
            }
        }

        // Expanded 7-Format Attachment Bottom Sheet
        if (showAttachmentMenu) {
            ModalBottomSheet(
                onDismissRequest = { showAttachmentMenu = false },
                containerColor = SurfaceSlateNavy,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Zilbio Medya & Belge",
                        color = AccentNeonLime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 4
                    ) {
                        AttachmentOptionItem(icon = Icons.Default.Image, label = strings.attachPhoto) {
                            showAttachmentMenu = false
                            photoPickerLauncher.launch("image/*")
                        }
                        AttachmentOptionItem(icon = Icons.Default.CameraAlt, label = strings.attachCamera) {
                            showAttachmentMenu = false
                            photoPickerLauncher.launch("image/*")
                        }
                        AttachmentOptionItem(icon = Icons.Default.Description, label = strings.attachDocument) {
                            showAttachmentMenu = false
                            documentPickerLauncher.launch("*/*")
                        }
                        AttachmentOptionItem(icon = Icons.Default.Videocam, label = strings.attachVideo) {
                            showAttachmentMenu = false
                            videoPickerLauncher.launch("video/*")
                        }
                        AttachmentOptionItem(icon = Icons.Default.Person, label = strings.attachContact) {
                            showAttachmentMenu = false
                            sendSimulatedContact(currentUser, friendUser, chatId, friendId, chatCustomization.disappearingDurationMs) { messages.add(it) }
                        }
                        AttachmentOptionItem(icon = Icons.Default.Audiotrack, label = strings.attachAudio) {
                            showAttachmentMenu = false
                            audioPickerLauncher.launch("audio/*")
                        }
                        AttachmentOptionItem(icon = Icons.Default.LocationOn, label = strings.attachLocation) {
                            showAttachmentMenu = false
                            sendSimulatedLocation(currentUser, friendUser, chatId, friendId, chatCustomization.disappearingDurationMs) { messages.add(it) }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun SwipeableMessageContainer(
    onSwipeReply: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxSwipe = 80f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (offsetX > 50f) {
                            onSwipeReply()
                        }
                        offsetX = 0f
                    },
                    onDragCancel = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount > 0 || offsetX > 0) {
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipe)
                        }
                    }
                )
            }
    ) {
        if (offsetX > 15f) {
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = "Yanıtla",
                tint = AccentNeonLime.copy(alpha = (offsetX / maxSwipe).coerceIn(0.3f, 1f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .size(20.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
        ) {
            content()
        }
    }
}

@Composable
fun ContextMenuActionRow(
    icon: ImageVector,
    text: String,
    tint: Color = TextOffWhite,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = text, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AttachmentOptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(SurfaceSlateNavyElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = AccentNeonLime, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = TextOffWhite,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    friendName: String,
    friendAvatar: String?,
    friendIsAdmin: Boolean = false,
    myIsAdmin: Boolean = false,
    currentUserId: String,
    bubbleOutgoingColor: Color,
    bubbleIncomingColor: Color,
    accentColor: Color,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    onQuickReplyCall: (replyText: String) -> Unit
) {
    val bubbleColor = if (isOutgoing) bubbleOutgoingColor else bubbleIncomingColor
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Incoming Avatar on the left
        if (!isOutgoing) {
            AvatarInitials(name = friendName, size = 32)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(horizontalAlignment = alignment) {
            // Incoming Sender Name & Admin Badge header
            if (!isOutgoing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = friendName,
                        color = AccentNeonLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (friendIsAdmin) {
                        AdminBadge(isCompact = true)
                    }
                }
            } else if (myIsAdmin) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)
                ) {
                    AdminBadge(isCompact = true)
                }
            }


            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(min = 70.dp, max = 290.dp)
                    .pointerInput(message.messageId) {
                        detectTapGestures(
                            onDoubleTap = { onDoubleTap() },
                            onLongPress = { onLongPress() }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Quoted reply header
                    if (!message.replyToText.isNullOrBlank()) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(accentColor, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = message.replyToSenderName ?: "Yanıt",
                                        color = accentColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = message.replyToText ?: "",
                                        color = TextMutedSlateGrey,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Content types
                    when (message.type) {
                        "voice" -> DualVoiceMessageContent(message = message, accentColor = accentColor)
                        "call_log" -> InChatCallLogContent(message = message, isOutgoing = isOutgoing, onQuickReply = onQuickReplyCall)
                        "sticker" -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                                Text(text = message.mediaUrlOrBase64 ?: "⭐", fontSize = 48.sp)
                                if (message.translatedText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = message.translatedText, color = TextOffWhite, fontSize = 13.sp)
                                }
                            }
                        }
                        "image" -> {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = message.mediaFileName ?: "Fotoğraf", color = TextOffWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (message.translatedText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = message.translatedText, color = TextOffWhite, fontSize = 14.sp)
                                }
                            }
                        }
                        "location" -> {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(text = "Konum", color = TextOffWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = message.translatedText.ifBlank { message.originalText }, color = accentColor, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Column {
                                val displayText = if (isOutgoing) {
                                    message.originalText.ifBlank { message.translatedText }
                                } else {
                                    message.translatedText.ifBlank { message.originalText }
                                }
                                Text(
                                    text = displayText,
                                    fontSize = 15.sp,
                                    color = TextOffWhite,
                                    lineHeight = 20.sp
                                )
                                if (!isOutgoing &&
                                    message.originalText.isNotBlank() &&
                                    message.originalText != message.translatedText &&
                                    message.originalText != "[Voice Note]"
                                ) {
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = message.originalText,
                                        fontSize = 13.sp,
                                        color = TextMutedSlateGrey,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }

                    // Reaction Badges on Corner of Bubble
                    if (message.reactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            val grouped = message.reactions.values.groupingBy { it }.eachCount()
                            grouped.forEach { (emoji, count) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldReactionBg,
                                    border = BorderStroke(1.dp, EmeraldReactionBorder),
                                    modifier = Modifier.padding(1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = emoji, fontSize = 12.sp)
                                        if (count > 1) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(text = "$count", fontSize = 10.sp, color = EmeraldReactionText, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Timestamp, Edited, Disappearing, Pin/Star badge, and Delivery Status Ticks
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                if (message.expiresAt != null) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Süreli",
                        tint = AccentNeonLime,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }

                if (message.isPinned) {
                    Icon(Icons.Default.PushPin, contentDescription = "Sabitlendi", tint = AccentNeonLime, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (message.isStarred) {
                    Icon(Icons.Default.Star, contentDescription = "Yıldızlı", tint = AccentNeonLime, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }

                val timeStr = remember(message.timestamp) {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                }
                Text(
                    text = timeStr,
                    fontSize = 11.sp,
                    color = TextMutedSlateGrey
                )

                if (message.isEdited) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(düzenlendi)",
                        fontSize = 10.sp,
                        color = TextMutedSlateGrey
                    )
                }

                // Outgoing delivery status ticks
                if (isOutgoing) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when (message.deliveryStatus) {
                        "SENDING" -> {
                            CircularProgressIndicator(
                                color = TextMutedSlateGrey,
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp
                            )
                        }
                        "SENT" -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Gönderildi",
                                tint = TickGrey,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        "DELIVERED" -> {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "İletildi",
                                tint = TickGrey,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        "READ" -> {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Okundu",
                                tint = TickReadLime,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Gönderildi",
                                tint = TickGrey,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DualVoiceMessageContent(
    message: Message,
    accentColor: Color
) {
    val context = LocalContext.current
    val currentPlayingId by AudioPlayerManager.currentPlayingMessageId.collectAsState()
    val isPlaying by AudioPlayerManager.isPlaying.collectAsState()
    val progress by AudioPlayerManager.progress.collectAsState()
    val currentPosMs by AudioPlayerManager.currentPositionMs.collectAsState()
    val playbackSpeed by AudioPlayerManager.playbackSpeed.collectAsState()

    val isThisPlaying = currentPlayingId == message.messageId && isPlaying
    val playbackProgress = if (currentPlayingId == message.messageId) progress else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val base64 = message.audioBase64
                    if (!base64.isNullOrBlank()) {
                        AudioPlayerManager.togglePlayVoiceNote(context, message.messageId, base64)
                    } else {
                        Toast.makeText(context, "Ses dosyası bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BackgroundMidnightNavy,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 20-Bar Waveform representation
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val waveHeights = listOf(
                    8, 14, 20, 12, 24, 18, 10, 22, 16, 26,
                    14, 20, 28, 16, 12, 22, 18, 14, 10, 8
                )
                waveHeights.forEachIndexed { index, barHeight ->
                    val barFraction = index.toFloat() / waveHeights.size
                    val isPast = playbackProgress >= barFraction
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isPast) accentColor else Color.White.copy(alpha = 0.35f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Playback Speed Toggle Chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.3f),
                border = BorderStroke(0.5.dp, accentColor.copy(alpha = 0.6f)),
                modifier = Modifier.clickable {
                    AudioPlayerManager.togglePlaybackSpeed()
                }
            ) {
                Text(
                    text = "${playbackSpeed}x",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            val durationSec = (message.audioDurationMs / 1000).toInt()
            val currentSec = if (currentPlayingId == message.messageId) (currentPosMs / 1000) else 0
            val timerText = remember(currentSec, durationSec) {
                String.format("%d:%02d", currentSec, durationSec)
            }
            Text(
                text = timerText,
                color = TextOffWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Translated text
        Text(
            text = message.translatedText.ifBlank { message.originalText },
            color = TextOffWhite,
            fontSize = 14.sp,
            lineHeight = 18.sp
        )

        // Voice Note Speech-to-Text Transcription box
        val transcript = message.voiceTranscript ?: message.originalText
        if (transcript.isNotBlank() && transcript != "[Voice Note]" && transcript != "[Sesli Mesaj]") {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(text = "📝 ", fontSize = 11.sp)
                    Text(
                        text = transcript,
                        color = TextMutedSlateGrey,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "🌐 ${message.originalLanguage.uppercase()} → Gemini",
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun InChatCallLogContent(
    message: Message,
    isOutgoing: Boolean,
    onQuickReply: (replyText: String) -> Unit
) {
    val isMissed = message.callStatus == "missed" || message.callStatus == "declined"
    val isEnded = message.callStatus == "ended" || message.callStatus == "completed"

    Column(modifier = Modifier.padding(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isMissed) DestructiveCoralRed.copy(alpha = 0.2f) else OnlineStatusGreen.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMissed) Icons.Default.CallMissed else Icons.Default.Phone,
                    contentDescription = null,
                    tint = if (isMissed) DestructiveCoralRed else OnlineStatusGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = if (isMissed) "Cevapsız Arama" else "Arama Sonlandı",
                    color = if (isMissed) DestructiveCoralRed else TextOffWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isEnded && message.callDurationSeconds > 0) {
                    val mins = message.callDurationSeconds / 60
                    val secs = message.callDurationSeconds % 60
                    Text(
                        text = "Süre: ${String.format("%02d:%02d", mins, secs)}",
                        color = TextMutedSlateGrey,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (isMissed && !isOutgoing) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onQuickReply("Kusura bakma aramayı kaçırdım, müsaitsen yazabilirsin.") },
                colors = ButtonDefaults.buttonColors(containerColor = DestructiveCoralRed.copy(alpha = 0.25f)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "💬 Aramayı kaçırdım, müsaitsen yaz",
                    color = DestructiveCoralRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Zero-Delay Optimistic Sending & Async Translation with Disappearing support
private fun sendMessage(
    text: String,
    currentUser: User?,
    friendUser: Friend?,
    chatId: String,
    friendId: String,
    disappearingDurationMs: Long = 0L,
    replyTo: Message? = null,
    context: Context? = null,
    onClear: () -> Unit,
    onSendState: (Boolean) -> Unit,
    onMessageCreated: (Message) -> Unit
) {
    val clean = text.trim()
    if (clean.isEmpty() || currentUser == null || chatId.isEmpty()) return

    val messageId = UUID.randomUUID().toString()
    val srcLang = currentUser.language.ifBlank { "tr" }
    val targetLang = friendUser?.language?.ifBlank { "en" } ?: "en"
    val expiresAt = if (disappearingDurationMs > 0L) System.currentTimeMillis() + disappearingDurationMs else null

    // 1. Instant optimistic creation (0ms delay)
    val optimisticMsg = Message(
        messageId = messageId,
        senderId = currentUser.userId,
        receiverId = friendId,
        chatId = chatId,
        originalText = clean,
        translatedText = clean, // Displays immediately
        originalLanguage = srcLang,
        timestamp = System.currentTimeMillis(),
        isRead = false,
        deliveryStatus = "SENDING",
        type = "text",
        expiresAt = expiresAt,
        replyToMessageId = replyTo?.messageId,
        replyToText = replyTo?.let { it.translatedText.ifBlank { it.originalText } },
        replyToSenderName = replyTo?.senderId
    )
    onMessageCreated(optimisticMsg)
    onClear()

    context?.let { ctx ->
        BadgeRewardManager.recordMessageSent(
            context = ctx,
            userId = currentUser.userId,
            displayName = currentUser.displayName.ifBlank { currentUser.username }
        )
    }

    // 2. Ultra-Fast Dispatch: Send to Firebase immediately (< 100ms), then translate in background
    chatIoScope.launch {
        try {
            val immediateMsg = optimisticMsg.copy(deliveryStatus = "SENT")
            CloudRealtimeService.sendMessage(chatId, immediateMsg)

            if (!srcLang.equals(targetLang, ignoreCase = true)) {
                val translated = GeminiTranslationEngine.translateText(clean, srcLang, targetLang).translatedText
                if (translated.isNotBlank() && translated != clean) {
                    CloudRealtimeService.updateMessageTranslation(chatId, messageId, translated)
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}

private fun processAndSendVoiceNote(
    audioFile: File,
    currentUser: User?,
    friendUser: Friend?,
    chatId: String,
    friendId: String,
    disappearingDurationMs: Long = 0L,
    context: Context? = null,
    onSendingState: (Boolean) -> Unit,
    onMessageCreated: (Message) -> Unit
) {
    if (currentUser == null || chatId.isEmpty() || !audioFile.exists()) return

    val durationMs = VoiceNoteManager.getAudioDurationMs(audioFile)
    if (durationMs > VoiceNoteManager.MAX_DURATION_MS) {
        audioFile.delete()
        return
    }

    val audioBase64 = VoiceNoteManager.encodeToBase64(audioFile)
    if (audioBase64.isNullOrBlank()) return

    onSendingState(true)

    context?.let { ctx ->
        BadgeRewardManager.recordMessageSent(
            context = ctx,
            userId = currentUser.userId,
            displayName = currentUser.displayName.ifBlank { currentUser.username }
        )
    }

    chatIoScope.launch {
        try {
            val targetLang = friendUser?.language?.ifBlank { "en" } ?: "en"
            val transResult = VoiceNoteManager.transcribeAndTranslate(audioFile, targetLang)

            val original = transResult?.originalText ?: "[Sesli Mesaj]"
            val translated = transResult?.translatedText ?: original
            val srcLang = transResult?.sourceLanguage ?: currentUser.language
            val expiresAt = if (disappearingDurationMs > 0L) System.currentTimeMillis() + disappearingDurationMs else null

            val newMsg = Message(
                messageId = UUID.randomUUID().toString(),
                senderId = currentUser.userId,
                receiverId = friendId,
                chatId = chatId,
                originalText = original,
                translatedText = translated,
                originalLanguage = srcLang,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                deliveryStatus = "SENT",
                type = "voice",
                audioBase64 = audioBase64,
                audioDurationMs = durationMs,
                voiceTranscript = original,
                expiresAt = expiresAt
            )

            onMessageCreated(newMsg)
            CloudRealtimeService.sendMessage(chatId, newMsg)
        } catch (e: Exception) {
            // Safe fallback
        } finally {
            onSendingState(false)
            audioFile.delete()
        }
    }
}

private fun sendMediaAttachment(
    uri: Uri,
    type: String,
    currentUser: User?,
    friendUser: Friend?,
    chatId: String,
    friendId: String,
    disappearingDurationMs: Long = 0L,
    context: Context,
    onMessageCreated: (Message) -> Unit
) {
    if (currentUser == null || chatId.isEmpty()) return

    BadgeRewardManager.recordMessageSent(
        context = context,
        userId = currentUser.userId,
        displayName = currentUser.displayName.ifBlank { currentUser.username }
    )

    chatIoScope.launch {
        try {
            val fileName = uri.lastPathSegment ?: "$type.dat"
            val expiresAt = if (disappearingDurationMs > 0L) System.currentTimeMillis() + disappearingDurationMs else null
            val newMsg = Message(
                messageId = UUID.randomUUID().toString(),
                senderId = currentUser.userId,
                receiverId = friendId,
                chatId = chatId,
                originalText = "[$type] $fileName",
                translatedText = "[$type] $fileName",
                originalLanguage = currentUser.language,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                deliveryStatus = "SENT",
                type = type,
                mediaFileName = fileName,
                mediaUrlOrBase64 = uri.toString(),
                expiresAt = expiresAt
            )
            onMessageCreated(newMsg)
            CloudRealtimeService.sendMessage(chatId, newMsg)
        } catch (e: Exception) {
            // Ignore
        }
    }
}

private fun sendSimulatedContact(
    currentUser: User?,
    friendUser: Friend?,
    chatId: String,
    friendId: String,
    disappearingDurationMs: Long = 0L,
    context: Context? = null,
    onMessageCreated: (Message) -> Unit
) {
    if (currentUser == null || chatId.isEmpty()) return

    context?.let { ctx ->
        BadgeRewardManager.recordMessageSent(
            context = ctx,
            userId = currentUser.userId,
            displayName = currentUser.displayName.ifBlank { currentUser.username }
        )
    }

    chatIoScope.launch {
        val expiresAt = if (disappearingDurationMs > 0L) System.currentTimeMillis() + disappearingDurationMs else null
        val newMsg = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = currentUser.userId,
            receiverId = friendId,
            chatId = chatId,
            originalText = "👤 ${currentUser.displayName} (${currentUser.username})",
            translatedText = "👤 ${currentUser.displayName} (${currentUser.username})",
            originalLanguage = currentUser.language,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            deliveryStatus = "SENT",
            type = "contact",
            expiresAt = expiresAt
        )
        onMessageCreated(newMsg)
        CloudRealtimeService.sendMessage(chatId, newMsg)
    }
}

private fun sendSimulatedLocation(
    currentUser: User?,
    friendUser: Friend?,
    chatId: String,
    friendId: String,
    disappearingDurationMs: Long = 0L,
    context: Context? = null,
    onMessageCreated: (Message) -> Unit
) {
    if (currentUser == null || chatId.isEmpty()) return

    context?.let { ctx ->
        BadgeRewardManager.recordMessageSent(
            context = ctx,
            userId = currentUser.userId,
            displayName = currentUser.displayName.ifBlank { currentUser.username }
        )
    }

    chatIoScope.launch {
        val mapsUrl = "https://maps.google.com/?q=41.0082,28.9784"
        val expiresAt = if (disappearingDurationMs > 0L) System.currentTimeMillis() + disappearingDurationMs else null
        val newMsg = Message(
            messageId = UUID.randomUUID().toString(),
            senderId = currentUser.userId,
            receiverId = friendId,
            chatId = chatId,
            originalText = "📍 $mapsUrl",
            translatedText = "📍 $mapsUrl",
            originalLanguage = currentUser.language,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            deliveryStatus = "SENT",
            type = "location",
            expiresAt = expiresAt
        )
        onMessageCreated(newMsg)
        CloudRealtimeService.sendMessage(chatId, newMsg)
    }
}
