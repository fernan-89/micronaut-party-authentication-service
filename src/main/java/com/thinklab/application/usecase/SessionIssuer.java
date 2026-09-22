package com.thinklab.application.usecase;

import com.thinklab.application.dto.response.SessionResponse;
import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.domain.model.User;
import com.thinklab.domain.model.User.UserRole;
import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.kit.security.JwtSigner;
import com.thinklab.kit.security.Role;
import com.thinklab.kit.security.SecurityProperties;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Mints the credentials of a session: an ES256 access token bound to the session id and a fresh opaque refresh token
 * (256 random bits) whose SHA-256 hash is stored. Used by login and by refresh rotation.
 */
@Singleton
public class SessionIssuer {

    private final SessionRepository sessionRepository;
    private final JwtSigner jwtSigner;
    private final SecurityProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public SessionIssuer(SessionRepository sessionRepository, JwtSigner jwtSigner, SecurityProperties properties) {
        this(sessionRepository, jwtSigner, properties, Clock.systemUTC());
    }

    SessionIssuer(SessionRepository sessionRepository, JwtSigner jwtSigner, SecurityProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.jwtSigner = jwtSigner;
        this.properties = properties;
        this.clock = clock;
    }

    /** Issues an access token and a new refresh token for the user within the given session. */
    public Mono<SessionResponse> issue(User user, String sessionId) {
        UserRole userRole = user.getRole() != null ? user.getRole() : UserRole.VIEWER;
        String accessToken = jwtSigner.issue(user.getId().toString(), user.getOrganisationId().toString(), Role.valueOf(userRole.name()), sessionId);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expiresAt = clock.instant().plusSeconds(properties.getRefreshTtlSeconds());
        RefreshTokenRecord record = new RefreshTokenRecord(hash(refreshToken), user.getId(), user.getOrganisationId(), sessionId, expiresAt, false);
        return sessionRepository.saveRefreshToken(record)
                .thenReturn(new SessionResponse(accessToken, "Bearer", jwtSigner.ttlSeconds(), refreshToken, properties.getRefreshTtlSeconds()));
    }

    /** SHA-256 of the opaque token, hex encoded: what is stored and looked up. */
    public static String hash(String refreshToken) {
        return hash(refreshToken, "SHA-256");
    }

    static String hash(String refreshToken, String algorithm) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Digest algorithm is not available in this JVM: " + algorithm, e);
        }
    }
}
