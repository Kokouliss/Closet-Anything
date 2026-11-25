package com.kokouliss.closetanything.core.domain.repository

import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import kotlinx.coroutines.flow.Flow

/** The rooms of a household and the spaces in them. */
interface PlaceRepository {
    fun observeRooms(householdId: String): Flow<List<Room>>

    fun observeSpaces(householdId: String): Flow<List<Space>>

    suspend fun createRoom(householdId: String, name: String)

    suspend fun renameRoom(householdId: String, roomId: String, name: String)

    /** Also deletes the spaces in the room and the things in them. */
    suspend fun deleteRoom(householdId: String, roomId: String)

    suspend fun createSpace(householdId: String, roomId: String, name: String)

    suspend fun renameSpace(householdId: String, spaceId: String, name: String)

    /** Also deletes the things in the space. */
    suspend fun deleteSpace(householdId: String, spaceId: String)
}
