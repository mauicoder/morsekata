package dev.mauicoder.morsekata.ui.decoder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodeFileUiTest {

    @Test
    fun title_isCorrect() {
        assertEquals("Decode Audio File", DecodeFileDefaults.TITLE)
    }

    @Test
    fun buttonText_whenDecoding_isDecodingEllipsis() {
        assertEquals("Decoding...", DecodeFileDefaults.buttonText(isDecoding = true))
    }

    @Test
    fun buttonText_whenNotDecoding_isDecodeFile() {
        assertEquals("Decode File", DecodeFileDefaults.buttonText(isDecoding = false))
    }

    @Test
    fun isButtonEnabled_whenDecoding_isFalse() {
        assertFalse(DecodeFileDefaults.isButtonEnabled(isDecoding = true))
    }

    @Test
    fun isButtonEnabled_whenNotDecoding_isTrue() {
        assertTrue(DecodeFileDefaults.isButtonEnabled(isDecoding = false))
    }
}
