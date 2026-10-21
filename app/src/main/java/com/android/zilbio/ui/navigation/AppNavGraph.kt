package com.android.zilbio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.android.zilbio.ui.screens.auth.AuthScreen
import com.android.zilbio.ui.screens.call.CallScreen
import com.android.zilbio.ui.screens.call.CallsScreen
import com.android.zilbio.ui.screens.call.IncomingCallScreen
import com.android.zilbio.ui.screens.chat.ChatListScreen
import com.android.zilbio.ui.screens.chat.ChatScreen
import com.android.zilbio.ui.screens.chat.ContactProfileScreen
import com.android.zilbio.ui.screens.friends.AddFriendScreen
import com.android.zilbio.ui.screens.friends.FriendsScreen
import com.android.zilbio.ui.screens.home.HomeScreen
import com.android.zilbio.ui.screens.settings.PersonalProfileScreen
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.android.zilbio.ui.screens.settings.PrivacyScreen
import com.android.zilbio.ui.screens.settings.SettingsScreen
import com.android.zilbio.ui.screens.about.AboutScreen
import com.android.zilbio.ui.screens.settings.StorageManagerScreen
import com.android.zilbio.ui.screens.admin.AdminPanelScreen

@Composable
fun AppNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth / 4 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth / 4 },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        }
    ) {
        composable("auth") { AuthScreen(navController) }
        composable("home") { ChatListScreen(navController) }
        composable("chatList") { ChatListScreen(navController) }
        composable(
            "chat/{friendId}",
            arguments = listOf(navArgument("friendId") { type = NavType.StringType })
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: return@composable
            ChatScreen(navController, friendId)
        }
        composable("calls") { CallsScreen(navController) }
        composable("friends") { FriendsScreen(navController) }
        composable("addFriend") { AddFriendScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("privacy") { PrivacyScreen(navController) }
        composable("storage_manager") { StorageManagerScreen(navController) }
        composable("admin_panel") { AdminPanelScreen(navController) }
        composable("about") { AboutScreen(navController) }
        composable("personal_profile") { PersonalProfileScreen(onNavigateBack = { navController.popBackStack() }) }
        composable(
            "contact_profile/{friendId}/{chatId}",
            arguments = listOf(
                navArgument("friendId") { type = NavType.StringType },
                navArgument("chatId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: return@composable
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ContactProfileScreen(navController = navController, friendId = friendId, chatId = chatId)
        }
        composable(
            "call/{friendId}?callId={callId}&isIncoming={isIncoming}&isVideo={isVideo}",
            arguments = listOf(
                navArgument("friendId") { type = NavType.StringType },
                navArgument("callId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("isIncoming") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("isVideo") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: return@composable
            val callId = backStackEntry.arguments?.getString("callId")
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            CallScreen(
                navController = navController,
                friendId = friendId,
                callId = callId,
                isIncoming = isIncoming,
                isVideo = isVideo
            )
        }
        composable(
            "incomingCall/{callId}/{callerId}?isVideo={isVideo}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType },
                navArgument("isVideo") {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            val callerId = backStackEntry.arguments?.getString("callerId") ?: return@composable
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            IncomingCallScreen(
                navController = navController,
                callId = callId,
                callerId = callerId,
                isVideo = isVideo
            )
        }
    }
}
