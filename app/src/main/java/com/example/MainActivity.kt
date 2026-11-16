package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.attemptAutoSignIn
import com.example.core.authStateFlow
import com.example.core.configureAppCheck
import com.example.core.signOutUser
import com.example.data.repository.StoryRepository
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.ChatGameScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.StoryQuestTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.ChatGameViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class ChatRoute(val storyId: String, val playerName: String)

@Serializable
data object AdminRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            StoryQuestTheme {
                StoryQuestApp()
            }
        }
    }
}

@Composable
private fun StoryQuestApp() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = {},
                onUnauthenticated = {},
            )
        }
    }

    val user = currentUser
    if (user == null) {
        SignInScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedStoryNavHost(
                user = user,
                onSignOut = {
                    signOutUser(
                        credentialManager = credentialManager,
                        scope = scope,
                    )
                },
            )
        }
    }
}

@Composable
private fun AuthenticatedStoryNavHost(
    user: FirebaseUser,
    onSignOut: () -> Unit,
) {
    val navController = rememberNavController()
    val isAdminUser = user.email.equals(StoryRepository.ADMIN_EMAIL, ignoreCase = true)

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
    ) {
        composable<HomeRoute> {
            val homeViewModel: HomeViewModel = viewModel(
                key = "home_${user.uid}",
                factory = HomeViewModel.provideFactory(
                    currentUserId = user.uid,
                    userEmail = user.email,
                ),
            )
            HomeScreen(
                viewModel = homeViewModel,
                defaultDisplayName = user.displayName,
                onOpenStory = { storyId, playerName ->
                    navController.navigate(ChatRoute(storyId = storyId, playerName = playerName))
                },
                onOpenAdmin = {
                    navController.navigate(AdminRoute)
                },
                onSignOut = onSignOut,
            )
        }

        composable<ChatRoute> { backStackEntry ->
            val route: ChatRoute = backStackEntry.toRoute()
            val chatViewModel: ChatGameViewModel = viewModel(
                key = "chat_${user.uid}_${route.storyId}",
                factory = ChatGameViewModel.provideFactory(
                    currentUserId = user.uid,
                    storyId = route.storyId,
                    fallbackPlayerName = route.playerName,
                ),
            )
            ChatGameScreen(
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable<AdminRoute> {
            val adminViewModel: AdminViewModel = viewModel(
                key = "admin_${user.uid}",
                factory = AdminViewModel.provideFactory(),
            )
            AdminScreen(
                viewModel = adminViewModel,
                isAdminUser = isAdminUser,
                currentUserEmail = user.email,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
