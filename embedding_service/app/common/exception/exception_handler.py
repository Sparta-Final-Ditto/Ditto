from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.common.exception.business_exception import BusinessException
from app.common.exception.error_code import CommonErrorCode
from app.common.response.api_response import ApiResponse


async def business_exception_handler(request: Request, exc: BusinessException) -> JSONResponse:
    ec = exc.error_code
    body = ApiResponse.error(ec.status, f"[{ec.code}] {ec.message}")
    return JSONResponse(status_code=ec.status, content=body.model_dump())


async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    ec = CommonErrorCode.INVALID_INPUT
    errors = [
        f"{'.'.join(str(loc) for loc in e['loc'])}: {e['msg']}"
        for e in exc.errors()
    ]
    body = ApiResponse.error(ec.status, f"[{ec.code}] {ec.message}", errors)
    return JSONResponse(status_code=ec.status, content=body.model_dump())


async def unhandled_exception_handler(request: Request, exc: Exception) -> JSONResponse:
    ec = CommonErrorCode.INTERNAL_SERVER_ERROR
    body = ApiResponse.error(ec.status, f"[{ec.code}] {ec.message}")
    return JSONResponse(status_code=ec.status, content=body.model_dump())
