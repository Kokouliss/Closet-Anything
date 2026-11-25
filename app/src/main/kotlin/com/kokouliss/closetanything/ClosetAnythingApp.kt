package com.kokouliss.closetanything

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.kokouliss.closetanything.core.domain.repository.AuthRepository
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.EmptyState
import com.kokouliss.closetanything.core.ui.component.LoadingBox
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme
import com.kokouliss.closetanything.core.ui.theme.pageChange
import com.kokouliss.closetanything.core.ui.theme.rememberReducedMotion
import com.kokouliss.closetanything.feature.auth.FirstNamePrompt
import com.kokouliss.closetanything.feature.auth.ProfileKey
import com.kokouliss.closetanything.feature.auth.SignInRoute
import com.kokouliss.closetanything.feature.auth.profileEntries
import com.kokouliss.closetanything.feature.household.HouseholdsKey
import com.kokouliss.closetanything.feature.household.householdEntries
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal enum class AuthState { Loading, SignedOut, SignedIn }

@HiltViewModel
internal class AppViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    val authState: StateFlow<AuthState> = authRepository.currentUser
        .map { user -> if (user == null) AuthState.SignedOut else AuthState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    /** Each time the app comes back, for example from the mail app after tapping the confirmation link. */
    fun onAppStart() {
        viewModelScope.launch { authRepository.refreshEmailVerification() }
    }
}

@Composable
internal fun ClosetAnythingApp(
    /** The code of an invite link that opened the app, handled once the person is signed in. */
    inviteCode: String?,
    onInviteHandled: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    LifecycleStartEffect(Unit) {
        viewModel.onAppStart()
        onStopOrDispose {}
    }
    when (authState) {
        AuthState.Loading -> Surface { LoadingBox() }
        AuthState.SignedOut -> SignInRoute()
        AuthState.SignedIn -> {
            SignedInNavigation(inviteCode, onInviteHandled)
            FirstNamePrompt()
        }
    }
}

@Composable
private fun SignedInNavigation(inviteCode: String?, onInviteHandled: () -> Unit) {
    val backStack = rememberNavBackStack(HouseholdsKey)
    val goBack: () -> Unit = { backStack.removeLastOrNull() }
    // A message from a screen that closed itself, shown once by the screen now on top.
    var notice by rememberSaveable { mutableStateOf<String?>(null) }

    // Deeper comes in from the right, back from the left, also under a back swipe.
    val slide = !rememberReducedMotion()
    NavDisplay(
        backStack = backStack,
        onBack = goBack,
        transitionSpec = { pageChange(forward = true, slide) },
        popTransitionSpec = { pageChange(forward = false, slide) },
        predictivePopTransitionSpec = { pageChange(forward = false, slide) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            householdEntries(
                onOpen = { backStack.add(it) },
                onOpenProfile = { backStack.add(ProfileKey) },
                onBack = goBack,
                onGone = { message ->
                    notice = message
                    goBack()
                },
                notice = notice,
                onNoticeShown = { notice = null },
                inviteCode = inviteCode,
                onInviteHandled = onInviteHandled,
            )
            profileEntries(onBack = goBack)
        },
    )
}

/** Shown by builds made without `app/google-services.json`, instead of crashing on the first Firebase call. */
@Composable
internal fun SetupNeededScreen() {
    Surface {
        EmptyState(
            icon = AppIcons.Refresh,
            title = stringResource(R.string.setup_needed_title),
            body = stringResource(R.string.setup_needed_body),
        )
    }
}

@PreviewLightDark
@Composable
private fun SetupNeededScreenPreview() {
    ClosetAnythingTheme { SetupNeededScreen() }
}
