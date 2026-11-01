package com.katiras.thessticketscanner

import org.junit.Assert.assertEquals
import org.junit.Test

class GetRemainingTripsTest {

    @Test
    fun `unused card reports the full allowance`() {
        assertEquals(65535, getRemainingTrips(0))
    }

    @Test
    fun `one trip used`() {
        assertEquals(65534, getRemainingTrips(1))
    }

    @Test
    fun `exhausted card reports zero`() {
        assertEquals(0, getRemainingTrips(0xFFFF))
    }

    @Test
    fun `counter above 16 bits keeps only the low word`() {
        assertEquals(65535 - 0x2345, getRemainingTrips(0x12345))
    }

    @Test
    fun `full 24 bit counter does not go negative`() {
        assertEquals(0, getRemainingTrips(0xFFFFFF))
    }
}
