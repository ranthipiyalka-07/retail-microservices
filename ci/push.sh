#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"
: "${REGISTRY:?}" "${TAG:?}"

for module in "${MODULES[@]}"; do
  docker push --all-tags "$REGISTRY/retail-$module"
done