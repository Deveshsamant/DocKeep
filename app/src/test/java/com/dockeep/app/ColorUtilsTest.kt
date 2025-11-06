package com.dockeep.app

import com.dockeep.app.utils.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorUtilsTest {

    @Test
    fun getColorIndexForName_returnsConsistentIndex() {
        val name = "Test Document"
        val index1 = ColorUtils.getColorIndexForName(name)
        val index2 = ColorUtils.getColorIndexForName(name)
        
        assertTrue("Color index should be consistent", index1 == index2)
        assertTrue("Color index should be within valid range", index1 >= 0 && index1 < 8)
    }

    @Test
    fun getColorIndexForName_handlesEmptyString() {
        val index = ColorUtils.getColorIndexForName("")
        assertTrue("Color index should be valid for empty string", index >= 0 && index < 8)
    }

    @Test
    fun getColorIndexForName_handlesSpecialCharacters() {
        val name = "Test@#$%^&*()Document"
        val index = ColorUtils.getColorIndexForName(name)
        assertTrue("Color index should be valid for special characters", index >= 0 && index < 8)
    }
}