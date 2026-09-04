from __future__ import annotations

import hashlib
import hmac
import re
import threading
from datetime import UTC, datetime
from pathlib import Path

from fastapi import HTTPException

from .models import BackupEnvelope, BackupReceipt

DEVICE_PATTERN = re.compile(r"^[A-Za-z0-9._-]{8,100}$")
BACKUP_PATTERN = re.compile(r"^[0-9]{8}T[0-9]{12}Z$")


class BackupStore:
    def __init__(self, root: Path, keep: int = 12) -> None:
        self.root = root
        self.keep = keep
        self._lock = threading.RLock()

    def _device_dir(self, device_id: str) -> Path:
        if not DEVICE_PATTERN.fullmatch(device_id):
            raise HTTPException(status_code=400, detail="设备 ID 无效")
        resolved_root = self.root.resolve()
        resolved = (self.root / device_id).resolve()
        if resolved.parent != resolved_root:
            raise HTTPException(status_code=400, detail="备份路径无效")
        return resolved

    def _backup_path(self, device_id: str, backup_id: str) -> Path:
        if not BACKUP_PATTERN.fullmatch(backup_id):
            raise HTTPException(status_code=400, detail="备份 ID 无效")
        device_dir = self._device_dir(device_id)
        resolved = (device_dir / f"{backup_id}.json").resolve()
        if resolved.parent != device_dir:
            raise HTTPException(status_code=400, detail="备份路径无效")
        return resolved

    @staticmethod
    def _read_verified(
        path: Path, expected_sha256: str | None = None
    ) -> BackupEnvelope:
        if not path.is_file():
            raise HTTPException(status_code=404, detail="指定备份不存在")
        try:
            envelope = BackupEnvelope.model_validate_json(path.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            raise HTTPException(status_code=400, detail="备份文件无效") from error
        actual_hash = hashlib.sha256(envelope.ciphertext.encode("utf-8")).hexdigest()
        valid = hmac.compare_digest(actual_hash, envelope.sha256)
        if expected_sha256 is not None:
            valid = valid and hmac.compare_digest(actual_hash, expected_sha256)
        if not valid:
            raise HTTPException(status_code=400, detail="备份完整性校验失败")
        return envelope

    @staticmethod
    def _receipt(path: Path, envelope: BackupEnvelope) -> BackupReceipt:
        return BackupReceipt(
            backup_id=path.stem,
            created_at=envelope.created_at,
            byte_count=path.stat().st_size,
            sha256=envelope.sha256,
        )

    @staticmethod
    def _write(path: Path, envelope: BackupEnvelope) -> None:
        serialized = envelope.model_dump_json(indent=2)
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(serialized, encoding="utf-8")
        temporary.replace(path)

    @staticmethod
    def _next_backup_path(target_dir: Path) -> Path:
        while True:
            backup_id = datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
            target = target_dir / f"{backup_id}.json"
            if not target.exists():
                return target

    def save(self, device_id: str, envelope: BackupEnvelope) -> BackupReceipt:
        actual_hash = hashlib.sha256(envelope.ciphertext.encode("utf-8")).hexdigest()
        if actual_hash != envelope.sha256:
            raise HTTPException(status_code=400, detail="备份完整性校验失败")
        with self._lock:
            target_dir = self._device_dir(device_id)
            target_dir.mkdir(parents=True, exist_ok=True)
            target = self._next_backup_path(target_dir)
            self._write(target, envelope)
            self._prune(target_dir)
            return self._receipt(target, envelope)

    def _prune(self, target_dir: Path) -> None:
        backups = sorted(target_dir.glob("*.json"), reverse=True)
        for old_backup in backups[self.keep :]:
            old_backup.unlink(missing_ok=True)

    def latest(self, device_id: str) -> BackupEnvelope:
        with self._lock:
            target_dir = self._device_dir(device_id)
            backups = (
                sorted(target_dir.glob("*.json"), reverse=True)
                if target_dir.exists()
                else []
            )
            if not backups:
                raise HTTPException(status_code=404, detail="没有可用备份")
            return self._read_verified(backups[0])

    def exact(
        self,
        device_id: str,
        backup_id: str,
        expected_sha256: str | None = None,
    ) -> BackupEnvelope:
        with self._lock:
            return self._read_verified(
                self._backup_path(device_id, backup_id), expected_sha256
            )

    def describe(self, device_id: str, backup_id: str) -> BackupReceipt:
        with self._lock:
            path = self._backup_path(device_id, backup_id)
            return self._receipt(path, self._read_verified(path))

    def list(self, device_id: str) -> list[BackupReceipt]:
        with self._lock:
            target_dir = self._device_dir(device_id)
            backups = (
                sorted(target_dir.glob("*.json"), reverse=True)
                if target_dir.exists()
                else []
            )
            return [self._receipt(path, self._read_verified(path)) for path in backups]

    def device_ids(self) -> list[str]:
        if not self.root.exists():
            return []
        result: list[str] = []
        for path in self.root.iterdir():
            if not path.is_dir() or not DEVICE_PATTERN.fullmatch(path.name):
                continue
            try:
                if self._device_dir(path.name) == path.resolve():
                    result.append(path.name)
            except HTTPException:
                continue
        return sorted(result)

    def copy_encrypted(
        self,
        source_device_id: str,
        backup_id: str,
        expected_sha256: str,
        target_device_id: str,
    ) -> BackupReceipt:
        if source_device_id == target_device_id:
            raise HTTPException(status_code=400, detail="恢复目标必须是新设备")
        with self._lock:
            source = self._read_verified(
                self._backup_path(source_device_id, backup_id), expected_sha256
            )
            target_dir = self._device_dir(target_device_id)
            target_dir.mkdir(parents=True, exist_ok=True)
            for existing_path in sorted(target_dir.glob("*.json"), reverse=True):
                existing = self._read_verified(existing_path)
                if hmac.compare_digest(existing.sha256, source.sha256):
                    return self._receipt(existing_path, existing)
            target = self._next_backup_path(target_dir)
            self._write(target, source)
            self._prune(target_dir)
            return self._receipt(target, source)

    def delete_all(self, device_id: str) -> int:
        with self._lock:
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
