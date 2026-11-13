#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

revision="${GITHUB_SHA:-$(git rev-parse HEAD 2> /dev/null || echo unknown)}"

for module in "${MODULES[@]}"; do
  jars=("$module"/target/"$module"-*.jar)
  [ -f "${jars[0]}" ] || { echo "No jar for $module: run ci/build.sh first" >&2; exit 1; }
  args=(-f Dockerfile.ci --build-arg "MODULE=$module" -t "retail/$module:dev"
        --label "org.opencontainers.image.revision=$revision"
        --label "org.opencontainers.image.title=retail-$module")
  if [ -n "${REGISTRY:-}" ] && [ -n "${TAG:-}" ]; then
    args+=(-t "$REGISTRY/retail-$module:$TAG")
  fi
  log "Image for $module"
  docker build "${args[@]}" .
done