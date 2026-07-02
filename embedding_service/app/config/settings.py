from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    SERVICE_NAME: str = "embedding-service"
    DATABASE_URL: str = "postgresql+asyncpg://embedding_user:embedding_pwd@localhost:5434/embedding_db"
    EMBEDDING_MODEL_NAME: str = "jhgan/ko-sroberta-multitask"
    EMA_ALPHA: float = 0.1
    MIN_RECORDS_FOR_MATCHING: int = 3
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_TOPIC_POST_EVENTS: str = "post-events"
    KAFKA_TOPIC_USER_EVENTS: str = "USER_REGISTERED"
    KAFKA_TOPIC_DLQ: str = "post-events-dlq"
    KAFKA_CONSUMER_GROUP: str = "embedding-service-group"
    KAFKA_TOPIC_PROFILE_EMBEDDING_UPDATED: str = "profile-embedding-updated"
    KAFKA_TOPIC_PROFILE_EMBEDDING_BULK_COMPLETED: str = "profile-embedding-bulk-completed"
    PROFILE_SYNC_BULK_THRESHOLD: int = 1000

    class Config:
        env_file = ".env"


settings = Settings()
