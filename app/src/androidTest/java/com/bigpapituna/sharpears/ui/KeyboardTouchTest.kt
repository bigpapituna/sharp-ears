package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.KeyboardLayout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeyboardTouchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun twoFingersPlayTwoNotesAndSlideRetriggers() {
        val log = mutableListOf<String>()
        rule.setContent {
            Keyboard(48, 84, emptyMap(), { m, _ -> log += "on$m" }, { m -> log += "off$m" }, Modifier.size(600.dp, 200.dp))
        }
        rule.onRoot().performTouchInput {
            val l = KeyboardLayout(48, 84, width.toFloat(), height.toFloat())
            fun at(m: Int) = Offset(l.rect(m).centerX, l.rect(m).touchY)
            down(0, at(60))          // C4
            down(1, at(64))          // E4 while C4 is held
            moveTo(1, at(67))        // slide second finger to G4
            up(0)
            up(1)
        }
        rule.waitForIdle()
        assertEquals(listOf("on60", "on64", "off64", "on67", "off60", "off67"), log)
    }
}
