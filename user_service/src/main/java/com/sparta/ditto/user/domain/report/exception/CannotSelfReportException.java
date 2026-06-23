package com.sparta.ditto.user.domain.report.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class CannotSelfReportException extends BusinessException {

    public CannotSelfReportException() {
        super(ReportErrorCode.CANNOT_SELF_REPORT);
    }
}