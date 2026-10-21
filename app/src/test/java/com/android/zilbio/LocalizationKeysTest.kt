package com.android.zilbio

import com.android.zilbio.ui.localization.EnglishStrings
import com.android.zilbio.ui.localization.GermanStrings
import com.android.zilbio.ui.localization.TurkishStrings
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalizationKeysTest {

    @Test
    fun testNavigationStringsCompleteness() {
        val stringSets = listOf(TurkishStrings, EnglishStrings, GermanStrings)

        for (strings in stringSets) {
            assertFalse(strings.navHome.isBlank())
            assertFalse(strings.navChats.isBlank())
            assertFalse(strings.navCalls.isBlank())
            assertFalse(strings.navFriends.isBlank())
            assertFalse(strings.navSettings.isBlank())
            assertFalse(strings.chatsTitle.isBlank())
            assertFalse(strings.noChatsYet.isBlank())
        }
    }
}
