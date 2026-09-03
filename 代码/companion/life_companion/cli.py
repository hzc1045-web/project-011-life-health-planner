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
from .models import PairStartResponse
from .security import PairingStore
from .settings import Settings


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


def cmd_set_key(_: argparse.Namespace) -> int:
    key = getpass.getpass("OpenAI API key (input hidden): ")
    WindowsCredentialStore().set_api_key(key)
    print("API key saved in Windows Credential Manager.")
    return 0


def cmd_clear_key(_: argparse.Namespace) -> int:
    WindowsCredentialStore().clear_api_key()
    print("API key removed from Windows Credential Manager.")
    return 0


def cmd_pair(args: argparse.Namespace) -> int:
    settings = Settings.load()
    settings.ensure_directories()
    server_url = args.server_url or _tailscale_server_url()
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
    output = settings.data_dir / "pairing-qr.png"
    qrcode.make(response.pairing_uri).save(output)
    print(f"Server: {response.server_url}")
    print(f"Code: {response.code}")
    print(f"Expires: {response.expires_at.isoformat()}")
    print(f"QR: {output}")
    webbrowser.open(output.as_uri())
    return 0


def cmd_serve(_: argparse.Namespace) -> int:
    settings = Settings.load()
    uvicorn.run(create_app(settings), host=settings.host, port=settings.port, access_log=True)
    return 0


def cmd_status(_: argparse.Namespace) -> int:
    settings = Settings.load()
    credentials = WindowsCredentialStore()
    payload = settings.public_dict() | {
        "api_key_configured": bool(credentials.get_api_key()),
        "devices": PairingStore(settings).list_devices(),
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="life-health-companion")
    subcommands = parser.add_subparsers(required=True)
    commands = {
        "set-key": cmd_set_key,
        "clear-key": cmd_clear_key,
        "pair": cmd_pair,
        "serve": cmd_serve,
        "status": cmd_status,
    }
    for name, function in commands.items():
        command = subcommands.add_parser(name)
        if name == "pair":
            command.add_argument("--server-url")
        command.set_defaults(handler=function)
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
