package com.sparta.ditto.user.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.sparta.ditto.user.domain.report.Report;
import com.sparta.ditto.user.domain.report.enums.ReportType;
import com.sparta.ditto.user.domain.report.exception.AlreadyReportedException;
import com.sparta.ditto.user.domain.report.exception.CannotSelfReportException;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.ReportRepository;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.presentation.dto.request.UserReportRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @InjectMocks
    private ReportService reportService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    private User reporter;
    private User reported;
    private UUID reporterId;
    private UUID reportedId;

    @BeforeEach
    void setUp() {
        reporterId = UUID.randomUUID();
        reportedId = UUID.randomUUID();
        reporter = User.createEmailUser(
                "reporter@test.com", "encodedPw", "reporterNick", Gender.MALE, "19900101");
        reported = User.createEmailUser(
                "reported@test.com", "encodedPw", "reportedNick", Gender.FEMALE, "19950101");
        ReflectionTestUtils.setField(reporter, "id", reporterId);
        ReflectionTestUtils.setField(reported, "id", reportedId);
    }

    @Nested
    class DoReport {

        @Test
        void 성공() {
            UserReportRequest request = new UserReportRequest(ReportType.SPAM, "스팸 계정입니다.");
            given(reportRepository.existsByReporterIdAndReportedId(reporterId, reportedId))
                    .willReturn(false);
            given(userRepository.findById(reporterId)).willReturn(Optional.of(reporter));
            given(userRepository.findById(reportedId)).willReturn(Optional.of(reported));

            reportService.report(reporterId, reportedId, request);

            then(reportRepository).should().save(any(Report.class));
        }

        @Test
        void 자기_자신_신고_예외() {
            UserReportRequest request = new UserReportRequest(ReportType.SPAM, null);

            assertThatThrownBy(() -> reportService.report(reporterId, reporterId, request))
                    .isInstanceOf(CannotSelfReportException.class);
        }

        @Test
        void 이미_신고_예외() {
            UserReportRequest request = new UserReportRequest(ReportType.HATE_SPEECH, null);
            given(reportRepository.existsByReporterIdAndReportedId(reporterId, reportedId))
                    .willReturn(true);

            assertThatThrownBy(() -> reportService.report(reporterId, reportedId, request))
                    .isInstanceOf(AlreadyReportedException.class);
        }

        @Test
        void 신고자_유저_없음_예외() {
            UserReportRequest request = new UserReportRequest(ReportType.VIOLENCE, null);
            given(reportRepository.existsByReporterIdAndReportedId(reporterId, reportedId))
                    .willReturn(false);
            given(userRepository.findById(reporterId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.report(reporterId, reportedId, request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 피신고자_유저_없음_예외() {
            UserReportRequest request = new UserReportRequest(ReportType.OTHER, null);
            given(reportRepository.existsByReporterIdAndReportedId(reporterId, reportedId))
                    .willReturn(false);
            given(userRepository.findById(reporterId)).willReturn(Optional.of(reporter));
            given(userRepository.findById(reportedId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.report(reporterId, reportedId, request))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }
}