package com.sparta.ditto.notification.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_notifications_type_target_id_receiver_id",
                        columnNames = {"type", "target_id", "receiver_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_notifications_receiver_id_created_at_id",
                        columnList = "receiver_id, created_at DESC, id DESC"
                )
        }
)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID receiverId;

    @Column(columnDefinition = "uuid")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TargetType targetType;

    @Column(nullable = false, updatable = false)
    private String targetId;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean isRead;

    @Column
    private String metaData;

    public static Notification create(
            UUID receiverId,
            UUID actorId,
            NotificationType type,
            TargetType targetType,
            String targetId,
            String message,
            String metaData
    ) {
        Notification notification = new Notification();
        notification.receiverId = receiverId;
        notification.actorId = actorId;
        notification.type = type;
        notification.targetType = targetType;
        notification.targetId = targetId;
        notification.message = message;
        notification.metaData = metaData;
        return notification;
    }

    public void read() {
        this.isRead = true;
    }
}