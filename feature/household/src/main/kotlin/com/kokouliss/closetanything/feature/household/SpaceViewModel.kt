package com.kokouliss.closetanything.feature.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.domain.repository.PlaceRepository
import com.kokouliss.closetanything.core.domain.repository.ThingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal sealed interface SpaceUiState {
    data object Loading : SpaceUiState

    /** The space or box was deleted, or access to the household ended. The screen closes itself. */
    data object Gone : SpaceUiState

    data class Ready(
        val householdId: String,
        val room: Room?,
        val space: Space,
        /** The box whose contents are shown, or null for the space itself. */
        val box: Thing?,
        /** The things shown: the box's contents, or the space's things and boxes outside any box. */
        val things: List<Thing>,
    ) : SpaceUiState {
        val title: String get() = box?.name ?: space.name

        /** Where this is, from the room down to the space, and the box when inside one. */
        val path: List<String> get() = listOfNotNull(room?.name, space.name.takeIf { box != null })
    }
}

@HiltViewModel(assistedFactory = SpaceViewModel.Factory::class)
internal class SpaceViewModel @AssistedInject constructor(
    @Assisted private val key: SpaceKey,
    private val placeRepository: PlaceRepository,
    thingRepository: ThingRepository,
) : ViewModel() {

    // Losing access ends these streams with an error, which reads as "gone".
    val uiState: StateFlow<SpaceUiState> = combine(
        placeRepository.observeRooms(key.householdId),
        placeRepository.observeSpaces(key.householdId),
        thingRepository.observeThings(key.householdId),
    ) { rooms, spaces, things ->
        val space = spaces.firstOrNull { it.id == key.spaceId } ?: return@combine SpaceUiState.Gone
        val box = key.boxId?.let { boxId -> things.firstOrNull { it.id == boxId } ?: return@combine SpaceUiState.Gone }
        SpaceUiState.Ready(
            householdId = key.householdId,
            room = rooms.firstOrNull { it.id == space.roomId },
            space = space,
            box = box,
            things = if (box != null) things.filter { it.boxId == box.id } else things.filter { it.spaceId == space.id && it.boxId == null },
        )
    }.catch { emit(SpaceUiState.Gone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpaceUiState.Loading)

    fun renameSpace(name: String) {
        viewModelScope.launch { placeRepository.renameSpace(key.householdId, key.spaceId, name) }
    }

    fun deleteSpace() {
        viewModelScope.launch { placeRepository.deleteSpace(key.householdId, key.spaceId) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: SpaceKey): SpaceViewModel
    }
}
