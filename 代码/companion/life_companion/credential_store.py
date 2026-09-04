from __future__ import annotations

import hashlib
import json
from typing import Protocol

import keyring

SERVICE_NAME = "LifeHealthPlannerCompanion"
CHUNK_SIZE = 900
MAX_KEY_LENGTH = 16_000


class KeyringBackend(Protocol):
    def get_password(self, service: str, username: str) -> str | None: ...

    def set_password(self, service: str, username: str, password: str) -> None: ...

    def delete_password(self, service: str, username: str) -> None: ...


class CredentialStore(Protocol):
    def get_api_key(self, provider_id: str) -> str | None: ...

    def set_api_key(self, provider_id: str, value: str) -> None: ...

    def clear_api_key(self, provider_id: str) -> None: ...


class WindowsCredentialStore:
    def __init__(self, backend: KeyringBackend = keyring) -> None:
        self.backend = backend

    @staticmethod
    def _manifest_user(provider_id: str) -> str:
        return f"ai-key-{provider_id}-manifest"

    @staticmethod
    def _part_user(provider_id: str, index: int) -> str:
        return f"ai-key-{provider_id}-part-{index:02d}"

    def get_api_key(self, provider_id: str) -> str | None:
        manifest_text = self.backend.get_password(
            SERVICE_NAME, self._manifest_user(provider_id)
        )
        if not manifest_text:
            return None
        try:
            manifest = json.loads(manifest_text)
            part_count = int(manifest["parts"])
            expected_hash = str(manifest["sha256"])
            parts = [
                self.backend.get_password(SERVICE_NAME, self._part_user(provider_id, index))
                for index in range(part_count)
            ]
            if any(part is None for part in parts):
                return None
            value = "".join(part or "" for part in parts)
            digest = hashlib.sha256(value.encode("utf-8")).hexdigest()
            return value if digest == expected_hash else None
        except (KeyError, TypeError, ValueError, json.JSONDecodeError):
            return None

    def set_api_key(self, provider_id: str, value: str) -> None:
        cleaned = value.strip()
        if (
            not cleaned.startswith("sk-")
            or not 20 <= len(cleaned) <= MAX_KEY_LENGTH
            or any(ord(character) < 33 or ord(character) > 126 for character in cleaned)
        ):
            raise ValueError("The AI API key format is not valid")
        parts = [
            cleaned[index : index + CHUNK_SIZE]
            for index in range(0, len(cleaned), CHUNK_SIZE)
        ]
        self.clear_api_key(provider_id)
        written: list[str] = []
        try:
            for index, part in enumerate(parts):
                username = self._part_user(provider_id, index)
                self.backend.set_password(SERVICE_NAME, username, part)
                written.append(username)
            manifest = json.dumps(
                {
                    "version": 1,
                    "parts": len(parts),
                    "sha256": hashlib.sha256(cleaned.encode("utf-8")).hexdigest(),
                },
                separators=(",", ":"),
            )
            username = self._manifest_user(provider_id)
            self.backend.set_password(SERVICE_NAME, username, manifest)
            written.append(username)
        except Exception as error:
            for username in written:
                self._delete(username)
            raise RuntimeError("无法安全保存 AI 密钥，请重新复制完整密钥") from error

    def clear_api_key(self, provider_id: str) -> None:
        manifest_text = self.backend.get_password(
            SERVICE_NAME, self._manifest_user(provider_id)
        )
        part_count = 0
        if manifest_text:
            try:
                part_count = int(json.loads(manifest_text).get("parts", 0))
            except (TypeError, ValueError, json.JSONDecodeError):
                part_count = 0
        for index in range(max(part_count, 32)):
            self._delete(self._part_user(provider_id, index))
        self._delete(self._manifest_user(provider_id))

    def _delete(self, username: str) -> None:
        try:
            self.backend.delete_password(SERVICE_NAME, username)
        except (keyring.errors.PasswordDeleteError, KeyError):
            pass
