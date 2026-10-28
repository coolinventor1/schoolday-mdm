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

This is a console pilot. Before enrolling a tablet, configure a reachable HTTPS
BASE_URL, build and sign the Android agent, and configure its verified APK URL and
signing-certificate checksum. Do not enroll using upstream debug defaults.
Esper ownership has not been changed by this deployment.
