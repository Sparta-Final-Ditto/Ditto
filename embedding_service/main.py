from contextlib import asynccontextmanager
from fastapi import FastAPI


@asynccontextmanager
async def lifespan(app: FastAPI):
    yield


app = FastAPI(title="Ditto Embedding Service", version="0.0.1", lifespan=lifespan)


@app.get("/health")
async def health():
    return {"status": "ok"}
