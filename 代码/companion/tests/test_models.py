from datetime import UTC, datetime, timedelta

import pytest
from pydantic import ValidationError

from life_companion.models import PlanItem, PlanRequest


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
