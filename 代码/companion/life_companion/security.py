from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import threading
import time
from collections import defaultdict, deque
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from pathlib import Path

from fastapi import HTTPException, Request, status

from .models import PairCompleteRequest, PairCompleteResponse
from .settings import Settings


def _atomic_json_write(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


@dataclass(frozen=True)
class DeviceIdentity:
    device_id: str
    name: str


class PairingStore:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.path = settings.data_dir / "paired_devices.json"
        self._lock = threading.Lock()
        self._pending_code_hash: str | None = None
        self._pending_expires_at: datetime | None = None
        self._devices: dict[str, dict[str, str]] = {}
        self._load()

    @staticmethod
    def _hash_secret(value: str) -> str:
        return hashlib.sha256(value.encode("utf-8")).hexdigest()

    def _load(self) -> None:
        if not self.path.exists():
            return
        payload = json.loads(self.path.read_text(encoding="utf-8"))
        self._devices = payload.get("devices", {})

    def _persist(self) -> None:
        _atomic_json_write(self.path, {"version": 1, "devices": self._devices})

    def start_pairing(self) -> tuple[str, datetime]:
        code = f"{secrets.randbelow(1_000_000):06d}"
        expires_at = datetime.now(UTC) + timedelta(seconds=self.settings.pairing_ttl_seconds)
        with self._lock:
            self._pending_code_hash = self._hash_secret(code)
            self._pending_expires_at = expires_at
        return code, expires_at

    def complete_pairing(self, request: PairCompleteRequest) -> PairCompleteResponse:
        now = datetime.now(UTC)
        with self._lock:
            valid = (
                self._pending_code_hash is not None
                and self._pending_expires_at is not None
                and now <= self._pending_expires_at
                and hmac.compare_digest(self._pending_code_hash, self._hash_secret(request.code))
            )
            if not valid:
                raise HTTPException(
                    status_code=status.HTTP_401_UNAUTHORIZED,
                    detail="配对码无效或已过期",
                )
            token = secrets.token_urlsafe(48)
            self._devices[request.device_id] = {
                "name": request.device_name,
                "token_hash": self._hash_secret(token),
                "paired_at": now.isoformat(),
            }
            self._pending_code_hash = None
            self._pending_expires_at = None
            self._persist()
        return PairCompleteResponse(device_id=request.device_id, token=token, server_time=now)

    def authenticate(self, device_id: str, token: str) -> DeviceIdentity:
        device = self._devices.get(device_id)
        if not device or not hmac.compare_digest(device["token_hash"], self._hash_secret(token)):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="设备认证失败")
        return DeviceIdentity(device_id=device_id, name=device["name"])

    def revoke(self, device_id: str) -> bool:
        with self._lock:
            removed = self._devices.pop(device_id, None) is not None
            if removed:
                self._persist()
            return removed

    def list_devices(self) -> list[dict[str, str]]:
        return [
            {"device_id": device_id, "name": entry["name"], "paired_at": entry["paired_at"]}
            for device_id, entry in sorted(self._devices.items())
        ]


class ReplayProtector:
    def __init__(self, settings: Settings, max_nonces_per_device: int = 1000) -> None:
        self.settings = settings
        self.max_nonces_per_device = max_nonces_per_device
        self._seen: dict[str, deque[tuple[str, int]]] = defaultdict(deque)
        self._lock = threading.Lock()

    def verify(self, device_id: str, timestamp_text: str, nonce: str) -> None:
        try:
            request_timestamp = int(timestamp_text)
        except ValueError as error:
            raise HTTPException(status_code=401, detail="请求时间戳无效") from error
        now = int(time.time())
        if abs(now - request_timestamp) > self.settings.request_clock_skew_seconds:
            raise HTTPException(status_code=401, detail="请求已过期")
        if len(nonce) < 16 or len(nonce) > 128:
            raise HTTPException(status_code=401, detail="请求 nonce 无效")
        with self._lock:
            device_nonces = self._seen[device_id]
            cutoff = now - self.settings.request_clock_skew_seconds
            while device_nonces and device_nonces[0][1] < cutoff:
                device_nonces.popleft()
            if any(seen_nonce == nonce for seen_nonce, _ in device_nonces):
                raise HTTPException(status_code=409, detail="检测到重复请求")
            device_nonces.append((nonce, request_timestamp))
            while len(device_nonces) > self.max_nonces_per_device:
                device_nonces.popleft()


def require_loopback(request: Request) -> None:
    host = request.client.host if request.client else ""
    if host not in {"127.0.0.1", "::1", "testclient"}:
        raise HTTPException(status_code=403, detail="此操作只能在本机执行")


def extract_bearer(request: Request) -> str:
    authorization = request.headers.get("Authorization", "")
    scheme, _, token = authorization.partition(" ")
    if scheme.lower() != "bearer" or not token:
        raise HTTPException(status_code=401, detail="缺少设备令牌")
    return token
