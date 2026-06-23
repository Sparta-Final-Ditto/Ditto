package com.sparta.ditto.user.domain.user;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.enums.LoginProvider;
import com.sparta.ditto.user.domain.user.enums.UserRole;
import com.sparta.ditto.user.domain.user.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_nickname", columnList = "nickname")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 255)
    private String password;

    @Column(nullable = false, unique = true, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(length = 8)
    private String birthdate;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(length = 200)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "login_provider", nullable = false, length = 20)
    private LoginProvider loginProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    private User(String email, String password, String nickname,
                 Gender gender, String birthdate, LoginProvider loginProvider) {
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.password = password;
        this.nickname = Objects.requireNonNull(nickname, "nickname must not be null");
        this.gender = Objects.requireNonNull(gender, "gender must not be null");
        this.birthdate = birthdate;
        this.loginProvider = Objects.requireNonNull(
                loginProvider, "loginProvider must not be null");
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    public static User createEmailUser(String email, String encodedPassword,
                                       String nickname, Gender gender, String birthdate) {
        return new User(email, encodedPassword, nickname, gender, birthdate, LoginProvider.EMAIL);
    }

    public static User createOAuthUser(String email, String nickname,
                                       Gender gender, String birthdate, LoginProvider provider) {
        return new User(email, null, nickname, gender, birthdate, provider);
    }

    public void updateLastLoginAt() {
        this.lastLoginAt = Instant.now();
    }

    public void updateProfile(String nickname, String bio, String profileImageUrl) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
        if (bio != null) {
            this.bio = bio;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }
}
