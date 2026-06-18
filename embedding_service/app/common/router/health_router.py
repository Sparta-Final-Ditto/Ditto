from fastapi import APIRouter

router = APIRouter(tags=["Health"])


@router.get("", summary="헬스체크")
async def health():
    return {"status": "ok"}
