#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
umask 077
if [ ! -f .env ]; then
  printf 'DB_NAME=mdmesh\nDB_USER=mdmesh\nDB_PASSWORD=%s\nHASH_SECRET=%s\nBASE_URL=http://localhost:9095\nSITE_ADDRESS=:80\nIMAGE_OWNER=local\nGITHUB_REPO=coolinventor1/schoolday-mdm\nAUTO_UPDATE=0\nAPPLY_SUPPORTED=0\nCOMPOSE_PROJECT_NAME=schoolday-mdm\nCOMPOSE_FILE=docker-compose.yml:docker-compose.schoolday.yml\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 24)" > .env
fi
export COMPOSE_PROJECT_NAME=schoolday-mdm
export COMPOSE_FILE=docker-compose.yml:docker-compose.schoolday.yml
docker compose up -d --build
ready=0
for attempt in $(seq 1 60); do
  if docker compose exec -T -u mdmesh server cat /opt/mdmesh/initialized.txt 2>/dev/null | grep -q '^OK'; then ready=1; break; fi
  sleep 5
done
[ "$ready" = 1 ] || { echo 'Server initialization failed'; exit 1; }
. ./install/lib/db.sh
PSQL=(docker compose exec -T postgres psql -U mdmesh -d mdmesh)
case "$(mdm_db_state)" in
  fresh)
    password=$(mdm_rand)
    token=$(openssl rand -hex 16)
    mdm_seed support@schoolday.one install/sql/hmdm_init.en.sql "$password" "$token"
    mkdir -p "$HOME/.local/share/schoolday-mdm"
    printf 'URL: http://localhost:9095\nUsername: admin\nTemporary password: %s\nChange password at first login.\n' "$password" > "$HOME/.local/share/schoolday-mdm/admin-login.txt"
    ;;
  seeded) ;;
  *) echo 'Unexpected database state; refusing to seed'; exit 1 ;;
esac
mdm_post_seed install/sql/post_seed.sql
curl --fail --silent http://127.0.0.1:9095/healthz
echo 'SchoolDay MDM running; admin credentials stored privately on server.'
