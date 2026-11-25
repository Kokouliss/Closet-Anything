package com.kokouliss.closetanything.core.domain.repository

import com.kokouliss.closetanything.core.domain.model.ImageData
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.domain.model.ThingDraft
import kotlinx.coroutines.flow.Flow

interface ThingRepository {
    /** Every thing of the household, boxes included. */
    fun observeThings(householdId: String): Flow<List<Thing>>

    /** Returns the id of the new thing. [boxId] is the box it goes into, if any; a box never goes into a box. */
    suspend fun createThing(householdId: String, draft: ThingDraft, roomId: String, spaceId: String, boxId: String?, isBox: Boolean): String

    suspend fun updateThing(householdId: String, thingId: String, draft: ThingDraft)

    /** Puts [thing] in another space, or into or out of a box. A box takes its contents along. */
    suspend fun moveThing(householdId: String, thing: Thing, roomId: String, spaceId: String, boxId: String?)

    /** A box is deleted together with its contents. */
    suspend fun deleteThing(householdId: String, thing: Thing)

    /** [photoUri] is a content URI the app may read. The picture is shrunk before it is stored. */
    suspend fun setPhoto(householdId: String, thingId: String, photoUri: String)

    suspend fun removePhoto(householdId: String, thingId: String)

    /** The full-size photo, or null when the thing has none or it is not available offline. */
    suspend fun loadPhoto(householdId: String, thingId: String): ImageData?
}
