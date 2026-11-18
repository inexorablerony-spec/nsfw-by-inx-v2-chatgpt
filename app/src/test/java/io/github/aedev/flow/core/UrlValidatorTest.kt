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
    fun `rejects multicast`() {
        assertFalse(UrlValidator.isAllowed("http://224.0.0.1/test"))
    }

    @Test
    fun `rejects reserved`() {
        assertFalse(UrlValidator.isAllowed("http://240.0.0.1/test"))
    }

    @Test
    fun `rejects file scheme`() {
        assertFalse(UrlValidator.isAllowed("file:///etc/passwd"))
    }

    @Test
    fun `rejects malformed url`() {
        assertFalse(UrlValidator.isAllowed("not a url"))
    }

    @Test
    fun `rejects ipv6 loopback`() {
        assertFalse(UrlValidator.isAllowed("http://[::1]/test"))
    }

    // Note: DNS rebinding protection requires connection-time validation,
    // not just pre-request DNS resolution. This validator checks resolved
    // addresses at request time but cannot prevent rebinding between
    // validation and connection. Full protection requires a custom
    // connection layer that pins the resolved address.
}
