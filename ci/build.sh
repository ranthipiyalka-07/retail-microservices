#!/usr/bin/env bash
source "$(dirname "$0")/lib.sh"

log "Build and unit tests"
mvn -B -ntp verify "$@"

log "Coverage"
mvn -B -ntp -q jacoco:report || echo "No coverage data (see the notes on JaCoCo)"

total_missed=0
total_covered=0
summary "### Unit test coverage (instructions)"
summary ""
summary "| Module | Covered |"
summary "|---|---|"
for csv in */target/site/jacoco/jacoco.csv; do
  [ -f "$csv" ] || continue
  module="${csv%%/*}"
  read -r missed covered < <(awk -F, 'NR > 1 { m += $4; c += $5 } END { print m + 0, c + 0 }' "$csv")
  total_missed=$((total_missed + missed))
  total_covered=$((total_covered + covered))
  pct=$(awk -v m="$missed" -v c="$covered" 'BEGIN { t = m + c; printf "%.1f", (t == 0 ? 0 : 100 * c / t) }')
  summary "| $module | $pct% |"
  echo "$module: $pct%"
done
total=$(awk -v m="$total_missed" -v c="$total_covered" 'BEGIN { t = m + c; printf "%.1f", (t == 0 ? 0 : 100 * c / t) }')
summary "| **all modules** | **$total%** |"
echo "all modules: $total%"

if awk -v t="$total" -v min="${COVERAGE_MIN:-0}" 'BEGIN { exit !(t + 0 < min + 0) }'; then
  echo "Coverage $total% is below COVERAGE_MIN=${COVERAGE_MIN}" >&2
  exit 1
fi