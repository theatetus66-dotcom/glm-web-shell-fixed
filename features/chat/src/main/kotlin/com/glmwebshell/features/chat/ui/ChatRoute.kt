package com.glmwebshell.features.chat.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import com.glmwebshell.pageengine.w1.WebViewEngine.WebFileChooserHost
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.glmwebshell.core.common.AppResult
import com.glmwebshell.core.common.AllowedOrigins
import com.glmwebshell.core.ui.components.GlmOfflineState
import com.glmwebshell.features.chat.viewmodel.ChatViewModel
import com.glmwebshell.pageengine.PageEngineHost
import com.glmwebshell.pageengine.proxy.ProxyConfig
import com.glmwebshell.pageengine.w1.WebViewEngine

/**
 * Full-screen WebView chat host (TZ §6.1).
 *
 * The engine itself is created by Hilt and injected into the VM. The
 * Composable is responsible for handing the engine a host (Context +
 * container ViewGroup) on which it can mount the WebView, plus an
 * ActivityResultLauncher for the file chooser (TZ §6.1.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(
    onOpenHistory: () -> Unit,
    onOpenPrompts: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(enabled = true) {
        if (viewModel.engine.canGoBack()) viewModel.goBack()
    }
    val context = LocalContext.current
    val engine = remember { viewModel.engine as WebViewEngine }

    // File-chooser launcher (TZ §6.1.3). Handed to the engine via its host.
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        val uris: Array<android.net.Uri>? = data?.let { intent ->
            intent.clipData?.let { cd ->
                Array(cd.itemCount) { cd.getItemAt(it).uri }
            } ?: intent.data?.let { arrayOf(it) }
        }
        engine.deliverFileChooserResult(uris?.takeIf { it.isNotEmpty() })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GLM Web Shell") },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::reload) { Icon(Icons.Filled.Refresh, contentDescription = "Reload") }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Filled.History, contentDescription = "History") }
                    IconButton(onClick = onOpenPrompts) { Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Prompts") }
                    IconButton(onClick = onOpenDiagnostics) { Icon(Icons.Filled.BugReport, contentDescription = "Diagnostics") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading && state.progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // The actual WebView surface.
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        // The engine needs a parent container it can add the WebView to.
                        val hostContainer = FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        }
                        engine.attach(object : PageEngineHost, WebFileChooserHost {
                            override val context = ctx
                            override val container = hostContainer
                            override val allowDebug = false
                            override val pullToRefreshEnabled = true
                            override val proxyConfig = AppResult.ok(ProxyConfig.NONE)
                            override val fileChooserLauncher = fileLauncher
                        })
                        hostContainer
                    },
                    onRelease = { _ ->
                        engine.detach()
                    },
                )
            }

            if (state.offline) {
                GlmOfflineState(
                    message = "The site is unavailable. Check your connection or proxy settings.",
                    onRetry = viewModel::reload,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.currentUrl.isBlank()) viewModel.loadInitialPage()
    }
    LaunchedEffect(state.pendingInsert) {
        val text = state.pendingInsert ?: return@LaunchedEffect
        // Best-effort: ask the page to fill the primary textbox.
        engine.postMessage(
            com.glmwebshell.pageengine.BridgeMessage.Outgoing.RequestFill(
                locator = "textarea, [contenteditable=true], [role=textbox]",
                text = text,
            )
        )
        viewModel.onInsertHandled()
    }
}
