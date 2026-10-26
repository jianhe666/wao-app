package app.zhizhang

import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.zhizhang.ui.ZhizhangRoot
import app.zhizhang.ui.components.AppLaunchScreen
import app.zhizhang.ui.theme.ZhizhangTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingSharedText = MutableStateFlow<String?>(null)
    private val pendingSharedImage = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        consumeShareIntent(intent)
        setContent {
            val sharedText by pendingSharedText.collectAsState()
            val sharedImage by pendingSharedImage.collectAsState()
            val isRobolectric = Build.FINGERPRINT.contains("robolectric", ignoreCase = true)
            var showLaunch by rememberSaveable {
                mutableStateOf(savedInstanceState == null && !isRobolectric)
            }
            LaunchedEffect(showLaunch) {
                if (showLaunch) {
                    delay(900)
                    showLaunch = false
                }
            }
            ZhizhangTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZhizhangRoot(
                        sharedText = sharedText,
                        onSharedTextConsumed = { pendingSharedText.value = null },
                        sharedImage = sharedImage,
                        onSharedImageConsumed = { pendingSharedImage.value = null },
                    )
                    AnimatedVisibility(
                        visible = showLaunch,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(320)),
                    ) {
                        AppLaunchScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val image = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (image != null) {
            pendingSharedImage.value = image
            intent.removeExtra(Intent.EXTRA_STREAM)
            intent.clipData = null
            intent.action = null
            return
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        pendingSharedText.value = text
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.action = null
    }
}
