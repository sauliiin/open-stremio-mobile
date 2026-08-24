package com.mdblisthub.tv

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdblisthub.tv.core.ui.theme.HubTheme
import com.mdblisthub.tv.navigation.HubNavHost
import com.mdblisthub.tv.update.AppUpdateManager
import com.mdblisthub.tv.update.AppUpdateOverlay
import com.mdblisthub.tv.ui.player.PlaybackCompletionNotifier
import java.util.Locale

/**
 * The single activity.
 *
 * A television app has no window management to speak of and one back stack, so
 * the whole interface is one activity and a Compose graph — which also means
 * the player never has to hand state across an activity boundary.
 */
class MainActivity : ComponentActivity() {

    private lateinit var appUpdateManager: AppUpdateManager
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Every OmniStream palette is dark. The automatic edge-to-edge style
        // follows the device theme instead, which produced black status-bar
        // icons whenever a light-system device opened this dark app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        hideStatusBar()
        PlaybackCompletionNotifier.createChannel(this)
        requestNotificationPermissionOnce()

        val graph = (application as HubApplication).graph
        appUpdateManager = AppUpdateManager(this, GITHUB_REPOSITORY)

        setContent {
            // Read the host configuration before replacing LocalConfiguration
            // with the locale-aware one below. The Activity handles rotation
            // itself, so keying the wrapped context only on language froze its
            // original portrait dimensions forever. Rebuilding it whenever
            // Android publishes a new configuration keeps landscape-specific
            // sizing and line limits in sync after the phone rotates.
            val systemConfiguration = LocalConfiguration.current
            val language by graph.uiPreferences.language
                .collectAsStateWithLifecycle(initialValue = DEFAULT_LANGUAGE)

            val activity = this
            val localeContext = remember(language, systemConfiguration) {
                val locale = Locale.forLanguageTag(language)
                val configuration = Configuration(systemConfiguration).apply {
                    setLocale(locale)
                }
                activity.createConfigurationContext(configuration)
            }

            // `Locale.setDefault` is a process-wide write and belongs nowhere
            // near a composable body: composition can run more than once for a
            // single state change, and can be discarded entirely. An effect
            // keyed on the language runs it exactly once per real change.
            LaunchedEffect(language) {
                Locale.setDefault(Locale.forLanguageTag(language))
            }

            CompositionLocalProvider(
                LocalContext provides localeContext,
                LocalConfiguration provides localeContext.resources.configuration,
                // The locale-wrapped context above is a bare `ContextImpl` —
                // not an `Activity`, and not even a `ContextWrapper`, so
                // nothing downstream can walk back to the Activity through it.
                // That silently broke Google sign-in, which needs a real
                // Activity to launch the credential picker. Publishing the
                // Activity separately is what keeps both facts available:
                // strings come from the locale context, windows from here.
                LocalHostActivity provides activity,
            ) {
                HubTheme {
                    Box {
                        HubNavHost(graph = graph)
                        AppUpdateOverlay(manager = appUpdateManager)
                    }
                }
            }
        }

        appUpdateManager.checkForUpdate()
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) appUpdateManager.onHostResumed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideStatusBar()
    }

    override fun onDestroy() {
        if (::appUpdateManager.isInitialized) appUpdateManager.close()
        super.onDestroy()
    }

    private fun hideStatusBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) return

        val preferences = getSharedPreferences(NOTIFICATION_PREFERENCES, MODE_PRIVATE)
        if (preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)) return
        preferences.edit().putBoolean(NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val DEFAULT_LANGUAGE = "en"
        const val GITHUB_REPOSITORY = "sauliiin/open-stremio-mobile"
        const val NOTIFICATION_PREFERENCES = "playback_notifications"
        const val NOTIFICATION_PERMISSION_REQUESTED = "permission_requested"
    }
}
