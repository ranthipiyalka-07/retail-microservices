#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

cache="${TRIVY_CACHE:-$HOME/.cache/trivy}"
mkdir -p "$cache"
ignore=()
if [ -f .trivyignore ]; then
  ignore=(-v "$PWD/.trivyignore:/.trivyignore:ro" -e TRIVY_IGNOREFILE=/.trivyignore)
fi

failed=()
for module in "${MODULES[@]}"; do
  log "Scanning retail/$module:dev"
  if ! docker run --rm \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v "$cache:/root/.cache/trivy" ${ignore[@]+"${ignore[@]}"} \
        "$TRIVY_IMAGE" image --no-progress --scanners vuln \
        --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 "retail/$module:dev"; then
    failed+=("$module")
  fi
done

if [ "${#failed[@]}" -gt 0 ]; then
  summary "### Image scan: HIGH or CRITICAL vulnerabilities with a fix in: ${failed[*]}"
  echo "Vulnerable images: ${failed[*]}" >&2
  exit 1
fi