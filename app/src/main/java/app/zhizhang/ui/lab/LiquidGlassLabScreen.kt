package app.zhizhang.ui.lab

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Liquid Glass 接入试验：WebView 内运行 Three.js WebGL2 原型。
 * 这是独立实验室页面，不替换主界面的 Compose/Haze 玻璃。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LiquidGlassLabScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pageReady by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf(false) }
    val webView = remember {
        @Suppress("DEPRECATION")
        WebView(context).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.allowFileAccessFromFileURLs = true
            settings.allowUniversalAccessFromFileURLs = true
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?,
                ): Boolean = true

                override fun onPageFinished(view: WebView?, url: String?) {
                    // WebView sometimes reports the first layout as 0×0; ask the
                    // page to re-fit after it has a real viewport.
                    view?.postDelayed({
                        view.evaluateJavascript("window.dispatchEvent(new Event('resize'));", null)
                    }, 250)
                    view?.postDelayed({
                        view.evaluateJavascript(
                            "window.__liquidGlassReady === true",
                        ) { result ->
                            if (result == "true") {
                                pageReady = true
                            } else {
                                pageError = true
                            }
                        }
                    }, 1_200)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) pageError = true
                }
            }
            loadUrl("file:///android_asset/liquid-glass/index.html")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black),
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )

        if (!pageReady && !pageError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor(0xFF04070D)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Liquid Glass 正在加载…",
                    color = ComposeColor(0xCCFFFFFF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (pageError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor(0xFF04070D)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("预览加载失败", color = ComposeColor.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "请确认系统 WebView 已启用并支持 WebGL2",
                        color = ComposeColor(0xAAFFFFFF),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { pageError = false; pageReady = false; webView.reload() }) {
                        Text("重新加载", color = ComposeColor.White)
                    }
                }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, top = 8.dp)
                .size(44.dp)
                .background(ComposeColor(0x66000000), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = ComposeColor.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // The WebView is the visual lab; keep this label out of the way.
        // Content itself is inside the WebView's DOM layer.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        ) {
            androidx.compose.material3.Text(
                text = "Liquid Glass Lab · WebGL2",
                style = MaterialTheme.typography.labelSmall,
                color = ComposeColor(0x88FFFFFF),
            )
        }
    }
}
