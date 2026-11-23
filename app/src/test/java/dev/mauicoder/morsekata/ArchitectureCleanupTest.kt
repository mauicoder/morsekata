package dev.mauicoder.morsekata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ArchitectureCleanupTest {

    private val legacyFragmentClassNames = listOf(
        "dev.mauicoder.morsekata.fragment.QsoTrainerFragment",
        "dev.mauicoder.morsekata.fragment.PlayTextFragment",
        "dev.mauicoder.morsekata.fragment.MorseDecoderFragment",
        "dev.mauicoder.morsekata.fragment.ConfigFragment"
    )

    @Test
    fun legacyFragments_noLongerExistInClasspath() {
        for (className in legacyFragmentClassNames) {
            assertThrows(
                "Legacy fragment $className must be deleted",
                ClassNotFoundException::class.java
            ) {
                Class.forName(className)
            }
        }
    }

    @Test
    fun legacyFragmentFolder_noLongerExists() {
        val fragmentDir = File("src/main/java/dev/mauicoder/morsekata/fragment")
        assertFalse(
            "Legacy fragment directory should be deleted if empty: ${fragmentDir.path}",
            fragmentDir.exists()
        )
    }

    @Test
    fun obsoleteLayoutXmlFiles_noLongerExist() {
        val activityMainXml = File("src/main/res/layout/activity_main.xml")
        val activityDecodeFileXml = File("src/main/res/layout/activity_decode_file.xml")
        val layoutDir = File("src/main/res/layout")

        assertFalse("activity_main.xml must be deleted", activityMainXml.exists())
        assertFalse("activity_decode_file.xml must be deleted", activityDecodeFileXml.exists())
        assertFalse("layout resource directory must be deleted if empty", layoutDir.exists())
    }

    @Test
    fun obsoleteNavigationAndMenuXmlFiles_noLongerExist() {
        val mainNavigationXml = File("src/main/res/navigation/main_navigation.xml")
        val navigationDir = File("src/main/res/navigation")
        val bottomNavMenuXml = File("src/main/res/menu/bottom_nav_menu.xml")
        val menuDir = File("src/main/res/menu")

        assertFalse("main_navigation.xml must be deleted", mainNavigationXml.exists())
        assertFalse("navigation resource directory must be deleted if empty", navigationDir.exists())
        assertFalse("bottom_nav_menu.xml must be deleted", bottomNavMenuXml.exists())
        assertFalse("menu resource directory must be deleted if empty", menuDir.exists())
    }

    @Test
    fun viewBinding_noBindingClassesGenerated() {
        assertThrows(
            "ActivityMainBinding should not exist as viewBinding is disabled and layout is deleted",
            ClassNotFoundException::class.java
        ) {
            Class.forName("dev.mauicoder.morsekata.databinding.ActivityMainBinding")
        }
        assertThrows(
            "ActivityDecodeFileBinding should not exist",
            ClassNotFoundException::class.java
        ) {
            Class.forName("dev.mauicoder.morsekata.databinding.ActivityDecodeFileBinding")
        }
    }

    @Test
    fun shareFile_acceptsContextDirectly() {
        val shareMethod = ShareFile::class.java.declaredMethods.find { it.name == "shareAsFile" }
        org.junit.Assert.assertNotNull("shareAsFile method must exist", shareMethod)
        val paramTypes = shareMethod!!.parameterTypes
        org.junit.Assert.assertEquals("shareAsFile should take 2 parameters", 2, paramTypes.size)
        org.junit.Assert.assertEquals(
            "First parameter of shareAsFile should be Context",
            android.content.Context::class.java,
            paramTypes[0]
        )
    }
}
