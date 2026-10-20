package com.translive.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.translive.app.ui.screens.auth.AuthScreen
import com.translive.app.ui.screens.call.CallScreen
import com.translive.app.ui.screens.call.IncomingCallScreen
import com.translive.app.ui.screens.chat.ChatListScreen
import com.translive.app.ui.screens.chat.ChatScreen
import com.translive.app.ui.screens.friends.AddFriendScreen
import com.translive.app.ui.screens.friends.FriendsScreen
import com.translive.app.ui.screens.home.HomeScreen
import com.translive.app.ui.screens.settings.SettingsScreen

@Composable
fun AppNavGraph(navController: NavHostController, startDestination: String) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable("auth") { AuthScreen(navController) }
        composable("home") { HomeScreen(navController) }
        composable("chatList") { ChatListScreen(navController) }
        composable(
            "chat/{friendId}",
            arguments = listOf(navArgument("friendId") { type = NavType.StringType })
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: return@composable
            ChatScreen(navController, friendId)
        }
        composable("friends") { FriendsScreen(navController) }
        composable("calls") { FriendsScreen(navController) }
        composable("addFriend") { AddFriendScreen(navController) }
        composable(
            "call/{friendId}",
            arguments = listOf(navArgument("friendId") { type = NavType.StringType })
        ) { backStackEntry ->
            val friendId = backStackEntry.arguments?.getString("friendId") ?: return@composable
            CallScreen(navController, friendId)
        }
        composable(
            "incomingCall/{callId}/{callerId}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: return@composable
            val callerId = backStackEntry.arguments?.getString("callerId") ?: return@composable
            IncomingCallScreen(navController, callId, callerId)
        }
        composable("settings") { SettingsScreen(navController) }
    }
}
