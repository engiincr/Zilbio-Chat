package com.translive.app.ui.screens.call

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.translive.app.data.local.UserPreferences
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.domain.model.CallSession
import com.translive.app.domain.model.CallTranscriptSegment
import com.translive.app.domain.model.Friend
import com.translive.app.domain.model.Language
import com.translive.app.domain.model.User
import com.translive.app.service.CallSessionManager
import com.translive.app.ui.screens.friends.AvatarInitials
import com.translive.app.ui.theme.AccentAmberOrange
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.BorderWarmSubtle
import com.translive.app.ui.theme.CallEndCoralRed
import com.translive.app.ui.theme.InputFieldBackground
import com.translive.app.ui.theme.OnlineStatusGreen
import com.translive.app.ui.theme.OriginalSpeechGrey
import com.translive.app.ui.theme.OutgoingBubblePeach
import com.translive.app.ui.theme.SurfaceCardWhite
import com.translive.app.ui.theme.TextPrimaryEspresso
import com.translive.app.ui.theme.TextSecondaryWarmGrey
import com.translive.app.ui.theme.TranslatedSpeechOrange
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CallScreen(
    navController: NavController? = null,
    friendId: String,
    incomingSession: CallSession? = null,
    onNavigateBack: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    var friendUser by remember { mutableStateOf<Friend?>(null) }

    val callSessionManager = remember { CallSessionManager(context) }
    val callDuration by callSessionManager.callDuration.collectAsState()
    val transcripts by callSessionManager.transcripts.collectAsState()
    val callSessionState by callSessionManager.callState.collectAsState()

    var vadStatus by remember { mutableStateOf("Bağlanıyor...") }
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }

    // 1. Fetch friend details and either initiate or answer call properly
    LaunchedEffect(currentUser?.userId, friendId) {
        val user = currentUser
        if (user != null && friendId.isNotEmpty()) {
            try {
                val myFriends = CloudRealtimeService.getFriends(user.userId)
                var friend = myFriends.find { it.userId == friendId }
                if (friend == null) {
                    val searched = CloudRealtimeService.searchUserById(friendId)
                    if (searched != null) {
                        friend = Friend(
                            userId = searched.userId,
                            username = searched.username,
                            displayName = searched.displayName,
                            avatarBase64 = searched.avatarBase64,
                            status = searched.status,
                            language = searched.language
                        )
                    }
                }

                if (friend != null) {
                    friendUser = friend
                    if (incomingSession != null) {
                        // Eğer gelen bir arama oturumu varsa doğrudan cevapla
                        callSessionManager.answerCall(incomingSession, user)
                    } else {
                        // Yoksa yeni arama başlat
                        callSessionManager.initiateCall(user, friend)
                    }
                }
            } catch (e: Exception) {
                // Fallback gracefully
            }
        }
    }

    // 2. Poll/Collect VAD status from chunker
    LaunchedEffect(callSessionManager) {
        while (true) {
            val chunker = callSessionManager.vadChunker
            if (chunker != null) {
                vadStatus = chunker.uiStatus.value
            } else if (callSessionState?.status == "active") {
                vadStatus = "Konuşma bekleniyor..."
            } else {
                vadStatus = "Bağlanıyor..."
            }
            kotlinx.coroutines.delay(400L)
        }
    }

    // 3. Auto-scroll to bottom on new transcript segment
    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    // 4. Dispose call session cleanly on exit
    DisposableEffect(Unit) {
        onDispose {
            callSessionManager.dispose()
        }
    }

    val formattedDuration = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        callDuration / 60,
        callDuration % 60
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundAlabasterCream)
            .padding(vertical = 16.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(OutgoingBubblePeach)
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            AvatarInitials(name = friendUser?.displayName ?: "Arkadaş", size = 80)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = friendUser?.displayName ?: "Görüşülüyor...",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryEspresso
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formattedDuration,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentAmberOrange
        )

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (callSessionState?.status == "active" || callDuration > 0) OnlineStatusGreen else AccentAmberOrange)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = vadStatus,
                fontSize = 12.sp,
                color = TextSecondaryWarmGrey
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(InputFieldBackground.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            if (transcripts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Canlı Altyazı & Çeviri",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryEspresso
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Görüşme sırasında söylenenler otomatik olarak algılanıp hedef dile çevrilecektir.",
                            fontSize = 12.sp,
                            color = TextSecondaryWarmGrey,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                ) {
                    items(transcripts) { segment ->
                        TranscriptCard(segment = segment)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    isMuted = !isMuted
                    callSessionManager.audioEngine?.setMicrophoneMute(isMuted)
                },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (isMuted) OutgoingBubblePeach else SurfaceCardWhite)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Mikrofon Aç/Kapat",
                    tint = if (isMuted) CallEndCoralRed else TextPrimaryEspresso,
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = {
                    callSessionManager.endCall()
                    onNavigateBack()
                },
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(CallEndCoralRed)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Aramayı Sonlandır",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick = {
                    isSpeakerOn = !isSpeakerOn
                    callSessionManager.audioEngine?.setSpeakerphoneOn(isSpeakerOn)
                },
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(if (isSpeakerOn) OutgoingBubblePeach else SurfaceCardWhite)
            ) {
                Icon(
                    imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = "Hoparlör Aç/Kapat",
                    tint = if (isSpeakerOn) AccentAmberOrange else TextPrimaryEspresso,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun CallScreen(
    friendId: String,
    onNavigateBack: () -> Unit
) {
    CallScreen(
        navController = null,
        friendId = friendId,
        incomingSession = null,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun TranscriptCard(segment: CallTranscriptSegment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardWhite),
        border = BorderStroke(1.dp, BorderWarmSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = segment.speakerName.ifEmpty { "Konuşmacı" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondaryWarmGrey
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = segment.originalText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = OriginalSpeechGrey,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = segment.translatedText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TranslatedSpeechOrange,
                lineHeight = 22.sp
            )
        }
    }
}
