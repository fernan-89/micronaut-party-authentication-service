package com.thinklab.domain.port;

/** Outbound port: one-way password hashing. Implementations must be salted and deliberately slow (USR-02). */
public interface PasswordHasher {

    /** Returns a self-describing encoded hash of the raw password. */
    String hash(String rawPassword);

    /** Whether the raw password matches the encoded hash (constant-time comparison). */
    boolean matches(String rawPassword, String encodedHash);
}
