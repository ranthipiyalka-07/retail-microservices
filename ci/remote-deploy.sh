#!/usr/bin/env bash
set -euo pipefail
cd "${DEPLOY_PATH:?}"
: "${REGISTRY:?}" "${TAG:?}"
export REGISTRY

previous="$(cat .deployed-tag 2> /dev/null || true)"

deploy() {
  export TAG="$1"
  docker compose pull --quiet
  docker compose up -d --remove-orphans --no-build --wait --wait-timeout 900
}

echo "Deploying $TAG (currently: ${previous:-nothing})"
if deploy "$TAG"; then
  echo "$TAG" > .deployed-tag
  docker compose ps
  exit 0
fi

echo "Deploying $TAG failed" >&2
docker compose ps -a || true
if [ -n "$previous" ] && [ "$previous" != "$TAG" ]; then
  echo "Rolling back to $previous" >&2
  deploy "$previous" || echo "The rollback failed too: manual action needed" >&2
fi
exit 1