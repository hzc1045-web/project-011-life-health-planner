from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path


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
    planning_model: str = "gpt-5.6-terra"
    economy_model: str = "gpt-5.6-luna"
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
        return cls(
            data_dir=root,
            host=os.environ.get("LHP_HOST", str(values.get("host", "127.0.0.1"))),
            port=int(os.environ.get("LHP_PORT", values.get("port", 8765))),
            planning_model=os.environ.get(
                "LHP_PLANNING_MODEL", str(values.get("planning_model", "gpt-5.6-terra"))
            ),
            economy_model=os.environ.get(
                "LHP_ECONOMY_MODEL", str(values.get("economy_model", "gpt-5.6-luna"))
            ),
        )

    def ensure_directories(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        (self.data_dir / "backups").mkdir(parents=True, exist_ok=True)

    def public_dict(self) -> dict[str, object]:
        return {
            "host": self.host,
            "port": self.port,
            "planning_model": self.planning_model,
            "economy_model": self.economy_model,
        }
