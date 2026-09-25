package com.example.ControlNGR.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IpMatcherTest {

    @Test
    void comodinCoincidePorOctetosCompletos() {
        assertTrue(IpMatcher.coincide("10.92.104.15", "10.92.104.%"));
        assertTrue(IpMatcher.coincide("192.168.113.200", "192.168.113.*"));
        assertFalse(IpMatcher.coincide("10.92.105.15", "10.92.104.%"));
        assertFalse(IpMatcher.coincide("10.92.1040.1", "10.92.104.%"));
        assertTrue(IpMatcher.coincide("10.92.7.1", "10.92.%"));
    }

    @Test
    void cidrYExacta() {
        assertTrue(IpMatcher.coincide("10.92.104.254", "10.92.104.0/24"));
        assertFalse(IpMatcher.coincide("10.92.105.1", "10.92.104.0/24"));
        assertTrue(IpMatcher.coincide("10.92.104.15", "10.92.104.15"));
        assertFalse(IpMatcher.coincide("10.92.104.16", "10.92.104.15"));
    }

    @Test
    void ipv4MapeadaEnIpv6() {
        assertTrue(IpMatcher.coincide("::ffff:10.92.104.3", "10.92.104.%"));
    }

    @Test
    void entradasInvalidas() {
        assertFalse(IpMatcher.coincide(null, "10.92.104.%"));
        assertFalse(IpMatcher.coincide("0:0:0:0:0:0:0:1", "10.92.104.%"));
        assertFalse(IpMatcher.coincide("10.92.104.3", "%"));
    }

    @Test
    void validacionDePatrones() {
        assertTrue(IpMatcher.patronValido("10.92.104.%"));
        assertTrue(IpMatcher.patronValido("192.168.113.*"));
        assertTrue(IpMatcher.patronValido("10.92.104.0/24"));
        assertTrue(IpMatcher.patronValido("10.92.104.15"));
        assertFalse(IpMatcher.patronValido("10.92.%.1"));
        assertFalse(IpMatcher.patronValido("%"));
        assertFalse(IpMatcher.patronValido("10.92.104.0/33"));
        assertFalse(IpMatcher.patronValido("300.1.1.1"));
    }
}
