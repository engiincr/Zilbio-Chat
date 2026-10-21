package com.android.zilbio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Distinctive Administrator & Verified Badges for Zilbio V3.0.1
val NeonLime = Color(0xFFA8FF00)
val DarkNeonLime = Color(0xFF1B3300)
val GoldShield = Color(0xFFFFD700)
val DarkGold = Color(0xFF3B3000)

@Composable
fun AdminBadge(
    modifier: Modifier = Modifier,
    customRole: String? = null,
    isCompact: Boolean = false
) {
    val roleTitle = customRole?.takeIf { it.isNotBlank() } ?: "Administrator"

    Row(
        modifier = modifier
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(NeonLime, GoldShield)),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = Color(0xFF14200D),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = if (isCompact) 4.dp else 6.dp, vertical = if (isCompact) 1.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = "Administrator",
            tint = NeonLime,
            modifier = Modifier.size(if (isCompact) 11.dp else 13.dp)
        )
        if (!isCompact) {
            Text(
                text = "($roleTitle)",
                color = NeonLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
fun VerifiedBadge(
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Row(
        modifier = modifier
            .border(
                width = 1.dp,
                color = Color(0xFF4DA3FF),
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = Color(0xFF0C2138),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = if (isCompact) 4.dp else 6.dp, vertical = if (isCompact) 1.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Verified,
            contentDescription = "Verified",
            tint = Color(0xFF4DA3FF),
            modifier = Modifier.size(if (isCompact) 11.dp else 13.dp)
        )
        if (!isCompact) {
            Text(
                text = "Doğrulanmış",
                color = Color(0xFF4DA3FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
