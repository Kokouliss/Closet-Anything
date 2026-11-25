package com.kokouliss.closetanything.feature.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kokouliss.closetanything.core.domain.model.ImageData
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.domain.model.ThingDraft
import com.kokouliss.closetanything.core.domain.model.folded
import com.kokouliss.closetanything.core.domain.model.locationIn
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A space, or a box in a space, that a thing can be moved to. */
internal data class Destination(val roomId: String, val spaceId: String, val boxId: String?, val path: List<String>)

internal sealed interface ThingUiState {
    data object Loading : ThingUiState

    /** The thing was deleted, or access to the household ended. The screen closes itself. */
    data object Gone : ThingUiState

    data class Ready(
        /** Null while a new thing is being added. */
        val thing: Thing?,
        /** Where the thing is, or where the new one goes. */
        val roomId: String,
        val spaceId: String,
        val boxId: String?,
        /** The same as names: room, space and, inside one, the box. */
        val path: List<String>,
        /** Where it can be moved to, without where it is now. */
        val destinations: List<Destination>,
        /** A new thing can be a box unless it goes into a box. */
        val canBeBox: Boolean,
    ) : ThingUiState
}

@HiltViewModel(assistedFactory = ThingViewModel.Factory::class)
internal class ThingViewModel @AssistedInject constructor(
    @Assisted private val key: ThingKey,
    private val thingRepository: ThingRepository,
    placeRepository: PlaceRepository,
) : ViewModel() {

    private val householdId = key.householdId

    val uiState: StateFlow<ThingUiState> = combine(
        placeRepository.observeRooms(householdId),
        placeRepository.observeSpaces(householdId),
        thingRepository.observeThings(householdId),
    ) { rooms, spaces, things ->
        val thing = key.thingId?.let { id -> things.firstOrNull { it.id == id } ?: return@combine ThingUiState.Gone }
        val spaceId = thing?.spaceId ?: key.spaceId ?: return@combine ThingUiState.Gone
        val boxId = thing?.boxId ?: key.boxId
        val space = spaces.firstOrNull { it.id == spaceId } ?: return@combine ThingUiState.Gone
        ThingUiState.Ready(
            thing = thing,
            roomId = space.roomId,
            spaceId = spaceId,
            boxId = boxId,
            path = listOfNotNull(
                rooms.firstOrNull { it.id == space.roomId }?.name,
                space.name,
                boxId?.let { id -> things.firstOrNull { it.id == id }?.name },
            ),
            destinations = destinations(rooms, spaces, things, thing).filterNot { it.spaceId == spaceId && it.boxId == boxId },
            canBeBox = thing == null && boxId == null,
        )
    }.catch { emit(ThingUiState.Gone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThingUiState.Loading)

    /** Every space, and every box when [thing] is not itself a box. */
    private fun destinations(rooms: List<Room>, spaces: List<Space>, things: List<Thing>, thing: Thing?): List<Destination> {
        val roomNames = rooms.associate { it.id to it.name }
        val toSpaces = spaces.map { Destination(it.roomId, it.id, null, listOfNotNull(roomNames[it.roomId], it.name)) }
        val toBoxes = if (thing?.isBox == true) {
            emptyList()
        } else {
            things.filter { it.isBox && it.id != thing?.id }.map { box -> Destination(box.roomId, box.spaceId, box.id, box.locationIn(rooms, spaces, things) + box.name) }
        }
        return (toSpaces + toBoxes).sortedBy { it.path.asPath().folded() }
    }

    /** The full photo, loaded again whenever the thumbnail changes. Null while loading or when there is none. */
    val photo: StateFlow<ImageData?> = uiState
        .map { (it as? ThingUiState.Ready)?.thing?.thumbnail }
        .distinctUntilChanged()
        .map { thumbnail -> if (thumbnail == null) null else key.thingId?.let { thingRepository.loadPhoto(householdId, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stores the thing. A new one is created where the key says, with [photoUri] attached when given. */
    suspend fun save(draft: ThingDraft, isBox: Boolean, photoUri: String?) {
        val thingId = key.thingId
        if (thingId != null) {
            thingRepository.updateThing(householdId, thingId, draft)
        } else {
            val ready = uiState.value as? ThingUiState.Ready ?: return
            val id = thingRepository.createThing(householdId, draft, ready.roomId, ready.spaceId, ready.boxId, isBox && ready.canBeBox)
            if (photoUri != null) thingRepository.setPhoto(householdId, id, photoUri)
        }
    }

    fun setPhoto(photoUri: String) {
        val thingId = key.thingId ?: return
        viewModelScope.launch { thingRepository.setPhoto(householdId, thingId, photoUri) }
    }

    fun removePhoto() {
        val thingId = key.thingId ?: return
        viewModelScope.launch { thingRepository.removePhoto(householdId, thingId) }
    }

    fun move(to: Destination) {
        val thing = (uiState.value as? ThingUiState.Ready)?.thing ?: return
        viewModelScope.launch { thingRepository.moveThing(householdId, thing, to.roomId, to.spaceId, to.boxId) }
    }

    fun delete() {
        val thing = (uiState.value as? ThingUiState.Ready)?.thing ?: return
        viewModelScope.launch { thingRepository.deleteThing(householdId, thing) }
    }

    @AssistedFactory
    interface Factory {
        fun create(key: ThingKey): ThingViewModel
    }
}
