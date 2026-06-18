from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    SERVICE_NAME: str = "embedding-service"

    class Config:
        env_file = ".env"


settings = Settings()
