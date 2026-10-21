package com.android.zilbio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.zilbio.data.rewards.Badge
import com.android.zilbio.data.rewards.CelebrationEvent
import com.android.zilbio.ui.theme.AccentNeonLime
import com.android.zilbio.ui.theme.BackgroundMidnightNavy
import com.android.zilbio.ui.theme.BorderSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavy
import com.android.zilbio.ui.theme.SurfaceSlateNavyElevated
import com.android.zilbio.ui.theme.TextMutedSlateGrey
import com.android.zilbio.ui.theme.TextOffWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileBadgesCard(
    badges: List<Badge>,
    title: String = "Başarı Rozetleri 🏆",
    modifier: Modifier = Modifier
) {
    var selectedBadgeForDetail by remember { mutableStateOf<Badge?>(null) }
    val unlockedCount = badges.count { it.isUnlocked }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceSlateNavy),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlateNavy)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = AccentNeonLime,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = TextOffWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentNeonLime.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentNeonLime.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "$unlockedCount / ${badges.size}",
                        color = AccentNeonLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                badges.forEach { badge ->
                    BadgeChipItem(
                        badge = badge,
                        onClick = { selectedBadgeForDetail = badge }
                    )
                }
            }
        }
    }

    // Detail dialog when clicked
    selectedBadgeForDetail?.let { badge ->
        BadgeDetailDialog(badge = badge, onDismiss = { selectedBadgeForDetail = null })
    }
}

@Composable
fun BadgeChipItem(
    badge: Badge,
    onClick: () -> Unit
) {
    val isUnlocked = badge.isUnlocked
    val borderColor = if (isUnlocked) AccentNeonLime.copy(alpha = 0.6f) else BorderSlateNavy
    val bgColor = if (isUnlocked) SurfaceSlateNavyElevated else SurfaceSlateNavy.copy(alpha = 0.5f)

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = badge.iconEmoji,
                fontSize = if (isUnlocked) 18.sp else 15.sp,
                modifier = Modifier.padding(end = 6.dp)
            )

            Text(
                text = badge.title,
                color = if (isUnlocked) TextOffWhite else TextMutedSlateGrey,
                fontSize = 12.sp,
                fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!isUnlocked) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Kilitli",
                    tint = TextMutedSlateGrey.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun BadgeDetailDialog(
    badge: Badge,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundMidnightNavy,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (badge.isUnlocked) AccentNeonLime.copy(alpha = 0.15f) else SurfaceSlateNavy)
                        .border(
                            1.dp,
                            if (badge.isUnlocked) AccentNeonLime else BorderSlateNavy,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = badge.iconEmoji, fontSize = 32.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = badge.title,
                    color = TextOffWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = badge.description,
                    color = TextOffWhite.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceSlateNavy,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlateNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kazanma Şartı: ${badge.requirementText}",
                            color = AccentNeonLime,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (badge.isUnlocked && badge.unlockedAt != null && badge.unlockedAt > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kazanıldı: ${dateFormat.format(Date(badge.unlockedAt))}",
                                color = TextMutedSlateGrey,
                                fontSize = 11.sp
                            )
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Henüz kazanılmadı (Kilitli)",
                                color = TextMutedSlateGrey,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNeonLime, contentColor = BackgroundMidnightNavy)
            ) {
                Text(text = "Kapat", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun BadgeCelebrationDialog(
    event: CelebrationEvent,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "celebration_pulse")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = BackgroundMidnightNavy,
            border = androidx.compose.foundation.BorderStroke(
                2.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0xFFE5854B), // Amber Orange
                        Color(0xFF7A3E1D), // Rich Terracotta
                        Color(0xFFE5854B)
                    )
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge icon with pulsing animation
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(scaleAnim)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFE5854B).copy(alpha = 0.35f),
                                    Color(0xFF7A3E1D).copy(alpha = 0.1f)
                                )
                            )
                        )
                        .border(2.dp, Color(0xFFE5854B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = event.badge.iconEmoji, fontSize = 44.sp)
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Congratulatory greeting with UPPERCASE user name
                Text(
                    text = "TEBRİKLER, ${event.userName}!",
                    color = Color(0xFFF2B896), // Warm Peach
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Yeni Bir Rozet Kazandın! 🎉",
                    color = TextOffWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Badge Title & Description Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceSlateNavy,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSlateNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = event.badge.title,
                            color = Color(0xFFE5854B),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = event.badge.description,
                            color = TextOffWhite.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE5854B),
                        contentColor = BackgroundMidnightNavy
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Harika, Teşekkürler!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
