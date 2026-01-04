#!/bin/bash
# Verify Fix #6 after bug fix

API_URL="http://localhost:3000"

TOKEN=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"epic7-test@example.com","password":"testpass123"}' | jq -r '.accessToken')

echo "🧪 Fix #6: Re-test After Bug Fix"
echo "================================="
echo ""

# Create a NEW task due in 15 minutes
echo "Step 1: Create task due in 15 minutes"
echo "--------------------------------------"
RESULT=$(curl -s -X POST $API_URL/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"rawTitle":"urgent meeting in 15 minutes"}')

TASK_ID=$(echo $RESULT | jq -r '.id')
DUE_DATE=$(echo $RESULT | jq -r '.due_date')
DUE_TIME=$(echo $RESULT | jq -r '.due_time')

echo "✅ Task created: $TASK_ID"
echo "   Due date: $DUE_DATE"
echo "   Due time: $DUE_TIME"
echo "   Combined: ${DUE_DATE}T${DUE_TIME}"
echo ""

# Generate wallpaper
echo "Step 2: Generate wallpaper"
echo "--------------------------------------"
curl -s "$API_URL/api/wallpaper?resolution=1080x1920&theme=cosmic" \
  -H "Authorization: Bearer $TOKEN" \
  -o wallpaper-fix6-CORRECTED.png

echo "✅ Wallpaper saved: wallpaper-fix6-CORRECTED.png"
ls -lh wallpaper-fix6-CORRECTED.png | awk '{print "   Size:", $5}'
echo ""

echo "================================="
echo "📸 VERIFY:"
echo ""
echo "Open: wallpaper-fix6-CORRECTED.png"
echo ""
echo "Expected: ⏰ DUE IN 15M (or 14M)"
echo "Color: RED (urgent, < 2h)"
echo ""
echo "If countdown is visible → Fix #6 WORKS! ✅"
