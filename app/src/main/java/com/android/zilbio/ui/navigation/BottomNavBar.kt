package com.android.zilbio.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.android.zilbio.ui.localization.AppStrings
import com.android.zilbio.ui.localization.LocalAppStrings
import com.android.zilbio.ui.theme.AccentWarmPeach
import com.android.zilbio.ui.theme.BackgroundDarkEspressoAlt
import com.android.zilbio.ui.theme.BorderDarkSubtle
import com.android.zilbio.ui.theme.TextMutedWarmGrey

sealed class BottomNavItem(
    val route: String,
    val getTitle: (AppStrings) -> String,
    val icon: ImageVector
) {
    data object Chats : BottomNavItem("chatList", { it.navChats }, Icons.AutoMirrored.Outlined.Chat)
    data object Calls : BottomNavItem("calls", { it.navCalls }, Icons.Outlined.Phone)
    data object Friends : BottomNavItem("friends", { it.navFriends }, Icons.Outlined.People)
    data object Settings : BottomNavItem("settings", { it.navSettings }, Icons.Outlined.Settings)
}

val bottomNavItems = listOf(
    BottomNavItem.Chats,
    BottomNavItem.Calls,
    BottomNavItem.Friends,
    BottomNavItem.Settings
)

@Composable
fun BottomNavBar(navController: NavController) {
    val strings = LocalAppStrings.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val strokeWidthPx = 1.dp.toPx()
                drawLine(
                    color = BorderDarkSubtle,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = strokeWidthPx
                )
            },
        containerColor = BackgroundDarkEspressoAlt,
        tonalElevation = 0.dp
    ) {
        bottomNavItems.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            val title = item.getTitle(strings)

            NavigationBarItem(
                selected = isSelected,
                onClick = {
                    if (currentDestination?.route != item.route) {
                        navController.navigate(item.route) {
                            popUpTo("chatList") {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = title
                    )
                },
                label = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentWarmPeach,
                    selectedTextColor = AccentWarmPeach,
                    unselectedIconColor = TextMutedWarmGrey,
                    unselectedTextColor = TextMutedWarmGrey,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
