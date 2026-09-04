from __future__ import annotations

import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from urllib.parse import urlencode

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import HTMLResponse
from starlette.types import ASGIApp, Message, Receive, Scope, Send

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
    RecoveryBackupSource,
    RecoveryPairStartRequest,
    StatusResponse,
)
from .openai_service import ChatCompletionsClient, OpenAIService, ResponsesClient
from .security import (
    DeviceIdentity,
    PairingStore,
    RecoverySelection,
    ReplayProtector,
    extract_bearer,
    normalize_server_url,
    require_loopback,
)
from .settings import Settings


class _RequestTooLarge(HTTPException):
    """Internal signal used to stop a streaming request at its configured limit."""

    def __init__(self) -> None:
        super().__init__(status_code=413, detail="request too large")


class RequestLimitMiddleware:
    """Enforce request limits while ASGI delivers the body, including chunked bodies."""

    def __init__(
        self,
        app: ASGIApp,
        max_ai_body_bytes: int,
        max_backup_body_bytes: int,
    ) -> None:
        self.app = app
        self.max_ai_body_bytes = max_ai_body_bytes
        self.max_backup_body_bytes = max_backup_body_bytes

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        limit = (
            self.max_backup_body_bytes
            if scope.get("path", "").startswith("/backups/")
            else self.max_ai_body_bytes
        )
        headers = {
            key.lower(): value for key, value in scope.get("headers", [])
        }
        declared = headers.get(b"content-length")
        if declared is not None:
            try:
                declared_length = int(declared)
            except (TypeError, ValueError):
                await self._respond(send, 400, b"invalid content-length")
                return
            if declared_length < 0:
                await self._respond(send, 400, b"invalid content-length")
                return
            if declared_length > limit:
                await self._respond(send, 413, b"request too large")
                return

        received = 0
        response_started = False

        async def limited_receive() -> Message:
            nonlocal received
            message = await receive()
            if message.get("type") == "http.request":
                received += len(message.get("body", b""))
                if received > limit:
                    raise _RequestTooLarge
            return message

        async def tracked_send(message: Message) -> None:
            nonlocal response_started
            if message.get("type") == "http.response.start":
                response_started = True
            await send(message)

        try:
            await self.app(scope, limited_receive, tracked_send)
        except _RequestTooLarge:
            # Route handlers parse the body before emitting a response. If a
            # future streaming route has already started its response, there
            # is no legal way to replace it with a 413 status.
            if not response_started:
                await self._respond(send, 413, b"request too large")

    @staticmethod
    async def _respond(send: Send, status_code: int, body: bytes) -> None:
        await send(
            {
                "type": "http.response.start",
                "status": status_code,
                "headers": [
                    (b"content-type", b"text/plain; charset=utf-8"),
                    (b"content-length", str(len(body)).encode("ascii")),
                ],
            }
        )
        await send({"type": "http.response.body", "body": body})


def create_app(
    settings: Settings | None = None,
    credential_store: CredentialStore | None = None,
    responses_client: ResponsesClient | None = None,
    chat_completions_client: ChatCompletionsClient | None = None,
) -> FastAPI:
    active_settings = settings or Settings.load()
    active_settings.ensure_directories()
    credentials = credential_store or WindowsCredentialStore()
    pairing = PairingStore(active_settings)
    replay = ReplayProtector(active_settings)
    backups = BackupStore(active_settings.data_dir / "backups")
    ai = OpenAIService(
        active_settings,
        credentials,
        responses_client,
        chat_completions_client,
    )

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
    app.state.backups = backups
    app.state.ai = ai

    app.add_middleware(
        RequestLimitMiddleware,
        max_ai_body_bytes=active_settings.max_ai_body_bytes,
        max_backup_body_bytes=active_settings.max_backup_body_bytes,
    )

    def authenticated_device(request: Request) -> DeviceIdentity:
        device_id = request.headers.get("X-Device-ID", "")
        timestamp = request.headers.get("X-Request-Timestamp", "")
        nonce = request.headers.get("X-Request-Nonce", "")
        token = extract_bearer(request)
        identity = pairing.authenticate(device_id, token)
        replay.verify(device_id, timestamp, nonce)
        return identity

    def authenticated_ai_device(request: Request) -> DeviceIdentity:
        identity = authenticated_device(request)
        expected_provider = request.headers.get("X-AI-Provider", "")
        if expected_provider != active_settings.active_provider:
            raise HTTPException(
                status_code=409,
                detail="AI 提供商已变化，请先刷新状态并重新确认数据接收方",
            )
        return identity

    @app.get("/status", response_model=StatusResponse)
    def get_status() -> StatusResponse:
        return StatusResponse(
            version=__version__,
            ai_configured=ai.is_configured(),
            active_provider=active_settings.active_provider,
            provider_display_name=active_settings.provider.display_name,
            provider_is_third_party=active_settings.provider.third_party,
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
        provider_notice = (
            "<p style='color:#b3261e'>当前为第三方服务，健康上下文会发送给该提供商。</p>"
            if active_settings.provider.third_party
            else ""
        )
        return (
            "<!doctype html><html lang='zh-CN'><meta charset='utf-8'>"
            "<title>人生健康规划助手 - 电脑中转</title>"
            "<style>body{font:16px system-ui;max-width:720px;margin:40px auto;"
            "padding:0 20px;color:#202124}"
            "h1{font-size:28px}code{background:#f1f3f4;padding:3px 6px;border-radius:4px}</style>"
            "<h1>人生健康规划助手</h1>"
            f"<p>服务版本：<code>{__version__}</code></p>"
            f"<p>当前 AI：<strong>{active_settings.provider.display_name}</strong></p>"
            f"<p>当前密钥：{configured}</p>{provider_notice}"
            f"<h2>已配对设备</h2><ul>{rows}</ul>"
            "<p>使用桌面的 <code>Pair-Device.cmd</code> 生成一次性二维码。</p></html>"
        )

    @app.post("/pair/start", response_model=PairStartResponse)
    def start_pairing(request: Request, server_url: str) -> PairStartResponse:
        require_loopback(request)
        normalized_server_url = normalize_server_url(server_url)
        if normalized_server_url is None:
            raise HTTPException(
                status_code=400,
                detail="配对地址无效，只能使用 HTTPS 的 Tailscale（ts.net）地址",
            )
        code, expires_at = pairing.start_pairing()
        query = urlencode({"server": normalized_server_url, "code": code})
        return PairStartResponse(
            server_url=normalized_server_url,
            code=code,
            expires_at=expires_at,
            pairing_uri=f"lifehealth://pair?{query}",
        )

    @app.post("/pair/complete", response_model=PairCompleteResponse)
    def complete_pairing(payload: PairCompleteRequest) -> PairCompleteResponse:
        def copy_recovery(selection: RecoverySelection, target_device_id: str) -> str:
            return backups.copy_encrypted(
                selection.source_device_id,
                selection.backup_id,
                selection.sha256,
                target_device_id,
            ).backup_id

        return pairing.complete_pairing(payload, copy_recovery)

    @app.get("/pair/recovery/sources", response_model=list[RecoveryBackupSource])
    def list_recovery_sources(request: Request) -> list[RecoveryBackupSource]:
        require_loopback(request)
        names = {
            device["device_id"]: device["name"] for device in pairing.list_devices()
        }
        sources: list[RecoveryBackupSource] = []
        for device_id in backups.device_ids():
            for receipt in backups.list(device_id):
                sources.append(
                    RecoveryBackupSource(
                        source_device_id=device_id,
                        device_name=names.get(device_id, "历史设备"),
                        **receipt.model_dump(),
                    )
                )
        return sorted(
            sources,
            key=lambda source: (source.created_at, source.backup_id),
            reverse=True,
        )

    @app.post("/pair/recovery/start", response_model=PairStartResponse)
    def start_recovery_pairing(
        request: Request, payload: RecoveryPairStartRequest
    ) -> PairStartResponse:
        require_loopback(request)
        normalized_server_url = normalize_server_url(payload.server_url)
        if normalized_server_url is None:
            raise HTTPException(
                status_code=400,
                detail="配对地址无效，只能使用 HTTPS 的 Tailscale（ts.net）地址",
            )
        receipt = backups.describe(payload.source_device_id, payload.backup_id)
        selection = RecoverySelection(
            source_device_id=payload.source_device_id,
            backup_id=payload.backup_id,
            sha256=receipt.sha256,
        )
        code, expires_at = pairing.start_pairing(selection)
        query = urlencode({"server": normalized_server_url, "code": code})
        return PairStartResponse(
            server_url=normalized_server_url,
            code=code,
            expires_at=expires_at,
            pairing_uri=f"lifehealth://pair?{query}",
        )

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
        _: DeviceIdentity = Depends(authenticated_ai_device),
    ) -> PlanDraft:
        return ai.create_plan(payload)

    @app.post("/ai/chat", response_model=ChatReply)
    def chat(
        payload: ChatRequest,
        _: DeviceIdentity = Depends(authenticated_ai_device),
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
