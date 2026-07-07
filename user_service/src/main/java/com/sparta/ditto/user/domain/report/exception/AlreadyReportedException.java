package com.sparta.ditto.user.domain.report.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class AlreadyReportedException extends BusinessException {

    public AlreadyReportedException() {
        super(ReportErrorCode.ALREADY_REPORTED);
    }
}
