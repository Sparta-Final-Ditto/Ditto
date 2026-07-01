import asyncio
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

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger

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
from app.common.kafka.dlq_producer import DlqProducer
from app.embedding.infrastructure.kafka.post_consumer import PostConsumer
from app.embedding.infrastructure.kafka.user_consumer import UserRegisteredConsumer
from app.embedding.infrastructure.batch.batch_embedding import run_batch, run_monthly_batch
from app.embedding.presentation.router.embedding_router import router as embedding_router
from app.embedding.presentation.router.internal_router import router as internal_router
from app.embedding.presentation.router.test_router import router as test_router

TAGS_METADATA = [
    {
        "name": "Embedding",
        "description": "게시글 임베딩 벡터 생성 및 유저 프로필 관리 API",
    },
    {
        "name": "Internal",
        "description": "서비스 간 내부 통신용 API (match_service OpenFeign)",
    },
    {
        "name": "Health",
        "description": "서비스 상태 확인",
    },
    {
        "name": "Test",
        "description": "개발·검증용 수동 트리거 엔드포인트 (배치 즉시 실행 등)",
    },
]


@asynccontextmanager
async def lifespan(_: FastAPI):
    ModelLoader.load()
    await DlqProducer.start()
    consumer_task = asyncio.create_task(PostConsumer().start())
    user_consumer_task = asyncio.create_task(UserRegisteredConsumer().start())

    scheduler = AsyncIOScheduler()
    scheduler.add_job(run_batch, CronTrigger(hour=3, minute=0, timezone="Asia/Seoul"))
    scheduler.add_job(run_monthly_batch, CronTrigger(day=1, hour=4, minute=0, timezone="Asia/Seoul"))
    scheduler.start()

    yield

    consumer_task.cancel()
    user_consumer_task.cancel()
    await DlqProducer.stop()
    scheduler.shutdown(wait=False)


app = FastAPI(
    title=settings.SERVICE_NAME,
    description="게시글 텍스트를 임베딩 벡터로 변환하고 EMA 기반 유저 프로필을 관리하는 서비스",
    version="0.1.0",
    openapi_tags=TAGS_METADATA,
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

app.add_exception_handler(BusinessException, business_exception_handler)  # type: ignore[arg-type]
app.add_exception_handler(RequestValidationError, validation_exception_handler)  # type: ignore[arg-type]
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
app.include_router(internal_router, prefix="/api/v1/internal/embedding")
app.include_router(test_router, prefix="/api/v1/test")
