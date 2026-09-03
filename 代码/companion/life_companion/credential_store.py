from __future__ import annotations

from typing import Protocol

import keyring

SERVICE_NAME = "LifeHealthPlannerCompanion"
API_KEY_USER = "openai-api-key"


class CredentialStore(Protocol):
    def get_api_key(self) -> str | None: ...

    def set_api_key(self, value: str) -> None: ...

    def clear_api_key(self) -> None: ...


class WindowsCredentialStore:
    def get_api_key(self) -> str | None:
        return keyring.get_password(SERVICE_NAME, API_KEY_USER)

    def set_api_key(self, value: str) -> None:
        cleaned = value.strip()
        if not cleaned.startswith("sk-") or len(cleaned) < 20:
            raise ValueError("The OpenAI API key format is not valid")
        keyring.set_password(SERVICE_NAME, API_KEY_USER, cleaned)

    def clear_api_key(self) -> None:
        try:
            keyring.delete_password(SERVICE_NAME, API_KEY_USER)
        except keyring.errors.PasswordDeleteError:
            pass
