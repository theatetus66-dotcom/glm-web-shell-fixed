package com.glmwebshell.app.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.glmwebshell.features.chat.ui.ChatRoute
import com.glmwebshell.features.chat.viewmodel.ChatViewModel
import com.glmwebshell.features.diagnostics.ui.DiagnosticsRoute
import com.glmwebshell.features.history.ui.HistoryRoute
import com.glmwebshell.features.prompts.ui.PromptsRoute
import com.glmwebshell.features.settings.ui.SettingsRoute

object Routes {
    const val CHAT = "chat"
    const val HISTORY = "history"
    const val PROMPTS = "prompts"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
}

@Composable
fun GlmNavHost(sharedText: String? = null) {
    val nav = rememberNavController()
    // Activity-scoped ChatViewModel so Prompts can insert into the chat surface.
    val activity = LocalContext.current as ComponentActivity
    val chatViewModel: ChatViewModel = hiltViewModel(activity)

    LaunchedEffect(sharedText) {
        chatViewModel.applySharedText(sharedText)
    }

    NavHost(navController = nav, startDestination = Routes.CHAT) {
        composable(Routes.CHAT) {
            ChatRoute(
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenPrompts = { nav.navigate(Routes.PROMPTS) },
                onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                viewModel = chatViewModel,
            )
        }
        composable(Routes.HISTORY) {
            HistoryRoute(
                onBack = { nav.popBackStack() },
                onOpenChat = { chatId ->
                    nav.popBackStack()
                    chatViewModel.openChatById(chatId)
                },
            )
        }
        composable(Routes.PROMPTS) {
            PromptsRoute(
                onBack = { nav.popBackStack() },
                onInsert = { text ->
                    chatViewModel.onPromptInsert(text)
                    nav.popBackStack()
                },
            )
        }
        composable(Routes.DIAGNOSTICS) {
            DiagnosticsRoute(onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsRoute(onBack = { nav.popBackStack() })
        }
    }
}
