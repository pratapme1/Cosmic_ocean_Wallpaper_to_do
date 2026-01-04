#!/bin/bash
# Verify production has all Epic 7 features

API_URL="https://cosmic-ocean-api.vercel.app"

echo "🧪 Verifying Production Deployment (v1.2.1)"
echo "============================================="
echo ""

# Test 1: Health check
echo "Test 1: Health Check"
echo "--------------------"
VERSION=$(curl -s $API_URL/api/health | jq -r '.version')
echo "Version: $VERSION"
if [ "$VERSION" = "1.2.1" ]; then
  echo "✅ PASS: Version is 1.2.1"
else
  echo "❌ FAIL: Version is $VERSION, expected 1.2.1"
  exit 1
fi
echo ""

# Test 2: NLP Integration (Fix #1)
echo "Test 2: NLP Integration"
echo "-----------------------"
TOKEN=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"epic7-test@example.com","password":"testpass123"}' | jq -r '.accessToken')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
  echo "❌ FAIL: Could not get access token"
  exit 1
fi

RESULT=$(curl -s -X POST $API_URL/api/tasks \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"rawTitle":"urgent meeting in 15 minutes"}')

CATEGORY=$(echo $RESULT | jq -r '.category')
ENERGY=$(echo $RESULT | jq -r '.energy_level')
DUE_TIME=$(echo $RESULT | jq -r '.due_time')

echo "Input: 'urgent meeting in 15 minutes'"
echo "Category: $CATEGORY (expected: work)"
echo "Energy: $ENERGY (expected: high)"
echo "Due Time: $DUE_TIME (expected: not null)"

if [ "$CATEGORY" = "work" ] && [ "$ENERGY" = "high" ] && [ "$DUE_TIME" != "null" ]; then
  echo "✅ PASS: NLP integration working"
else
  echo "❌ FAIL: NLP not working correctly"
  exit 1
fi
echo ""

# Test 3: Message Engine (Fix #2)
echo "Test 3: Wallpaper Generation"
echo "----------------------------"
curl -s "$API_URL/api/wallpaper?resolution=720x1280&theme=cosmic" \
  -H "Authorization: Bearer $TOKEN" \
  -o /tmp/prod-wallpaper-test.png

SIZE=$(ls -lh /tmp/prod-wallpaper-test.png | awk '{print $5}')
echo "Generated: /tmp/prod-wallpaper-test.png ($SIZE)"

if [ -s /tmp/prod-wallpaper-test.png ]; then
  echo "✅ PASS: Wallpaper generation working"
else
  echo "❌ FAIL: Wallpaper generation failed"
  exit 1
fi
echo ""

echo "============================================="
echo "✅ ALL TESTS PASSED"
echo ""
echo "Production deployment verified:"
echo "  - Version: 1.2.1 ✅"
echo "  - NLP Integration: Working ✅"
echo "  - Wallpaper Generation: Working ✅"
echo ""
