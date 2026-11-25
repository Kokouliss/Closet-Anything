package com.kokouliss.closetanything.core.domain.model

/** A room of the home, for example "My room" or "Storage". */
data class Room(
    val id: String,
    val name: String,
)

/** A place in a room where things are kept: a wardrobe, a shelf, under the bed. */
data class Space(
    val id: String,
    val roomId: String,
    val name: String,
)
