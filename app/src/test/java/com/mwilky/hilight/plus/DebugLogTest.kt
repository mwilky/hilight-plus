package com.mwilky.hilight.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLogTest {

    @Test
    fun formattedLineReadsBackItsLevel() {
        val line = DebugLog.format(0L, 'W', "daemon", "LightEngine", "Ring -> off")
        assertEquals('W', DebugLog.levelOf(line))
        assertTrue(line.endsWith(" W daemon LightEngine: Ring -> off"))
    }

    @Test
    fun continuationLinesAreIndentedAndHaveNoLevel() {
        val line = DebugLog.format(0L, 'E', "app", "Tag", "failed\nat Foo.bar")
        val continuation = line.lines()[1]
        assertEquals("    at Foo.bar", continuation)
        assertNull(DebugLog.levelOf(continuation))
    }

    @Test
    fun redactedKeyKeepsPackageButDropsTag() {
        val key = "0|com.whatsapp|1|447700900123@s.whatsapp.net|10123"
        val redacted = DebugLog.redactKey(key)
        assertTrue(redacted.startsWith("com.whatsapp#"))
        assertFalse(redacted.contains("447700900123"))
        assertEquals(redacted, DebugLog.redactKey(key))
    }

    @Test
    fun parseSplitsAFormattedLineBackIntoItsParts() {
        val parsed = DebugLog.parse(DebugLog.format(0L, 'I', "daemon", "LightEngine", "Ring -> queue [a: b]\nsecond line"))!!
        assertEquals('I', parsed.level)
        assertEquals("daemon", parsed.source)
        assertEquals("LightEngine", parsed.tag)
        assertEquals("Ring -> queue [a: b]\nsecond line", parsed.message)
        assertNull(DebugLog.parse("    at Foo.bar"))
    }
}
