package com.translive.app.ui.screens.call

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.translive.app.data.local.UserPreferences
import com.translive.app.data.remote.CloudRealtimeService
import com.translive.app.domain.model.Language
import com.translive.app.domain.model.User
import com.translive.app.ui.screens.friends.AvatarInitials
import com.translive.app.ui.theme.AccentAmberOrange
import com.translive.app.ui.theme.BackgroundAlabasterCream
import com.translive.app.ui.theme.CallAcceptGreen
import com.translive.app.ui.theme.CallEndCoralRed
import com.translive.app.ui.theme.OutgoingBubblePeach
import com.translive.app.ui.theme.TextPrimaryEspresso
import com.translive.app.ui.theme.TextSecondaryWarmGrey
import kotlinx.coroutines.launch

@Composable
fun IncomingCallScreen(
    navController: NavController? = null,
    callId: String,
    callerId: String,
    onAcceptCall: (callId: String, callerId: String) -> Unit = { cId, clrId ->
        navController?.navigate("call/$clrId") {
            popUpTo("incomingCall/$cId/$clrId") { inclusive = true }
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

    // Infinite pulsing animation for caller avatar and "Gelen Arama..." text
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
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
            .background(BackgroundAlabasterCream)
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
                    .size(130.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(OutgoingBubblePeach.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(OutgoingBubblePeach)
                        .border(2.dp, AccentAmberOrange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val callerName = callerUser?.displayName ?: "Arayan Kişi"
                    AvatarInitials(name = callerName, size = 100)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Name
            Text(
                text = callerUser?.displayName ?: "Arayan Kişi",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryEspresso,
                textAlign = TextAlign.Center
            )

            if (!callerUser?.username.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "@${callerUser?.username}",
                    fontSize = 14.sp,
                    color = TextSecondaryWarmGrey
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Language Badge if available
            callerUser?.let { u ->
                val lang = Language.fromCode(u.language)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OutgoingBubblePeach
                ) {
                    Text(
                        text = "${lang.flag} ${lang.displayName}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentAmberOrange,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Pulsing "Gelen Arama..." Text
            Text(
                text = "Gelen Canlı Çevirili Arama...",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentAmberOrange,
                modifier = Modifier.alpha(pulseAlpha)
            )
        }

        // Bottom Call Action Buttons (Accept / Decline)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button (Red circle)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val currentUid = currentUser?.userId ?: ""
                        coroutineScope.launch {
                            try {
                                CloudRealtimeService.endCall(callId, currentUid)
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
                    color = TextSecondaryWarmGrey
                )
            }

            // Accept Button (Green circle)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        onAcceptCall(callId, callerId)
                    },
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(CallAcceptGreen)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
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
    onAcceptCall: (callId: String, callerId: String) -> Unit,
    onDeclineCall: () -> Unit
) {
    IncomingCallScreen(
        navController = null,
        callId = callId,
        callerId = callerId,
        onAcceptCall = onAcceptCall,
        onDeclineCall = onDeclineCall
    )
}
