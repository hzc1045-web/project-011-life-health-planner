from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Final, Literal

ProviderId = Literal["deepseek", "subkkai"]


@dataclass(frozen=True)
class ProviderSpec:
    provider_id: ProviderId
    display_name: str
    base_url: str
    api_style: Literal["chat_completions", "responses"]
    planning_model: str
    economy_model: str
    third_party: bool = False


PROVIDERS: Final[dict[str, ProviderSpec]] = {
    "deepseek": ProviderSpec(
        provider_id="deepseek",
        display_name="DeepSeek 官方",
        base_url="https://api.deepseek.com",
        api_style="chat_completions",
        planning_model="deepseek-v4-pro",
        economy_model="deepseek-v4-flash",
    ),
    "subkkai": ProviderSpec(
        provider_id="subkkai",
        display_name="AI小站（第三方）",
        base_url="https://subkkai.com",
        api_style="responses",
        planning_model="gpt-5.6-sol",
        economy_model="gpt-5.6-sol",
        third_party=True,
    ),
}


def _default_data_dir() -> Path:
    local_app_data = os.environ.get("LOCALAPPDATA")
    if local_app_data:
        return Path(local_app_data) / "LifeHealthPlannerCompanion"
    return Path.home() / ".life-health-planner-companion"


@dataclass(frozen=True)
class Settings:
    data_dir: Path
    host: str = "127.0.0.1"
    port: int = 8765
    active_provider: ProviderId = "deepseek"
    deepseek_planning_model: str = "deepseek-v4-pro"
    deepseek_economy_model: str = "deepseek-v4-flash"
    subkkai_planning_model: str = "gpt-5.6-sol"
    subkkai_economy_model: str = "gpt-5.6-sol"
    third_party_acknowledged: bool = False
    pairing_ttl_seconds: int = 600
    request_clock_skew_seconds: int = 300
    max_ai_body_bytes: int = 512 * 1024
    max_backup_body_bytes: int = 20 * 1024 * 1024

    @classmethod
    def load(cls, data_dir: Path | None = None) -> Settings:
        root = data_dir or _default_data_dir()
        config_path = root / "config.json"
        values: dict[str, object] = {}
        if config_path.exists():
            values = json.loads(config_path.read_text(encoding="utf-8"))
        active_provider = os.environ.get(
            "LHP_ACTIVE_PROVIDER", str(values.get("active_provider", "deepseek"))
        )
        if active_provider not in PROVIDERS:
            active_provider = "deepseek"
        acknowledged = bool(values.get("third_party_acknowledged", False))
        if active_provider == "subkkai" and not acknowledged:
            active_provider = "deepseek"
        return cls(
            data_dir=root,
            host=os.environ.get("LHP_HOST", str(values.get("host", "127.0.0.1"))),
            port=int(os.environ.get("LHP_PORT", values.get("port", 8765))),
            active_provider=active_provider,
            deepseek_planning_model=os.environ.get(
                "LHP_DEEPSEEK_PLANNING_MODEL",
                str(values.get("deepseek_planning_model", "deepseek-v4-pro")),
            ),
            deepseek_economy_model=os.environ.get(
                "LHP_DEEPSEEK_ECONOMY_MODEL",
                str(values.get("deepseek_economy_model", "deepseek-v4-flash")),
            ),
            subkkai_planning_model=os.environ.get(
                "LHP_SUBKKAI_PLANNING_MODEL",
                str(values.get("subkkai_planning_model", "gpt-5.6-sol")),
            ),
            subkkai_economy_model=os.environ.get(
                "LHP_SUBKKAI_ECONOMY_MODEL",
                str(values.get("subkkai_economy_model", "gpt-5.6-sol")),
            ),
            third_party_acknowledged=acknowledged,
        )

    @property
    def provider(self) -> ProviderSpec:
        return PROVIDERS[self.active_provider]

    @property
    def planning_model(self) -> str:
        if self.active_provider == "deepseek":
            return self.deepseek_planning_model
        return self.subkkai_planning_model

    @property
    def economy_model(self) -> str:
        if self.active_provider == "deepseek":
            return self.deepseek_economy_model
        return self.subkkai_economy_model

    def ensure_directories(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        (self.data_dir / "backups").mkdir(parents=True, exist_ok=True)

    def activate_provider(self, provider_id: str, acknowledge_third_party: bool = False) -> None:
        if provider_id not in PROVIDERS:
            raise ValueError("不支持的 AI 提供商")
        provider = PROVIDERS[provider_id]
        if provider.third_party and not acknowledge_third_party:
            raise ValueError("切换到第三方 AI 前必须明确确认数据接收方")
        self.ensure_directories()
        config_path = self.data_dir / "config.json"
        values: dict[str, object] = {}
        if config_path.exists():
            values = json.loads(config_path.read_text(encoding="utf-8"))
        values["active_provider"] = provider_id
        if provider.third_party:
            values["third_party_acknowledged"] = True
        temporary = config_path.with_suffix(".tmp")
        temporary.write_text(
            json.dumps(values, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        temporary.replace(config_path)

    def public_dict(self) -> dict[str, object]:
        return {
            "host": self.host,
            "port": self.port,
            "active_provider": self.active_provider,
            "provider_display_name": self.provider.display_name,
            "provider_is_third_party": self.provider.third_party,
            "planning_model": self.planning_model,
            "economy_model": self.economy_model,
        }
