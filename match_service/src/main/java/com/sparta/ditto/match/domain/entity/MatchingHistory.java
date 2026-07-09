package com.sparta.ditto.match.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.match.application.dto.MatchResponseDto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matching_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingHistory extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID matchedUserId;

    @Column(nullable = false)
    private Float similarityScore;

    @Column(nullable = false)
    private Float finalScore;

    @Column(nullable = false)
    private Instant matchedAt;

    @Column(nullable = false, length = 10)
    private String genderFilter;

    @Column(nullable = false)
    private Boolean locationFilterOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MatchStatus status;

    public static MatchingHistory of(
            UUID userId,
            UUID matchedUserId,
            Float similarityScore,
            Float finalScore,
            String genderFilter,
            Boolean locationFilterOn
    ) {
        MatchingHistory history = new MatchingHistory();
        history.userId = userId;
        history.matchedUserId = matchedUserId;
        history.similarityScore = similarityScore;
        history.finalScore = finalScore;
        history.genderFilter = genderFilter;
        history.locationFilterOn = locationFilterOn;
        history.status = MatchStatus.PENDING;
        history.matchedAt = Instant.now();
        return history;
    }

    public void accept() {
        this.status = MatchStatus.ACCEPTED;
    }

    public void reject() {
        this.status = MatchStatus.REJECTED;
    }

    public MatchResponseDto toDto(String explanation) {
        return new MatchResponseDto(
                this.getId(),
                this.matchedUserId,
                this.similarityScore,
                this.finalScore,
                this.matchedAt,
                this.status,
                explanation
        );
    }

}

