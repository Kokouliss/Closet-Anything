package com.kokouliss.closetanything.feature.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.JoinResult
import com.kokouliss.closetanything.core.domain.repository.AuthRepository
import com.kokouliss.closetanything.core.domain.repository.HouseholdRepository
import com.kokouliss.closetanything.core.ui.component.Undoable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface HouseholdsUiState {
    data object Loading : HouseholdsUiState

    /** The households could not be read, for example because the security rules are not deployed yet. */
    data object Error : HouseholdsUiState

    data class Ready(
        val userName: String,
        val households: List<Household>,
        val invitations: List<Household>,
        val join: JoinState,
    ) : HouseholdsUiState
}

/** Where joining a household with a code stands. */
internal sealed interface JoinState {
    data object Idle : JoinState
    data object Joining : JoinState

    /** The screen shows [result], then calls [HouseholdsViewModel.joinShown]. */
    data class Done(val result: JoinResult) : JoinState
}

@HiltViewModel
internal class HouseholdsViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val join = MutableStateFlow<JoinState>(JoinState.Idle)

    /** Invitations taken off the screen at once and declined only once their Undo snackbar is gone. */
    val declines = Undoable<Household>(viewModelScope) { householdRepository.declineInvitation(it.id) }

    private val retries = MutableStateFlow(0)

    val uiState: StateFlow<HouseholdsUiState> = retries
        .flatMapLatest {
            combine(
                authRepository.currentUser,
                householdRepository.observeHouseholds(),
                householdRepository.observeInvitations(),
                join,
                declines.pending,
            ) { user, households, invitations, joining, declined ->
                HouseholdsUiState.Ready(
                    userName = user?.name.orEmpty(),
                    households = households,
                    invitations = invitations.filter { it.id != declined?.removal?.id },
                    join = joining,
                ) as HouseholdsUiState
            }.catch { emit(HouseholdsUiState.Error) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdsUiState.Loading)

    /** A failed stream does not restart by itself, so the error screen offers this. */
    fun retry() {
        retries.update { it + 1 }
    }

    fun createHousehold(name: String) {
        viewModelScope.launch { householdRepository.createHousehold(name) }
    }

    fun joinWithCode(code: String) {
        if (join.value == JoinState.Joining) return
        join.value = JoinState.Joining
        viewModelScope.launch { join.value = JoinState.Done(householdRepository.joinWithCode(code)) }
    }

    fun joinShown() {
        join.update { if (it is JoinState.Done) JoinState.Idle else it }
    }

    fun acceptInvitation(householdId: String) {
        viewModelScope.launch { householdRepository.acceptInvitation(householdId) }
    }

    fun declineInvitation(householdId: String) {
        val ready = uiState.value as? HouseholdsUiState.Ready ?: return
        ready.invitations.find { it.id == householdId }?.let(declines::remove)
    }

    override fun onCleared() = declines.flush()
}
