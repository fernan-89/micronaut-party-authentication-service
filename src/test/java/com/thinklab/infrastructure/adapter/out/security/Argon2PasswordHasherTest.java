package com.thinklab.infrastructure.adapter.out.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher(64, 1, 1);

    @Test
    @DisplayName("a hash verifies the original password and rejects any other")
    void roundTrip() {
        String hash = hasher.hash("correct horse battery staple");

        assertTrue(hash.startsWith("$argon2id$v=19$m=64,t=1,p=1$"));
        assertTrue(hasher.matches("correct horse battery staple", hash));
        assertFalse(hasher.matches("Correct horse battery staple", hash));
    }

    @Test
    @DisplayName("every hash uses a fresh salt")
    void saltIsRandom() {
        assertNotEquals(hasher.hash("same-password-123"), hasher.hash("same-password-123"));
    }

    @Test
    @DisplayName("the default constructor uses the OWASP baseline parameters")
    void defaultParameters() {
        Argon2PasswordHasher defaults = new Argon2PasswordHasher();

        String hash = defaults.hash("a-long-enough-password");

        assertTrue(hash.contains("m=19456,t=2,p=1"));
        assertTrue(defaults.matches("a-long-enough-password", hash));
    }

    @Test
    @DisplayName("parameters embedded in the hash are honoured, so hashes made with other costs still verify")
    void embeddedParameters() {
        String hash = new Argon2PasswordHasher(128, 2, 1).hash("portable-password-1");

        assertTrue(hasher.matches("portable-password-1", hash));
    }

    @Test
    @DisplayName("null and malformed encodings never match and never throw")
    void malformed() {
        String valid = hasher.hash("some-password-value");

        assertFalse(hasher.matches(null, valid));
        assertFalse(hasher.matches("x", null));
        assertFalse(hasher.matches("x", "plaintext"));
        assertFalse(hasher.matches("x", valid.replace("argon2id", "argon2i")));
        assertFalse(hasher.matches("x", valid.replace("v=19", "v=16")));
        assertFalse(hasher.matches("x", valid.replace("m=64", "m=abc")));
        assertFalse(hasher.matches("x", valid.replace("t=1", "t")));
        assertFalse(hasher.matches("x", "$argon2id$v=19$m=64,t=1,p=1$!!!$###"));
    }

    @Test
    @DisplayName("hashing rejects a null password")
    void nullPassword() {
        assertThrows(NullPointerException.class, () -> hasher.hash(null));
    }
}
