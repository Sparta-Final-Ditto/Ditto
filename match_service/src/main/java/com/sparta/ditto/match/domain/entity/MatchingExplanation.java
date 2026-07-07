package com.sparta.ditto.match.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "matching_explanations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchingExplanation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID matchedUserId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanationText;

    public static MatchingExplanation of(
            UUID userId,
            UUID matchedUserId,
            String explanationText
    ) {
        MatchingExplanation explanation = new MatchingExplanation();
        explanation.userId = userId;
        explanation.matchedUserId = matchedUserId;
        explanation.explanationText = explanationText;
        return explanation;
    }
}