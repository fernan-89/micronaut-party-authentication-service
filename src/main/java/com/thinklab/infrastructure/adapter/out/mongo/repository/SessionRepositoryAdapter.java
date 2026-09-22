package com.thinklab.infrastructure.adapter.out.mongo.repository;

import com.thinklab.domain.model.RefreshTokenRecord;
import com.thinklab.domain.model.RevokedSession;
import com.thinklab.domain.repository.SessionRepository;
import com.thinklab.infrastructure.adapter.out.mongo.entity.RefreshTokenEntity;
import com.thinklab.infrastructure.adapter.out.mongo.entity.RevokedSessionEntity;
import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Mongo adapter for refresh tokens and revoked sessions. */
@Singleton
public class SessionRepositoryAdapter implements SessionRepository {

    private final RefreshTokenMongoRepository refreshTokens;
    private final RevokedSessionMongoRepository revokedSessions;

    public SessionRepositoryAdapter(RefreshTokenMongoRepository refreshTokens, RevokedSessionMongoRepository revokedSessions) {
        this.refreshTokens = Objects.requireNonNull(refreshTokens, "Infrastructure constraint violated: RefreshTokenMongoRepository cannot be null.");
        this.revokedSessions = Objects.requireNonNull(revokedSessions, "Infrastructure constraint violated: RevokedSessionMongoRepository cannot be null.");
    }

    @Override
    public Mono<Void> saveRefreshToken(RefreshTokenRecord record) {
        Objects.requireNonNull(record, "Infrastructure constraint violated: Refresh token record is mandatory.");
        return refreshTokens.save(RefreshTokenEntity.fromDomain(record)).then();
    }

    @Override
    public Mono<RefreshTokenRecord> findRefreshToken(String tokenHash) {
        Objects.requireNonNull(tokenHash, "Infrastructure constraint violated: Token hash is mandatory.");
        return refreshTokens.findById(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public Mono<Boolean> markRefreshTokenUsed(String tokenHash) {
        Objects.requireNonNull(tokenHash, "Infrastructure constraint violated: Token hash is mandatory.");
        return refreshTokens.findById(tokenHash)
                .flatMap(entity -> entity.used()
                        ? Mono.just(false)
                        : refreshTokens.update(entity.asUsed()).thenReturn(true))
                // A concurrent rotation loses the optimistic-lock race: only one caller may win.
                .onErrorReturn(false)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> revokeSession(String sessionId, Instant revokedAt) {
        Objects.requireNonNull(sessionId, "Infrastructure constraint violated: Session ID is mandatory.");
        Objects.requireNonNull(revokedAt, "Infrastructure constraint violated: Revocation time is mandatory.");
        return revokedSessions.existsById(sessionId)
                .flatMap(exists -> exists ? Mono.<RevokedSessionEntity>empty() : revokedSessions.save(new RevokedSessionEntity(sessionId, revokedAt)))
                .then();
    }

    @Override
    public Mono<Boolean> isSessionRevoked(String sessionId) {
        Objects.requireNonNull(sessionId, "Infrastructure constraint violated: Session ID is mandatory.");
        return revokedSessions.existsById(sessionId);
    }

    @Override
    public Flux<String> findSessionIdsByUserId(UUID userId) {
        Objects.requireNonNull(userId, "Infrastructure constraint violated: User ID is mandatory.");
        return refreshTokens.findByUserId(userId).map(RefreshTokenEntity::sessionId).distinct();
    }

    @Override
    public Flux<RevokedSession> findRevokedSince(Instant since) {
        Objects.requireNonNull(since, "Infrastructure constraint violated: Instant is mandatory.");
        return revokedSessions.findByRevokedAtGreaterThanEquals(since).map(RevokedSessionEntity::toDomain);
    }
}
