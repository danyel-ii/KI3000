package com.threestrip.core.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class ForbiddenPermissionsTest {
    @Test
    fun manifestDoesNotDeclareNetworkPermissions() {
        val manifest = File("../app/src/main/AndroidManifest.xml").readText()
        assertThat(manifest).doesNotContain("android.permission.INTERNET")
        assertThat(manifest).doesNotContain("android.permission.ACCESS_NETWORK_STATE")
    }
}
