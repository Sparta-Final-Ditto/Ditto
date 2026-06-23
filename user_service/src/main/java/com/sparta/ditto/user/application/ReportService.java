package com.sparta.ditto.user.application;

import com.sparta.ditto.user.domain.report.Report;
import com.sparta.ditto.user.domain.report.exception.AlreadyReportedException;
import com.sparta.ditto.user.domain.report.exception.CannotSelfReportException;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.ReportRepository;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.presentation.dto.request.UserReportRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @Transactional
    public void report(UUID reporterId, UUID reportedId, UserReportRequest request) {
        if (reporterId.equals(reportedId)) {
            throw new CannotSelfReportException();
        }
        if (reportRepository.existsByReporterIdAndReportedId(reporterId, reportedId)) {
            throw new AlreadyReportedException();
        }

        User reporter = userRepository.findById(reporterId).orElseThrow(UserNotFoundException::new);
        User reported = userRepository.findById(reportedId).orElseThrow(UserNotFoundException::new);

        reportRepository.save(
                Report.of(reporter, reported, request.reportType(), request.content()));
    }
}
