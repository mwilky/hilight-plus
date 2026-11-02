package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Test

class SeenChannelsTest {

    @Test
    fun roundTripsAndSortsByNameIgnoringCase() {
        val channels = listOf(
            SeenChannel("b", "server messages"),
            SeenChannel("a", "Direct messages"),
            SeenChannel("c", "Mentions")
        )
        assertEquals(
            listOf("Direct messages", "Mentions", "server messages"),
            decodeSeenChannels(encodeSeenChannels(channels)).map { it.name }
        )
    }

    @Test
    fun blankNameFallsBackToTheChannelId() {
        assertEquals(listOf(SeenChannel("promo", "promo")), decodeSeenChannels("""{"promo":""}"""))
    }

    @Test
    fun missingOrBrokenDataReadsAsNoneSeen() {
        assertEquals(emptyList<SeenChannel>(), decodeSeenChannels(null))
        assertEquals(emptyList<SeenChannel>(), decodeSeenChannels("not json"))
    }
}
