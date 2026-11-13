#!/bin/sh
set -eu

ES="${ES_URL:-http://elasticsearch:9200}"
KB="${KIBANA_URL:-http://kibana:5601}"
LOGS_DAYS="${LOGS_RETENTION_DAYS:-30}"
AUDIT_DAYS="${AUDIT_RETENTION_DAYS:-200}"      # BRD 5.5: at least 6 months
TRACES_DAYS="${TRACES_RETENTION_DAYS:-7}"

es_put() {                                      # path: the JSON body comes from stdin
  curl -fsS -X PUT "$ES$1" -H 'Content-Type: application/json' -d @- > /dev/null
}

ilm_policy() {                                  # name days
  es_put "/_ilm/policy/$1" <<JSON
{"policy":{"phases":{"delete":{"min_age":"${2}d","actions":{"delete":{}}}}}}
JSON
}

init() {
  ilm_policy retail-logs "$LOGS_DAYS"
  ilm_policy retail-audit "$AUDIT_DAYS"
  ilm_policy retail-traces "$TRACES_DAYS"

  # Strings become keywords (exact match, aggregations), except free text. Single node: no replicas.
  es_put "/_index_template/retail-logs" <<'JSON'
{
  "index_patterns": ["retail-logs-*"],
  "priority": 200,
  "template": {
    "settings": {"number_of_shards": 1, "number_of_replicas": 0, "index.lifecycle.name": "retail-logs"},
    "mappings": {
      "dynamic_templates": [
        {"strings_as_keywords": {"match_mapping_type": "string",
                                 "path_unmatch": ["message", "error.stack_trace"],
                                 "mapping": {"type": "keyword", "ignore_above": 2048}}}
      ],
      "properties": {
        "@timestamp": {"type": "date"},
        "message": {"type": "text"},
        "error": {"properties": {"stack_trace": {"type": "text"}}},
        "traceId": {"type": "keyword"},
        "spanId": {"type": "keyword"}
      }
    }
  }
}
JSON

  es_put "/_index_template/retail-audit" <<'JSON'
{
  "index_patterns": ["retail-audit-*"],
  "priority": 200,
  "template": {
    "settings": {"number_of_shards": 1, "number_of_replicas": 0, "index.lifecycle.name": "retail-audit"},
    "mappings": {
      "dynamic_templates": [
        {"strings_as_keywords": {"match_mapping_type": "string",
                                 "path_unmatch": ["message"],
                                 "mapping": {"type": "keyword", "ignore_above": 2048}}}
      ],
      "properties": {
        "@timestamp": {"type": "date"},
        "message": {"type": "text"},
        "httpStatus": {"type": "integer"},
        "durationMs": {"type": "long"},
        "traceId": {"type": "keyword"}
      }
    }
  }
}
JSON

  for view in "retail-logs|retail-logs-*|Retail logs" "retail-audit|retail-audit-*|Retail audit"; do
    id="${view%%|*}"; rest="${view#*|}"; title="${rest%%|*}"; name="${rest#*|}"
    curl -fsS -X POST "$KB/api/data_views/data_view" -H 'kbn-xsrf: true' -H 'Content-Type: application/json' \
      -d "{\"override\":true,\"data_view\":{\"id\":\"$id\",\"title\":\"$title\",\"name\":\"$name\",\"timeFieldName\":\"@timestamp\",\"allowNoIndex\":true}}" > /dev/null
  done
  echo "elk-init: retention policies, templates and data views are in place"
}

maintain() {
  while true; do
    # Zipkin creates a new daily index and its own template: attach the retention policy to the indices instead.
    curl -fsS -X PUT "$ES/zipkin*/_settings?ignore_unavailable=true&allow_no_indices=true" \
      -H 'Content-Type: application/json' -d '{"index.lifecycle.name":"retail-traces"}' > /dev/null || true
    sleep 600
  done
}

case "${1:-init}" in
  init) init ;;
  maintain) maintain ;;
  *) echo "usage: setup.sh init|maintain" >&2; exit 2 ;;
esac