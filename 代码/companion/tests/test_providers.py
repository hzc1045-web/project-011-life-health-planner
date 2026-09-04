from __future__ import annotations

import json
from types import SimpleNamespace

import pytest
from conftest import MemoryCredentials

from life_companion.credential_store import CHUNK_SIZE, SERVICE_NAME, WindowsCredentialStore
from life_companion.models import PlanRequest
from life_companion.openai_service import OpenAIService
from life_companion.settings import Settings


class MemoryKeyring:
    def __init__(self) -> None:
        self.values: dict[tuple[str, str], str] = {}

    def get_password(self, service: str, username: str) -> str | None:
        return self.values.get((service, username))

    def set_password(self, service: str, username: str, password: str) -> None:
        self.values[(service, username)] = password

    def delete_password(self, service: str, username: str) -> None:
        self.values.pop((service, username), None)


class FakeChatCompletions:
    def __init__(self) -> None:
        self.calls: list[dict] = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        output = json.dumps(
            {
                "title": "DeepSeek 平衡计划",
                "summary": "保持稳定节奏。",
                "rationale": ["先满足健康约束"],
                "risk_level": "normal",
                "risk_message": "",
                "items": [],
                "review_questions": ["本周精力如何？"],
            },
            ensure_ascii=False,
        )
        return SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(content=output))]
        )


def test_long_provider_key_is_chunked_and_round_trips():
    backend = MemoryKeyring()
    store = WindowsCredentialStore(backend)
    value = "sk-" + "a" * 4_000

    store.set_api_key("subkkai", value)

    assert store.get_api_key("subkkai") == value
    stored_parts = [
        password
        for (service, username), password in backend.values.items()
        if service == SERVICE_NAME and "-part-" in username
    ]
    assert len(stored_parts) > 1
    assert all(len(part) <= CHUNK_SIZE for part in stored_parts)
    store.clear_api_key("subkkai")
    assert store.get_api_key("subkkai") is None


def test_third_party_provider_requires_explicit_acknowledgement(tmp_path):
    settings = Settings(data_dir=tmp_path)

    with pytest.raises(ValueError, match="第三方"):
        settings.activate_provider("subkkai")

    settings.activate_provider("subkkai", acknowledge_third_party=True)
    loaded = Settings.load(tmp_path)
    assert loaded.active_provider == "subkkai"
    assert loaded.provider.third_party is True


def test_deepseek_uses_chat_completions_json_mode(tmp_path, plan_payload):
    settings = Settings(data_dir=tmp_path, active_provider="deepseek")
    fake_chat = FakeChatCompletions()
    service = OpenAIService(
        settings,
        MemoryCredentials(),
        chat_completions_client=fake_chat,
    )

    result = service.create_plan(PlanRequest.model_validate(plan_payload))

    assert result.title == "DeepSeek 平衡计划"
    call = fake_chat.calls[-1]
    assert call["model"] == "deepseek-v4-pro"
    assert call["response_format"] == {"type": "json_object"}
    assert call["stream"] is False
    assert "store" not in call
    assert "JSON Schema" in call["messages"][0]["content"]
