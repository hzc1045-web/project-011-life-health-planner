from __future__ import annotations

import argparse
import getpass
import json
import os
import subprocess
import sys
import webbrowser
from shutil import which
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

import qrcode
import uvicorn

from .app import create_app
from .credential_store import WindowsCredentialStore
from .models import PairStartResponse, RecoveryBackupSource
from .security import PairingStore, normalize_server_url
from .settings import PROVIDERS, Settings


def _tailscale_executable() -> str:
    executable = which("tailscale")
    if executable:
        return executable
    for root_name in ("ProgramFiles", "ProgramW6432"):
        root = os.environ.get(root_name)
        if root:
            candidate = os.path.join(root, "Tailscale", "tailscale.exe")
            if os.path.isfile(candidate):
                return candidate
    raise RuntimeError("Tailscale is not installed")


def _tailscale_server_url(port: int = 8443) -> str:
    executable = _tailscale_executable()
    try:
        process = subprocess.run(  # noqa: S603
            [executable, "status", "--json"],
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        status = json.loads(process.stdout)
        dns_name = status.get("Self", {}).get("DNSName", "").rstrip(".")
        if dns_name:
            return f"https://{dns_name}:{port}"
    except (OSError, subprocess.CalledProcessError, json.JSONDecodeError):
        pass
    raise RuntimeError("Tailscale is not connected or MagicDNS is unavailable")


def cmd_set_key(args: argparse.Namespace) -> int:
    settings = Settings.load()
    provider_id = args.provider or settings.active_provider
    key = getpass.getpass("AI API key (input hidden): ")
    WindowsCredentialStore().set_api_key(provider_id, key)
    print(f"{PROVIDERS[provider_id].display_name} API key saved in Windows Credential Manager.")
    return 0


def cmd_clear_key(args: argparse.Namespace) -> int:
    settings = Settings.load()
    provider_id = args.provider or settings.active_provider
    WindowsCredentialStore().clear_api_key(provider_id)
    print(f"{PROVIDERS[provider_id].display_name} API key removed from Windows Credential Manager.")
    return 0


def cmd_use_provider(args: argparse.Namespace) -> int:
    settings = Settings.load()
    settings.activate_provider(args.provider, args.acknowledge_third_party)
    print(f"Active AI provider: {PROVIDERS[args.provider].display_name}")
    print("Restart the companion to apply this change.")
    return 0


def cmd_pair(args: argparse.Namespace) -> int:
    settings = Settings.load()
    settings.ensure_directories()
    server_url = args.server_url or _tailscale_server_url()
    server_url = normalize_server_url(server_url)
    if server_url is None:
        raise RuntimeError(
            "Invalid pairing server URL; use an HTTPS Tailscale (*.ts.net) address"
        )
    start_url = (
        f"http://127.0.0.1:{settings.port}/pair/start?"
        + urlencode({"server_url": server_url})
    )
    try:
        request = Request(start_url, method="POST")
        with urlopen(request, timeout=5) as result:  # noqa: S310
            response = PairStartResponse.model_validate_json(result.read())
    except (OSError, URLError) as error:
        raise RuntimeError("Start the companion before creating a pairing code") from error
    _show_pairing_qr(settings, response, "pairing-qr.png")
    return 0


def _show_pairing_qr(
    settings: Settings, response: PairStartResponse, filename: str
) -> None:
    output = settings.data_dir / filename
    qrcode.make(response.pairing_uri).save(output)
    print(f"Server: {response.server_url}")
    print(f"Code: {response.code}")
    print(f"Expires: {response.expires_at.isoformat()}")
    print(f"QR: {output}")
    webbrowser.open(output.as_uri())


def cmd_recover_pair(args: argparse.Namespace) -> int:
    settings = Settings.load()
    settings.ensure_directories()
    server_url = args.server_url or _tailscale_server_url()
    server_url = normalize_server_url(server_url)
    if server_url is None:
        raise RuntimeError(
            "Invalid pairing server URL; use an HTTPS Tailscale (*.ts.net) address"
        )
    base_url = f"http://127.0.0.1:{settings.port}"
    try:
        with urlopen(f"{base_url}/pair/recovery/sources", timeout=5) as result:  # noqa: S310
            sources_payload = json.loads(result.read())
        sources = [RecoveryBackupSource.model_validate(item) for item in sources_payload]
    except (OSError, URLError, ValueError) as error:
        raise RuntimeError("Start the companion before selecting a recovery backup") from error
    if not sources:
        raise RuntimeError("No encrypted backups are available for recovery")

    print("Available encrypted backups:")
    for index, source in enumerate(sources, start=1):
        safe_name = "".join(
            character if character.isprintable() else "?"
            for character in source.device_name
        )
        print(
            f"{index}. {safe_name} | {source.created_at.isoformat()} | "
            f"{source.byte_count} bytes | SHA-256 {source.sha256[:12]}..."
        )
    try:
        selected_index = int(input("Select backup number: ").strip()) - 1
        selected = sources[selected_index]
    except (ValueError, IndexError) as error:
        raise RuntimeError("Invalid backup selection") from error
    if selected_index < 0:
        raise RuntimeError("Invalid backup selection")
    confirmation = input("Type RESTORE to confirm copying this encrypted backup: ").strip()
    if confirmation != "RESTORE":
        raise RuntimeError("Recovery pairing cancelled")

    payload = json.dumps(
        {
            "server_url": server_url,
            "source_device_id": selected.source_device_id,
            "backup_id": selected.backup_id,
            "confirmed": True,
        }
    ).encode("utf-8")
    start_request = Request(  # noqa: S310
        f"{base_url}/pair/recovery/start",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urlopen(start_request, timeout=5) as result:  # noqa: S310
            response = PairStartResponse.model_validate_json(result.read())
    except (OSError, URLError, ValueError) as error:
        raise RuntimeError("Unable to start recovery pairing") from error
    _show_pairing_qr(settings, response, "recovery-pairing-qr.png")
    return 0


def cmd_serve(_: argparse.Namespace) -> int:
    settings = Settings.load()
    uvicorn.run(create_app(settings), host=settings.host, port=settings.port, access_log=True)
    return 0


def cmd_status(_: argparse.Namespace) -> int:
    settings = Settings.load()
    credentials = WindowsCredentialStore()
    payload = settings.public_dict() | {
        "api_key_configured": bool(credentials.get_api_key(settings.active_provider)),
        "configured_providers": {
            provider_id: bool(credentials.get_api_key(provider_id))
            for provider_id in PROVIDERS
        },
        "devices": PairingStore(settings).list_devices(),
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="life-health-companion")
    subcommands = parser.add_subparsers(required=True)
    commands = {
        "pair": cmd_pair,
        "recover-pair": cmd_recover_pair,
        "serve": cmd_serve,
        "status": cmd_status,
    }
    for name, function in commands.items():
        command = subcommands.add_parser(name)
        if name in {"pair", "recover-pair"}:
            command.add_argument("--server-url")
        command.set_defaults(handler=function)
    set_key = subcommands.add_parser("set-key")
    set_key.add_argument("--provider", choices=PROVIDERS)
    set_key.set_defaults(handler=cmd_set_key)
    clear_key = subcommands.add_parser("clear-key")
    clear_key.add_argument("--provider", choices=PROVIDERS)
    clear_key.set_defaults(handler=cmd_clear_key)
    use_provider = subcommands.add_parser("use-provider")
    use_provider.add_argument("provider", choices=PROVIDERS)
    use_provider.add_argument("--acknowledge-third-party", action="store_true")
    use_provider.set_defaults(handler=cmd_use_provider)
    return parser


def main() -> None:
    try:
        args = build_parser().parse_args()
        raise SystemExit(args.handler(args))
    except (RuntimeError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(1) from error


if __name__ == "__main__":
    main()
