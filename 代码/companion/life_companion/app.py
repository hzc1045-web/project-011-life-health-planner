from __future__ import annotations

import time
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from urllib.parse import urlencode

from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import HTMLResponse

from . import __version__
from .backup_store import BackupStore
from .credential_store import CredentialStore, WindowsCredentialStore
from .models import (
    BackupEnvelope,
    BackupReceipt,
    ChatReply,
    ChatRequest,
    PairCompleteRequest,
    PairCompleteResponse,
    PairStartResponse,
    PlanDraft,
    PlanRequest,
    StatusResponse,
)
from .openai_service import OpenAIService, ResponsesClient
from .security import (
    DeviceIdentity,
    PairingStore,
    ReplayProtector,
    extract_bearer,
    require_loopback,
)
from .settings import Settings


def create_app(
    settings: Settings | None = None,
    credential_store: CredentialStore | None = None,
    responses_client: ResponsesClient | None = None,
) -> FastAPI:
    active_settings = settings or Settings.load()
    active_settings.ensure_directories()
    credentials = credential_store or WindowsCredentialStore()
    pairing = PairingStore(active_settings)
    replay = ReplayProtector(active_settings)
    backups = BackupStore(active_settings.data_dir / "backups")
    ai = OpenAIService(active_settings, credentials, responses_client)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield

    app = FastAPI(
        title="Life Health Planner Companion",
        version=__version__,
        docs_url=None,
        redoc_url=None,
        lifespan=lifespan,
    )
    app.state.settings = active_settings
    app.state.pairing = pairing
    app.state.ai = ai

    @app.middleware("http")
    async def request_limits(request: Request, call_next: Callable) -> Response:
        content_length = request.headers.get("content-length")
        limit = (
            active_settings.max_backup_body_bytes
            if request.url.path.startswith("/backups/")
            else active_settings.max_ai_body_bytes
        )
        if content_length:
            try:
                if int(content_length) > limit:
                    return Response(status_code=413, content="request too large")
            except ValueError:
                return Response(status_code=400, content="invalid content-length")
        return await call_next(request)

    def authenticated_device(request: Request) -> DeviceIdentity:
        device_id = request.headers.get("X-Device-ID", "")
        timestamp = request.headers.get("X-Request-Timestamp", "")
        nonce = request.headers.get("X-Request-Nonce", "")
        token = extract_bearer(request)
        identity = pairing.authenticate(device_id, token)
        replay.verify(device_id, timestamp, nonce)
        return identity

    @app.get("/status", response_model=StatusResponse)
    def get_status() -> StatusResponse:
        return StatusResponse(
            version=__version__,
            ai_configured=ai.is_configured(),
            planning_model=active_settings.planning_model,
            economy_model=active_settings.economy_model,
        )

    @app.get("/setup", response_class=HTMLResponse)
    def setup_page(request: Request) -> str:
        require_loopback(request)
        rows = (
            "".join(
                f"<li>{device['name']} ({device['device_id']})</li>"
                for device in pairing.list_devices()
            )
            or "<li>尚未配对设备</li>"
        )
        configured = "已配置" if ai.is_configured() else "未配置"
        return (
            "<!doctype html><html lang='zh-CN'><meta charset='utf-8'>"
            "<title>人生健康规划助手 - 电脑中转</title>"
            "<style>body{font:16px system-ui;max-width:720px;margin:40px auto;"
            "padding:0 20px;color:#202124}"
            "h1{font-size:28px}code{background:#f1f3f4;padding:3px 6px;border-radius:4px}</style>"
            "<h1>人生健康规划助手</h1>"
            f"<p>服务版本：<code>{__version__}</code></p><p>OpenAI 密钥：{configured}</p>"
            f"<h2>已配对设备</h2><ul>{rows}</ul>"
            "<p>使用桌面的 <code>Pair-Device.cmd</code> 生成一次性二维码。</p></html>"
        )

    @app.post("/pair/start", response_model=PairStartResponse)
    def start_pairing(request: Request, server_url: str) -> PairStartResponse:
        require_loopback(request)
        if not server_url.startswith("https://") and not server_url.startswith("http://10.0.2.2"):
            raise HTTPException(status_code=400, detail="配对地址必须使用 HTTPS")
        code, expires_at = pairing.start_pairing()
        query = urlencode({"server": server_url.rstrip("/"), "code": code})
        return PairStartResponse(
            server_url=server_url.rstrip("/"),
            code=code,
            expires_at=expires_at,
            pairing_uri=f"lifehealth://pair?{query}",
        )

    @app.post("/pair/complete", response_model=PairCompleteResponse)
    def complete_pairing(payload: PairCompleteRequest) -> PairCompleteResponse:
        return pairing.complete_pairing(payload)

    @app.get("/pair/devices")
    def list_devices(request: Request) -> list[dict[str, str]]:
        require_loopback(request)
        return pairing.list_devices()

    @app.delete("/pair/{device_id}")
    def revoke_device(request: Request, device_id: str) -> dict[str, bool]:
        require_loopback(request)
        return {"revoked": pairing.revoke(device_id)}

    @app.post("/ai/plan", response_model=PlanDraft)
    def create_plan(
        payload: PlanRequest,
        _: DeviceIdentity = Depends(authenticated_device),
    ) -> PlanDraft:
        return ai.create_plan(payload)

    @app.post("/ai/chat", response_model=ChatReply)
    def chat(
        payload: ChatRequest,
        _: DeviceIdentity = Depends(authenticated_device),
    ) -> ChatReply:
        return ai.chat(payload)

    @app.put("/backups/{device_id}", response_model=BackupReceipt)
    def upload_backup(
        device_id: str,
        payload: BackupEnvelope,
        device: DeviceIdentity = Depends(authenticated_device),
    ) -> BackupReceipt:
        if device.device_id != device_id:
            raise HTTPException(status_code=403, detail="设备无权写入此备份")
        return backups.save(device_id, payload)

    @app.get("/backups/{device_id}/latest", response_model=BackupEnvelope)
    def latest_backup(
        device_id: str,
        device: DeviceIdentity = Depends(authenticated_device),
    ) -> BackupEnvelope:
        if device.device_id != device_id:
            raise HTTPException(status_code=403, detail="设备无权读取此备份")
        return backups.latest(device_id)

    @app.get("/backups/{device_id}", response_model=list[BackupReceipt])
    def list_backups(
        device_id: str,
        device: DeviceIdentity = Depends(authenticated_device),
    ) -> list[BackupReceipt]:
        if device.device_id != device_id:
            raise HTTPException(status_code=403, detail="设备无权读取此备份")
        return backups.list(device_id)

    @app.delete("/backups/{device_id}")
    def delete_backups(
        device_id: str,
        device: DeviceIdentity = Depends(authenticated_device),
    ) -> dict[str, int]:
        if device.device_id != device_id:
            raise HTTPException(status_code=403, detail="设备无权删除此备份")
        return {"deleted": backups.delete_all(device_id)}

    @app.get("/internal/time")
    def current_time_for_tests(request: Request) -> dict[str, int]:
        require_loopback(request)
        return {"unix": int(time.time())}

    return app


app = create_app()
