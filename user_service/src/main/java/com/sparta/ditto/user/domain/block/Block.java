package com.sparta.ditto.user.domain.block;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.user.domain.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "blocks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_blocks_blocker_blocked",
                columnNames = {"blocker_id", "blocked_id"}
        ),
        indexes = {
                @Index(name = "idx_blocks_blocker_id", columnList = "blocker_id"),
                @Index(name = "idx_blocks_blocked_id", columnList = "blocked_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    private Block(User blocker, User blocked) {
        this.blocker = Objects.requireNonNull(blocker, "blocker must not be null");
        this.blocked = Objects.requireNonNull(blocked, "blocked must not be null");
    }

    public static Block of(User blocker, User blocked) {
        return new Block(blocker, blocked);
    }
}
