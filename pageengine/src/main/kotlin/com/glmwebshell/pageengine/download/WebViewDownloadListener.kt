package com.glmwebshell.pageengine.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.URLUtil
import androidx.core.content.getSystemService
import com.glmwebshell.core.common.Logger
import com.glmwebshell.pageengine.BridgeMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class WebViewDownloadListener(
    private val ctx: Context,
) : DownloadListener {

    private val _blobRequests = MutableSharedFlow<BridgeMessage.Incoming.BlobUrl>(extraBufferCapacity = 8)
    val blobRequests: SharedFlow<BridgeMessage.Incoming.BlobUrl> = _blobRequests.asSharedFlow()

    override fun onDownloadStart(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimetype: String?,
        contentLength: Long,
    ) {
        if (url.startsWith("blob:")) {
            _blobRequests.tryEmit(
                BridgeMessage.Incoming.BlobUrl(
                    url = url,
                    mime = mimetype ?: "application/octet-stream",
                    suggestedName = URLUtil.guessFileName(url, contentDisposition, mimetype),
                )
            )
            return
        }
        if (url.startsWith("data:") || !url.startsWith("http")) {
            Logger.w(TAG, "Unsupported download URL scheme: ${url.take(32)}")
            return
        }
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype ?: "*/*")
                val name = URLUtil.guessFileName(url, contentDisposition, mimetype)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "GLMWebShell/$name")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setTitle(name)
                if (!userAgent.isNullOrBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
                if (!cookies.isNullOrBlank()) {
                    addRequestHeader("Cookie", cookies)
                }
            }
            ctx.getSystemService<DownloadManager>()?.enqueue(request)
                ?: Logger.w(TAG, "DownloadManager unavailable")
        } catch (t: SecurityException) {
            Logger.w(TAG, "Download rejected: ${t.message}", t)
        } catch (t: IllegalArgumentException) {
            Logger.w(TAG, "Download request invalid: ${t.message}", t)
        } catch (t: Throwable) {
            Logger.w(TAG, "Download failed: ${t.message}", t)
        }
    }

    companion object { private const val TAG = "Download" }
}
