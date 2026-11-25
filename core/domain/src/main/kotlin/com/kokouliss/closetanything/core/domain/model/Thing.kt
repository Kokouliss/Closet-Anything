package com.kokouliss.closetanything.core.domain.model

import java.text.Normalizer

/** Something kept in a space: a pair of shoes, a ring, or a box that holds more things. */
data class Thing(
    val id: String,
    val name: String,
    val roomId: String,
    val spaceId: String,
    /** The box this thing is in, if any. A box is never inside another box. */
    val boxId: String?,
    val isBox: Boolean,
    /** More about it, for example "size 42, the black ones". */
    val note: String?,
    val quantity: Int,
    /** Free words to find it by, for example "winter" or "gift". */
    val tags: List<String>,
    /** Small preview of the photo. The full photo is loaded on demand. */
    val thumbnail: ImageData?,
    val createdByName: String,
    val createdAtMillis: Long,
)

/** What a person types about a thing. */
data class ThingDraft(
    val name: String,
    val note: String? = null,
    val quantity: Int = 1,
    val tags: List<String> = emptyList(),
)

/** Encoded image bytes (JPEG). Compared by content so Compose can skip rows that did not change. */
class ImageData(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is ImageData && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}

const val MAX_NOTE_LENGTH = 500
const val MAX_QUANTITY = 999
const val MAX_TAGS = 10
const val MAX_TAG_LENGTH = 30

private val ACCENTS = Regex("\\p{M}+")

/** Lower case without accents, so "Παπούτσια" matches "παπουτσια" and "Café" matches "cafe". */
fun String.folded(): String =
    ACCENTS.replace(Normalizer.normalize(this, Normalizer.Form.NFD), "").lowercase().replace('ς', 'σ')

/** The tags in a comma-separated field, trimmed, without doubles, within the limits the rules enforce. */
fun parseTags(input: String): List<String> =
    input.split(',').map { it.trim().take(MAX_TAG_LENGTH) }.filter { it.isNotEmpty() }.distinctBy { it.folded() }.take(MAX_TAGS)

/** Things whose name, note or a tag contains [query], whatever the accents and capitals. Empty for a blank query. */
fun List<Thing>.search(query: String): List<Thing> {
    val words = query.trim().folded()
    if (words.isEmpty()) return emptyList()
    return filter { thing ->
        words in thing.name.folded() || thing.note?.folded()?.contains(words) == true || thing.tags.any { words in it.folded() }
    }
}

/** Where [thing] is, from the room down: room, space and, inside one, the box. Unknown parts are left out. */
fun Thing.locationIn(rooms: List<Room>, spaces: List<Space>, things: List<Thing>): List<String> = listOfNotNull(
    rooms.firstOrNull { it.id == roomId }?.name,
    spaces.firstOrNull { it.id == spaceId }?.name,
    boxId?.let { box -> things.firstOrNull { it.id == box }?.name },
)
