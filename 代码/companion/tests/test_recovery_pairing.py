from __future__ import annotations

import base64
import hashlib
import json
import os
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime, timedelta

from life_companion.models import BackupEnvelope, PairCompleteRequest

SERVER_URL = "https://desktop.example.ts.net:8443"
SOURCE_DEVICE_ID = "source-device-1234"
TARGET_DEVICE_ID = "target-device-5678"


def _envelope() -> BackupEnvelope:
    ciphertext = base64.urlsafe_b64encode(os.urandom(96)).decode().rstrip("=")
    return BackupEnvelope(
        version=1,
        created_at=datetime.now(UTC),
        salt=base64.urlsafe_b64encode(os.urandom(16)).decode().rstrip("="),
        nonce=base64.urlsafe_b64encode(os.urandom(12)).decode().rstrip("="),
        ciphertext=ciphertext,
        sha256=hashlib.sha256(ciphertext.encode("utf-8")).hexdigest(),
    )


def _pair(app, device_id: str, name: str):
    code, _ = app.state.pairing.start_pairing()
    return app.state.pairing.complete_pairing(
        PairCompleteRequest(code=code, device_id=device_id, device_name=name)
    )


def _headers(pairing, nonce: str) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {pairing.token}",
        "X-Device-ID": pairing.device_id,
        "X-Request-Timestamp": str(int(datetime.now(UTC).timestamp())),
        "X-Request-Nonce": nonce,
    }


def _seed_source(app):
    source_pairing = _pair(app, SOURCE_DEVICE_ID, "旧手机")
    envelope = _envelope()
    receipt = app.state.backups.save(SOURCE_DEVICE_ID, envelope)
    return source_pairing, envelope, receipt


def _start_recovery(client, receipt):
    response = client.post(
        "/pair/recovery/start",
        json={
            "server_url": SERVER_URL,
            "source_device_id": SOURCE_DEVICE_ID,
            "backup_id": receipt.backup_id,
            "confirmed": True,
        },
    )
    assert response.status_code == 200
    return response.json()


def _complete(client, code: str, device_id: str = TARGET_DEVICE_ID, **extra):
    return client.post(
        "/pair/complete",
        json={
            "code": code,
            "device_id": device_id,
            "device_name": "新手机",
            **extra,
        },
    )


def test_normal_pairing_does_not_copy_an_old_backup(client, app):
    _seed_source(app)
    start = client.post("/pair/start", params={"server_url": SERVER_URL})
    assert start.status_code == 200

    completed = _complete(client, start.json()["code"])

    assert completed.status_code == 200
    assert completed.json()["recovery_backup_available"] is False
    assert completed.json()["recovery_backup_id"] is None
    assert app.state.backups.list(TARGET_DEVICE_ID) == []


def test_recovery_pairing_copies_only_ciphertext_and_preserves_device_isolation(
    client, app
):
    source_pairing, envelope, receipt = _seed_source(app)
    sources = client.get("/pair/recovery/sources")
    assert sources.status_code == 200
    assert sources.json() == [
        {
            "source_device_id": SOURCE_DEVICE_ID,
            "device_name": "旧手机",
            "backup_id": receipt.backup_id,
            "created_at": envelope.created_at.isoformat().replace("+00:00", "Z"),
            "byte_count": receipt.byte_count,
            "sha256": envelope.sha256,
        }
    ]
    assert "ciphertext" not in sources.text

    started = _start_recovery(client, receipt)
    completed = _complete(client, started["code"])

    assert completed.status_code == 200
    result = completed.json()
    assert result["recovery_backup_available"] is True
    copied = app.state.backups.exact(
        TARGET_DEVICE_ID, result["recovery_backup_id"]
    )
    assert copied == envelope
    assert app.state.backups.exact(SOURCE_DEVICE_ID, receipt.backup_id) == envelope

    target_pairing = type("Pairing", (), {"token": result["token"], "device_id": TARGET_DEVICE_ID})
    target_to_source = client.get(
        f"/backups/{SOURCE_DEVICE_ID}/latest",
        headers=_headers(target_pairing, "nonce-target-to-source-1234"),
    )
    source_to_target = client.get(
        f"/backups/{TARGET_DEVICE_ID}/latest",
        headers=_headers(source_pairing, "nonce-source-to-target-1234"),
    )
    assert target_to_source.status_code == 403
    assert source_to_target.status_code == 403


def test_recovery_selection_requires_confirmation_and_existing_backup(client, app):
    _seed_source(app)
    missing_id = "20260101T000000000000Z"
    unconfirmed = client.post(
        "/pair/recovery/start",
        json={
            "server_url": SERVER_URL,
            "source_device_id": SOURCE_DEVICE_ID,
            "backup_id": missing_id,
            "confirmed": False,
        },
    )
    missing = client.post(
        "/pair/recovery/start",
        json={
            "server_url": SERVER_URL,
            "source_device_id": SOURCE_DEVICE_ID,
            "backup_id": missing_id,
            "confirmed": True,
        },
    )

    assert unconfirmed.status_code == 422
    assert missing.status_code == 404


def test_selected_backup_tampering_fails_and_consumes_pairing_code(client, app, settings):
    _, _, receipt = _seed_source(app)
    started = _start_recovery(client, receipt)
    backup_path = (
        settings.data_dir
        / "backups"
        / SOURCE_DEVICE_ID
        / f"{receipt.backup_id}.json"
    )
    payload = json.loads(backup_path.read_text(encoding="utf-8"))
    payload["ciphertext"] = base64.urlsafe_b64encode(os.urandom(96)).decode().rstrip("=")
    backup_path.write_text(json.dumps(payload), encoding="utf-8")

    failed = _complete(client, started["code"])
    repeated = _complete(client, started["code"])

    assert failed.status_code == 400
    assert repeated.status_code == 401
    assert app.state.backups.list(TARGET_DEVICE_ID) == []


def test_expired_recovery_pairing_does_not_copy_backup(client, app):
    _, _, receipt = _seed_source(app)
    started = _start_recovery(client, receipt)
    app.state.pairing._pending_expires_at = datetime.now(UTC) - timedelta(seconds=1)

    completed = _complete(client, started["code"])

    assert completed.status_code == 401
    assert app.state.backups.list(TARGET_DEVICE_ID) == []


def test_phone_cannot_override_recovery_source(client, app):
    _, envelope, receipt = _seed_source(app)
    started = _start_recovery(client, receipt)
    injected = _complete(
        client,
        started["code"],
        source_device_id="attacker-device-9999",
        backup_id="20260101T000000000000Z",
    )
    completed = _complete(client, started["code"])

    assert injected.status_code == 422
    assert completed.status_code == 200
    assert app.state.backups.latest(TARGET_DEVICE_ID) == envelope


def test_later_source_backup_does_not_replace_explicit_selection(client, app):
    _, selected_envelope, selected_receipt = _seed_source(app)
    started = _start_recovery(client, selected_receipt)
    later_envelope = _envelope()
    app.state.backups.save(SOURCE_DEVICE_ID, later_envelope)

    completed = _complete(client, started["code"])

    assert completed.status_code == 200
    assert app.state.backups.latest(TARGET_DEVICE_ID) == selected_envelope
    assert app.state.backups.latest(TARGET_DEVICE_ID) != later_envelope


def test_same_recovery_code_can_only_complete_once_under_concurrency(client, app):
    _, _, receipt = _seed_source(app)
    started = _start_recovery(client, receipt)

    def complete_once(index: int) -> int:
        return _complete(
            client, started["code"], device_id=f"target-device-{index:04d}"
        ).status_code

    with ThreadPoolExecutor(max_workers=2) as executor:
        statuses = list(executor.map(complete_once, range(2)))

    assert sorted(statuses) == [200, 401]
    copied_count = sum(
        len(app.state.backups.list(f"target-device-{index:04d}")) for index in range(2)
    )
    assert copied_count == 1


def test_recovery_copy_is_idempotent_for_same_target(app):
    _, envelope, receipt = _seed_source(app)

    first = app.state.backups.copy_encrypted(
        SOURCE_DEVICE_ID, receipt.backup_id, receipt.sha256, TARGET_DEVICE_ID
    )
    second = app.state.backups.copy_encrypted(
        SOURCE_DEVICE_ID, receipt.backup_id, receipt.sha256, TARGET_DEVICE_ID
    )

    assert first.backup_id == second.backup_id
    assert app.state.backups.list(TARGET_DEVICE_ID) == [first]
    assert app.state.backups.latest(TARGET_DEVICE_ID) == envelope


def test_pairing_code_is_disabled_after_five_wrong_attempts(client, app):
    _, _, receipt = _seed_source(app)
    started = _start_recovery(client, receipt)
    wrong_code = "000000" if started["code"] != "000000" else "000001"

    for _ in range(app.state.pairing.MAX_FAILED_ATTEMPTS):
        assert _complete(client, wrong_code).status_code == 401

    assert _complete(client, started["code"]).status_code == 401
    assert app.state.backups.list(TARGET_DEVICE_ID) == []


def test_companion_disk_files_do_not_contain_health_plaintext(app, settings):
    _, _, receipt = _seed_source(app)
    sensitive_plaintext = "diagnosis=private-health-marker-92741"
    app.state.backups.copy_encrypted(
        SOURCE_DEVICE_ID, receipt.backup_id, receipt.sha256, TARGET_DEVICE_ID
    )

    for path in settings.data_dir.rglob("*"):
        if path.is_file():
            assert sensitive_plaintext not in path.read_text(encoding="utf-8")
