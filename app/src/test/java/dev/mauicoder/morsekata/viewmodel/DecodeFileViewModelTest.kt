package dev.mauicoder.morsekata.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class DecodeFileViewModelTest {

    private lateinit var viewModel: DecodeFileViewModel

    @Before
    fun setUp() {
        viewModel = DecodeFileViewModel()
    }

    @Test
    fun initialState_hasEmptyDecodedTextAndNotDecoding() {
        assertEquals("", viewModel.decodedText.value)
        assertFalse(viewModel.isDecoding.value)
    }
}
