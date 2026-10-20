package com.translive.app.ui.screens.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.translive.app.data.audio.VoiceNoteManager
import com.translive.app.data.local.UserPreferences
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.data.remote.GeminiTranslationEngine
import com.translive.app.domain.model.Friend
import com.translive.app.domain.model.Language
import com.translive.app.domain.model.Message
import com.translive.app.domain.model.User
import com.translive.app.ui.screens.friends.AvatarInitials
import com.translive.app.ui.theme.AccentAmberOrange
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.BorderWarmSubtle
import com.translive.app.ui.theme.CallEndCoralRed
import com.translive.app.ui.theme.InputFieldBackground
import com.translive.app.ui.theme.OnlineStatusGreen
import com.translive.app.ui.theme.OutgoingBubblePeach
import com.translive.app.ui.theme.QuotaWarningAmber
import com.translive.app.ui.theme.SurfaceCardWhite
import com.translive.app.ui.theme.TextPrimaryEspresso
import com.translive.app.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.Call
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController? = null,
    friendId: String,
    onNavigateBack: () -> Unit = { navController?.popBackStack() },
    onNavigateToCall: (friendId: String) -> Unit = { fId -> navController?.navigate("call/$fId") }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    var friendUser by remember { mutableStateOf<Friend?>(null) }
    val messages = remember { mutableStateListOf<Message>() }

    var inputText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Quota warning banner
    var quotaWarningText by remember { mutableStateOf<String?>(null) }
    var showQuotaBanner by remember { mutableStateOf(false) }

    // Dialog for displaying original text on long press
    var originalTextToInspect by remember { mutableStateOf<Message?>(null) }

    // Voice recording state
    val isRecordingVoice by VoiceNoteManager.isRecording.collectAsState()
    var isTranscribingVoice by remember { mutableStateOf(false) }

    val myUserId = currentUser?.userId ?: ""
    val chatId = remember(myUserId, friendId) {
        if (myUserId.isNotEmpty() && friendId.isNotEmpty()) {
            CloudRealtimeService.generateChatId(myUserId, friendId)
        } else ""
    }

    // 1. Fetch friend details and initial messages
    LaunchedEffect(friendId, myUserId) {
        if (friendId.isNotEmpty() && myUserId.isNotEmpty()) {
            try {
                val myFriends = CloudRealtimeService.getFriends(myUserId)
                val foundFriend = myFriends.find { it.userId == friendId }
                if (foundFriend != null) {
                    friendUser = foundFriend
                } else {
                    val searched = CloudRealtimeService.searchUserById(friendId)
                    if (searched != null) {
                        friendUser = Friend(
                            userId = searched.userId,
                            username = searched.username,
                            displayName = searched.displayName,
                            avatarBase64 = searched.avatarBase64,
                            status = searched.status,
                            language = searched.language
                        )
                    }
                }

                val initialMessages = CloudRealtimeService.getMessages(chatId)
                messages.clear()
                messages.addAll(initialMessages)
            } catch (e: Exception) {
                // Keep loaded state
            }
        }
    }

    // 2. Listen for incoming messages via SSE (Anlık Akış)
    DisposableEffect(chatId) {
        var messageCall: Call? = null
        if (chatId.isNotEmpty()) {
            messageCall = CloudRealtimeService.listenForMessages(chatId) { newMessage ->
                coroutineScope.launch {
                    val exists = messages.any { it.messageId == newMessage.messageId }
                    if (!exists) {
                        messages.add(newMessage)
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                }
            }
        }
        onDispose {
            messageCall?.cancel()
            VoiceNoteManager.release()
        }
    }

    // 3. Collect Quota Warning from Gemini Translation Engine
    LaunchedEffect(Unit) {
        GeminiTranslationEngine.quotaWarning.collectLatest { warningMessage ->
            quotaWarningText = warningMessage
            showQuotaBanner = true
            delay(5000L)
            showQuotaBanner = false
        }
    }

    // Auto-scroll when messages size changes
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AvatarInitials(name = friendUser?.displayName ?: "Arkadaş", size = 38)
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (friendUser?.status == "online") OnlineStatusGreen else Color.LightGray)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = friendUser?.displayName ?: "Yükleniyor...",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimaryEspresso
                                )
                                val lang = Language.fromCode(friendUser?.language ?: "en")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = lang.flag, fontSize = 12.sp)
                            }
                            Text(
                                text = if (friendUser?.status == "online") "Çevrimiçi" else "Çevrimdışı",
                                fontSize = 11.sp,
                                color = if (friendUser?.status == "online") OnlineStatusGreen else TextSecondaryWarmGrey
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = TextPrimaryEspresso)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToCall(friendId) }) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Canlı Çevirili Arama",
                            tint = AccentAmberOrange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceCardWhite
                )
            )
        },
        containerColor = BackgroundAlabasterCream
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
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
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = quotaWarningText ?: "⚠️ API kotası azalıyor. Yedek servise geçildi.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryEspresso,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Message List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Henüz mesaj yok.\nİlk mesajınızı yazarak canlı çeviriyi deneyin!",
                            fontSize = 13.sp,
                            color = TextSecondaryWarmGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(messages, key = { it.messageId }) { msg ->
                            val isOutgoing = msg.senderId == myUserId
                            MessageBubble(
                                message = msg,
                                isOutgoing = isOutgoing,
                                onLongPress = { originalTextToInspect = msg }
                            )
                        }
                    }
                }
            }

            // Voice Recording In-Progress Banner
            if (isRecordingVoice || isTranscribingVoice) {
                Surface(
                    color = OutgoingBubblePeach,
                    border = BorderStroke(1.dp, BorderWarmSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = AccentAmberOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isTranscribingVoice) "Ses çevriliyor..." else "Ses kaydediliyor...",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmberOrange
                            )
                        }

                        if (isRecordingVoice) {
                            Button(
                                onClick = {
                                    val audioFile = VoiceNoteManager.stopRecording()
                                    if (audioFile != null && friendUser != null) {
                                        coroutineScope.launch {
                                            isTranscribingVoice = true
                                            try {
                                                val srcLang = currentUser?.language?.ifBlank { "tr" } ?: "tr"
                                                val targetLang = friendUser?.language?.ifBlank { "en" } ?: "en"

                                                // OPTİMİZASYON: Aynı dilde ise sesli not çevirisi yapılmaz
                                                val transResult = VoiceNoteManager.transcribeAndTranslate(audioFile, targetLang)
                                                if (transResult != null) {
                                                    val finalTranslated = if (srcLang.equals(targetLang, ignoreCase = true)) {
                                                        transResult.originalText
                                                    } else {
                                                        transResult.translatedText
                                                    }

                                                    val newMsg = Message(
                                                        messageId = UUID.randomUUID().toString(),
                                                        senderId = myUserId,
                                                        receiverId = friendId,
                                                        chatId = chatId,
                                                        originalText = transResult.originalText,
                                                        translatedText = finalTranslated,
                                                        originalLanguage = srcLang,
                                                        timestamp = System.currentTimeMillis(),
                                                        isRead = false,
                                                        type = "voice"
                                                    )
                                                    messages.add(newMsg)
                                                    CloudRealtimeService.sendMessage(chatId, newMsg)
                                                } else {
                                                    Toast.makeText(context, "Ses algılanamadı", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Hata: ${e.message}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isTranscribingVoice = false
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CallEndCoralRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bitir & Gönder", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Input Bar at Bottom
            Surface(
                color = SurfaceCardWhite,
                border = BorderStroke(1.dp, BorderWarmSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic button for voice notes
                    IconButton(
                        onClick = {
                            if (isRecordingVoice) {
                                val audioFile = VoiceNoteManager.stopRecording()
                                if (audioFile != null && friendUser != null) {
                                    coroutineScope.launch {
                                        isTranscribingVoice = true
                                        try {
                                            val srcLang = currentUser?.language?.ifBlank { "tr" } ?: "tr"
                                            val targetLang = friendUser?.language?.ifBlank { "en" } ?: "en"

                                            val transResult = VoiceNoteManager.transcribeAndTranslate(audioFile, targetLang)
                                            if (transResult != null) {
                                                val finalTranslated = if (srcLang.equals(targetLang, ignoreCase = true)) {
                                                    transResult.originalText
                                                } else {
                                                    transResult.translatedText
                                                }

                                                val newMsg = Message(
                                                    messageId = UUID.randomUUID().toString(),
                                                    senderId = myUserId,
                                                    receiverId = friendId,
                                                    chatId = chatId,
                                                    originalText = transResult.originalText,
                                                    translatedText = finalTranslated,
                                                    originalLanguage = srcLang,
                                                    timestamp = System.currentTimeMillis(),
                                                    isRead = false,
                                                    type = "voice"
                                                )
                                                messages.add(newMsg)
                                                CloudRealtimeService.sendMessage(chatId, newMsg)
                                            }
                                        } catch (e: Exception) {
                                            // Ignore
                                        } finally {
                                            isTranscribingVoice = false
                                        }
                                    }
                                }
                            } else {
                                VoiceNoteManager.startRecording(context)
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isRecordingVoice) CallEndCoralRed else InputFieldBackground)
                    ) {
                        Icon(
                            imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = "Sesli Not",
                            tint = if (isRecordingVoice) Color.White else AccentAmberOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Text Field
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Mesaj yazın...", fontSize = 14.sp, color = TextSecondaryWarmGrey) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            sendMessage(
                                text = inputText,
                                currentUser = currentUser,
                                friendUser = friendUser,
                                chatId = chatId,
                                friendId = friendId,
                                onClear = { inputText = "" },
                                onSendState = { isSending = it },
                                onMessageCreated = { messages.add(it) }
                            )
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = InputFieldBackground,
                            unfocusedContainerColor = InputFieldBackground,
                            focusedIndicatorColor = AccentAmberOrange,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimaryEspresso,
                            unfocusedTextColor = TextPrimaryEspresso
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    IconButton(
                        onClick = {
                            sendMessage(
                                text = inputText,
                                currentUser = currentUser,
                                friendUser = friendUser,
                                chatId = chatId,
                                friendId = friendId,
                                onClear = { inputText = "" },
                                onSendState = { isSending = it },
                                onMessageCreated = { messages.add(it) }
                            )
                        },
                        enabled = inputText.trim().isNotEmpty() && !isSending,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (inputText.trim().isNotEmpty()) AccentAmberOrange else InputFieldBackground)
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Gönder",
                                tint = if (inputText.trim().isNotEmpty()) Color.White else TextSecondaryWarmGrey,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog showing Original Text on bubble Long-Press
    originalTextToInspect?.let { msg ->
        val srcLang = Language.fromCode(msg.originalLanguage)
        AlertDialog(
            onDismissRequest = { originalTextToInspect = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Orijinal Metin", fontWeight = FontWeight.Bold, color = TextPrimaryEspresso)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = OutgoingBubblePeach
                    ) {
                        Text(
                            text = "${srcLang.flag} ${srcLang.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentAmberOrange,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column {
                    Text(
                        text = msg.originalText.ifEmpty { "[Orijinal metin boş]" },
                        fontSize = 15.sp,
                        color = TextPrimaryEspresso
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Çevrilmiş: ${msg.translatedText}",
                        fontSize = 13.sp,
                        color = TextSecondaryWarmGrey
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { originalTextToInspect = null }) {
                    Text("Tamam", color = AccentAmberOrange, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceCardWhite,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ChatScreen(
    friendId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCall: (friendId: String) -> Unit
) {
    ChatScreen(
        navController = null,
        friendId = friendId,
        onNavigateBack = onNavigateBack,
        onNavigateToCall = onNavigateToCall
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    onLongPress: () -> Unit
) {
    val bubbleColor = if (isOutgoing) OutgoingBubblePeach else SurfaceCardWhite
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            border = if (isOutgoing) null else BorderStroke(1.dp, BorderWarmSubtle),
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(min = 60.dp, max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (message.type == "voice") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = AccentAmberOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sesli Mesaj Çevirisi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentAmberOrange
                        )
                    }
                }

                Text(
                    text = message.translatedText.ifEmpty { message.originalText },
                    fontSize = 15.sp,
                    color = TextPrimaryEspresso,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
        Text(
            text = timeStr,
            fontSize = 11.sp,
            color = TextSecondaryWarmGrey,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

private fun sendMessage(
    text: String,
    currentUser: User?,
    friendUser: Friend?,
    chatId: String,
    friendId: String,
    onClear: () -> Unit,
    onSendState: (Boolean) -> Unit,
    onMessageCreated: (Message) -> Unit
) {
    val clean = text.trim()
    if (clean.isEmpty() || currentUser == null || chatId.isEmpty()) return

    onSendState(true)
    kotlinx.coroutines.GlobalScope.launch {
        try {
            val srcLang = currentUser.language.ifBlank { "tr" }
            val targetLang = friendUser?.language?.ifBlank { "en" } ?: "en"

            // AKILLI OPTİMİZASYON: Eğer iki tarafın da dili aynı ise (örn: ikisi de tr)
            // Yapay zeka çevirisi yapılmaz, doğrudan orijinal metin gönderilir (Maliyet ve Hız tasarrufu)
            val translatedText = if (srcLang.equals(targetLang, ignoreCase = true)) {
                clean
            } else {
                val translationResult = GeminiTranslationEngine.translateText(clean, srcLang, targetLang)
                translationResult.translatedText
            }

            val newMessage = Message(
                messageId = UUID.randomUUID().toString(),
                senderId = currentUser.userId,
                receiverId = friendId,
                chatId = chatId,
                originalText = clean,
                translatedText = translatedText,
                originalLanguage = srcLang,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                type = "text"
            )

            onMessageCreated(newMessage)
            onClear()
            CloudRealtimeService.sendMessage(chatId, newMessage)
        } catch (e: Exception) {
            // Keep app from crashing
        } finally {
            onSendState(false)
        }
    }
}
