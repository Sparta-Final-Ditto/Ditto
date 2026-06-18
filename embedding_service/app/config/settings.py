from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    SERVICE_NAME: str = "embedding-service"
    EMBEDDING_MODEL_NAME: str = "jhgan/ko-sroberta-multitask"
    EMA_ALPHA: float = 0.1

    class Config:
        env_file = ".env"


settings = Settings()
