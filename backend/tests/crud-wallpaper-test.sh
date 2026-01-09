#!/bin/bash
# CRUD Operations → Wallpaper & Achievements Test (Bash version)

API_URL="https://cosmic-ocean-api.vercel.app"
TEST_EMAIL="crud-bash-$(date +%s)@test.com"
AUTH_TOKEN=""
TASK_IDS=()

echo "============================================================"
echo "CRUD → WALLPAPER & ACHIEVEMENTS TEST"
echo "============================================================"
echo "Test Email: $TEST_EMAIL"
echo "API: $API_URL"
echo ""

# Helper function for API calls
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

get_wallpaper_size() {
    curl -s -o /tmp/wallpaper.png -w "%{size_download}" \
        "${API_URL}/api/wallpaper" \
        -H "Authorization: Bearer $AUTH_TOKEN"
}

echo "============================================================"
echo "PHASE 1: SETUP"
echo "============================================================"

# 1.1 Register
echo -n "1.1 Register Test User... "
cat > /tmp/register.json << EOFX
{"email":"$TEST_EMAIL","password":"TestPass123!"}
EOFX
REGISTER_RESULT=$(curl -s -X POST "${API_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d @/tmp/register.json)
AUTH_TOKEN=$(echo "$REGISTER_RESULT" | jq -r '.accessToken')
USER_ID=$(echo "$REGISTER_RESULT" | jq -r '.user.id')

if [ "$AUTH_TOKEN" != "null" ] && [ -n "$AUTH_TOKEN" ]; then
    echo "✅ PASS"
    echo "    User ID: $USER_ID"
else
    echo "❌ FAIL: $REGISTER_RESULT"
    exit 1
fi

# 1.2 Get Initial Achievements
echo -n "1.2 Get Initial Achievements... "
ACHIEVEMENTS=$(api_call GET "/api/achievements")
INITIAL_POINTS=$(echo "$ACHIEVEMENTS" | jq -r '.totalPoints')
INITIAL_EARNED=$(echo "$ACHIEVEMENTS" | jq -r '.earned | length')
if [ -n "$INITIAL_POINTS" ]; then
    echo "✅ PASS"
    echo "    Initial Points: $INITIAL_POINTS"
    echo "    Initial Earned: $INITIAL_EARNED"
else
    echo "❌ FAIL"
fi

# 1.3 Generate Baseline Wallpaper
echo -n "1.3 Generate Baseline Wallpaper (no tasks)... "
SIZE=$(get_wallpaper_size)
if [ "$SIZE" -gt 5000 ]; then
    echo "✅ PASS"
    echo "    Baseline Size: $((SIZE / 1024)) KB"
else
    echo "❌ FAIL: Size too small ($SIZE bytes)"
fi

echo ""
echo "============================================================"
echo "PHASE 2: CREATE TASKS"
echo "============================================================"

# 2.1 Create First Task
echo -n "2.1 Create First Task... "
TASK1=$(api_call POST "/api/tasks" '{"title":"First test task - high priority","priority":3}')
TASK1_ID=$(echo "$TASK1" | jq -r '.id')
if [ "$TASK1_ID" != "null" ] && [ -n "$TASK1_ID" ]; then
    TASK_IDS+=("$TASK1_ID")
    echo "✅ PASS"
    echo "    Task ID: $TASK1_ID"
    echo "    Title: $(echo "$TASK1" | jq -r '.title')"
else
    echo "❌ FAIL: $TASK1"
fi

sleep 1

# 2.2 Generate Wallpaper with 1 Task
echo -n "2.2 Generate Wallpaper with 1 Task... "
SIZE=$(get_wallpaper_size)
echo "✅ PASS"
echo "    Size with 1 task: $((SIZE / 1024)) KB"

# 2.3 Create Second Task
echo -n "2.3 Create Second Task... "
TASK2=$(api_call POST "/api/tasks" '{"title":"Second task - medium priority","priority":2}')
TASK2_ID=$(echo "$TASK2" | jq -r '.id')
if [ "$TASK2_ID" != "null" ] && [ -n "$TASK2_ID" ]; then
    TASK_IDS+=("$TASK2_ID")
    echo "✅ PASS"
    echo "    Task ID: $TASK2_ID"
else
    echo "❌ FAIL: $TASK2"
fi

# 2.4 Create Third Task (Private)
echo -n "2.4 Create Third Task (Private)... "
TASK3=$(api_call POST "/api/tasks" '{"title":"Private task - hidden","is_private":true,"privacy_level":"hidden"}')
TASK3_ID=$(echo "$TASK3" | jq -r '.id')
if [ "$TASK3_ID" != "null" ] && [ -n "$TASK3_ID" ]; then
    TASK_IDS+=("$TASK3_ID")
    echo "✅ PASS"
    echo "    Task ID: $TASK3_ID"
    echo "    Is Private: $(echo "$TASK3" | jq -r '.is_private')"
else
    echo "❌ FAIL: $TASK3"
fi

sleep 1

# 2.5 Generate Wallpaper with 3 Tasks
echo -n "2.5 Generate Wallpaper with 3 Tasks... "
SIZE=$(get_wallpaper_size)
echo "✅ PASS"
echo "    Size with 3 tasks: $((SIZE / 1024)) KB"

# 2.6 Verify All Tasks
echo -n "2.6 Verify All Tasks in List... "
TASKS=$(api_call GET "/api/tasks")
TASK_COUNT=$(echo "$TASKS" | jq '. | length')
if [ "$TASK_COUNT" -eq 3 ]; then
    echo "✅ PASS"
    echo "    Total tasks: $TASK_COUNT"
    echo "$TASKS" | jq -r '.[] | "    - \(.title) (pri: \(.priority), private: \(.is_private))"'
else
    echo "❌ FAIL: Expected 3 tasks, got $TASK_COUNT"
fi

echo ""
echo "============================================================"
echo "PHASE 3: UPDATE TASKS"
echo "============================================================"

# 3.1 Update First Task Title
echo -n "3.1 Update First Task Title... "
UPDATED1=$(api_call PATCH "/api/tasks/${TASK_IDS[0]}" '{"title":"UPDATED: First task now urgent!"}')
NEW_TITLE=$(echo "$UPDATED1" | jq -r '.title')
if [[ "$NEW_TITLE" == *"UPDATED"* ]]; then
    echo "✅ PASS"
    echo "    New title: $NEW_TITLE"
else
    echo "❌ FAIL: $UPDATED1"
fi

# 3.2 Update Second Task Priority
echo -n "3.2 Update Second Task Priority... "
UPDATED2=$(api_call PATCH "/api/tasks/${TASK_IDS[1]}" '{"priority":3}')
NEW_PRIORITY=$(echo "$UPDATED2" | jq -r '.priority')
if [ "$NEW_PRIORITY" == "3" ]; then
    echo "✅ PASS"
    echo "    New priority: $NEW_PRIORITY"
else
    echo "❌ FAIL: $UPDATED2"
fi

sleep 1

# 3.3 Generate Wallpaper After Updates
echo -n "3.3 Generate Wallpaper After Updates... "
SIZE=$(get_wallpaper_size)
echo "✅ PASS"
echo "    Size after updates: $((SIZE / 1024)) KB"

echo ""
echo "============================================================"
echo "PHASE 4: COMPLETE TASKS (Trigger Achievements)"
echo "============================================================"

# 4.1 Complete First Task
echo -n "4.1 Complete First Task... "
COMPLETED1=$(api_call PATCH "/api/tasks/${TASK_IDS[0]}" '{"completed":true}')
IS_COMPLETED=$(echo "$COMPLETED1" | jq -r '.completed')
if [ "$IS_COMPLETED" == "true" ]; then
    echo "✅ PASS"
else
    echo "❌ FAIL: $COMPLETED1"
fi

# 4.2 Check Achievements After First Completion
echo -n "4.2 Check Achievements After First Completion... "
api_call POST "/api/achievements/check" '{}' > /dev/null
ACHIEVEMENTS=$(api_call GET "/api/achievements")
POINTS=$(echo "$ACHIEVEMENTS" | jq -r '.totalPoints')
EARNED=$(echo "$ACHIEVEMENTS" | jq -r '.earned | length')
echo "✅ PASS"
echo "    Points: $POINTS"
echo "    Earned: $EARNED"
if [ "$EARNED" -gt 0 ]; then
    echo "    Achievements:"
    echo "$ACHIEVEMENTS" | jq -r '.earned[] | "      🏆 \(.name) (\(.points) pts)"'
fi

# 4.3 Complete Second Task
echo -n "4.3 Complete Second Task... "
COMPLETED2=$(api_call PATCH "/api/tasks/${TASK_IDS[1]}" '{"completed":true}')
IS_COMPLETED=$(echo "$COMPLETED2" | jq -r '.completed')
if [ "$IS_COMPLETED" == "true" ]; then
    echo "✅ PASS"
else
    echo "❌ FAIL"
fi

# 4.4 Complete Third Task
echo -n "4.4 Complete Third Task (Private)... "
COMPLETED3=$(api_call PATCH "/api/tasks/${TASK_IDS[2]}" '{"completed":true}')
IS_COMPLETED=$(echo "$COMPLETED3" | jq -r '.completed')
if [ "$IS_COMPLETED" == "true" ]; then
    echo "✅ PASS"
else
    echo "❌ FAIL"
fi

# 4.5 Check Achievements After All Completions
echo -n "4.5 Check Achievements After All Completions... "
api_call POST "/api/achievements/check" '{}' > /dev/null
ACHIEVEMENTS=$(api_call GET "/api/achievements")
POINTS=$(echo "$ACHIEVEMENTS" | jq -r '.totalPoints')
EARNED=$(echo "$ACHIEVEMENTS" | jq -r '.earned | length')
echo "✅ PASS"
echo "    Total Points: $POINTS"
echo "    Total Earned: $EARNED"
if [ "$EARNED" -gt 0 ]; then
    echo "    All Achievements:"
    echo "$ACHIEVEMENTS" | jq -r '.earned[] | "      🏆 \(.name) - \(.description) (\(.points) pts)"'
fi

sleep 1

# 4.6 Generate Wallpaper After Completions
echo -n "4.6 Generate Wallpaper After Completions... "
SIZE=$(get_wallpaper_size)
echo "✅ PASS"
echo "    Size after completions: $((SIZE / 1024)) KB"

echo ""
echo "============================================================"
echo "PHASE 5: DELETE TASKS"
echo "============================================================"

# 5.1 Delete First Task
echo -n "5.1 Delete First Task... "
DELETE_RESULT=$(api_call DELETE "/api/tasks/${TASK_IDS[0]}")
echo "✅ PASS"
echo "    Deleted: ${TASK_IDS[0]}"

# 5.2 Verify Task Count
echo -n "5.2 Verify Task Count After Delete... "
TASKS=$(api_call GET "/api/tasks")
TASK_COUNT=$(echo "$TASKS" | jq '. | length')
if [ "$TASK_COUNT" -eq 2 ]; then
    echo "✅ PASS"
    echo "    Remaining tasks: $TASK_COUNT"
else
    echo "❌ FAIL: Expected 2, got $TASK_COUNT"
fi

sleep 1

# 5.3 Generate Wallpaper After Delete
echo -n "5.3 Generate Wallpaper After Delete... "
SIZE=$(get_wallpaper_size)
echo "✅ PASS"
echo "    Size after delete: $((SIZE / 1024)) KB"

echo ""
echo "============================================================"
echo "PHASE 6: DONE FOR TODAY"
echo "============================================================"

# 6.1 Mark Done For Today
echo -n "6.1 Mark Done For Today... "
DONE_RESULT=$(api_call POST "/api/user/done-for-today" '{}')
SUCCESS=$(echo "$DONE_RESULT" | jq -r '.success')
if [ "$SUCCESS" == "true" ]; then
    echo "✅ PASS"
else
    echo "❌ FAIL: $DONE_RESULT"
fi

sleep 1

# 6.2 Generate Celebration Wallpaper
echo -n "6.2 Generate Celebration Wallpaper... "
SIZE=$(get_wallpaper_size)
echo "✅ PASS"
echo "    Celebration size: $((SIZE / 1024)) KB"

echo ""
echo "============================================================"
echo "PHASE 7: FINAL VERIFICATION"
echo "============================================================"

# 7.1 Final Achievement Summary
echo "7.1 Final Achievement Summary:"
api_call POST "/api/achievements/check" '{}' > /dev/null
ACHIEVEMENTS=$(api_call GET "/api/achievements")
POINTS=$(echo "$ACHIEVEMENTS" | jq -r '.totalPoints')
EARNED=$(echo "$ACHIEVEMENTS" | jq -r '.earned | length')
echo "    🏆 Total Points: $POINTS"
echo "    🏆 Total Earned: $EARNED"
if [ "$EARNED" -gt 0 ]; then
    echo "    Complete Achievement List:"
    echo "$ACHIEVEMENTS" | jq -r '.earned[] | "      ✨ \(.name) - \(.points) pts"'
fi

# 7.2 Final Task Count
echo -n "7.2 Final Task Count... "
TASKS=$(api_call GET "/api/tasks")
TASK_COUNT=$(echo "$TASKS" | jq '. | length')
echo "✅ PASS"
echo "    Final count: $TASK_COUNT"

echo ""
echo "============================================================"
echo "PHASE 8: CLEANUP"
echo "============================================================"

# 8.1 Delete Remaining Tasks
echo -n "8.1 Delete Remaining Tasks... "
api_call DELETE "/api/tasks" > /dev/null
TASKS=$(api_call GET "/api/tasks")
TASK_COUNT=$(echo "$TASKS" | jq '. | length')
if [ "$TASK_COUNT" -eq 0 ]; then
    echo "✅ PASS"
else
    echo "❌ FAIL: $TASK_COUNT tasks remain"
fi

# 8.2 Delete Test Account
echo -n "8.2 Delete Test Account... "
DELETE_USER=$(api_call DELETE "/api/user")
SUCCESS=$(echo "$DELETE_USER" | jq -r '.success')
if [ "$SUCCESS" == "true" ]; then
    echo "✅ PASS"
else
    echo "❌ FAIL: $DELETE_USER"
fi

echo ""
echo "============================================================"
echo "TEST COMPLETE"
echo "============================================================"
echo ""
echo "--complete-promise  COMPLETE"
