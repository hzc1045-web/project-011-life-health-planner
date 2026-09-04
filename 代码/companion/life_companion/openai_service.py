from __future__ import annotations

import json
from typing import Any, Protocol, TypeVar

from fastapi import HTTPException
from openai import OpenAI
from pydantic import BaseModel, ValidationError

from .credential_store import CredentialStore
from .models import ChatReply, ChatRequest, PlanDraft, PlanRequest, RiskLevel
from .risk import detect_risk, urgent_message
from .settings import Settings

T = TypeVar("T", bound=BaseModel)


class ResponsesClient(Protocol):
    def create(self, **kwargs: Any) -> Any: ...


class ChatCompletionsClient(Protocol):
    def create(self, **kwargs: Any) -> Any: ...


class OpenAIService:
    def __init__(
        self,
        settings: Settings,
        credential_store: CredentialStore,
        responses_client: ResponsesClient | None = None,
        chat_completions_client: ChatCompletionsClient | None = None,
    ) -> None:
        self.settings = settings
        self.credential_store = credential_store
        self._responses_client = responses_client
        self._chat_completions_client = chat_completions_client

    def is_configured(self) -> bool:
        return bool(self.credential_store.get_api_key(self.settings.active_provider))

    def _api_key(self) -> str:
        api_key = self.credential_store.get_api_key(self.settings.active_provider)
        if not api_key:
            raise HTTPException(
                status_code=503,
                detail=f"{self.settings.provider.display_name} API 密钥尚未配置",
            )
        return api_key

    def _responses(self) -> ResponsesClient:
        if self._responses_client is not None:
            return self._responses_client
        return OpenAI(
            api_key=self._api_key(),
            base_url=self.settings.provider.base_url,
            timeout=45,
            max_retries=1,
        ).responses

    def _chat_completions(self) -> ChatCompletionsClient:
        if self._chat_completions_client is not None:
            return self._chat_completions_client
        return OpenAI(
            api_key=self._api_key(),
            base_url=self.settings.provider.base_url,
            timeout=45,
            max_retries=1,
        ).chat.completions

    @staticmethod
    def _format(model: type[T], name: str) -> dict[str, object]:
        return {
            "type": "json_schema",
            "name": name,
            "strict": True,
            "schema": model.model_json_schema(),
        }

    @staticmethod
    def _parse_text(output_text: str | None, model: type[T]) -> T:
        if not output_text:
            raise HTTPException(status_code=502, detail="AI 没有返回可解析内容")
        try:
            return model.model_validate(json.loads(output_text))
        except (json.JSONDecodeError, ValidationError) as error:
            raise HTTPException(status_code=502, detail="AI 返回内容未通过结构校验") from error

    def _structured_response(
        self,
        *,
        model_name: str,
        instructions: str,
        input_text: str,
        output_model: type[T],
        schema_name: str,
    ) -> T:
        if self.settings.provider.api_style == "responses":
            response = self._responses().create(
                model=model_name,
                store=False,
                instructions=instructions,
                input=input_text,
                text={"format": self._format(output_model, schema_name)},
            )
            return self._parse_text(getattr(response, "output_text", None), output_model)

        schema = json.dumps(output_model.model_json_schema(), ensure_ascii=False)
        response = self._chat_completions().create(
            model=model_name,
            messages=[
                {
                    "role": "system",
                    "content": (
                        f"{instructions}\n只输出一个 JSON 对象，不要输出 Markdown。"
                        f"输出必须符合此 JSON Schema：{schema}"
                    ),
                },
                {"role": "user", "content": input_text},
            ],
            response_format={"type": "json_object"},
            stream=False,
        )
        choices = getattr(response, "choices", None)
        output_text = None
        if choices:
            output_text = getattr(getattr(choices[0], "message", None), "content", None)
        return self._parse_text(output_text, output_model)

    def create_plan(self, request: PlanRequest) -> PlanDraft:
        risk_text = " ".join(request.context.health_constraints + [request.focus])
        if detect_risk(risk_text) is RiskLevel.URGENT:
            return PlanDraft(
                title="需要优先处理健康风险",
                summary="当前不生成普通生活计划。",
                rationale=[],
                risk_level=RiskLevel.URGENT,
                risk_message=urgent_message(request.context.emergency_number),
                items=[],
                review_questions=[],
            )
        return self._structured_response(
            model_name=self.settings.planning_model,
            instructions=(
                "你是非医疗诊断性质的中文生活规划助手。健康安全、固定日程和预算是硬约束。"
                "不要诊断疾病、开药或调整剂量。输出严格符合给定 JSON Schema。"
                "健康、事业、学习优先，同时保留合理休息。不得安排与 busy_blocks 重叠的事项。"
            ),
            input_text=request.model_dump_json(),
            output_model=PlanDraft,
            schema_name="life_plan_draft",
        )

    def chat(self, request: ChatRequest) -> ChatReply:
        risk = detect_risk(" ".join(request.context.health_constraints + [request.message]))
        if risk is RiskLevel.URGENT:
            message = urgent_message(request.context.emergency_number)
            return ChatReply(
                reply=message,
                risk_level=RiskLevel.URGENT,
                risk_message=message,
                suggested_actions=[],
            )
        return self._structured_response(
            model_name=self.settings.economy_model,
            instructions=(
                "你是中文生活规划助手，不提供医疗诊断、处方、剂量调整或专业投资指令。"
                "建议转换成日程前必须要求用户确认。输出严格符合给定 JSON Schema。"
            ),
            input_text=request.model_dump_json(),
            output_model=ChatReply,
            schema_name="life_planner_chat_reply",
        )
