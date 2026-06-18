from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    SERVICE_NAME: str = "embedding-service"
    EMBEDDING_MODEL_NAME: str = "jhgan/ko-sroberta-multitask"

    class Config:
        env_file = ".env"


settings = Settings()
