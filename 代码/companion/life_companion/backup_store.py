from __future__ import annotations

import hashlib
import json
import re
from datetime import UTC, datetime
from pathlib import Path

from fastapi import HTTPException

from .models import BackupEnvelope, BackupReceipt

DEVICE_PATTERN = re.compile(r"^[A-Za-z0-9._-]{8,100}$")


class BackupStore:
    def __init__(self, root: Path, keep: int = 12) -> None:
        self.root = root
        self.keep = keep

    def _device_dir(self, device_id: str) -> Path:
        if not DEVICE_PATTERN.fullmatch(device_id):
            raise HTTPException(status_code=400, detail="设备 ID 无效")
        resolved_root = self.root.resolve()
        resolved = (self.root / device_id).resolve()
        if resolved.parent != resolved_root:
            raise HTTPException(status_code=400, detail="备份路径无效")
        return resolved

    def save(self, device_id: str, envelope: BackupEnvelope) -> BackupReceipt:
        actual_hash = hashlib.sha256(envelope.ciphertext.encode("utf-8")).hexdigest()
        if actual_hash != envelope.sha256:
            raise HTTPException(status_code=400, detail="备份完整性校验失败")
        target_dir = self._device_dir(device_id)
        target_dir.mkdir(parents=True, exist_ok=True)
        now = datetime.now(UTC)
        backup_id = now.strftime("%Y%m%dT%H%M%S%fZ")
        target = target_dir / f"{backup_id}.json"
        serialized = envelope.model_dump_json(indent=2)
        temporary = target.with_suffix(".tmp")
        temporary.write_text(serialized, encoding="utf-8")
        temporary.replace(target)
        self._prune(target_dir)
        return BackupReceipt(
            backup_id=backup_id,
            created_at=envelope.created_at,
            byte_count=len(serialized.encode("utf-8")),
            sha256=envelope.sha256,
        )

    def _prune(self, target_dir: Path) -> None:
        backups = sorted(target_dir.glob("*.json"), reverse=True)
        for old_backup in backups[self.keep :]:
            old_backup.unlink(missing_ok=True)

    def latest(self, device_id: str) -> BackupEnvelope:
        target_dir = self._device_dir(device_id)
        backups = sorted(target_dir.glob("*.json"), reverse=True) if target_dir.exists() else []
        if not backups:
            raise HTTPException(status_code=404, detail="没有可用备份")
        return BackupEnvelope.model_validate(json.loads(backups[0].read_text(encoding="utf-8")))

    def list(self, device_id: str) -> list[BackupReceipt]:
        target_dir = self._device_dir(device_id)
        backups = sorted(target_dir.glob("*.json"), reverse=True) if target_dir.exists() else []
        receipts: list[BackupReceipt] = []
        for path in backups:
            envelope = BackupEnvelope.model_validate(json.loads(path.read_text(encoding="utf-8")))
            receipts.append(
                BackupReceipt(
                    backup_id=path.stem,
                    created_at=envelope.created_at,
                    byte_count=path.stat().st_size,
                    sha256=envelope.sha256,
                )
            )
        return receipts

    def delete_all(self, device_id: str) -> int:
        target_dir = self._device_dir(device_id)
        if not target_dir.exists():
            return 0
        count = 0
        for path in target_dir.glob("*.json"):
            path.unlink()
            count += 1
        try:
            target_dir.rmdir()
        except OSError:
            pass
        return count
