# Windows Companion

The companion keeps the OpenAI API key on Windows and accepts authenticated requests from the paired Android app over Tailscale.

## Local development

```powershell
py -3.13 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\life-health-companion.exe set-key
.\.venv\Scripts\life-health-companion.exe serve
```

The API key is stored through Windows Credential Manager by `keyring`. It is never read from an environment file.

## Tailscale

The service binds to `127.0.0.1:8765`. `Start-Companion.cmd` publishes it only
inside the user's tailnet when Tailscale is signed in. The equivalent manual command is:

```powershell
tailscale serve --bg --https=8443 http://127.0.0.1:8765
```

Then run `life-health-companion pair` to create a one-time QR code.
