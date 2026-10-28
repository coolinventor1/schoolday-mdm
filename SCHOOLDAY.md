# SchoolDay Device Management

SchoolDay fork of MDMesh. Original Apache-2.0 license and notices remain intact.

Run `bash schoolday-bootstrap.sh` on the server. This builds the fork from source,
uses a separate `schoolday-mdm` Compose project and database volumes, and exposes
only `127.0.0.1:9095`. Forward that port over SSH for console access.

The generated `.env` and bootstrap admin credential file have owner-only permissions.
Credentials are stored outside the repository in `~/.local/share/schoolday-mdm/`.
The admin must change the temporary password at first login.

Automatic upstream updates are disabled, and the supervisor has no Docker socket
or write access to the checkout. Deploy reviewed fork changes explicitly.

Public console: https://mdm.schoolday.one. The dedicated Cloudflare tunnel uses
the user service `schoolday-mdm-tunnel.service`; the origin remains loopback-only.
The existing SchoolDay application and SSH tunnel are separate.

## Signed enrollment agent

Run `python scripts/setup-agent-sdk.py`, then `python scripts/build-schoolday-agent.py`
on the Windows build machine. The permanent signing key and its password live outside
Git in `~/SchoolDay-private-backups/mdm-signing/`. Keep these backed up: future agent
updates must retain the same signing certificate.

Deploy the release APK as `enrollment/schoolday-agent.apk` with public certificate
metadata in `enrollment/agent-metadata.json`, run
`python3 scripts/configure-schoolday-enrollment.py`, then rebuild/recreate Caddy and
the server. The web build embeds the release package, APK URL and Android provisioning
certificate checksum. The enrollment directory is mounted read-only into Caddy.

## Enrolling the Lenovo tablet

1. Sign into the public console and change the temporary administrator password.
2. Open Enroll and generate a fresh QR code with the intended configuration.
3. Only after backing up the tablet, remove Esper management/reset the tablet.
4. At Android's initial welcome screen, tap the blank area six times to start QR
   provisioning, connect to Wi-Fi, and scan the console's enrollment QR.
5. Verify the tablet appears online before applying kiosk restrictions or installing
   the separate SchoolDay PassPoint application.

Do not share enrollment QR codes: they contain authorization tokens (and Wi-Fi
credentials when supplied). HTTPS/download checks do not prove successful physical
tablet enrollment or unattended remote-control support. Esper ownership is not
changed by deploying this server.

## Fleet operations (agent v19 and PassPoint v9)

- Device control: media volume 0–100 (0 mutes), cooperative PassPoint restart, diagnostics.
- Health telemetry: UI heartbeat, foreground state, authenticated backend probe, app/agent versions, storage and media settings. Readings show timestamps and stale status.
- Diagnostics export: bounded metadata and event categories; never student records, cookies, PINs, raw logcat or screenshots.
- Persistent kiosk: assign a dedicated copy of the device configuration, pinned to the PassPoint library version. Original configuration and other devices remain available; empty exit PIN prevents on-device exit. Server reconciliation and boot reapply retain kiosk across agent updates.
- Devices: create groups, add/remove selected devices, filter groups, and apply existing bulk actions/configurations.
- App updates: scan installed versions, compare library releases, queue a checksum-verified update and inspect per-device command status. Publishing a new version of an existing package now creates a release instead of a duplicate app. Rescan after self-update because the old agent process cannot always acknowledge installation.
- No screen schedules or maintenance mode were added.

Commands: `device.volume` `{ "percent": 0..100 }`; `device.diagnostics` no payload; `device.passpointRestart` no payload. Each is capability gated. Diagnostics and restart run off the main thread.
