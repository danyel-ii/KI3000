package com.threestrip.feature.console

import com.google.common.truth.Truth.assertThat
import com.threestrip.core.storage.ConsoleMode
import org.junit.Test

class ConsoleModeTest {
    @Test
    fun consoleModesRemainDistinct() {
        assertThat(ConsoleMode.SPEAKING).isNotEqualTo(ConsoleMode.IDLE)
    }
}
