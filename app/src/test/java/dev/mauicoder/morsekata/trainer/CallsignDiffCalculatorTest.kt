package dev.mauicoder.morsekata.trainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsignDiffCalculatorTest {

    @Test
    fun `exact match returns isMatch true and all segments correct`() {
        val result = CallsignDiffCalculator.calculate("W1AW", "W1AW")

        assertTrue("Expected isMatch to be true", result.isMatch)
        assertEquals("W1AW", result.typed)
        assertEquals("W1AW", result.expected)
        assertEquals(4, result.diffSegments.size)
        assertTrue("All segments should be correct", result.diffSegments.all { it.isCorrect })
        assertEquals(listOf('W', '1', 'A', 'W'), result.diffSegments.map { it.char })
    }

    @Test
    fun `exact match is case insensitive and trims whitespace`() {
        val result = CallsignDiffCalculator.calculate("  w1aw  ", "W1AW")

        assertTrue("Expected isMatch to be true", result.isMatch)
        assertEquals(4, result.diffSegments.size)
        assertTrue(result.diffSegments.all { it.isCorrect })
        assertEquals(listOf('W', '1', 'A', 'W'), result.diffSegments.map { it.char })
    }

    @Test
    fun `single character mismatch flags incorrect character`() {
        val result = CallsignDiffCalculator.calculate("W1AX", "W1AW")

        assertFalse("Expected isMatch to be false", result.isMatch)
        assertEquals(4, result.diffSegments.size)
        assertEquals(listOf('W', '1', 'A', 'X'), result.diffSegments.map { it.char })
        assertEquals(listOf(true, true, true, false), result.diffSegments.map { it.isCorrect })
    }

    @Test
    fun `extra character is flagged incorrect`() {
        val result = CallsignDiffCalculator.calculate("W1AWX", "W1AW")

        assertFalse(result.isMatch)
        assertEquals(5, result.diffSegments.size)
        assertEquals(listOf('W', '1', 'A', 'W', 'X'), result.diffSegments.map { it.char })
        assertEquals(listOf(true, true, true, true, false), result.diffSegments.map { it.isCorrect })
    }

    @Test
    fun `missing character at end is flagged incorrect with expected character`() {
        val result = CallsignDiffCalculator.calculate("W1A", "W1AW")

        assertFalse(result.isMatch)
        assertEquals(4, result.diffSegments.size)
        assertEquals(listOf('W', '1', 'A', 'W'), result.diffSegments.map { it.char })
        assertEquals(listOf(true, true, true, false), result.diffSegments.map { it.isCorrect })
    }

    @Test
    fun `missing character at start is flagged incorrect`() {
        val result = CallsignDiffCalculator.calculate("1AW", "W1AW")

        assertFalse(result.isMatch)
        assertEquals(4, result.diffSegments.size)
        assertEquals(listOf('W', '1', 'A', 'W'), result.diffSegments.map { it.char })
        assertEquals(listOf(false, true, true, true), result.diffSegments.map { it.isCorrect })
    }

    @Test
    fun `empty typed string flags all expected characters as incorrect`() {
        val result = CallsignDiffCalculator.calculate("", "K6QRP")

        assertFalse(result.isMatch)
        assertEquals(5, result.diffSegments.size)
        assertEquals(listOf('K', '6', 'Q', 'R', 'P'), result.diffSegments.map { it.char })
        assertTrue("All characters should be flagged incorrect", result.diffSegments.none { it.isCorrect })
    }

    @Test
    fun `contest exchange with cut number errors highlights exact differences`() {
        // User typed standard numbers instead of cut numbers
        val result = CallsignDiffCalculator.calculate("W1AW 599 001", "W1AW 5NN TT1")

        assertFalse(result.isMatch)
        assertEquals("W1AW 599 001", result.typed)
        assertEquals("W1AW 5NN TT1", result.expected)
        assertEquals(12, result.diffSegments.size)

        // Verify correct matches
        val correctIndices = setOf(0, 1, 2, 3, 4, 5, 8, 11)
        for (i in result.diffSegments.indices) {
            if (i in correctIndices) {
                assertTrue("Segment $i should be correct", result.diffSegments[i].isCorrect)
            } else {
                assertFalse("Segment $i should be incorrect", result.diffSegments[i].isCorrect)
            }
        }
    }

    @Test
    fun `both empty strings return isMatch true and empty segments`() {
        val result = CallsignDiffCalculator.calculate("", "")

        assertTrue(result.isMatch)
        assertTrue(result.diffSegments.isEmpty())
    }

    @Test
    fun `insertion in middle is flagged incorrect`() {
        val result = CallsignDiffCalculator.calculate("W1ABW", "W1AW")

        assertFalse(result.isMatch)
        assertEquals(listOf('W', '1', 'A', 'B', 'W'), result.diffSegments.map { it.char })
        assertEquals(listOf(true, true, true, false, true), result.diffSegments.map { it.isCorrect })
    }

    @Test
    fun `deletion in middle is flagged incorrect with expected character`() {
        val result = CallsignDiffCalculator.calculate("W1W", "W1AW")

        assertFalse(result.isMatch)
        assertEquals(listOf('W', '1', 'A', 'W'), result.diffSegments.map { it.char })
        assertEquals(listOf(true, true, false, true), result.diffSegments.map { it.isCorrect })
    }
}
