package com.thinklab.infrastructure.adapter.out.security;

import com.thinklab.domain.port.PasswordHasher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Argon2id password hashing (RFC 9106) with a random 16-byte salt. The encoded form is
 * {@code $argon2id$v=19$m=<KiB>,t=<iterations>,p=<lanes>$<salt>$<hash>} (unpadded Base64), so the parameters
 * travel with the hash and can be raised later without invalidating existing credentials.
 */
@Singleton
public class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final SecureRandom random = new SecureRandom();

    /** OWASP-recommended baseline: 19 MiB, 2 iterations, 1 lane. */
    @Inject
    public Argon2PasswordHasher() {
        this(19456, 2, 1);
    }

    Argon2PasswordHasher(int memoryKiB, int iterations, int parallelism) {
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    @Override
    public String hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "Password cannot be null.");
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = derive(rawPassword, salt, memoryKiB, iterations, parallelism);
        return "$argon2id$v=19$m=" + memoryKiB + ",t=" + iterations + ",p=" + parallelism
                + "$" + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(digest);
    }

    @Override
    public boolean matches(String rawPassword, String encodedHash) {
        if (rawPassword == null || encodedHash == null) {
            return false;
        }
        String[] parts = encodedHash.split("\\$");
        if (parts.length != 6 || !"argon2id".equals(parts[1]) || !"v=19".equals(parts[2])) {
            return false;
        }
        try {
            String[] cost = parts[3].split(",");
            int m = Integer.parseInt(cost[0].substring(2));
            int t = Integer.parseInt(cost[1].substring(2));
            int p = Integer.parseInt(cost[2].substring(2));
            byte[] salt = DECODER.decode(parts[4]);
            byte[] expected = DECODER.decode(parts[5]);
            return MessageDigest.isEqual(expected, derive(rawPassword, salt, m, t, p));
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static byte[] derive(String rawPassword, byte[] salt, int memoryKiB, int iterations, int parallelism) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .withSalt(salt)
                .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] out = new byte[HASH_BYTES];
        generator.generateBytes(rawPassword.getBytes(StandardCharsets.UTF_8), out);
        return out;
    }
}
