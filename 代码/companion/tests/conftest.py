from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from life_companion.app import create_app
from life_companion.models import PairCompleteRequest
from life_companion.settings import Settings


class MemoryCredentials:
    def __init__(self, value: str | None = "test-api-key-not-a-secret") -> None:
        self.values = {"deepseek": value, "subkkai": value}

    def get_api_key(self, provider_id: str) -> str | None:
        return self.values.get(provider_id)

    def set_api_key(self, provider_id: str, value: str) -> None:
        self.values[provider_id] = value

    def clear_api_key(self, provider_id: str) -> None:
        self.values[provider_id] = None


class FakeResponse:
    def __init__(self, output_text: str) -> None:
        self.output_text = output_text


class FakeResponses:
    def __init__(self) -> None:
        self.calls: list[dict] = []
        self.next_output = json.dumps(
            {
                "title": "平衡一周",
                "summary": "保持稳定节奏。",
                "rationale": ["优先健康与学习"],
                "risk_level": "normal",
                "risk_message": "",
                "items": [],
                "review_questions": ["本周精力如何？"],
            },
            ensure_ascii=False,
        )

    def create(self, **kwargs):
        self.calls.append(kwargs)
        return FakeResponse(self.next_output)


@pytest.fixture
def settings(tmp_path):
    return Settings(
        data_dir=tmp_path,
        active_provider="subkkai",
        third_party_acknowledged=True,
    )


@pytest.fixture
def fake_responses():
    return FakeResponses()


@pytest.fixture
def app(settings, fake_responses):
    return create_app(settings, MemoryCredentials(), fake_responses)


@pytest.fixture
def client(app):
    return TestClient(app)


@pytest.fixture
def paired(client, app):
    code, _ = app.state.pairing.start_pairing()
    response = app.state.pairing.complete_pairing(
        PairCompleteRequest(code=code, device_id="device-12345678", device_name="Test Phone")
    )

    def headers(nonce: str = "nonce-1234567890abcdef") -> dict[str, str]:
        return {
            "Authorization": f"Bearer {response.token}",
            "X-Device-ID": response.device_id,
            "X-Request-Timestamp": str(int(datetime.now(UTC).timestamp())),
            "X-Request-Nonce": nonce,
            "X-AI-Provider": "subkkai",
        }

    return response, headers


@pytest.fixture
def plan_payload():
    now = datetime.now(UTC).replace(microsecond=0)
    return {
        "context": {
            "age_band": "30-39",
            "timezone": "Asia/Shanghai",
            "region": "中国",
            "health_constraints": [],
            "schedule_constraints": [],
            "preferences": ["晚间学习"],
            "goals": [],
            "metrics": [],
            "busy_blocks": [],
            "weekly_budget": 500,
            "recent_feedback": [],
            "emergency_number": "120",
        },
        "period_start": now.isoformat(),
        "period_end": (now + timedelta(days=7)).isoformat(),
        "focus": "安排平衡的一周",
    }
