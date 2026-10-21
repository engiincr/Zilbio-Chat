#!/bin/bash
set -euo pipefail

INVENTORY_FILE="docs/qa/BUTTON_INVENTORY.md"
REPORTS_DIR="docs/qa/reports"
OUTPUT_FILE="$REPORTS_DIR/qa_count.txt"

mkdir -p "$REPORTS_DIR"

if [ ! -f "$INVENTORY_FILE" ]; then
    echo "Error: $INVENTORY_FILE not found!" >&2
    exit 1
fi

echo "==================================================" > "$OUTPUT_FILE"
echo "  ZILBIO v3.0.4 — QA STATUS COUNT REPORT" >> "$OUTPUT_FILE"
echo "  Generated from: $INVENTORY_FILE" >> "$OUTPUT_FILE"
echo "  Date: $(date -u)" >> "$OUTPUT_FILE"
echo "==================================================" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Parse table rows (ignore header and separator lines)
TOTAL_ROWS=$(awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 != "") print $7}' "$INVENTORY_FILE" | wc -l)
PASS_COUNT=$(awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 ~ /^PASS$/) print $7}' "$INVENTORY_FILE" | wc -l)
NOT_VERIFIED_COUNT=$(awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 ~ /NOT VERIFIED/) print $7}' "$INVENTORY_FILE" | wc -l)
MANUAL_COUNT=$(awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 ~ /MANUAL/) print $7}' "$INVENTORY_FILE" | wc -l)
FAIL_COUNT=$(awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 ~ /^FAIL$/) print $7}' "$INVENTORY_FILE" | wc -l)
BLOCKED_COUNT=$(awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 ~ /BLOCKED/) print $7}' "$INVENTORY_FILE" | wc -l)

echo "Total Inventory Items   : $TOTAL_ROWS" >> "$OUTPUT_FILE"
echo "PASS                    : $PASS_COUNT" >> "$OUTPUT_FILE"
echo "NOT VERIFIED            : $NOT_VERIFIED_COUNT" >> "$OUTPUT_FILE"
echo "MANUAL                  : $MANUAL_COUNT" >> "$OUTPUT_FILE"
echo "FAIL                    : $FAIL_COUNT" >> "$OUTPUT_FILE"
echo "BLOCKED                 : $BLOCKED_COUNT" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "Status Breakdown:" >> "$OUTPUT_FILE"
awk -F'|' 'NR>5 && NF>=8 {gsub(/^[ \t]+|[ \t]+$/, "", $7); if ($7 != "") print $7}' "$INVENTORY_FILE" | sort | uniq -c | sed 's/^ *//' >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "==================================================" >> "$OUTPUT_FILE"

cat "$OUTPUT_FILE"
