from __future__ import annotations

import json
from typing import Any, Protocol, TypeVar

from fastapi import HTTPException
from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI
from pydantic import BaseModel, ValidationError

from .credential_store import CredentialStore
from .models import ChatReply, ChatRequest, PlanDraft, PlanRequest, RiskLevel
from .risk import detect_risk, urgent_message
from .settings import Settings

T = TypeVar("T", bound=BaseModel)
AI_REQUEST_TIMEOUT_SECONDS = 180
MAX_OUTPUT_TOKENS = 8192


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
            timeout=AI_REQUEST_TIMEOUT_SECONDS,
            max_retries=0,
        ).responses

    def _chat_completions(self) -> ChatCompletionsClient:
        if self._chat_completions_client is not None:
            return self._chat_completions_client
        return OpenAI(
            api_key=self._api_key(),
            base_url=self.settings.provider.base_url,
            timeout=AI_REQUEST_TIMEOUT_SECONDS,
            max_retries=0,
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
        try:
            if self.settings.provider.api_style == "responses":
                response = self._responses().create(
                    model=model_name,
                    store=False,
                    instructions=instructions,
                    input=input_text,
                    text={"format": self._format(output_model, schema_name)},
                    max_output_tokens=MAX_OUTPUT_TOKENS,
                )
                return self._parse_text(
                    getattr(response, "output_text", None), output_model
                )

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
                max_tokens=MAX_OUTPUT_TOKENS,
                extra_body={"thinking": {"type": "disabled"}},
                stream=False,
            )
        except APITimeoutError as error:
            raise HTTPException(status_code=504, detail="AI 响应超时，请稍后重试") from error
        except APIConnectionError as error:
            raise HTTPException(status_code=503, detail="无法连接当前 AI 提供商") from error
        except APIStatusError as error:
            if error.status_code in {401, 403}:
                detail = "当前 AI 提供商拒绝了 API 密钥"
            elif error.status_code in {402, 429}:
                detail = "当前 AI 额度不足或请求过于频繁"
            else:
                detail = "当前 AI 提供商请求失败"
            raise HTTPException(status_code=503, detail=detail) from error
        choices = getattr(response, "choices", None)
        output_text = None
        finish_reason = None
        if choices:
            choice = choices[0]
            finish_reason = getattr(choice, "finish_reason", None)
            output_text = getattr(getattr(choice, "message", None), "content", None)
        if finish_reason == "length":
            raise HTTPException(status_code=502, detail="AI 输出被截断，请缩短计划周期后重试")
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
        period_days = (request.period_end - request.period_start).total_seconds() / 86400
        if period_days <= 1:
            item_limit = 6
        elif period_days <= 7:
            item_limit = 14
        else:
            item_limit = 30
        draft = self._structured_response(
            model_name=self.settings.planning_model,
            instructions=(
                "你是非医疗诊断性质的中文生活规划助手。健康安全、固定日程和预算是硬约束。"
                "不要诊断疾病、开药或调整剂量。输出严格符合给定 JSON Schema。"
                "健康、事业、学习优先，同时保留合理休息。不得安排与 busy_blocks 重叠的事项。"
                f"本次计划的 items 最多 {item_limit} 个，只安排关键且可执行的事项，"
                "不要按小时填满，也不要把同一件事拆成多个细小条目。"
            ),
            input_text=request.model_dump_json(),
            output_model=PlanDraft,
            schema_name="life_plan_draft",
        )
        if len(draft.items) > item_limit:
            raise HTTPException(status_code=502, detail="AI 返回的计划事项过多，请重试")
        return draft

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
