from typing import Generic, TypeVar
from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    status: int
    code: str | None = None
    message: str
    data: T | None = None
    errors: list[str] | None = None

    @classmethod
    def success(cls, data: T) -> "ApiResponse[T]":
        return cls(status=200, message="SUCCESS", data=data)

    @classmethod
    def success_no_content(cls) -> "ApiResponse[None]":
        return cls(status=200, message="SUCCESS")

    @classmethod
    def created(cls, data: T) -> "ApiResponse[T]":
        return cls(status=201, message="CREATED", data=data)

    @classmethod
    def updated(cls, data: T) -> "ApiResponse[T]":
        return cls(status=200, message="UPDATED", data=data)

    @classmethod
    def deleted(cls) -> "ApiResponse[None]":
        return cls(status=200, message="DELETED")

    @classmethod
    def accepted(cls) -> "ApiResponse[None]":
        return cls(status=202, message="ACCEPTED")

    @classmethod
    def error(cls, status: int, code: str, message: str, errors: list[str] | None = None) -> "ApiResponse[None]":
        return cls(status=status, code=code, message=message, errors=errors)
