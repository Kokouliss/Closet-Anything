package com.kokouliss.closetanything.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.folded
import com.kokouliss.closetanything.core.domain.repository.PlaceRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

internal class FirestorePlaceRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : PlaceRepository {

    private fun rooms(householdId: String) = db.household(householdId).collection(Collections.ROOMS)
    private fun spaces(householdId: String) = db.household(householdId).collection(Collections.SPACES)
    private fun things(householdId: String) = db.household(householdId).collection(Collections.THINGS)

    override fun observeRooms(householdId: String): Flow<List<Room>> =
        rooms(householdId).snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toRoom() }.sortedBy { it.name.folded() } }

    override fun observeSpaces(householdId: String): Flow<List<Space>> =
        spaces(householdId).snapshots().map { snapshot -> snapshot.documents.mapNotNull { it.toSpace() }.sortedBy { it.name.folded() } }

    override suspend fun createRoom(householdId: String, name: String) {
        rooms(householdId).add(mapOf(Fields.NAME to name, Fields.CREATED_AT to FieldValue.serverTimestamp())).logFailure("create room")
    }

    override suspend fun renameRoom(householdId: String, roomId: String, name: String) {
        rooms(householdId).document(roomId).update(Fields.NAME, name).logFailure("rename room")
    }

    override suspend fun deleteRoom(householdId: String, roomId: String) {
        // shortcut: what is in the room is deleted from the phone, which offline only knows what it has cached.
        try {
            db.deleteThings(things(householdId).whereEqualTo(Fields.ROOM_ID, roomId).get().await().documents.map { it.reference })
            val batch = db.batch()
            spaces(householdId).whereEqualTo(Fields.ROOM_ID, roomId).get().await().documents.forEach { batch.delete(it.reference) }
            batch.delete(rooms(householdId).document(roomId)).commit().logFailure("delete room")
        } catch (e: FirebaseFirestoreException) {
            rooms(householdId).document(roomId).delete().logFailure("delete room")
        }
    }

    override suspend fun createSpace(householdId: String, roomId: String, name: String) {
        spaces(householdId)
            .add(mapOf(Fields.ROOM_ID to roomId, Fields.NAME to name, Fields.CREATED_AT to FieldValue.serverTimestamp()))
            .logFailure("create space")
    }

    override suspend fun renameSpace(householdId: String, spaceId: String, name: String) {
        spaces(householdId).document(spaceId).update(Fields.NAME, name).logFailure("rename space")
    }

    override suspend fun deleteSpace(householdId: String, spaceId: String) {
        try {
            db.deleteThings(things(householdId).whereEqualTo(Fields.SPACE_ID, spaceId).get().await().documents.map { it.reference })
        } catch (e: FirebaseFirestoreException) {
            // Offline with nothing cached: the space itself still goes.
        }
        spaces(householdId).document(spaceId).delete().logFailure("delete space")
    }
}
