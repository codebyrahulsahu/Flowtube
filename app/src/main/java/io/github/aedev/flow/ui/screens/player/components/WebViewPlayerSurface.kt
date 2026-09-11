package io.github.aedev.flow.ui.screens.player.components

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aedev.flow.data.model.Video

/**
 * Brave-style WebView YouTube player.
 *
 * Uses Android System WebView with a Chrome desktop UA and Widevine DRM support to play
 * YouTube videos directly in the embedded player. This mirrors how Brave browser loads
 * m.youtube.com / youtube.com embed and is used as a fallback when ExoPlayer shows a
 * black screen (typically due to DRM-protected streams, codec quirks, or device-specific
 * decoder issues).
 *
 * Notes:
 * - Playback uses the YouTube HTML5 player, so DRM/Widevine is handled by the system WebView.
 * - Background playback/PiP/SponsorBlock/captions from Flow's native controls are NOT available
 *   inside this WebView; it is a "bypass black screen" fallback only.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayerSurface(
    video: Video,
    modifier: Modifier = Modifier,
    autoplay: Boolean = true,
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = false
                        allowContentAccess = true
                        mediaPlaybackRequiresUserGesture = false
                        loadsImagesAutomatically = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        // Allow WebView to access the device's Widevine DRM module so
                        // encrypted streams don't show a black screen.
                        @Suppress("DEPRECATION")
                        setPluginState(WebSettings.PluginState.ON)

                        // Brave/Chrome-like UA so YouTube serves its standard HTML5 player
                        // (with Widevine L3 DRM support through the system WebView).
                        // Using a Chrome Android UA similar to what Brave ships.
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/131.0.0.0 Mobile Safari/537.36"
                    }

                    // Hardware acceleration for DRM video decoding.
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)

                    webViewClient =
                        object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                // Keep all navigation inside this WebView.
                                return false
                            }
                        }

                    webChromeClient = WebChromeClient()

                    webView = this

                    // Load YouTube's embed player for the given video.
                    // autoplay=1 -> start immediately; controls=1 -> show YouTube controls;
                    // rel=0 -> don't show unrelated videos at the end; playsinline=1 -> don't force fullscreen.
                    val url =
                        "https://www.youtube.com/embed/${video.id}" +
                            "?autoplay=${if (autoplay) 1 else 0}" +
                            "&controls=1" +
                            "&rel=0" +
                            "&modestbranding=1" +
                            "&playsinline=1" +
                            "&fs=1"
                    loadUrl(url)
                }
            },
            update = { view ->
                val currentUrl = view.url
                val expectedUrlPart = "/embed/${video.id}"
                if (currentUrl == null || !currentUrl.contains(expectedUrlPart)) {
                    val url =
                        "https://www.youtube.com/embed/${video.id}" +
                            "?autoplay=${if (autoplay) 1 else 0}" +
                            "&controls=1" +
                            "&rel=0" +
                            "&modestbranding=1" +
                            "&playsinline=1" +
                            "&fs=1"
                    view.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    DisposableEffect(webView) {
        onDispose {
            webView?.apply {
                stopLoading()
                onPause()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}
