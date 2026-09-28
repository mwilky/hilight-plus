package com.mwilky.hilight.plus

import com.mwilky.hilight.plus.ui.parseHexInput
import org.junit.Assert.assertEquals
import org.junit.Test

class HexInputTest {

    @Test
    fun `strips hash and uppercases`() {
        assertEquals("FF8800", parseHexInput("#ff8800"))
    }

    @Test
    fun `drops alpha from pasted ARGB`() {
        assertEquals("FF8800", parseHexInput("#FFFF8800"))
    }

    @Test
    fun `ignores non hex characters and caps at six digits`() {
        assertEquals("AB12", parseHexInput(" ab-12 "))
        assertEquals("123456", parseHexInput("1234567"))
    }
}
