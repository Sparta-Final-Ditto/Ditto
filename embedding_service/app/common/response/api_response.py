from typing import Generic, TypeVar
from pydantic import BaseModel

T = TypeVar("T")


class ApiResponse(BaseModel, Generic[T]):
    status: int
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
    def error(cls, status: int, message: str, errors: list[str] | None = None) -> "ApiResponse[None]":
        return cls(status=status, message=message, errors=errors)
