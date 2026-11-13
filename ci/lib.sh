#!/usr/bin/env bash
# Shared settings. Sourced by the other scripts, never run on its own.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."          # always work from the repository root

MODULES=(config-server eureka-server api-gateway auth-service customer-service product-service
         inventory-service cart-service order-service payment-service review-service campaign-service
         support-service wishlist-service notification-service)

TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:0.57.1}"            # pinned: bump by hand or with Dependabot
GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-zricethezav/gitleaks:v8.21.2}"

log() { printf '\n==> %s\n' "$*"; }
summary() { if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then printf '%s\n' "$*" >> "$GITHUB_STEP_SUMMARY"; fi; }