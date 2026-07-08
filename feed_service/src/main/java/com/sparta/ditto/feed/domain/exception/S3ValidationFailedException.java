package com.sparta.ditto.feed.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/**
 * S3 객체의 존재 여부를 <b>확인할 수 없을 때</b> 발생(권한 오류·네트워크 오류 등 일시적 인프라 장애).
 *
 * <p>"파일이 없음"({@link S3ObjectNotFoundException}, 400)과 구분한다. 확인 불가를 파일 없음으로
 * 뭉개면 정상 업로드한 사용자에게 잘못된 메시지가 나가므로, 별도 예외로 분리하여 503으로 매핑한다.</p>
 */
public class S3ValidationFailedException extends BusinessException {

    public S3ValidationFailedException() {
        super(FeedErrorCode.S3_VALIDATION_FAILED);
    }
}