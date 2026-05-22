package com.easyhooon.dari

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class DariConfigTest {
    @Test
    fun `default retention period is null (disabled)`() {
        assertNull(DariConfig().retentionPeriod)
    }

    @Test
    fun `positive retention period is accepted`() {
        val config = DariConfig(retentionPeriod = 1.days)
        assertEquals(1.days, config.retentionPeriod)
    }

    @Test
    fun `retention period can be set to smaller durations`() {
        val config = DariConfig(retentionPeriod = 6.hours)
        assertEquals(6.hours, config.retentionPeriod)
    }

    @Test
    fun `zero retention period throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            DariConfig(retentionPeriod = Duration.ZERO)
        }
    }

    @Test
    fun `negative retention period throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            DariConfig(retentionPeriod = (-1).seconds)
        }
    }

    @Test
    fun `other validation still applies`() {
        // Sanity check that retentionPeriod was added without breaking maxContentLength validation.
        assertThrows(IllegalArgumentException::class.java) {
            DariConfig(maxContentLength = 0)
        }
    }
}
