package com.kokouliss.closetanything

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kokouliss.closetanything.core.data.isBackendConfigured
import com.kokouliss.closetanything.core.domain.model.InviteCode
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme
import dagger.hilt.android.AndroidEntryPoint

// The link in an intent comes from outside the app. It is only ever parsed into an invite code,
// which the security rules check.
private fun Intent.inviteCode(): String? = if (action == Intent.ACTION_VIEW) dataString?.let(InviteCode::fromLink) else null

// AppCompatActivity, because it is what applies the language chosen in Settings on Android 9 to 12.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** The code of a tapped invite link, until the households screen has joined with it. */
    private var inviteCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Not after a rotation: by then the join has already started.
        if (savedInstanceState == null) inviteCode = intent.inviteCode()
        // Checked before any screen asks Hilt for Firebase, which would crash without a configuration.
        val isConfigured = isBackendConfigured(this)
        setContent {
            ClosetAnythingTheme {
                if (isConfigured) {
                    ClosetAnythingApp(inviteCode = inviteCode, onInviteHandled = { inviteCode = null })
                } else {
                    SetupNeededScreen()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        inviteCode = intent.inviteCode()
    }
}
