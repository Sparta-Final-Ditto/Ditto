import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from starlette.middleware.base import BaseHTTPMiddleware

logging.basicConfig(
    level=logging.INFO,
    format="%(levelname)s: %(message)s",
)

from app.config.settings import settings
from app.common.exception.business_exception import BusinessException
from app.common.exception.exception_handler import (
    business_exception_handler,
    validation_exception_handler,
    unhandled_exception_handler,
)
from app.common.router.health_router import router as health_router
from app.common.middleware.logging_middleware import logging_middleware
from app.embedding.infrastructure.model.model_loader import ModelLoader
from app.embedding.presentation.router.embedding_router import router as embedding_router

TAGS_METADATA = [
    {
        "name": "Embedding",
        "description": "게시글 임베딩 벡터 생성 및 유저 프로필 관리 API",
    },
    {
        "name": "Health",
        "description": "서비스 상태 확인",
    },
]


@asynccontextmanager
async def lifespan(app: FastAPI):
    ModelLoader.load()
    # TODO: DB 초기화, Kafka Consumer, Scheduler 순서로 추가
    yield


app = FastAPI(
    title=settings.SERVICE_NAME,
    description="게시글 텍스트를 임베딩 벡터로 변환하고 EMA 기반 유저 프로필을 관리하는 서비스",
    version="0.1.0",
    openapi_tags=TAGS_METADATA,
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

app.add_exception_handler(BusinessException, business_exception_handler)
app.add_exception_handler(RequestValidationError, validation_exception_handler)
app.add_exception_handler(Exception, unhandled_exception_handler)

app.add_middleware(BaseHTTPMiddleware, dispatch=logging_middleware)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router, prefix="/health")
app.include_router(embedding_router, prefix="/api/v1/embedding")
