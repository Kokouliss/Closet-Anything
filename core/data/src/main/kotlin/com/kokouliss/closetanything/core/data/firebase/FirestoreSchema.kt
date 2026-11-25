package com.kokouliss.closetanything.core.data.firebase

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FirebaseFirestore
import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.ImageData
import com.kokouliss.closetanything.core.domain.model.MAX_QUANTITY
import com.kokouliss.closetanything.core.domain.model.Member
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.Thing

// Document layout. firebase/firestore.rules validates the same names; change both together.
//
// households/{householdId}                    name, ownerId, memberIds[], members{uid: {name}}, inviteCode, invitedEmails[], createdAt
// invites/{inviteCode}                        householdId
// households/{householdId}/rooms/{roomId}     name, createdAt
// households/{householdId}/spaces/{spaceId}   roomId, name, createdAt
// households/{householdId}/things/{thingId}   name, roomId, spaceId, boxId?, box?, note?, quantity?, tags?[], thumbnail?,
//                                             createdByName, createdAt. A box is a thing with box = true; its contents carry its id in boxId.
// .../things/{thingId}/photo/full             data (JPEG bytes)

internal object Collections {
    const val HOUSEHOLDS = "households"
    const val INVITES = "invites"
    const val ROOMS = "rooms"
    const val SPACES = "spaces"
    const val THINGS = "things"
    const val PHOTO = "photo"
}

/** Id of the single document under a thing's `photo` collection that holds the full photo. */
internal const val FULL_PHOTO_ID = "full"

internal object Fields {
    const val NAME = "name"
    const val OWNER_ID = "ownerId"
    const val MEMBER_IDS = "memberIds"
    const val MEMBERS = "members"
    const val INVITE_CODE = "inviteCode"
    const val INVITED_EMAILS = "invitedEmails"
    const val CREATED_AT = "createdAt"
    const val HOUSEHOLD_ID = "householdId"
    const val ROOM_ID = "roomId"
    const val SPACE_ID = "spaceId"
    const val BOX_ID = "boxId"
    const val BOX = "box"
    const val NOTE = "note"
    const val QUANTITY = "quantity"
    const val TAGS = "tags"
    const val THUMBNAIL = "thumbnail"
    const val CREATED_BY_NAME = "createdByName"
    const val DATA = "data"
}

internal fun DocumentSnapshot.toHousehold(): Household? {
    if (!exists()) return null
    val ownerId = getString(Fields.OWNER_ID) ?: return null
    val members = (get(Fields.MEMBERS) as? Map<*, *>).orEmpty()
        .mapNotNull { (id, entry) ->
            val name = (entry as? Map<*, *>)?.get(Fields.NAME) as? String
            if (id is String && name != null) Member(id, name) else null
        }
        .sortedWith(compareByDescending<Member> { it.id == ownerId }.thenBy { it.name.lowercase() })
    return Household(
        id = id,
        name = getString(Fields.NAME).orEmpty(),
        ownerId = ownerId,
        inviteCode = getString(Fields.INVITE_CODE).orEmpty(),
        members = members,
        invitedEmails = (get(Fields.INVITED_EMAILS) as? List<*>).orEmpty().filterIsInstance<String>(),
    )
}

internal fun DocumentSnapshot.toRoom(): Room? = if (exists()) Room(id, getString(Fields.NAME).orEmpty()) else null

internal fun DocumentSnapshot.toSpace(): Space? {
    if (!exists()) return null
    return Space(id, roomId = getString(Fields.ROOM_ID) ?: return null, name = getString(Fields.NAME).orEmpty())
}

internal fun DocumentSnapshot.toThing(): Thing? {
    if (!exists()) return null
    return Thing(
        id = id,
        name = getString(Fields.NAME).orEmpty(),
        roomId = getString(Fields.ROOM_ID) ?: return null,
        spaceId = getString(Fields.SPACE_ID) ?: return null,
        boxId = getString(Fields.BOX_ID),
        isBox = getBoolean(Fields.BOX) == true,
        note = getString(Fields.NOTE),
        quantity = getLong(Fields.QUANTITY)?.toInt()?.coerceIn(1, MAX_QUANTITY) ?: 1,
        tags = (get(Fields.TAGS) as? List<*>).orEmpty().filterIsInstance<String>(),
        thumbnail = getBlob(Fields.THUMBNAIL)?.let { ImageData(it.toBytes()) },
        createdByName = getString(Fields.CREATED_BY_NAME).orEmpty(),
        // A just-added thing has no server time yet; the estimate keeps it in place.
        createdAtMillis = getTimestamp(Fields.CREATED_AT, ServerTimestampBehavior.ESTIMATE)?.toDate()?.time ?: 0L,
    )
}

internal fun FirebaseFirestore.household(householdId: String): DocumentReference = collection(Collections.HOUSEHOLDS).document(householdId)

internal fun DocumentReference.fullPhoto(): DocumentReference = collection(Collections.PHOTO).document(FULL_PHOTO_ID)

/** Deletes things together with their photos, in batches under Firestore's 500-write limit. */
internal fun FirebaseFirestore.deleteThings(things: List<DocumentReference>) {
    things.chunked(200).forEach { chunk ->
        val batch = batch()
        chunk.forEach { thing ->
            batch.delete(thing.fullPhoto())
            batch.delete(thing)
        }
        batch.commit().logFailure("delete things")
    }
}
