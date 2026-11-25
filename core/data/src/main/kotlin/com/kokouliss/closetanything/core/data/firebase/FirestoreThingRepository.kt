package com.kokouliss.closetanything.core.data.firebase

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import com.kokouliss.closetanything.core.data.di.IoDispatcher
import com.kokouliss.closetanything.core.domain.model.ImageData
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.domain.model.ThingDraft
import com.kokouliss.closetanything.core.domain.model.folded
import com.kokouliss.closetanything.core.domain.repository.ThingRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal class FirestoreThingRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val photoCompressor: PhotoCompressor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ThingRepository {

    private fun things(householdId: String) = db.household(householdId).collection(Collections.THINGS)

    // shortcut: one listener for every thing of the household, filtered on the phone. Fine for the
    // hundreds of things a home has; page by space if a household ever holds tens of thousands.
    override fun observeThings(householdId: String): Flow<List<Thing>> =
        things(householdId).snapshots().map { snapshot ->
            snapshot.documents.mapNotNull { it.toThing() }.sortedWith(compareByDescending<Thing> { it.isBox }.thenBy { it.name.folded() })
        }

    override suspend fun createThing(householdId: String, draft: ThingDraft, roomId: String, spaceId: String, boxId: String?, isBox: Boolean): String {
        val thing = things(householdId).document()
        val data = buildMap {
            draft.fields().forEach { (field, value) -> if (value != null) put(field, value) }
            put(Fields.ROOM_ID, roomId)
            put(Fields.SPACE_ID, spaceId)
            boxId?.let { put(Fields.BOX_ID, it) }
            if (isBox) put(Fields.BOX, true)
            put(Fields.CREATED_BY_NAME, auth.requireUser().memberName)
            put(Fields.CREATED_AT, FieldValue.serverTimestamp())
        }
        thing.set(data).logFailure("add thing")
        return thing.id
    }

    override suspend fun updateThing(householdId: String, thingId: String, draft: ThingDraft) {
        // A field that went back to its default is removed; set() could not take the delete marker.
        val changes = draft.fields().mapValues { (_, value) -> value ?: FieldValue.delete() }
        things(householdId).document(thingId).update(changes).logFailure("change thing")
    }

    /** Fields the person edits. An empty note, a quantity of one and no tags are null: not stored. */
    private fun ThingDraft.fields(): Map<String, Any?> = mapOf(
        Fields.NAME to name,
        Fields.NOTE to note?.takeIf { it.isNotBlank() },
        Fields.QUANTITY to quantity.takeIf { it > 1 },
        Fields.TAGS to tags.takeIf { it.isNotEmpty() },
    )

    override suspend fun moveThing(householdId: String, thing: Thing, roomId: String, spaceId: String, boxId: String?) {
        val batch = db.batch().update(
            things(householdId).document(thing.id),
            mapOf(Fields.ROOM_ID to roomId, Fields.SPACE_ID to spaceId, Fields.BOX_ID to (boxId ?: FieldValue.delete())),
        )
        if (thing.isBox) {
            // The contents follow the box. Offline, the ones the phone has cached follow at once and the rest when it reconnects.
            try {
                contents(householdId, thing.id).forEach { batch.update(it, mapOf(Fields.ROOM_ID to roomId, Fields.SPACE_ID to spaceId)) }
            } catch (e: FirebaseFirestoreException) {
                // Nothing cached: the box moves alone and its contents keep pointing at it.
            }
        }
        batch.commit().logFailure("move thing")
    }

    override suspend fun deleteThing(householdId: String, thing: Thing) {
        val inside = if (thing.isBox) {
            try {
                contents(householdId, thing.id)
            } catch (e: FirebaseFirestoreException) {
                emptyList()
            }
        } else {
            emptyList()
        }
        db.deleteThings(inside + things(householdId).document(thing.id))
    }

    private suspend fun contents(householdId: String, boxId: String) =
        things(householdId).whereEqualTo(Fields.BOX_ID, boxId).get().await().documents.map { it.reference }

    override suspend fun setPhoto(householdId: String, thingId: String, photoUri: String) {
        val photo = withContext(ioDispatcher) { photoCompressor.compress(Uri.parse(photoUri)) }
        // shortcut: photos live in Firestore so the app needs no billing account. A document holds
        // at most 1 MiB, hence the size cap in PhotoCompressor. Move the full photo to Firebase
        // Storage when full-resolution pictures or more than about 1 GiB of photos are needed.
        val thing = things(householdId).document(thingId)
        db.batch()
            .set(thing.fullPhoto(), mapOf(Fields.DATA to Blob.fromBytes(photo.full)))
            .update(thing, Fields.THUMBNAIL, Blob.fromBytes(photo.thumbnail))
            .commit()
            .logFailure("attach photo")
    }

    override suspend fun removePhoto(householdId: String, thingId: String) {
        val thing = things(householdId).document(thingId)
        db.batch()
            .delete(thing.fullPhoto())
            .update(thing, Fields.THUMBNAIL, FieldValue.delete())
            .commit()
            .logFailure("remove photo")
    }

    override suspend fun loadPhoto(householdId: String, thingId: String): ImageData? =
        try {
            things(householdId).document(thingId).fullPhoto().get().await().getBlob(Fields.DATA)?.let { ImageData(it.toBytes()) }
        } catch (e: FirebaseFirestoreException) {
            // Offline and not in the local cache yet.
            null
        }
}
