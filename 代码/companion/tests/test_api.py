from __future__ import annotations

import base64
import hashlib
import os
from datetime import UTC, datetime


def test_status_is_public_and_does_not_expose_key(client):
    response = client.get("/status")
    assert response.status_code == 200
    assert response.json()["ai_configured"] is True
    assert "api_key" not in response.text


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
    assert "sk-test" not in str(call)


def test_replay_nonce_is_rejected(client, paired, plan_payload):
    _, headers = paired
    fixed = headers("nonce-replay-123456789")
    assert client.post("/ai/plan", json=plan_payload, headers=fixed).status_code == 200
    assert client.post("/ai/plan", json=plan_payload, headers=fixed).status_code == 409


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
