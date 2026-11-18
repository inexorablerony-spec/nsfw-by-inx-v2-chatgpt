package io.github.aedev.flow.core

import org.junit.Assert.*
import org.junit.Test

class UrlValidatorTest {
    @Test
    fun `allows public https urls`() {
        assertTrue(UrlValidator.isAllowed("https://example.com/video"))
    }

    @Test
    fun `rejects localhost`() {
        assertFalse(UrlValidator.isAllowed("http://localhost/test"))
    }

    @Test
    fun `rejects loopback ipv4`() {
        assertFalse(UrlValidator.isAllowed("http://127.0.0.1/test"))
    }

    @Test
    fun `rejects private 10 network`() {
        assertFalse(UrlValidator.isAllowed("http://10.0.0.1/test"))
    }

    @Test
    fun `rejects private 192.168 network`() {
        assertFalse(UrlValidator.isAllowed("http://192.168.1.1/test"))
    }

    @Test
    fun `rejects private 172.16 network`() {
        assertFalse(UrlValidator.isAllowed("http://172.16.0.1/test"))
    }

    @Test
    fun `rejects link local`() {
        assertFalse(UrlValidator.isAllowed("http://169.254.1.1/test"))
    }

    @Test
    fun `rejects file scheme`() {
        assertFalse(UrlValidator.isAllowed("file:///etc/passwd"))
    }

    @Test
    fun `rejects malformed url`() {
        assertFalse(UrlValidator.isAllowed("not a url"))
    }
}
