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


class OpenAIService:
    def __init__(
        self,
        settings: Settings,
        credential_store: CredentialStore,
        responses_client: ResponsesClient | None = None,
    ) -> None:
        self.settings = settings
        self.credential_store = credential_store
        self._responses_client = responses_client

    def is_configured(self) -> bool:
        return bool(self.credential_store.get_api_key())

    def _client(self) -> ResponsesClient:
        if self._responses_client is not None:
            return self._responses_client
        api_key = self.credential_store.get_api_key()
        if not api_key:
            raise HTTPException(status_code=503, detail="OpenAI API 密钥尚未配置")
        return OpenAI(api_key=api_key, timeout=45, max_retries=1).responses

    @staticmethod
    def _format(model: type[T], name: str) -> dict[str, object]:
        return {
            "type": "json_schema",
            "name": name,
            "strict": True,
            "schema": model.model_json_schema(),
        }

    @staticmethod
    def _parse(response: Any, model: type[T]) -> T:
        output_text = getattr(response, "output_text", None)
        if not output_text:
            raise HTTPException(status_code=502, detail="AI 没有返回可解析内容")
        try:
            return model.model_validate(json.loads(output_text))
        except (json.JSONDecodeError, ValidationError) as error:
            raise HTTPException(status_code=502, detail="AI 返回内容未通过结构校验") from error

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
        response = self._client().create(
            model=self.settings.planning_model,
            store=False,
            instructions=(
                "你是非医疗诊断性质的中文生活规划助手。健康安全、固定日程和预算是硬约束。"
                "不要诊断疾病、开药或调整剂量。输出严格符合给定 JSON Schema。"
                "健康、事业、学习优先，同时保留合理休息。不得安排与 busy_blocks 重叠的事项。"
            ),
            input=request.model_dump_json(),
            text={"format": self._format(PlanDraft, "life_plan_draft")},
        )
        return self._parse(response, PlanDraft)

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
        response = self._client().create(
            model=self.settings.economy_model,
            store=False,
            instructions=(
                "你是中文生活规划助手，不提供医疗诊断、处方、剂量调整或专业投资指令。"
                "建议转换成日程前必须要求用户确认。输出严格符合给定 JSON Schema。"
            ),
            input=request.model_dump_json(),
            text={"format": self._format(ChatReply, "life_planner_chat_reply")},
        )
        return self._parse(response, ChatReply)
