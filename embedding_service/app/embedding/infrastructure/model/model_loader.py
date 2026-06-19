import logging
import numpy as np
from sentence_transformers import SentenceTransformer
from app.config.settings import settings
from app.embedding.application.port.embedding_model_port import EmbeddingModelPort

logger = logging.getLogger("embedding_service")


class ModelLoader(EmbeddingModelPort):
    _model: SentenceTransformer | None = None

    @classmethod
    def load(cls) -> None:
        if cls._model is None:
            logger.info(f"임베딩 모델 로딩 중: {settings.EMBEDDING_MODEL_NAME}")
            cls._model = SentenceTransformer(settings.EMBEDDING_MODEL_NAME)
            logger.info("임베딩 모델 로드 완료")

    @classmethod
    def get_model(cls) -> SentenceTransformer:
        if cls._model is None:
            cls.load()
        assert cls._model is not None
        return cls._model

    def encode(self, text: str) -> list[float]:
        return np.array(self.get_model().encode(text)).tolist()
