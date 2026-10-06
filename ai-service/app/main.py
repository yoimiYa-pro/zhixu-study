import logging
from fastapi import FastAPI
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from fastapi.exception_handlers import request_validation_exception_handler
from app.api.routes import router
from app.core.errors import ServiceError
from app.core.usage import UsageMiddleware

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
app = FastAPI(title="公考 AI 服务", version="1.0.0", docs_url=None, redoc_url=None)
logging.getLogger("httpx").setLevel(logging.WARNING)
logging.getLogger("httpcore").setLevel(logging.WARNING)
app.include_router(router)
app.add_middleware(UsageMiddleware)


@app.exception_handler(RequestValidationError)
async def validation_error(request, error):
    if request.url.path.startswith("/ai/models/connections") or request.headers.get("x-study-api-key"):
        return JSONResponse(status_code=400, content={"code": "AI_MODEL_INVALID", "message": "请检查模型接入的字段格式"})
    return await request_validation_exception_handler(request, error)


@app.exception_handler(ServiceError)
async def service_error(request, error: ServiceError):
    logging.getLogger("ai-service").warning("request_failed code=%s status=%s", error.code, error.status)
    return JSONResponse(status_code=error.status, content={"code": error.code, "message": error.message})


@app.get("/health")
def health():
    return {"status": "UP", "service": "ai-service", "version": "1.0.0"}
