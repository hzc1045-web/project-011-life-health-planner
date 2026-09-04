from __future__ import annotations

import asyncio
import base64
import hashlib
import os
from datetime import UTC, datetime

import httpx
import pytest

from life_companion.security import normalize_server_url


def test_status_is_public_and_does_not_expose_key(client):
    response = client.get("/status")
    assert response.status_code == 200
    assert response.json()["ai_configured"] is True
    assert response.json()["active_provider"] == "subkkai"
    assert response.json()["provider_is_third_party"] is True
    assert "api_key" not in response.text


@pytest.mark.parametrize(
    "raw_url",
    [
        "http://desktop.example.ts.net:8443",
        "https://desktop.example.com",
        "https://desktop.example.ts.net:8765",
        "https://user@desktop.example.ts.net",
        "https://desktop.example.ts.net/path",
        "https://desktop.example.ts.net?redirect=elsewhere",
    ],
)
def test_pairing_server_url_is_limited_to_canonical_tailscale_endpoint(client, raw_url):
    response = client.post("/pair/start", params={"server_url": raw_url})
    assert response.status_code == 400
    assert "Tailscale" in response.json()["detail"]


def test_pairing_server_url_is_normalized_in_qr_payload(client):
    response = client.post(
        "/pair/start",
        params={"server_url": "https://Desktop.Example.TS.NET:443/"},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["server_url"] == "https://desktop.example.ts.net"
    assert "server=https%3A%2F%2Fdesktop.example.ts.net" in payload["pairing_uri"]


@pytest.mark.parametrize(
    "raw_url, expected",
    [
        ("https://Desktop.Example.TS.NET:443/", "https://desktop.example.ts.net"),
        ("https://desktop.example.ts.net:8443", "https://desktop.example.ts.net:8443"),
        ("https://desktop.example.ts.net", "https://desktop.example.ts.net"),
    ],
)
def test_normalize_server_url_matches_android_pairing_rules(raw_url, expected):
    assert normalize_server_url(raw_url) == expected


def test_chunked_request_without_content_length_is_limited(app):
    class OversizedStream(httpx.AsyncByteStream):
        async def __aiter__(self):
            yield b"x" * (512 * 1024 + 1)

    async def send_request():
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.post(
                "/ai/plan",
                content=OversizedStream(),
                headers={"content-type": "application/json"},
            )

    response = asyncio.run(send_request())
    assert response.status_code == 413


def test_local_admin_routes_reject_tailnet_host_header(client):
    response = client.post(
        "/pair/start",
        params={"server_url": "https://desktop.example.ts.net:8443"},
        headers={"Host": "desktop.example.ts.net:8443"},
    )

    assert response.status_code == 403
    assert response.json()["detail"] == "此操作只能在本机执行"

    spoofed_host = client.post(
        "/pair/start",
        params={"server_url": "https://desktop.example.ts.net:8443"},
        headers={
            "Host": "127.0.0.1:8765",
            "X-Forwarded-For": "100.64.0.10",
            "X-Forwarded-Host": "desktop.example.ts.net:8443",
        },
    )
    assert spoofed_host.status_code == 403


def test_protected_route_requires_auth(client, plan_payload):
    response = client.post("/ai/plan", json=plan_payload)
    assert response.status_code == 401


def test_plan_uses_structured_output_and_store_false(
    client, paired, plan_payload, fake_responses
):
    _, headers = paired
    response = client.post("/ai/plan", json=plan_payload, headers=headers())
    assert response.status_code == 200
    assert response.json()["risk_level"] == "normal"
    call = fake_responses.calls[-1]
    assert call["store"] is False
    assert call["text"]["format"]["type"] == "json_schema"
    assert call["max_output_tokens"] == 8192
    assert "sk-test" not in str(call)


def test_replay_nonce_is_rejected(client, paired, plan_payload):
    _, headers = paired
    fixed = headers("nonce-replay-123456789")
    assert client.post("/ai/plan", json=plan_payload, headers=fixed).status_code == 200
    assert client.post("/ai/plan", json=plan_payload, headers=fixed).status_code == 409


def test_stale_ai_provider_is_rejected_before_model_call(
    client, paired, plan_payload, fake_responses
):
    _, headers = paired
    stale = headers("nonce-stale-provider-1234")
    stale["X-AI-Provider"] = "deepseek"
    response = client.post("/ai/plan", json=plan_payload, headers=stale)
    assert response.status_code == 409
    assert "AI 提供商已变化" in response.json()["detail"]
    assert not fake_responses.calls


def test_urgent_health_text_blocks_openai(client, paired, plan_payload, fake_responses):
    _, headers = paired
    plan_payload["context"]["health_constraints"] = ["胸痛并且呼吸困难和出汗"]
    response = client.post(
        "/ai/plan", json=plan_payload, headers=headers("nonce-urgent-123456789")
    )
    assert response.status_code == 200
    assert response.json()["risk_level"] == "urgent"
    assert not fake_responses.calls


def test_encrypted_backup_round_trip(client, paired):
    pairing, headers = paired
    ciphertext = base64.urlsafe_b64encode(os.urandom(64)).decode().rstrip("=")
    payload = {
        "version": 1,
        "created_at": datetime.now(UTC).isoformat(),
        "salt": base64.urlsafe_b64encode(os.urandom(16)).decode().rstrip("="),
        "nonce": base64.urlsafe_b64encode(os.urandom(12)).decode().rstrip("="),
        "ciphertext": ciphertext,
        "sha256": hashlib.sha256(ciphertext.encode()).hexdigest(),
    }
    upload = client.put(
        f"/backups/{pairing.device_id}",
        json=payload,
        headers=headers("nonce-backup-upload-1234"),
    )
    assert upload.status_code == 200
    latest = client.get(
        f"/backups/{pairing.device_id}/latest",
        headers=headers("nonce-backup-read-123456"),
    )
    assert latest.status_code == 200
    assert latest.json()["ciphertext"] == ciphertext


def test_backup_rejects_wrong_ciphertext_hash(client, paired):
    pairing, headers = paired
    payload = {
        "version": 1,
        "created_at": datetime.now(UTC).isoformat(),
        "salt": base64.urlsafe_b64encode(os.urandom(16)).decode().rstrip("="),
        "nonce": base64.urlsafe_b64encode(os.urandom(12)).decode().rstrip("="),
        "ciphertext": base64.urlsafe_b64encode(os.urandom(64)).decode().rstrip("="),
        "sha256": "0" * 64,
    }
    response = client.put(
        f"/backups/{pairing.device_id}",
        json=payload,
        headers=headers("nonce-bad-backup-hash-123"),
    )
    assert response.status_code == 400


def test_device_cannot_access_other_backup(client, paired):
    _, headers = paired
    response = client.get(
        "/backups/other-device-1234/latest",
        headers=headers("nonce-other-device-12345"),
    )
    assert response.status_code == 403
