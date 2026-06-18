import logging
from sentence_transformers import SentenceTransformer
from app.config.settings import settings

logger = logging.getLogger("embedding_service")


class ModelLoader:
    _model: SentenceTransformer | None = None

    @classmethod
    def load(cls):
        if cls._model is None:
            logger.info(f"임베딩 모델 로딩 중: {settings.EMBEDDING_MODEL_NAME}")
            cls._model = SentenceTransformer(settings.EMBEDDING_MODEL_NAME)
            logger.info("임베딩 모델 로드 완료")

    @classmethod
    def get_model(cls) -> SentenceTransformer:
        if cls._model is None:
            cls.load()
        return cls._model
