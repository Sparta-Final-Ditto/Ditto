package com.sparta.ditto.user.domain.report;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.user.domain.report.enums.ReportType;
import com.sparta.ditto.user.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Entity
@Table(
        name = "reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reports_reporter_reported",
                columnNames = {"reporter_id", "reported_id"}
        ),
        indexes = {
                @Index(name = "idx_reports_reporter_id", columnList = "reporter_id"),
                @Index(name = "idx_reports_reported_id", columnList = "reported_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_id", nullable = false)
    private User reported;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @Column(columnDefinition = "TEXT")
    private String content;

    private Report(User reporter, User reported, ReportType reportType, String content) {
        this.reporter = Objects.requireNonNull(reporter, "reporter must not be null");
        this.reported = Objects.requireNonNull(reported, "reported must not be null");
        this.reportType = Objects.requireNonNull(reportType, "reportType must not be null");
        this.content = content;
    }

    public static Report of(User reporter, User reported, ReportType reportType, String content) {
        return new Report(reporter, reported, reportType, content);
    }
}
