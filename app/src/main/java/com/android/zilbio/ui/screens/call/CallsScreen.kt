package com.android.zilbio.ui.screens.call

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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.android.zilbio.data.local.UserPreferences
import com.android.zilbio.data.remote.CloudRealtimeService
import com.android.zilbio.domain.model.CallHistoryItem
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.theme.AccentWarmPeach
import com.android.zilbio.ui.theme.BackgroundDarkEspresso
import com.android.zilbio.ui.theme.BorderDarkSubtle
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.OnlineStatusGreen
import com.android.zilbio.ui.theme.SurfaceDarkWarm
import com.android.zilbio.ui.theme.TextMutedWarmGrey
import com.android.zilbio.ui.theme.TextOffWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(navController: NavController) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    var callLogs by remember { mutableStateOf<List<CallHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val user = UserPreferences.getUserSync(context)
        if (user != null) {
            val history = CloudRealtimeService.getCallHistory(user.userId)
            callLogs = history
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.callsTitle,
                        color = TextOffWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDarkEspresso
                )
            )
        },
        containerColor = BackgroundDarkEspresso
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentWarmPeach
                )
            } else if (callLogs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SurfaceDarkWarm),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = AccentWarmPeach,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.callHistoryEmpty,
                        color = TextOffWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.callHistoryEmptyDesc,
                        color = TextMutedWarmGrey,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                ) {
                    items(callLogs, key = { it.callId }) { item ->
                        CallHistoryRow(
                            item = item,
                            onCallClick = {
                                navController.navigate("call/${item.peerId}")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallHistoryRow(
    item: CallHistoryItem,
    onCallClick: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isMissed = item.callStatus == "missed" || item.callStatus == "declined"
    val timeFormatted = remember(item.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(item.timestamp))
    }

    val durationText = remember(item.durationSeconds) {
        val mins = item.durationSeconds / 60
        val secs = item.durationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkWarm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon indicating call status & direction
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMissed) DestructiveCoralRed.copy(alpha = 0.15f)
                        else AccentWarmPeach.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    isMissed -> Icons.AutoMirrored.Filled.CallMissed
                    item.isIncoming -> Icons.AutoMirrored.Filled.CallReceived
                    else -> Icons.AutoMirrored.Filled.CallMade
                }
                val iconTint = if (isMissed) DestructiveCoralRed else OnlineStatusGreen
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.peerName.ifBlank { item.peerId },
                    color = if (isMissed) DestructiveCoralRed else TextOffWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isMissed) strings.missedCall else "${strings.callDuration}$durationText",
                        color = if (isMissed) DestructiveCoralRed.copy(alpha = 0.8f) else TextMutedWarmGrey,
                        fontSize = 13.sp
                    )
                    Text(
                        text = " • $timeFormatted",
                        color = TextMutedWarmGrey,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentWarmPeach.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = strings.startAudioCall,
                    tint = AccentWarmPeach,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
