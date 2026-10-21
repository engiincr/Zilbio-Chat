package com.android.zilbio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.zilbio.ui.theme.AccentAmberOrange
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.DestructiveCoralRed
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class DynamicIslandType {
    BATTERY_ALERT,
    BADGE_CELEBRATION,
    MESSAGE_PREVIEW,
    CALL_STATUS,
    INFO
}

data class DynamicIslandPayload(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String? = null,
    val emoji: String = "⚡",
    val accentColor: Color = AccentNeonLime,
    val durationMs: Long = 4800L,
    val type: DynamicIslandType = DynamicIslandType.INFO,
    val onClick: (() -> Unit)? = null
)

object ZilbioDynamicIslandManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var dismissJob: Job? = null

    private val _overlayFlow = MutableStateFlow<DynamicIslandPayload?>(null)
    val overlayFlow: StateFlow<DynamicIslandPayload?> = _overlayFlow.asStateFlow()

    fun show(payload: DynamicIslandPayload) {
        dismissJob?.cancel()
        _overlayFlow.value = payload

        if (payload.durationMs > 0L) {
            dismissJob = scope.launch {
                delay(payload.durationMs)
                if (_overlayFlow.value?.id == payload.id) {
                    _overlayFlow.value = null
                }
            }
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        _overlayFlow.value = null
    }

    fun showBatteryAlert(userName: String, percent: Int, isLocal: Boolean = false) {
        val titleText = if (isLocal) {
            "Cihazınızın şarjı %$percent kaldı!"
        } else {
            "🔋 $userName - Pil Seviyesi %$percent'a geriledi"
        }
        val accent = if (percent <= 5) DestructiveCoralRed else AccentAmberOrange

        show(
            DynamicIslandPayload(
                title = titleText,
                subtitle = "Görüşmenin kesilmemesi için şarja takmanız önerilir.",
                emoji = "🔋",
                accentColor = accent,
                durationMs = 5500L,
                type = DynamicIslandType.BATTERY_ALERT
            )
        )
    }

    fun showBadgeUnlocked(userName: String, badgeTitle: String, iconEmoji: String) {
        show(
            DynamicIslandPayload(
                title = "🎉 Tebrikler, $userName!",
                subtitle = "$badgeTitle Rozeti Kazandın! 🌟",
                emoji = iconEmoji,
                accentColor = AccentAmberOrange,
                durationMs = 6000L,
                type = DynamicIslandType.BADGE_CELEBRATION
            )
        )
    }

    fun showInfo(title: String, subtitle: String? = null, emoji: String = "⚡") {
        show(
            DynamicIslandPayload(
                title = title,
                subtitle = subtitle,
                emoji = emoji,
                accentColor = AccentNeonLime,
                durationMs = 4000L,
                type = DynamicIslandType.INFO
            )
        )
    }
}

@Composable
fun ZilbioDynamicOverlay(
    modifier: Modifier = Modifier
) {
    val payload by ZilbioDynamicIslandManager.overlayFlow.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = payload != null,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        ) { -it } + fadeIn() + scaleIn(initialScale = 0.82f),
        exit = slideOutVertically(
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        ) { -it } + fadeOut() + scaleOut(targetScale = 0.82f),
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
    ) {
        payload?.let { current ->
            DynamicIslandContent(
                payload = current,
                isExpanded = isExpanded,
                onToggleExpand = { isExpanded = !isExpanded },
                onDismiss = {
                    isExpanded = false
                    ZilbioDynamicIslandManager.dismiss()
                }
            )
        }
    }
}

@Composable
private fun DynamicIslandContent(
    payload: DynamicIslandPayload,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit
) {
    val borderGradient = Brush.horizontalGradient(
        listOf(
            payload.accentColor.copy(alpha = 0.6f),
            BorderSlateNavy,
            payload.accentColor.copy(alpha = 0.4f)
        )
    )

    Surface(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 420.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = payload.accentColor)
            .clip(RoundedCornerShape(26.dp))
            .background(SurfaceSlateNavy)
            .border(1.5.dp, borderGradient, RoundedCornerShape(26.dp))
            .clickable {
                if (payload.onClick != null) {
                    payload.onClick.invoke()
                } else if (!payload.subtitle.isNullOrBlank()) {
                    onToggleExpand()
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -15) {
                        onDismiss() // Swipe up to dismiss
                    }
                }
            }
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        color = SurfaceSlateNavy,
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Leading Emoji with soft glowing halo
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(payload.accentColor.copy(alpha = 0.18f))
                        .border(1.dp, payload.accentColor.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = payload.emoji,
                        fontSize = 17.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title Text
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = payload.title,
                        color = TextOffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (isExpanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!payload.subtitle.isNullOrBlank() && !isExpanded) {
                        Text(
                            text = payload.subtitle,
                            color = TextMutedSlateGrey,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Dismiss icon
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = TextMutedSlateGrey,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Expanded extra details view
            if (isExpanded && !payload.subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = payload.subtitle,
                        color = TextOffWhite.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
