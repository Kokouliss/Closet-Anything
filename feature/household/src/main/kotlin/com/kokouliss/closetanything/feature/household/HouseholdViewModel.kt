package com.kokouliss.closetanything.feature.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.normalizeEmail
import com.kokouliss.closetanything.core.domain.repository.AuthRepository
import com.kokouliss.closetanything.core.domain.repository.HouseholdRepository
import com.kokouliss.closetanything.core.domain.repository.PlaceRepository
import com.kokouliss.closetanything.core.domain.repository.ThingRepository
import com.kokouliss.closetanything.core.ui.component.Undoable
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface HouseholdUiState {
    data object Loading : HouseholdUiState

    /** The household was deleted, or this user left or was removed. The screen closes itself. */
    data object Gone : HouseholdUiState

    data class Ready(
        val household: Household,
        val rooms: List<Room>,
        val spaces: List<Space>,
        /** How many things each space holds, by space id. Boxes and their contents all count. */
        val thingCounts: Map<String, Int>,
        val currentUserId: String,
    ) : HouseholdUiState {
        val isOwner: Boolean get() = household.ownerId == currentUserId
    }
}

@HiltViewModel(assistedFactory = HouseholdViewModel.Factory::class)
internal class HouseholdViewModel @AssistedInject constructor(
    @Assisted private val key: HouseholdKey,
    private val householdRepository: HouseholdRepository,
    private val placeRepository: PlaceRepository,
    thingRepository: ThingRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val householdId = key.householdId

    /** Email invites taken off the list at once and cancelled only once their Undo snackbar is gone. */
    val cancelledInvites = Undoable<String>(viewModelScope) { householdRepository.cancelEmailInvite(householdId, it) }

    // Losing access ends these streams with an error; the household stream reports that as "gone".
    val uiState: StateFlow<HouseholdUiState> = combine(
        householdRepository.observeHousehold(householdId),
        placeRepository.observeRooms(householdId).catch { emit(emptyList()) },
        placeRepository.observeSpaces(householdId).catch { emit(emptyList()) },
        thingRepository.observeThings(householdId).catch { emit(emptyList()) }.map { things -> things.groupingBy { it.spaceId }.eachCount() },
        combine(authRepository.currentUser, cancelledInvites.pending) { user, cancelled -> user to cancelled },
    ) { household, rooms, spaces, counts, (user, cancelled) ->
        if (household == null || user == null) {
            HouseholdUiState.Gone
        } else {
            val invited = household.invitedEmails.filter { it != cancelled?.removal }
            HouseholdUiState.Ready(household.copy(invitedEmails = invited), rooms, spaces, counts, user.id)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HouseholdUiState.Loading)

    private val household: Household? get() = (uiState.value as? HouseholdUiState.Ready)?.household

    fun createRoom(name: String) {
        viewModelScope.launch { placeRepository.createRoom(householdId, name) }
    }

    fun renameRoom(roomId: String, name: String) {
        viewModelScope.launch { placeRepository.renameRoom(householdId, roomId, name) }
    }

    fun deleteRoom(roomId: String) {
        viewModelScope.launch { placeRepository.deleteRoom(householdId, roomId) }
    }

    fun createSpace(roomId: String, name: String) {
        viewModelScope.launch { placeRepository.createSpace(householdId, roomId, name) }
    }

    fun renameHousehold(name: String) {
        viewModelScope.launch { householdRepository.renameHousehold(householdId, name) }
    }

    /** The screen only enables sending for a valid address; the new invite then shows up in the pending list. */
    fun inviteEmail(input: String) {
        val email = normalizeEmail(input) ?: return
        viewModelScope.launch { householdRepository.inviteEmail(householdId, email) }
    }

    fun cancelEmailInvite(email: String) = cancelledInvites.remove(email)

    fun removeMember(memberId: String) {
        val household = household ?: return
        viewModelScope.launch { householdRepository.removeMember(household, memberId) }
    }

    fun resetInviteCode() {
        val household = household ?: return
        viewModelScope.launch { householdRepository.resetInviteCode(household) }
    }

    fun leaveHousehold() {
        viewModelScope.launch { householdRepository.leaveHousehold(householdId) }
    }

    fun deleteHousehold() {
        val household = household ?: return
        viewModelScope.launch { householdRepository.deleteHousehold(household) }
    }

    override fun onCleared() = cancelledInvites.flush()

    @AssistedFactory
    interface Factory {
        fun create(key: HouseholdKey): HouseholdViewModel
    }
}
