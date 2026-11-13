#!/bin/sh
set -eu

export VAULT_ADDR="http://vault:8200"
STATE="/vault-init/init.txt"                 # unseal key and root token: DEVELOPMENT ONLY
BOOT="/bootstrap"                            # mounted into config-server only
SERVICES="auth-service customer-service product-service inventory-service cart-service order-service payment-service review-service campaign-service support-service wishlist-service notification-service"

say() { echo "vault-init: $*"; }
rand_hex() { head -c "$1" /dev/urandom | od -An -tx1 | tr -d ' \n'; }
rand_b64() { head -c "$1" /dev/urandom | base64 | tr -d '\n'; }
status_code() { c=0; vault status > /dev/null 2>&1 || c=$?; echo "$c"; }
is_sealed() { vault status -format=json 2> /dev/null | grep -q '"sealed": true'; }
has_secret() { vault kv get "secret/$1" > /dev/null 2>&1; }
need() { [ -n "$2" ] || { say "$1 is empty: keep it in .env for the first start (see the guide)"; exit 1; }; }

say "waiting for Vault"
until [ "$(status_code)" != "1" ]; do sleep 2; done          # 0 = unsealed, 2 = sealed, 1 = not reachable

if vault status -format=json 2> /dev/null | grep -q '"initialized": false'; then
  say "initializing Vault (1 key share: development only)"
  vault operator init -key-shares=1 -key-threshold=1 > "$STATE"
  chmod 600 "$STATE"
fi
[ -s "$STATE" ] || { say "Vault is initialized but $STATE is missing: restore the vault-init-data volume, or wipe vault-data to start over"; exit 1; }
UNSEAL_KEY="$(awk '/^Unseal Key 1:/ {print $NF}' "$STATE")"
ROOT_TOKEN="$(awk '/^Initial Root Token:/ {print $NF}' "$STATE")"

unseal() {
  if is_sealed; then
    vault operator unseal "$UNSEAL_KEY" > /dev/null && say "unsealed"
  fi
}
unseal || { say "could not unseal Vault"; exit 1; }
export VAULT_TOKEN="$ROOT_TOKEN"

vault secrets list -format=json | grep -q '"secret/"' || vault secrets enable -path=secret -version=2 kv > /dev/null
vault audit list -format=json 2> /dev/null | grep -q '"file/"' \
  || vault audit enable file file_path=/vault/logs/audit.log > /dev/null \
  || say "could not enable the audit device"

# First start only: import the values that exist today so nothing already encrypted becomes unreadable.
if ! has_secret application; then
  need DB_PASSWORD "${IMPORT_DB_PASSWORD:-}"
  need AES_KEY "${IMPORT_AES_KEY:-}"
  vault kv put secret/application \
    "retail.internal.api-key=${IMPORT_INTERNAL_API_KEY:-$(rand_hex 24)}" \
    "retail.security.aes-key=${IMPORT_AES_KEY}" \
    "spring.datasource.password=${IMPORT_DB_PASSWORD}" > /dev/null
  say "created secret/application"
fi
if ! has_secret auth-service; then
  need JWT_PRIVATE_KEY "${IMPORT_JWT_PRIVATE_KEY:-}"
  need JWT_PUBLIC_KEY "${IMPORT_JWT_PUBLIC_KEY:-}"
  need ADMIN_PASSWORD "${IMPORT_ADMIN_PASSWORD:-}"
  vault kv put secret/auth-service \
    "retail.jwt.private-key=${IMPORT_JWT_PRIVATE_KEY}" \
    "retail.jwt.public-key=${IMPORT_JWT_PUBLIC_KEY}" \
    "retail.auth.bootstrap-admin.email=${IMPORT_ADMIN_EMAIL:-admin@retail.local}" \
    "retail.auth.bootstrap-admin.password=${IMPORT_ADMIN_PASSWORD}" > /dev/null
  say "created secret/auth-service"
fi
# Not under an application name, so config-server never serves it to a client
if ! has_secret platform/config-encrypt; then
  vault kv put secret/platform/config-encrypt "value=$(rand_b64 32)" > /dev/null
  say "created secret/platform/config-encrypt"
fi

# The config server may read the KV secrets, except the platform area (its own encryption key)
POLICY='path "secret/data/*" { capabilities = ["read"] }
path "secret/metadata/*" { capabilities = ["read", "list"] }
path "secret/data/platform/*" { capabilities = ["deny"] }'
printf '%s\n' "$POLICY" | vault policy write config-server - > /dev/null

# A periodic token (renewed below), valid only while it is renewed
mkdir -p "$BOOT"
TOKEN_FILE="$BOOT/spring.cloud.config.server.vault.token"
CS_TOKEN=""
[ -s "$TOKEN_FILE" ] && CS_TOKEN="$(cat "$TOKEN_FILE")"
if [ -z "$CS_TOKEN" ] || ! VAULT_TOKEN="$CS_TOKEN" vault token lookup > /dev/null 2>&1; then
  CS_TOKEN="$(vault token create -policy=config-server -period=24h -orphan -display-name=config-server -field=token)"
  say "created a new token for the config server"
fi
printf '%s' "$CS_TOKEN" > "$TOKEN_FILE"
vault kv get -field=value secret/platform/config-encrypt | tr -d '\n' > "$BOOT/encrypt.key"
chmod 755 "$BOOT"
chmod 644 "$BOOT"/*

renew() { VAULT_TOKEN="$(cat "$TOKEN_FILE")" vault token renew > /dev/null 2>&1 || say "token renewal failed"; }
renew
touch /tmp/ready
say "ready: Vault is unsealed and configured"

# Vault is sealed again after every restart: unseal it, and keep the config server's token alive
n=0
while true; do
  sleep 15
  unseal || say "unseal failed"
  n=$((n + 1))
  if [ $((n % 240)) -eq 0 ]; then renew; fi
done
