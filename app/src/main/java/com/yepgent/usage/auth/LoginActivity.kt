package com.yepgent.usage.auth

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yepgent.usage.data.Backoff
import com.yepgent.usage.data.ClaudeWebClient
import com.yepgent.usage.data.Store
import com.yepgent.usage.data.UsageRepository
import com.yepgent.usage.ui.Header
import com.yepgent.usage.ui.MonoButton
import com.yepgent.usage.ui.MonoIcons
import com.yepgent.usage.ui.MonoText
import com.yepgent.usage.ui.SquareButton
import com.yepgent.usage.ui.theme.Mono
import com.yepgent.usage.ui.theme.MonoTheme
import kotlinx.coroutines.delay

/**
 * claude.ai's own login page in a WebView. Done once the WebView's cookie jar holds a
 * session; the session stays in that jar, which the usage client reads per request.
 */
class LoginActivity : ComponentActivity() {
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MonoTheme { LoginScreen(onDone = ::done, onClose = ::finish) } }
    }

    private fun done() {
        if (finished) return
        finished = true
        CookieManager.getInstance().flush()
        Store(this).apply {
            orgId = null
            backoff = Backoff()
            authExpired = false
            lastError = null
        }
        setResult(RESULT_OK)
        finish()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginScreen(onDone: () -> Unit, onClose: () -> Unit) {
        val c = Mono.colors
        var hint by remember { mutableStateOf("use the email code. google sign-in is blocked inside apps.") }
        val web = remember {
            WebView(this).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if (UsageRepository.signedIn()) onDone()
                    }
                }
                // An expired session cookie would read as signed in, so start from an empty jar.
                CookieManager.getInstance().removeAllCookies { loadUrl("${ClaudeWebClient.ORIGIN}/login") }
            }
        }

        // claude.ai finishes login with client-side navigation, which fires no page callback.
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                if (UsageRepository.signedIn()) onDone()
            }
        }

        BackHandler { if (web.canGoBack()) web.goBack() else onClose() }

        Column(
            Modifier
                .fillMaxSize()
                .background(c.ground)
                .safeDrawingPadding(),
        ) {
            Header("sign in", leading = { SquareButton(MonoIcons.Back, "Close", onClose) })
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonoText(hint, size = 11.sp, color = c.ink2, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                MonoButton("paste link", filled = false, onClick = {
                    val link = getSystemService(ClipboardManager::class.java).primaryClip
                        ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@LoginActivity)?.toString()?.trim()
                    if (link != null && link.startsWith("${ClaudeWebClient.ORIGIN}/")) web.loadUrl(link)
                    else hint = "copy the sign-in link from the claude.ai email first."
                })
            }
            AndroidView(factory = { web }, modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }
}
