#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

args=(detect --source=/repo --no-banner --redact)
[ -f ci/gitleaks-baseline.json ] && args+=(--baseline-path=/repo/ci/gitleaks-baseline.json)

log "Scanning the repository history for secrets"
docker run --rm -u "$(id -u):$(id -g)" \
  -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0=/repo \
  -v "$PWD:/repo:ro" "$GITLEAKS_IMAGE" "${args[@]}"