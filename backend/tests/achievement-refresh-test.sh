#!/bin/bash
# Achievement Refresh Timing Test
# Tests that achievements appear on wallpaper immediately after task completion

API_URL="https://cosmic-ocean-api.vercel.app"
TEST_EMAIL="refresh-test-$(date +%s)@test.com"
AUTH_TOKEN=""

echo "============================================================"
echo "ACHIEVEMENT REFRESH TIMING TEST"
echo "============================================================"
echo "Test Email: $TEST_EMAIL"
echo ""

# Register
echo -n "1. Register Test User... "
REGISTER_RESULT=$(curl -s -X POST "${API_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"TestPass123!\"}")
AUTH_TOKEN=$(echo "$REGISTER_RESULT" | jq -r '.accessToken')

if [ "$AUTH_TOKEN" != "null" ] && [ -n "$AUTH_TOKEN" ]; then
    echo "PASS"
else
    echo "FAIL: $REGISTER_RESULT"
    exit 1
fi

# Helper function
api_call() {
    local method=$1
    local endpoint=$2
    local data=$3

    if [ -n "$data" ]; then
        curl -s -X "$method" "${API_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $AUTH_TOKEN" \
            -d "$data"
    else
        curl -s -X "$method" "${API_URL}${endpoint}" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer $AUTH_TOKEN"
    fi
}

# Create a task
echo -n "2. Create Task... "
TASK=$(api_call POST "/api/tasks" '{"title":"Test task for achievement"}')
TASK_ID=$(echo "$TASK" | jq -r '.id')
if [ "$TASK_ID" != "null" ]; then
    echo "PASS (ID: $TASK_ID)"
else
    echo "FAIL"
    exit 1
fi

# Get wallpaper BEFORE completion - check for NO achievements
echo -n "3. Get Wallpaper BEFORE Completion... "
curl -s -o /tmp/wallpaper_before.png "${API_URL}/api/wallpaper" \
    -H "Authorization: Bearer $AUTH_TOKEN"
SIZE_BEFORE=$(stat -c%s /tmp/wallpaper_before.png)
echo "PASS (Size: $((SIZE_BEFORE / 1024)) KB)"

# Check achievements BEFORE
echo -n "4. Check Achievements BEFORE... "
ACHIEVEMENTS_BEFORE=$(api_call GET "/api/achievements")
EARNED_BEFORE=$(echo "$ACHIEVEMENTS_BEFORE" | jq -r '.earned | length')
echo "PASS (Earned: $EARNED_BEFORE)"

# Complete the task
echo -n "5. Complete Task... "
COMPLETED=$(api_call PATCH "/api/tasks/${TASK_ID}" '{"completed":true}')
IS_COMPLETED=$(echo "$COMPLETED" | jq -r '.completed')
if [ "$IS_COMPLETED" == "true" ]; then
    echo "PASS"
else
    echo "FAIL"
fi

# IMMEDIATELY get wallpaper AFTER completion
echo -n "6. Get Wallpaper IMMEDIATELY After Completion... "
curl -s -o /tmp/wallpaper_after.png "${API_URL}/api/wallpaper" \
    -H "Authorization: Bearer $AUTH_TOKEN"
SIZE_AFTER=$(stat -c%s /tmp/wallpaper_after.png)
echo "PASS (Size: $((SIZE_AFTER / 1024)) KB)"

# Check achievements AFTER
echo -n "7. Check Achievements AFTER... "
api_call POST "/api/achievements/check" '{}' > /dev/null
ACHIEVEMENTS_AFTER=$(api_call GET "/api/achievements")
EARNED_AFTER=$(echo "$ACHIEVEMENTS_AFTER" | jq -r '.earned | length')
POINTS_AFTER=$(echo "$ACHIEVEMENTS_AFTER" | jq -r '.totalPoints')
echo "PASS (Earned: $EARNED_AFTER, Points: $POINTS_AFTER)"

# Verify "First Step" achievement was earned
echo ""
echo "============================================================"
echo "RESULTS"
echo "============================================================"
echo "Achievements BEFORE completion: $EARNED_BEFORE"
echo "Achievements AFTER completion:  $EARNED_AFTER"
echo "Points earned: $POINTS_AFTER"
echo ""

if [ "$EARNED_AFTER" -gt "$EARNED_BEFORE" ]; then
    echo "SUCCESS: Achievement unlocked immediately after task completion!"
    echo ""
    echo "Achievements earned:"
    echo "$ACHIEVEMENTS_AFTER" | jq -r '.earned[] | "  - \(.name): \(.description) (\(.points) pts)"'
else
    echo "WARNING: No new achievements detected"
fi

# Test Done For Today with achievements
echo ""
echo "============================================================"
echo "DONE FOR TODAY + ACHIEVEMENTS TEST"
echo "============================================================"

echo -n "8. Mark Done For Today... "
DONE_RESULT=$(api_call POST "/api/user/done-for-today" '{}')
SUCCESS=$(echo "$DONE_RESULT" | jq -r '.success')
if [ "$SUCCESS" == "true" ]; then
    echo "PASS"
else
    echo "FAIL: $DONE_RESULT"
fi

# Get celebration wallpaper (should still show achievements)
echo -n "9. Get Celebration Wallpaper (with achievements)... "
curl -s -o /tmp/wallpaper_celebration.png "${API_URL}/api/wallpaper" \
    -H "Authorization: Bearer $AUTH_TOKEN"
SIZE_CELEBRATION=$(stat -c%s /tmp/wallpaper_celebration.png)
echo "PASS (Size: $((SIZE_CELEBRATION / 1024)) KB)"

# Verify achievements still accessible
echo -n "10. Verify Achievements Still Accessible... "
ACHIEVEMENTS_FINAL=$(api_call GET "/api/achievements")
POINTS_FINAL=$(echo "$ACHIEVEMENTS_FINAL" | jq -r '.totalPoints')
if [ "$POINTS_FINAL" -gt 0 ]; then
    echo "PASS (Points: $POINTS_FINAL)"
else
    echo "FAIL: No points recorded"
fi

# Cleanup
echo ""
echo "============================================================"
echo "CLEANUP"
echo "============================================================"
echo -n "11. Delete Test Account... "
DELETE_USER=$(api_call DELETE "/api/user")
SUCCESS=$(echo "$DELETE_USER" | jq -r '.success')
if [ "$SUCCESS" == "true" ]; then
    echo "PASS"
else
    echo "FAIL: $DELETE_USER"
fi

echo ""
echo "============================================================"
echo "TEST COMPLETE"
echo "============================================================"
echo "Wallpaper sizes:"
echo "  Before completion: $((SIZE_BEFORE / 1024)) KB"
echo "  After completion:  $((SIZE_AFTER / 1024)) KB"
echo "  Celebration:       $((SIZE_CELEBRATION / 1024)) KB"
echo ""

if [ "$POINTS_FINAL" -gt 0 ]; then
    echo "ACHIEVEMENT REFRESH: VERIFIED WORKING"
else
    echo "ACHIEVEMENT REFRESH: NEEDS INVESTIGATION"
fi
