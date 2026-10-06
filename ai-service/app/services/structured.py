from pathlib import Path
from pydantic import ValidationError
from app.core.errors import ServiceError
from app.providers.base import LLMProvider

PROMPTS = Path(__file__).resolve().parents[1] / "prompts"


async def structured(provider: LLMProvider, prompt_name: str, payload: dict, output_type):
    prompt = (PROMPTS / prompt_name).read_text(encoding="utf-8")
    result = await provider.complete(prompt, payload, output_type.model_json_schema())
    try:
        return output_type.model_validate(result)
    except ValidationError:
        raise ServiceError("AI_INVALID_OUTPUT", "模型输出未通过结构校验", 502) from None
