#!/bin/sh
# Usage: docker compose exec vault-init sh /init/vault.sh kv get secret/application
export VAULT_ADDR="http://vault:8200"
case "${1:-}" in
  root-token) awk '/^Initial Root Token:/ {print $NF}' /vault-init/init.txt; exit 0 ;;
  status) ;;
  *) VAULT_TOKEN="$(awk '/^Initial Root Token:/ {print $NF}' /vault-init/init.txt)"; export VAULT_TOKEN ;;
esac
exec vault "$@"