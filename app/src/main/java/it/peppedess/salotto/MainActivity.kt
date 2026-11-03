package it.peppedess.salotto

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.peppedess.salotto.data.Settings
import it.peppedess.salotto.kiosk.Kiosk
import it.peppedess.salotto.ui.HomeViewModel
import it.peppedess.salotto.ui.NavSoundProvider
import it.peppedess.salotto.ui.components.AdaptiveScale
import it.peppedess.salotto.ui.SalottoApp
import it.peppedess.salotto.ui.theme.SalottoTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Come un launcher: "Indietro" sulla Home riporta a "Per te" e non chiude nulla.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vm.goHome()
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(vm.settings, vm.apps, vm.deviceOwner, vm.profile) { s, apps, owner, profile ->
                    // Con un profilo bambini il blocco consente solo le sue app.
                    val packages = if (profile.kids) profile.allowedApps else apps.map { it.packageName }.distinct()
                    val effective = if (profile.kids && owner) s.copy(kiosk = true) else s
                    Triple(effective, packages, owner)
                }
                    .distinctUntilChanged()
                    .collect { (s, packages, _) -> applySettings(s, packages) }
            }
        }

        setContent {
            SalottoTheme {
                AdaptiveScale {
                    NavSoundProvider(vm) {
                        SalottoApp(vm)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.goHome()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshStatus()
        vm.setForeground(true)
        hideSystemBars()
    }

    override fun onPause() {
        vm.setForeground(false)
        super.onPause()
    }

    /** Il primo tocco sul salvaschermo lo chiude e non arriva all'interfaccia sotto. */
    private var swallowGesture = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            swallowGesture = vm.userActivity()
        }
        if (swallowGesture) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                swallowGesture = false
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private var swallowKey = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode in NAV_KEYS) {
            vm.navKeyPressed()
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (vm.userActivity()) {
                swallowKey = true
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP && swallowKey) {
            swallowKey = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun applySettings(settings: Settings, packages: List<String>) {
        if (settings.keepScreenOn || settings.kiosk) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        requestedOrientation = if (settings.allowPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        Kiosk.apply(this, settings.kiosk, packages)
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        val NAV_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_TAB,
        )
    }
}
