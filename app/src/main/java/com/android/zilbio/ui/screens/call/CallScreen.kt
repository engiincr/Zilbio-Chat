package com.android.zilbio.ui.screens.call

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.AudioOutputRoute
import com.android.zilbio.domain.model.CallTranscriptSegment
import com.android.zilbio.domain.model.Friend
import com.android.zilbio.domain.model.Language as AppLanguage
import com.android.zilbio.domain.model.NetworkQuality
import com.android.zilbio.domain.model.isOwnerOrAdmin
import com.android.zilbio.ui.components.AdminBadge
import com.android.zilbio.ui.components.VerifiedBadge
import com.android.zilbio.service.CallSessionManager
import com.android.zilbio.service.GlobalCallState
import com.android.zilbio.ui.screens.friends.AvatarInitials
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.CallEndCoralRed
import com.android.zilbio.ui.theme.NeonLimeAccent
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.SurfaceDarkWarmElevated
import com.android.zilbio.ui.theme.SurfaceMidnightDark
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.TextMutedWarmGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CallScreen(
    navController: NavController? = null,
    friendId: String,
    callId: String? = null,
    isIncoming: Boolean = false,
    isVideo: Boolean = false,
    onNavigateBack: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)
    var friendUser by remember { mutableStateOf<Friend?>(null) }
    var allFriends by remember { mutableStateOf<List<Friend>>(emptyList()) }

    val callSessionManager = remember { CallSessionManager(context) }
    val callDuration by callSessionManager.callDuration.collectAsState()
    val transcripts by callSessionManager.transcripts.collectAsState()
    val callSessionState by callSessionManager.callState.collectAsState()
    val isCallEnded by callSessionManager.isCallEnded.collectAsState()
    val participants by callSessionManager.participants.collectAsState()
    val joinBanner by callSessionManager.joinBanner.collectAsState()
    val peerBatteryAlert by callSessionManager.peerBatteryAlert.collectAsState()
    val localMicLevel by callSessionManager.localMicLevel.collectAsState()
    val remoteMicLevel by callSessionManager.remoteMicLevel.collectAsState()

    // Picture-in-Picture state (Item #1)
    val isInPipMode by GlobalCallState.isInPipMode.collectAsState()

    var vadStatus by remember { mutableStateOf(if (isIncoming) "Bağlanıyor..." else "Aranıyor...") }
    var isMuted by remember { mutableStateOf(false) }
    var isVideoMuted by remember { mutableStateOf(false) }
    var currentAudioRoute by remember { mutableStateOf(AudioOutputRoute.SPEAKER) }
    var showAudioRouteDialog by remember { mutableStateOf(false) }
    var showAddParticipantDialog by remember { mutableStateOf(false) }
    var showSubtitleLanguageDialog by remember { mutableStateOf(false) }
    var preferredSubtitleLang by remember { mutableStateOf("tr") }

    // Register with GlobalCallState for PiP & smart background camera management
    LaunchedEffect(isVideo, callSessionManager.audioEngine) {
        GlobalCallState.isInActiveVideoCall = isVideo
        GlobalCallState.activeAudioEngine = callSessionManager.audioEngine
    }

    // Synchronized call termination observer (Item #4)
    LaunchedEffect(isCallEnded) {
        if (isCallEnded) {
            onNavigateBack()
        }
    }

    // Load friend details and initiate/answer call
    LaunchedEffect(currentUser?.userId, friendId) {
        val user = currentUser
        if (user != null && friendId.isNotEmpty()) {
            preferredSubtitleLang = user.language.ifBlank { "tr" }
            try {
                val myFriends = CloudRealtimeService.getFriends(user.userId)
                allFriends = myFriends
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
                    if (isIncoming && !callId.isNullOrEmpty()) {
                        callSessionManager.answerCall(
                            callId = callId,
                            callerId = friend.userId,
                            callerName = friend.displayName.ifBlank { friend.username },
                            myUser = user,
                            isVideo = isVideo,
                            targetLanguage = preferredSubtitleLang
                        )
                    } else {
                        callSessionManager.initiateCall(user, friend, isVideo = isVideo)
                    }
                }
            } catch (e: Exception) {
                // Ignore load error
            }
        }
    }

    // Update VAD chunker target language when preferred subtitle language changes (Item B)
    LaunchedEffect(preferredSubtitleLang) {
        callSessionManager.vadChunker?.targetLanguage = preferredSubtitleLang
    }

    // Collect speech status from VAD chunker
    LaunchedEffect(callSessionManager) {
        while (true) {
            val chunker = callSessionManager.vadChunker
            if (chunker != null) {
                vadStatus = chunker.uiStatus.value
            } else if (callSessionState?.status == "active") {
                vadStatus = "Konuşma bekleniyor..."
            } else {
                vadStatus = "Aranıyor..."
            }
            delay(350L)
        }
    }

    // Auto-scroll transcripts
    LaunchedEffect(transcripts.size) {
        if (transcripts.isNotEmpty()) {
            listState.animateScrollToItem(transcripts.size - 1)
        }
    }

    // Cleanup session on exit
    DisposableEffect(Unit) {
        onDispose {
            GlobalCallState.isInActiveVideoCall = false
            GlobalCallState.activeAudioEngine = null
            callSessionManager.dispose()
        }
    }

    val formattedDuration = String.format(
        Locale.getDefault(),
        "%02d:%02d",
        callDuration / 60,
        callDuration % 60
    )

    val activeAudioEngine = callSessionManager.audioEngine
    val networkQuality by (activeAudioEngine?.networkQuality?.collectAsState()
        ?: remember { mutableStateOf(NetworkQuality.EXCELLENT) })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
    ) {
        // VIDEO RENDERING BACKGROUND (Item #5 & PiP Mode: WhatsApp / Zoom style floating window)
        if (isVideo && activeAudioEngine != null) {
            val localTrack = activeAudioEngine.getLocalVideoTrack()
            val remoteTracks = activeAudioEngine.remoteVideoTracks.values.toList()

            Box(modifier = Modifier.fillMaxSize()) {
                if (remoteTracks.isNotEmpty()) {
                    if (remoteTracks.size == 1) {
                        VideoRendererView(
                            videoTrack = remoteTracks.first(),
                            eglBaseContext = activeAudioEngine.rootEglBase?.eglBaseContext,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            remoteTracks.chunked(2).forEach { rowTracks ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    rowTracks.forEach { rTrack ->
                                        VideoRendererView(
                                            videoTrack = rTrack,
                                            eglBaseContext = activeAudioEngine.rootEglBase?.eglBaseContext,
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceMidnightDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isInPipMode) "" else "Karşı tarafın video görüntüsü bekleniyor...",
                            color = TextMutedWarmGrey,
                            fontSize = 14.sp
                        )
                    }
                }

                // Local Camera PIP Floating Window (Top-Right)
                if (localTrack != null && !isVideoMuted) {
                    val pipWidth = if (isInPipMode) 70.dp else 110.dp
                    val pipHeight = if (isInPipMode) 95.dp else 150.dp
                    val pipPaddingTop = if (isInPipMode) 8.dp else 48.dp
                    val pipPaddingEnd = if (isInPipMode) 8.dp else 16.dp

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = pipPaddingTop, end = pipPaddingEnd)
                            .size(width = pipWidth, height = pipHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, NeonLimeAccent, RoundedCornerShape(12.dp))
                    ) {
                        VideoRendererView(
                            videoTrack = localTrack,
                            eglBaseContext = activeAudioEngine.rootEglBase?.eglBaseContext,
                            modifier = Modifier.fillMaxSize(),
                            isMirror = true
                        )
                    }
                }
            }
        }

        // FOREGROUND OVERLAY (Completely hidden when in PiP mode for clean floating video)
        if (!isInPipMode) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(28.dp))

                // 3-Second Join Banner (Item #5)
                AnimatedVisibility(
                    visible = joinBanner != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = NeonLimeAccent,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = joinBanner ?: "",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }

                // Peer Battery Warning Banner (Item #6)
                AnimatedVisibility(
                    visible = peerBatteryAlert != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFE56B63).copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = peerBatteryAlert ?: "",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }

                // Header Action Row: Network indicator, Subtitle Language, Participants count, Export Transcripts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.android.zilbio.R.drawable.zilbio_logo_transparent),
                            contentDescription = "Zilbio Logo",
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Network Quality Chip (Item #6)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceSlateNavy.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, BorderSlateNavy)
                        ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = when (networkQuality) {
                                    NetworkQuality.EXCELLENT -> OnlineStatusGreen
                                    NetworkQuality.GOOD -> NeonLimeAccent
                                    NetworkQuality.WEAK -> Color(0xFFF2A900)
                                    NetworkQuality.CRITICAL -> CallEndCoralRed
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when (networkQuality) {
                                    NetworkQuality.EXCELLENT -> "Mükemmel"
                                    NetworkQuality.GOOD -> "İyi"
                                    NetworkQuality.WEAK -> "Zayıf"
                                    NetworkQuality.CRITICAL -> "Kesildi"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextOffWhite
                            )
                        }
                    }
                }

                // Multi-Language Subtitles Selector (Item B)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceSlateNavy.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        modifier = Modifier.clickable { showSubtitleLanguageDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Altyazı Dili",
                                tint = NeonLimeAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = preferredSubtitleLang.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonLimeAccent
                            )
                        }
                    }

                    // Add Participant Button (Mesh Conference up to 4, Item #5)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceSlateNavy.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        modifier = Modifier.clickable { showAddParticipantDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Katılımcı Ekle",
                                tint = NeonLimeAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val currentCount = participants.size.coerceAtLeast(2)
                            Text(
                                text = "$currentCount/4",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextOffWhite
                            )
                        }
                    }

                    // Call Transcript Export Button (Item A)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceSlateNavy.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, BorderSlateNavy),
                        modifier = Modifier.clickable {
                            exportTranscripts(context, transcripts, formattedDuration)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Transkripti Paylaş/Dışa Aktar",
                                tint = NeonLimeAccent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // User Info & Status
                if (!isVideo) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(SurfaceSlateNavy)
                            .border(2.dp, NeonLimeAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AvatarInitials(
                            name = friendUser?.displayName ?: "Arkadaş",
                            size = 68,
                            avatarBase64 = friendUser?.avatarBase64
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = friendUser?.displayName ?: "Aranıyor...",
                        fontSize = if (isVideo) 16.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOffWhite
                    )
                    if (friendUser?.isOwnerOrAdmin() == true) {
                        AdminBadge(customRole = friendUser?.permissions?.customRoleLabel, isCompact = true)
                    } else if (friendUser?.permissions?.isVerifiedBadge == true) {
                        VerifiedBadge(isCompact = true)
                    }
                }


                // Duration timer
                Text(
                    text = formattedDuration,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonLimeAccent
                )

                // Dynamic Audio Level Meter (Voice activity indicators for local & remote)
                if (callSessionState?.status == "active") {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Local mic indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = null,
                                tint = if (isMuted) CallEndCoralRed else if (localMicLevel > 0.08f) NeonLimeAccent else TextMutedWarmGrey,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SurfaceSlateNavy)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .fillMaxWidth(fraction = localMicLevel.coerceIn(0f, 1f))
                                        .background(NeonLimeAccent)
                                )
                            }
                        }

                        // Remote mic indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = null,
                                tint = if (remoteMicLevel > 0.08f) OnlineStatusGreen else TextMutedWarmGrey,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SurfaceSlateNavy)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .fillMaxWidth(fraction = remoteMicLevel.coerceIn(0f, 1f))
                                        .background(OnlineStatusGreen)
                                )
                            }
                        }
                    }
                }

                // VAD Speech Status Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (callSessionState?.status == "active") OnlineStatusGreen else NeonLimeAccent)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = vadStatus,
                        fontSize = 12.sp,
                        color = TextMutedWarmGrey
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // LIVE TRANSCRIPT FEED OVERLAY
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceMidnightDark.copy(alpha = if (isVideo) 0.65f else 0.85f))
                        .border(1.dp, BorderSlateNavy.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    if (transcripts.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Canlı Altyazı & Anında Çeviri",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextOffWhite
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Görüşme sırasında konuşulanlar sıfır gecikmeyle [${preferredSubtitleLang.uppercase()}] diline çevrilip altyazı olarak akacaktır.",
                                    fontSize = 12.sp,
                                    color = TextMutedWarmGrey,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)
                        ) {
                            items(transcripts) { segment ->
                                LiveTranscriptCard(segment = segment)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // BOTTOM CONTROLS ROW (Mute, Video Mute, Camera Switch, Audio Route, End Call)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SurfaceSlateNavy.copy(alpha = 0.9f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mic Mute toggle
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            activeAudioEngine?.setMicrophoneMute(isMuted)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) CallEndCoralRed.copy(alpha = 0.2f) else SurfaceMidnightDark)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mikrofon Aç/Kapat",
                            tint = if (isMuted) CallEndCoralRed else TextOffWhite,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // If Video Call: Video Camera Toggle & Camera Switch
                    if (isVideo) {
                        IconButton(
                            onClick = {
                                isVideoMuted = !isVideoMuted
                                activeAudioEngine?.setVideoEnabled(!isVideoMuted)
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (isVideoMuted) CallEndCoralRed.copy(alpha = 0.2f) else SurfaceMidnightDark)
                        ) {
                            Icon(
                                imageVector = if (isVideoMuted) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                contentDescription = "Kamera Aç/Kapat",
                                tint = if (isVideoMuted) CallEndCoralRed else TextOffWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                activeAudioEngine?.switchCamera()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(SurfaceMidnightDark)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Kamera Değiştir",
                                tint = TextOffWhite,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Audio Route Selector (Earpiece, Speaker, Bluetooth/Headset) (Item #3)
                    IconButton(
                        onClick = { showAudioRouteDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SurfaceMidnightDark)
                    ) {
                        val routeIcon = when (currentAudioRoute) {
                            AudioOutputRoute.EARPIECE -> Icons.Default.Hearing
                            AudioOutputRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                            AudioOutputRoute.WIRED_HEADSET -> Icons.Default.Headphones
                            AudioOutputRoute.BLUETOOTH -> Icons.Default.Bluetooth
                            else -> Icons.AutoMirrored.Filled.VolumeUp
                        }
                        Icon(
                            imageVector = routeIcon,
                            contentDescription = "Ses Çıkışını Değiştir",
                            tint = NeonLimeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // End Call Button (Large Coral Red) (Item #4)
                    IconButton(
                        onClick = {
                            val currentUid = currentUser?.userId ?: ""
                            callSessionManager.endCall(currentUid)
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(CallEndCoralRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Aramayı Sonlandır",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // Multi-Language Subtitles Dialog (Item B)
    if (showSubtitleLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleLanguageDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Language, contentDescription = null, tint = NeonLimeAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Altyazı & Çeviri Dili",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOffWhite
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Diğer katılımcıları etkilemeden yalnızca kendi ekranınızda gösterilecek altyazı dilini seçin:",
                        fontSize = 13.sp,
                        color = TextMutedWarmGrey,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    AppLanguage.entries.forEach { lang ->
                        val isSelected = preferredSubtitleLang == lang.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    preferredSubtitleLang = lang.code
                                    showSubtitleLanguageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    preferredSubtitleLang = lang.code
                                    showSubtitleLanguageDialog = false
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
                    }
                }
            },
            containerColor = SurfaceDarkWarmElevated,
            confirmButton = {
                TextButton(onClick = { showSubtitleLanguageDialog = false }) {
                    Text("Kapat", color = NeonLimeAccent)
                }
            }
        )
    }

    // Audio Output Route Dialog (Earpiece, Speaker, Wired Headset, Bluetooth with live device detection)
    if (showAudioRouteDialog) {
        val connectedDevices = remember {
            activeAudioEngine?.audioDeviceController?.getConnectedAudioDevices() ?: listOf(
                com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.EARPIECE, "Dahili Ahize (Kulaklık)", true),
                com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.SPEAKER, "Dahili Hoparlör (Dış Ses)", true),
                com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.WIRED_HEADSET, "Kablolu Kulaklık", false),
                com.android.zilbio.domain.model.ConnectedAudioDeviceInfo(AudioOutputRoute.BLUETOOTH, "Bluetooth Kulaklık", false)
            )
        }
        AlertDialog(
            onDismissRequest = { showAudioRouteDialog = false },
            title = {
                Text(
                    text = "Ses Çıkışını ve Cihazı Seçin",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextOffWhite
                )
            },
            text = {
                Column {
                    connectedDevices.forEach { dev ->
                        val isSelected = currentAudioRoute == dev.route
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    currentAudioRoute = dev.route
                                    activeAudioEngine?.audioDeviceController?.setAudioRoute(dev.route)
                                    showAudioRouteDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    currentAudioRoute = dev.route
                                    activeAudioEngine?.audioDeviceController?.setAudioRoute(dev.route)
                                    showAudioRouteDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = NeonLimeAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val icon = when (dev.route) {
                                AudioOutputRoute.EARPIECE -> Icons.Default.Hearing
                                AudioOutputRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                                AudioOutputRoute.WIRED_HEADSET -> Icons.Default.Headphones
                                AudioOutputRoute.BLUETOOTH -> Icons.Default.Bluetooth
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (dev.isConnected) NeonLimeAccent else TextMutedWarmGrey,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = dev.name,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (dev.isConnected) TextOffWhite else TextMutedWarmGrey
                            )
                        }
                    }
                }
            },
            containerColor = SurfaceDarkWarmElevated,
            confirmButton = {
                TextButton(onClick = { showAudioRouteDialog = false }) {
                    Text("Kapat", color = NeonLimeAccent)
                }
            }
        )
    }

    // Add Participant to Conference Dialog (Item #5: 4-person mesh conference guard)
    if (showAddParticipantDialog) {
        val currentCount = participants.size.coerceAtLeast(2)
        val canAddMore = currentCount < 4

        AlertDialog(
            onDismissRequest = { showAddParticipantDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, tint = NeonLimeAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Görüşmeye Katılımcı Ekle",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextOffWhite
                    )
                }
            },
            text = {
                Column {
                    if (!canAddMore) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CallEndCoralRed.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "Maksimum 4 kişi sınırına ulaşıldı. 5. kişi eklenemez.",
                                color = CallEndCoralRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Konferans görüşmesi için arkadaşınızı davet edin ($currentCount/4):",
                            fontSize = 13.sp,
                            color = TextMutedWarmGrey,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        val availableFriends = allFriends.filter { friend ->
                            friend.userId != friendId && !participants.containsKey(friend.userId)
                        }

                        if (availableFriends.isEmpty()) {
                            Text(
                                text = "Eklenebilecek uygun arkadaş bulunamadı.",
                                fontSize = 13.sp,
                                color = TextMutedWarmGrey,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.height(200.dp)) {
                                items(availableFriends) { friend ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AvatarInitials(
                                                name = friend.displayName,
                                                size = 36,
                                                avatarBase64 = friend.avatarBase64
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = friend.displayName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = TextOffWhite
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    callSessionManager.addParticipantToCall(
                                                        friend.userId,
                                                        friend.displayName
                                                    )
                                                }
                                                showAddParticipantDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonLimeAccent),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("Ekle", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = SurfaceDarkWarmElevated,
            confirmButton = {
                TextButton(onClick = { showAddParticipantDialog = false }) {
                    Text("Kapat", color = TextOffWhite)
                }
            }
        )
    }
}

// Helper to export Call Transcripts as .txt via Android Intent.ACTION_SEND (Item A)
private fun exportTranscripts(context: Context, transcripts: List<CallTranscriptSegment>, durationStr: String) {
    val sb = StringBuilder()
    sb.append("==============================================\n")
    sb.append("   Zilbio Canlı Çeviri & Çağrı Transkripti\n")
    sb.append("==============================================\n")
    sb.append("Tarih: ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
    sb.append("Görüşme Süresi: $durationStr\n")
    sb.append("Toplam Cümle: ${transcripts.size}\n")
    sb.append("----------------------------------------------\n\n")

    if (transcripts.isEmpty()) {
        sb.append("(Bu görüşmede kayıtlı altyazı bulunmamaktadır.)\n")
    } else {
        transcripts.forEach { segment ->
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(segment.timestamp))
            val speaker = segment.speakerName.ifEmpty { "Konuşmacı" }
            sb.append("[$time] $speaker:\n")
            sb.append("  Orijinal : ${segment.originalText}\n")
            sb.append("  Çeviri   : ${segment.translatedText}\n\n")
        }
    }
    sb.append("==============================================\n")
    sb.append("Zilbio V3.0.1 ile güvenle kaydedildi.\n")

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Zilbio Çağrı Transkripti")
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    context.startActivity(Intent.createChooser(shareIntent, "Transkripti Paylaş / Kaydet"))
}

@Composable
fun CallScreen(
    friendId: String,
    callId: String? = null,
    isIncoming: Boolean = false,
    isVideo: Boolean = false,
    onNavigateBack: () -> Unit
) {
    CallScreen(
        navController = null,
        friendId = friendId,
        callId = callId,
        isIncoming = isIncoming,
        isVideo = isVideo,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun VideoRendererView(
    videoTrack: VideoTrack?,
    eglBaseContext: org.webrtc.EglBase.Context?,
    modifier: Modifier = Modifier,
    isMirror: Boolean = false
) {
    if (videoTrack == null || eglBaseContext == null) {
        Box(modifier = modifier.background(SurfaceMidnightDark))
        return
    }

    var rendererRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(isMirror)
                videoTrack.addSink(this)
                rendererRef = this
            }
        },
        modifier = modifier
    )

    DisposableEffect(videoTrack) {
        onDispose {
            rendererRef?.let { r ->
                try {
                    videoTrack.removeSink(r)
                    r.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            }
        }
    }
}

@Composable
private fun LiveTranscriptCard(segment: CallTranscriptSegment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
        border = BorderStroke(1.dp, BorderSlateNavy),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = segment.speakerName.ifEmpty { "Konuşmacı" },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonLimeAccent
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Original speech text
            Text(
                text = segment.originalText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = TextMutedWarmGrey,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Translated speech text in crisp off-white with bold styling
            Text(
                text = segment.translatedText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextOffWhite,
                lineHeight = 20.sp
            )
        }
    }
}
