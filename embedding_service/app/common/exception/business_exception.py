from app.common.exception.error_code import ErrorCode


class BusinessException(Exception):
    def __init__(self, error_code: ErrorCode) -> None:
        super().__init__(error_code.message)
        self.error_code = error_code
