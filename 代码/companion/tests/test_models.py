from datetime import UTC, datetime, timedelta

import pytest
from pydantic import ValidationError

from life_companion.models import PlanDraft, PlanItem, PlanRequest


def test_plan_item_rejects_invalid_time_range():
    now = datetime.now(UTC)
    with pytest.raises(ValidationError):
        PlanItem(
            id="one",
            domain="health",
            title="步行",
            start_at=now,
            end_at=now - timedelta(minutes=1),
            priority=3,
            energy="low",
            estimated_cost=0,
        )


def test_plan_request_rejects_more_than_35_days(plan_payload):
    start = datetime.now(UTC)
    plan_payload["period_start"] = start.isoformat()
    plan_payload["period_end"] = (start + timedelta(days=36)).isoformat()
    with pytest.raises(ValidationError):
        PlanRequest.model_validate(plan_payload)


def test_plan_draft_rejects_more_than_30_items():
    now = datetime.now(UTC)
    item = {
        "id": "one",
        "domain": "health",
        "title": "步行",
        "start_at": now.isoformat(),
        "end_at": (now + timedelta(minutes=30)).isoformat(),
        "priority": 3,
        "energy": "low",
        "estimated_cost": 0,
    }
    payload = {
        "title": "月计划",
        "summary": "保持稳定节奏。",
        "rationale": [],
        "risk_level": "normal",
        "risk_message": "",
        "items": [{**item, "id": f"item-{index}"} for index in range(31)],
        "review_questions": [],
    }

    with pytest.raises(ValidationError):
        PlanDraft.model_validate(payload)
