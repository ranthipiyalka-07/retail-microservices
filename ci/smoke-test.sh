#!/usr/bin/env bash
# Needs: Docker Compose v2, java 17+, curl, jq. CONFIG_REPO_PATH = a clone of the config repo.
source "$(dirname "$0")/lib.sh"

CONFIG_REPO_PATH="$(cd "${CONFIG_REPO_PATH:?set CONFIG_REPO_PATH to the config repo checkout}" && pwd)"
export COMPOSE_FILE="docker-compose.yml:docker-compose.tls.yml:docker-compose.vault.yml"
SERVICES="${SMOKE_SERVICES:-mysql kafka mailhog vault vault-init config-server eureka-server auth-service customer-service product-service notification-service api-gateway}"
CA="docker/tls/ca/ca.crt"
BASE="https://localhost:8080"
BODY="$(mktemp)"
failures=0

pass() { echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; failures=$((failures + 1)); }
check() { if [ "$2" = "$3" ]; then pass "$1 (HTTP $3)"; else fail "$1: expected HTTP $2, got $3"; fi; }
http() { curl -sS -o "$BODY" -w '%{http_code}' --cacert "$CA" --max-time 30 "$@" 2> /dev/null || true; }

collect() {
  mkdir -p ci-logs
  docker compose ps -a > ci-logs/ps.txt 2>&1 || true
  docker compose logs --no-color --timestamps > ci-logs/compose.log 2>&1 || true
  if [ "${KEEP_STACK:-false}" != "true" ]; then
    docker compose down -v --remove-orphans > /dev/null 2>&1 || true
  fi
  rm -f "$BODY"
}
trap collect EXIT

log "Secrets and certificates for the test stack"
[ -f .env ] || java GenEnv.java "$CONFIG_REPO_PATH"
[ -d docker/tls/out ] || java GenTls.java

log "Starting: $SERVICES"
# shellcheck disable=SC2086
docker compose up -d --no-build --wait --wait-timeout 900 $SERVICES

log "Checks"
code=$(http "$BASE/actuator/health");                          check "gateway health over TLS" 200 "$code"
plain=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 http://localhost:8080/actuator/health || true)
if [ "$plain" != "200" ]; then pass "plain HTTP is not served (got $plain)"; else fail "plain HTTP answered 200"; fi

code=$(http "$BASE/api/v1/products");                          check "public catalog" 200 "$code"
code=$(http "$BASE/api/v1/orders");                            check "protected path without a token" 401 "$code"
code=$(http -X POST "$BASE/internal/inventory/reservations");  check "internal path through the gateway" 401 "$code"

email="ci-$(date +%s)-$RANDOM@example.com"
payload=$(jq -n --arg e "$email" '{firstName:"Ci",lastName:"Smoke",email:$e,password:"Password1",phone:"+94770000099"}')
code=$(http -X POST -H 'Content-Type: application/json' -d "$payload" "$BASE/api/v1/customers/register")
check "customer registration" 201 "$code"

login=$(jq -n --arg e "$email" '{email:$e,password:"Password1"}')
code=$(http -X POST -H 'Content-Type: application/json' -d "$login" "$BASE/api/v1/auth/login"); check "customer login" 200 "$code"
token=$(jq -r '.accessToken // empty' "$BODY")
if [ -n "$token" ]; then pass "access token issued"; else fail "no access token in the login answer"; fi
code=$(http -H "Authorization: Bearer $token" "$BASE/api/v1/customers/me");     check "profile with the token (gateway and service validate the JWT)" 200 "$code"
code=$(http -H "Authorization: Bearer $token" "$BASE/api/v1/notifications");    check "customer cannot read the email log" 403 "$code"

admin_pw=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
admin=$(jq -n --arg p "$admin_pw" '{email:"admin@retail.local",password:$p}')
code=$(http -X POST -H 'Content-Type: application/json' -d "$admin" "$BASE/api/v1/auth/login"); check "admin login" 200 "$code"
admin_token=$(jq -r '.accessToken // empty' "$BODY")
code=$(http -H "Authorization: Bearer $admin_token" "$BASE/api/v1/notifications"); check "admin reads the email log" 200 "$code"

found=false
for _ in $(seq 1 45); do
  if curl -fsS http://localhost:8025/api/v2/messages \
      | jq -e --arg e "$email" '[.items[] | select(.Content.Headers.To[0] == $e)] | length > 0' > /dev/null 2>&1; then
    found=true
    break
  fi
  sleep 2
done
if [ "$found" = "true" ]; then pass "welcome email delivered (Kafka outbox to notification-service)"; else fail "no welcome email for $email within 90 seconds"; fi

if curl -fsS --cacert "$CA" --max-time 5 https://localhost:8082/actuator/health > /dev/null 2>&1; then
  fail "customer-service answered a client without a certificate"
else
  pass "customer-service refuses clients without a certificate (mutual TLS)"
fi

if [ "${CHECK_CLEAN_ENV:-true}" = "true" ]; then
  if docker compose exec -T customer-service env | grep -Eq '^(AES_KEY|INTERNAL_API_KEY|JWT_PRIVATE_KEY|ADMIN_PASSWORD)='; then
    fail "an application secret is still in the container environment (finish the cleanup step of the secrets guide)"
  else
    pass "no application secrets in the container environment"
  fi
fi

[ "$failures" -eq 0 ] || { echo "$failures check(s) failed" >&2; exit 1; }
log "All smoke checks passed"