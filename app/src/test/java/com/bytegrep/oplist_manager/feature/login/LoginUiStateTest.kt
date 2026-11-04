package com.bytegrep.oplist_manager.feature.login

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginUiStateTest {

    @Test
    fun `LAN address without scheme is flagged insecure`() {
        val state = LoginUiState(url = "192.168.1.100:5244")
        assertTrue(state.isInsecure)
        assertFalse(state.isSecure)
    }

    @Test
    fun `public domain without scheme is secure`() {
        val state = LoginUiState(url = "nas.example.com")
        assertTrue(state.isSecure)
        assertFalse(state.isInsecure)
    }

    @Test
    fun `explicit http is insecure regardless of case`() {
        assertTrue(LoginUiState(url = "HTTP://nas.example.com").isInsecure)
    }

    @Test
    fun `blank or unparseable url is neither secure nor insecure`() {
        listOf("", "   ", "http://", "http://exa mple.com").forEach { url ->
            val state = LoginUiState(url = url)
            assertFalse(url, state.isSecure)
            assertFalse(url, state.isInsecure)
        }
    }
}
