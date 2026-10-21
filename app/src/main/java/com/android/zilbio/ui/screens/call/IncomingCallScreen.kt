package com.android.zilbio.ui.screens.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.domain.model.Language
import com.android.zilbio.domain.model.User
import com.android.zilbio.service.GlobalRealtimeSyncManager
import com.android.zilbio.service.ZilbioNotificationService
import com.android.zilbio.ui.screens.friends.AvatarInitials
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.CallAcceptGreen
import com.android.zilbio.ui.theme.CallEndCoralRed
import com.android.zilbio.ui.theme.NeonLimeAccent
import com.android.zilbio.ui.theme.SurfaceMidnightDark
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.TextMutedWarmGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun IncomingCallScreen(
    navController: NavController? = null,
    callId: String,
    callerId: String,
    isVideo: Boolean = false,
    onAcceptCall: (callId: String, callerId: String, isVideo: Boolean) -> Unit = { cId, clrId, video ->
        navController?.context?.let { ctx ->
            GlobalRealtimeSyncManager.dismissIncomingCall(ctx)
        }
        navController?.navigate("call/$clrId?callId=$cId&isIncoming=true&isVideo=$video") {
            popUpTo("incomingCall/$cId/$clrId?isVideo=$video") { inclusive = true }
        }
    },
    onDeclineCall: () -> Unit = { navController?.popBackStack() }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by UserPreferences.getUser(context).collectAsState(initial = null)

    var callerUser by remember { mutableStateOf<User?>(null) }

    // Fetch caller user info from cloud
    LaunchedEffect(callerId) {
        if (callerId.isNotEmpty()) {
            try {
                callerUser = CloudRealtimeService.searchUserById(callerId)
            } catch (e: Exception) {
                // Ignore load error
            }
        }
    }

    // Stop alert when screen disposes
    DisposableEffect(Unit) {
        onDispose {
            ZilbioNotificationService.stopIncomingCallAlert(context)
        }
    }

    // Strict 30-Second Inbound Call Timeout (Item #2)
    LaunchedEffect(Unit) {
        delay(30_000L)
        // Auto-dismiss after 30 seconds
        val currentUid = currentUser?.userId ?: ""
        if (currentUid.isNotEmpty()) {
            val missedItem = CallHistoryItem(
                callId = callId,
                peerId = callerId,
                peerName = callerUser?.displayName ?: "Arayan Kişi",
                isIncoming = true,
                isVideo = isVideo,
                callStatus = "missed",
                durationSeconds = 0L,
                timestamp = System.currentTimeMillis()
            )
            CloudRealtimeService.saveCallHistoryItem(currentUid, missedItem)
            CloudRealtimeService.endCallWithSignal(callId, callerId, currentUid)
            val chatId = CloudRealtimeService.generateChatId(currentUid, callerId)
            CloudRealtimeService.logCallToChat(chatId, callerId, currentUid, isVideo, "missed", 0L)
        }
        GlobalRealtimeSyncManager.dismissIncomingCall(context)
        onDeclineCall()
    }

    // Pulsing animation for avatar
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundMidnightNavy)
            .padding(24.dp)
    ) {
        // Main Center Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Pulsing Ring around Avatar
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(NeonLimeAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(114.dp)
                        .clip(CircleShape)
                        .background(SurfaceSlateNavy)
                        .border(3.dp, NeonLimeAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val callerName = callerUser?.displayName ?: "Arayan Kişi"
                    AvatarInitials(
                        name = callerName,
                        size = 104,
                        avatarBase64 = callerUser?.avatarBase64
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Name
            Text(
                text = callerUser?.displayName ?: "Arayan Kişi",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextOffWhite,
                textAlign = TextAlign.Center
            )

            if (!callerUser?.username.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "@${callerUser?.username}",
                    fontSize = 14.sp,
                    color = TextMutedWarmGrey
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badges row: Language & Video Call badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                callerUser?.let { u ->
                    val lang = Language.fromCode(u.language)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceSlateNavy
                    ) {
                        Text(
                            text = "${lang.flag} ${lang.displayName}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonLimeAccent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (isVideo) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonLimeAccent.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonLimeAccent)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = NeonLimeAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Görüntülü Arama",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonLimeAccent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Pulsing "Gelen Canlı Çevirili Arama..." Text
            Text(
                text = if (isVideo) "Gelen Canlı Çevirili Görüntülü Arama..." else "Gelen Canlı Çevirili Sesli Arama...",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonLimeAccent,
                modifier = Modifier.alpha(pulseAlpha)
            )
        }

        // Bottom Call Action Buttons (Accept / Decline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button (Red circle)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val currentUid = currentUser?.userId ?: ""
                        GlobalRealtimeSyncManager.dismissIncomingCall(context)
                        coroutineScope.launch {
                            try {
                                val missedItem = CallHistoryItem(
                                    callId = callId,
                                    peerId = callerId,
                                    peerName = callerUser?.displayName ?: "Arayan Kişi",
                                    isIncoming = true,
                                    isVideo = isVideo,
                                    callStatus = "declined",
                                    durationSeconds = 0L,
                                    timestamp = System.currentTimeMillis()
                                )
                                CloudRealtimeService.saveCallHistoryItem(currentUid, missedItem)
                                CloudRealtimeService.endCallWithSignal(callId, callerId, currentUid)
                                val chatId = CloudRealtimeService.generateChatId(currentUid, callerId)
                                CloudRealtimeService.logCallToChat(chatId, callerId, currentUid, isVideo, "declined", 0L)
                            } catch (e: Exception) {
                                // Ignore
                            }
                            onDeclineCall()
                        }
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(CallEndCoralRed)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Reddet",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reddet",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMutedWarmGrey
                )
            }

            // Accept Button (Green circle)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        ZilbioNotificationService.stopIncomingCallAlert(context)
                        onAcceptCall(callId, callerId, isVideo)
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(CallAcceptGreen)
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                        contentDescription = "Cevapla",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Cevapla",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CallAcceptGreen
                )
            }
        }
    }
}

@Composable
fun IncomingCallScreen(
    callId: String,
    callerId: String,
    isVideo: Boolean = false,
    onAcceptCall: (callId: String, callerId: String, isVideo: Boolean) -> Unit,
    onDeclineCall: () -> Unit
) {
    IncomingCallScreen(
        navController = null,
        callId = callId,
        callerId = callerId,
        isVideo = isVideo,
        onAcceptCall = onAcceptCall,
        onDeclineCall = onDeclineCall
    )
}
