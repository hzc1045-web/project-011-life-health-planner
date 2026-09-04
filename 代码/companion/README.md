# Windows Companion

The companion keeps separate DeepSeek official and AI Xiaozhan API keys on Windows and accepts authenticated requests from the paired Android app over Tailscale. DeepSeek is the default; switching to the third-party AI Xiaozhan route requires explicit acknowledgement.

## Local development

```powershell
py -3.13 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -e ".[dev]"
.\.venv\Scripts\life-health-companion.exe set-key --provider deepseek
.\.venv\Scripts\life-health-companion.exe set-key --provider subkkai
.\.venv\Scripts\life-health-companion.exe use-provider deepseek
.\.venv\Scripts\life-health-companion.exe use-provider subkkai --acknowledge-third-party
.\.venv\Scripts\life-health-companion.exe serve
```

Each provider key is split into safe-size chunks and stored through Windows Credential Manager by `keyring`. Keys are never read from an environment file. Provider choice is manual and there is no automatic cross-provider fallback.

## Tailscale

The service binds to `127.0.0.1:8765`. `Start-Companion.cmd` publishes it only
inside the user's tailnet when Tailscale is signed in. The equivalent manual command is:

```powershell
tailscale serve --bg --https=8443 http://127.0.0.1:8765
```

Then run `life-health-companion pair` to create a one-time QR code.

For a fresh Android installation, run `life-health-companion recover-pair`. Select the
exact encrypted backup on the computer and type `RESTORE`; the resulting one-time QR
code pairs the new device and makes only that selected ciphertext available to it.
The recovery password remains on Android and is never sent to the companion.
