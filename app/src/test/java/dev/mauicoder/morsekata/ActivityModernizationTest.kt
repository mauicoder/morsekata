package dev.mauicoder.morsekata

import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityModernizationTest {

    @Test
    fun mainActivity_inheritsFromComponentActivity() {
        assertTrue(
            "MainActivity must inherit from ComponentActivity",
            ComponentActivity::class.java.isAssignableFrom(MainActivity::class.java)
        )
    }

    @Test
    fun mainActivity_doesNotInheritFromAppCompatActivity() {
        assertFalse(
            "MainActivity should be modernized to ComponentActivity and not AppCompatActivity",
            AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java)
        )
    }

    @Test
    fun mainActivity_hasNoFragmentFields() {
        val fragmentFields = MainActivity::class.java.declaredFields.filter {
            it.type.name.contains("Fragment", ignoreCase = true)
        }
        assertTrue(
            "MainActivity should not have fragment field injections, but found: ${fragmentFields.map { it.name }}",
            fragmentFields.isEmpty()
        )
    }

    @Test
    fun mainActivity_hasNoViewBindingField() {
        val bindingFields = MainActivity::class.java.declaredFields.filter {
            it.name.contains("binding", ignoreCase = true)
        }
        assertTrue(
            "MainActivity should not have binding fields, but found: ${bindingFields.map { it.name }}",
            bindingFields.isEmpty()
        )
    }

    @Test
    fun decodeFileActivity_inheritsFromComponentActivity() {
        assertTrue(
            "DecodeFileActivity must inherit from ComponentActivity",
            ComponentActivity::class.java.isAssignableFrom(DecodeFileActivity::class.java)
        )
    }

    @Test
    fun decodeFileActivity_doesNotInheritFromAppCompatActivity() {
        assertFalse(
            "DecodeFileActivity should be modernized to ComponentActivity and not AppCompatActivity",
            AppCompatActivity::class.java.isAssignableFrom(DecodeFileActivity::class.java)
        )
    }
}
