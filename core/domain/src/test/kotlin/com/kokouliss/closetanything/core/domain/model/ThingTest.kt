package com.kokouliss.closetanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThingTest {
    private val room = Room("r1", "My room")
    private val space = Space("s1", "r1", "Under the bed")
    private val box = thing("b1", "Jewellery box", isBox = true)
    private val ring = thing("t1", "Ruby ring", boxId = "b1", tags = listOf("Gift", "Γάμος"))
    private val shoes = thing("t2", "Nike shoes", note = "Size 42, the black ones")

    private fun thing(id: String, name: String, isBox: Boolean = false, boxId: String? = null, note: String? = null, tags: List<String> = emptyList()) =
        Thing(id, name, "r1", "s1", boxId, isBox, note, 1, tags, null, "Panos", 0)

    @Test
    fun searchIgnoresAccentsAndCaseAndLooksAtNotesAndTags() {
        val all = listOf(box, ring, shoes)
        assertThat(all.search("RUBY")).containsExactly(ring)
        assertThat(all.search("black")).containsExactly(shoes)
        assertThat(all.search("γαμοσ")).containsExactly(ring)
        assertThat(all.search("  ")).isEmpty()
    }

    @Test
    fun locationReadsFromTheRoomDownAndSkipsWhatIsMissing() {
        assertThat(ring.locationIn(listOf(room), listOf(space), listOf(box))).containsExactly("My room", "Under the bed", "Jewellery box").inOrder()
        assertThat(shoes.locationIn(emptyList(), listOf(space), emptyList())).containsExactly("Under the bed")
    }

    @Test
    fun tagsAreTrimmedDedupedAndCapped() {
        assertThat(parseTags(" winter, Winter ,, gift ")).containsExactly("winter", "gift").inOrder()
        assertThat(parseTags((1..20).joinToString(",") { "t$it" })).hasSize(MAX_TAGS)
    }
}
