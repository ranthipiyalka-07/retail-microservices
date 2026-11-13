#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

: "${DEPLOY_HOST:?}" "${DEPLOY_USER:?}" "${DEPLOY_PATH:?}" "${DEPLOY_SSH_KEY:?}" "${DEPLOY_KNOWN_HOSTS:?}" "${REGISTRY:?}" "${TAG:?}"
[[ "$TAG" =~ ^[A-Za-z0-9._-]{1,64}$ ]] || { echo "Invalid TAG" >&2; exit 1; }
[[ "$REGISTRY" =~ ^[a-z0-9./:_-]+$ ]] || { echo "Invalid REGISTRY" >&2; exit 1; }
[[ "$DEPLOY_PATH" =~ ^/[A-Za-z0-9._/-]+$ ]] || { echo "Invalid DEPLOY_PATH" >&2; exit 1; }

install -d -m 700 ~/.ssh
printf '%s\n' "$DEPLOY_SSH_KEY" > ~/.ssh/deploy_key
chmod 600 ~/.ssh/deploy_key
printf '%s\n' "$DEPLOY_KNOWN_HOSTS" > ~/.ssh/known_hosts

SSH_OPTS=(-i ~/.ssh/deploy_key -p "${DEPLOY_PORT:-22}" -o StrictHostKeyChecking=yes -o BatchMode=yes)
ssh_run() { ssh "${SSH_OPTS[@]}" "$DEPLOY_USER@$DEPLOY_HOST" "$@"; }

echo "Syncing compose files to $DEPLOY_HOST"
RSYNC_SSH="ssh ${SSH_OPTS[*]}"
rsync -a -e "$RSYNC_SSH" docker-compose.yml docker-compose.tls.yml docker-compose.vault.yml docker-compose.deploy.yml \
  "$DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/"
for dir in docker/vault docker/elk docker/mysql; do
  [ -d "$dir" ] && rsync -a -e "$RSYNC_SSH" "$dir" "$DEPLOY_USER@$DEPLOY_HOST:$DEPLOY_PATH/docker/"
done

if [ -n "${REGISTRY_TOKEN:-}" ]; then
  printf '%s' "$REGISTRY_TOKEN" | ssh_run "docker login ${REGISTRY%%/*} -u '${REGISTRY_USER:-token}' --password-stdin"
fi

ssh_run "DEPLOY_PATH='$DEPLOY_PATH' REGISTRY='$REGISTRY' TAG='$TAG' bash -s" < ci/remote-deploy.sh