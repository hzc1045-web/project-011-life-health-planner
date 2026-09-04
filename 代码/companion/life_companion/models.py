from __future__ import annotations

from datetime import UTC, datetime
from enum import StrEnum
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, StringConstraints, field_validator

SafeText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=4000)]
ShortText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]


class RiskLevel(StrEnum):
    NORMAL = "normal"
    CAUTION = "caution"
    URGENT = "urgent"


class LifeDomain(StrEnum):
    HEALTH = "health"
    CAREER = "career"
    LEARNING = "learning"
    FINANCE = "finance"
    RELATIONSHIPS = "relationships"
    LEISURE = "leisure"


class PairCompleteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: Annotated[str, StringConstraints(pattern=r"^[0-9]{6}$")]
    device_id: Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9._-]{8,100}$")]
    device_name: Annotated[
        str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)
    ]


class PairCompleteResponse(BaseModel):
    device_id: str
    token: str
    server_time: datetime


class PairStartResponse(BaseModel):
    server_url: str
    code: str
    expires_at: datetime
    pairing_uri: str


class BusyBlock(BaseModel):
    model_config = ConfigDict(extra="forbid")

    start_at: datetime
    end_at: datetime
    label: Annotated[str, StringConstraints(strip_whitespace=True, max_length=80)] = "忙碌"

    @field_validator("end_at")
    @classmethod
    def validate_end(cls, value: datetime, info):
        start = info.data.get("start_at")
        if start and value <= start:
            raise ValueError("end_at must be after start_at")
        return value


class GoalSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str
    domain: LifeDomain
    title: ShortText
    target: Annotated[str, StringConstraints(strip_whitespace=True, max_length=500)] = ""
    priority: int = Field(ge=1, le=5)
    target_date: datetime | None = None


class MetricSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")

    kind: Annotated[str, StringConstraints(pattern=r"^[a-z_]{2,40}$")]
    value: float
    unit: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=20)]
    measured_at: datetime


class AiContextSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")

    age_band: Literal["18-29", "30-39", "40-49", "50-59", "60-69", "70+"]
    timezone: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=80)]
    region: Annotated[str, StringConstraints(strip_whitespace=True, min_length=2, max_length=80)]
    health_constraints: list[ShortText] = Field(default_factory=list, max_length=30)
    schedule_constraints: list[ShortText] = Field(default_factory=list, max_length=30)
    preferences: list[ShortText] = Field(default_factory=list, max_length=30)
    goals: list[GoalSnapshot] = Field(default_factory=list, max_length=50)
    metrics: list[MetricSnapshot] = Field(default_factory=list, max_length=100)
    busy_blocks: list[BusyBlock] = Field(default_factory=list, max_length=100)
    weekly_budget: float | None = Field(default=None, ge=0, le=1_000_000)
    recent_feedback: list[ShortText] = Field(default_factory=list, max_length=50)
    emergency_number: Annotated[str, StringConstraints(strip_whitespace=True, max_length=20)] = ""


class PlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    context: AiContextSnapshot
    period_start: datetime
    period_end: datetime
    focus: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] = ""

    @field_validator("period_end")
    @classmethod
    def validate_period(cls, value: datetime, info):
        start = info.data.get("period_start")
        if start and value <= start:
            raise ValueError("period_end must be after period_start")
        if start and (value - start).days > 35:
            raise ValueError("planning period cannot exceed 35 days")
        return value


class PlanItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9._-]{1,80}$")]
    domain: LifeDomain
    title: ShortText
    description: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] = ""
    start_at: datetime
    end_at: datetime
    priority: int = Field(ge=1, le=5)
    energy: Literal["low", "medium", "high"]
    estimated_cost: float = Field(ge=0, le=1_000_000)
    goal_ids: list[str] = Field(default_factory=list, max_length=20)
    reminder_minutes: list[int] = Field(default_factory=list, max_length=5)
    safety_tags: list[str] = Field(default_factory=list, max_length=20)

    @field_validator("end_at")
    @classmethod
    def validate_end(cls, value: datetime, info):
        start = info.data.get("start_at")
        if start and value <= start:
            raise ValueError("end_at must be after start_at")
        return value


class PlanDraft(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: ShortText
    summary: SafeText
    rationale: list[ShortText] = Field(default_factory=list, max_length=20)
    risk_level: RiskLevel
    risk_message: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] = ""
    items: list[PlanItem] = Field(default_factory=list, max_length=30)
    review_questions: list[ShortText] = Field(default_factory=list, max_length=10)


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    context: AiContextSnapshot
    message: SafeText
    local_summary: Annotated[str, StringConstraints(strip_whitespace=True, max_length=8000)] = ""


class SuggestedAction(BaseModel):
    model_config = ConfigDict(extra="forbid")

    title: ShortText
    domain: LifeDomain
    details: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] = ""
    requires_plan_confirmation: bool = True


class ChatReply(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reply: SafeText
    risk_level: RiskLevel
    risk_message: Annotated[str, StringConstraints(strip_whitespace=True, max_length=1000)] = ""
    suggested_actions: list[SuggestedAction] = Field(default_factory=list, max_length=8)


class BackupEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    version: Literal[1]
    created_at: datetime
    salt: Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9_-]{20,200}$")]
    nonce: Annotated[str, StringConstraints(pattern=r"^[A-Za-z0-9_-]{12,100}$")]
    ciphertext: Annotated[str, StringConstraints(min_length=16, max_length=25_000_000)]
    sha256: Annotated[str, StringConstraints(pattern=r"^[a-f0-9]{64}$")]


class BackupReceipt(BaseModel):
    backup_id: str
    created_at: datetime
    byte_count: int
    sha256: str


class StatusResponse(BaseModel):
    version: str
    ai_configured: bool
    active_provider: str
    provider_display_name: str
    provider_is_third_party: bool
    planning_model: str
    economy_model: str
    server_time: datetime = Field(default_factory=lambda: datetime.now(UTC))
