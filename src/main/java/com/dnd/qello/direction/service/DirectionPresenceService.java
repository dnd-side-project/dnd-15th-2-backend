package com.dnd.qello.direction.service;

import com.dnd.qello.account.domain.Account;
import com.dnd.qello.account.domain.AccountRole;
import com.dnd.qello.account.domain.AccountStatus;
import com.dnd.qello.account.repository.AccountRepository;
import com.dnd.qello.direction.config.DirectionPresenceProperties;
import com.dnd.qello.direction.domain.ActiveUserPresence;
import com.dnd.qello.direction.error.DirectionErrorCode;
import com.dnd.qello.direction.error.DirectionException;
import com.dnd.qello.direction.repository.ActiveUserPresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DirectionPresenceService {

    private final AccountRepository accountRepository;
    private final ActiveUserPresenceRepository presenceRepository;
    private final DirectionPresenceProperties properties;
    private final Clock clock;

    @Transactional
    public boolean update(long userId, UpdateCommand command) {
        if (userId <= 0) {
            throw new DirectionException(DirectionErrorCode.INVALID_ID, "userId", "인증 사용자 식별자가 유효하지 않습니다");
        }
        if (command == null) {
            throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, "command", "presence 요청은 필수입니다");
        }

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new DirectionException(DirectionErrorCode.PRESENCE_ACCOUNT_NOT_FOUND, "userId",
                        "위치 정보를 갱신할 계정을 찾을 수 없습니다"));
        if (account.getRole() != AccountRole.USER || account.getStatus() != AccountStatus.ACTIVE) {
            throw new DirectionException(DirectionErrorCode.PRESENCE_ACCOUNT_NOT_ELIGIBLE, "userId",
                    "현재 계정은 위치 정보를 갱신할 수 없습니다");
        }

        validateCommand(command);
        Instant expiresAt = command.observedAt().plus(properties.ttl());
        ActiveUserPresence presence = ActiveUserPresence.create(userId, command.latitude(), command.longitude(), null,
                account.getCoarseRegionCode(), command.accuracyMeters(), command.receiveAllowed(), command.observedAt(), expiresAt);
        return presenceRepository.saveIfNewer(presence);
    }

    private void validateCommand(UpdateCommand command) {
        if (command.latitude() == null || command.longitude() == null || command.accuracyMeters() == null
                || command.receiveAllowed() == null || command.observedAt() == null) {
            throw new DirectionException(DirectionErrorCode.REQUIRED_VALUE_MISSING, null, "presence 필수 값이 없습니다");
        }
        if (command.accuracyMeters().signum() < 0
                || command.accuracyMeters().compareTo(properties.maxAccuracyMeters()) > 0) {
            throw new DirectionException(DirectionErrorCode.INVALID_VALUE_RANGE, "accuracyMeters",
                    "위치 정확도가 허용 범위를 벗어났습니다");
        }

        Instant now = Instant.now(clock);
        if (command.observedAt().isAfter(now.plus(properties.maxFutureSkew()))
                || command.observedAt().isBefore(now.minus(properties.maxObservationAge()))) {
            throw new DirectionException(DirectionErrorCode.INVALID_TIME_ORDER, "observedAt",
                    "위치 관측 시각이 허용 범위를 벗어났습니다");
        }
    }

    public record UpdateCommand(
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            Boolean receiveAllowed,
            Instant observedAt
    ) {
    }
}
