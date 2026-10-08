package com.yepgent.usage.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.yepgent.usage.auth.LoginActivity
import com.yepgent.usage.data.Store
import com.yepgent.usage.data.UsageRepository
import com.yepgent.usage.surfaces.Surfaces
import com.yepgent.usage.ui.theme.Mono
import com.yepgent.usage.ui.theme.MonoTheme
import com.yepgent.usage.work.Scheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val login = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) lifecycleScope.launch { UsageRepository.refresh(this@MainActivity, manual = true) }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        UsageRepository.load(this)
        setContent {
            MonoTheme {
                App(onSignIn = { login.launch(Intent(this, LoginActivity::class.java)) })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val fetchedAt = UsageRepository.state.value?.fetchedAt ?: 0
        if (System.currentTimeMillis() - fetchedAt > 60_000) {
            lifecycleScope.launch { UsageRepository.refresh(this@MainActivity, manual = true) }
        }
    }
}

@Composable
private fun App(onSignIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Store(context) }
    val snap by UsageRepository.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("dashboard") }
    var refreshing by remember { mutableStateOf(false) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }

    fun refresh() = scope.launch {
        refreshing = true
        UsageRepository.refresh(context, manual = true)
        refreshing = false
    }

    BackHandler(enabled = screen == "settings") { screen = "dashboard" }

    Box(
        Modifier
            .fillMaxSize()
            .background(Mono.colors.ground)
            .safeDrawingPadding(),
    ) {
        val current = snap ?: return@Box
        when {
            screen == "settings" -> Settings(
                store = store,
                signedIn = current.signedIn,
                onBack = { screen = "dashboard" },
                onChanged = { scope.launch { Surfaces.publish(context, UsageRepository.load(context)) } },
                onIntervalChanged = { Scheduler.schedule(context, it, replace = true) },
                onSimulate = { refresh() },
                onSignIn = onSignIn,
                onSignOut = { scope.launch { UsageRepository.signOut(context) } },
            )
            !current.signedIn -> SignIn(onSignIn)
            else -> Dashboard(
                snap = current,
                store = store,
                now = now,
                refreshing = refreshing,
                onRefresh = { refresh() },
                onSettings = { screen = "settings" },
                onSignIn = onSignIn,
            )
        }
    }
}

@Composable
private fun SignIn(onSignIn: () -> Unit) {
    val c = Mono.colors
    Column(Modifier.fillMaxSize()) {
        Header("usage")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            MonoText("sign in to claude.ai to start tracking your session and weekly limits.", size = 15.sp, lineHeight = 22.sp)
            MonoButton("sign in", onSignIn)
            MonoText(
                "use the email code. google sign-in is blocked inside apps.",
                size = 12.sp,
                color = c.ink2,
                lineHeight = 18.sp,
            )
        }
    }
}
