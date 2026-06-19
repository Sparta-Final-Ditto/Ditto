from enum import Enum


class ErrorCode(Enum):
    @property
    def code(self) -> str:
        return self.value[0]

    @property
    def message(self) -> str:
        return self.value[1]

    @property
    def status(self) -> int:
        return self.value[2]


class CommonErrorCode(ErrorCode):
    INVALID_INPUT          = ("COMMON-001", "입력값이 올바르지 않습니다.", 400)
    UNAUTHORIZED           = ("COMMON-002", "인증이 필요합니다.", 401)
    FORBIDDEN              = ("COMMON-003", "접근 권한이 없습니다.", 403)
    NOT_FOUND              = ("COMMON-004", "요청한 리소스를 찾을 수 없습니다.", 404)
    INTERNAL_SERVER_ERROR  = ("COMMON-005", "서버 내부 오류가 발생했습니다.", 500)


class EmbeddingErrorCode(ErrorCode):
    EMBEDDING_NOT_FOUND = ("EMBED-001", "해당 게시글의 임베딩 정보를 찾을 수 없습니다.", 404)
    EMBEDDING_FAILED    = ("EMBED-002", "임베딩 처리 중 오류가 발생했습니다.", 500)
    ALREADY_EMBEDDED    = ("EMBED-003", "이미 처리된 임베딩입니다.", 409)
    PROFILE_NOT_FOUND   = ("EMBED-004", "해당 유저의 프로필 임베딩 정보를 찾을 수 없습니다.", 404)
