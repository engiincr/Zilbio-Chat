package com.android.zilbio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class ProfileDateFormatTest {

    @Test
    fun testBirthDateDisplayAndStorageRoundTrip() {
        // Display format: dd.MM.yyyy
        val displayDate = "15.08.1995"
        val displayFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        val parsedDate = LocalDate.parse(displayDate, displayFormatter)

        // Storage format: yyyy-MM-dd
        val storageFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val storageDate = parsedDate.format(storageFormatter)
        assertEquals("1995-08-15", storageDate)

        // Round-trip back to display
        val roundTripDate = LocalDate.parse(storageDate, storageFormatter).format(displayFormatter)
        assertEquals(displayDate, roundTripDate)
    }

    @Test
    fun testAgeComputationFromBirthDate() {
        val birthDate = LocalDate.of(2000, 1, 1)
        val referenceDate = LocalDate.of(2026, 10, 21)
        val computedAge = Period.between(birthDate, referenceDate).years
        assertEquals(26, computedAge)
    }
}
