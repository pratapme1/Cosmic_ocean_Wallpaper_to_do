#!/bin/bash
# Achievement Placement Visual Test
# Tests various wallpaper scenarios to verify achievement bar placement

API_URL="https://cosmic-ocean-api.vercel.app"
TEST_EMAIL="placement-$(date +%s)@test.com"
AUTH_TOKEN=""
OUTPUT_DIR="/home/vi/supernova/test-screenshots/achievement-placement"

mkdir -p "$OUTPUT_DIR"

echo "============================================================"
echo "ACHIEVEMENT PLACEMENT VISUAL TEST"
echo "============================================================"
echo "Output: $OUTPUT_DIR"
echo ""

# Register
echo -n "Registering test user... "
REGISTER_RESULT=$(curl -s --max-time 30 -X POST "${API_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$TEST_EMAIL\",\"password\":\"TestPass123!\"}")
AUTH_TOKEN=$(echo "$REGISTER_RESULT" | jq -r '.accessToken')

if [ "$AUTH_TOKEN" == "null" ] || [ -z "$AUTH_TOKEN" ]; then
    echo "FAIL"
    echo "Response: $REGISTER_RESULT"
    exit 1
fi
echo "OK"

api_call() {
    local method=$1
    local endpoint=$2
    local data=$3
    curl -s --max-time 30 -X "$method" "${API_URL}${endpoint}" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $AUTH_TOKEN" \
        ${data:+-d "$data"}
}

get_wallpaper() {
    local filename=$1
    curl -s --max-time 60 -o "${OUTPUT_DIR}/${filename}" "${API_URL}/api/wallpaper" \
        -H "Authorization: Bearer $AUTH_TOKEN"
    local size=$(stat -c%s "${OUTPUT_DIR}/${filename}" 2>/dev/null || echo "0")
    echo "$size"
}

echo ""
echo "============================================================"
echo "SCENARIO 1: Fresh User - No Tasks, No Achievements"
echo "============================================================"
SIZE=$(get_wallpaper "01-fresh-no-tasks.png")
echo "Wallpaper: 01-fresh-no-tasks.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 2: Single Task (Pending)"
echo "============================================================"
TASK1=$(api_call POST "/api/tasks" '{"title":"Buy groceries","priority":2}')
TASK1_ID=$(echo "$TASK1" | jq -r '.id')
echo "Created task: $TASK1_ID"
sleep 1
SIZE=$(get_wallpaper "02-single-task-pending.png")
echo "Wallpaper: 02-single-task-pending.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 3: Multiple Tasks (3 tasks)"
echo "============================================================"
TASK2=$(api_call POST "/api/tasks" '{"title":"Call mom","priority":3}')
TASK3=$(api_call POST "/api/tasks" '{"title":"Finish report","priority":1}')
TASK2_ID=$(echo "$TASK2" | jq -r '.id')
TASK3_ID=$(echo "$TASK3" | jq -r '.id')
echo "Created tasks: $TASK2_ID, $TASK3_ID"
sleep 1
SIZE=$(get_wallpaper "03-multiple-tasks.png")
echo "Wallpaper: 03-multiple-tasks.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 4: First Task Completed (Triggers 'First Step')"
echo "============================================================"
api_call PATCH "/api/tasks/${TASK1_ID}" '{"completed":true}' > /dev/null
api_call POST "/api/achievements/check" '{}' > /dev/null
ACHIEVEMENTS=$(api_call GET "/api/achievements")
echo "Achievements: $(echo "$ACHIEVEMENTS" | jq -r '.earned | length') earned"
echo "$ACHIEVEMENTS" | jq -r '.earned[] | "  - \(.name) (\(.points) pts)"' 2>/dev/null
sleep 1
SIZE=$(get_wallpaper "04-first-completion-achievement.png")
echo "Wallpaper: 04-first-completion-achievement.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 5: All Tasks Completed (Triggers 'Zero Inbox')"
echo "============================================================"
api_call PATCH "/api/tasks/${TASK2_ID}" '{"completed":true}' > /dev/null
api_call PATCH "/api/tasks/${TASK3_ID}" '{"completed":true}' > /dev/null
api_call POST "/api/achievements/check" '{}' > /dev/null
ACHIEVEMENTS=$(api_call GET "/api/achievements")
echo "Achievements: $(echo "$ACHIEVEMENTS" | jq -r '.earned | length') earned"
echo "$ACHIEVEMENTS" | jq -r '.earned[] | "  - \(.name) (\(.points) pts)"' 2>/dev/null
sleep 1
SIZE=$(get_wallpaper "05-all-completed-zero-inbox.png")
echo "Wallpaper: 05-all-completed-zero-inbox.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 6: Done For Today (Celebration Mode)"
echo "============================================================"
api_call POST "/api/user/done-for-today" '{}' > /dev/null
sleep 1
SIZE=$(get_wallpaper "06-done-for-today-celebration.png")
echo "Wallpaper: 06-done-for-today-celebration.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 7: Many Tasks (5+ tasks)"
echo "============================================================"
# Reset done_for_today first
api_call PATCH "/api/user/preferences" '{"done_for_today":false}' > /dev/null
# Delete old tasks and create new ones
api_call DELETE "/api/tasks" > /dev/null
for i in {1..6}; do
    api_call POST "/api/tasks" "{\"title\":\"Task number $i\",\"priority\":$((i % 3 + 1))}" > /dev/null
done
echo "Created 6 new tasks"
sleep 1
SIZE=$(get_wallpaper "07-many-tasks.png")
echo "Wallpaper: 07-many-tasks.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "SCENARIO 8: Long Task Titles"
echo "============================================================"
api_call DELETE "/api/tasks" > /dev/null
api_call POST "/api/tasks" '{"title":"This is a very long task title that might wrap to multiple lines","priority":3}' > /dev/null
api_call POST "/api/tasks" '{"title":"Another lengthy task description for testing layout","priority":2}' > /dev/null
echo "Created tasks with long titles"
sleep 1
SIZE=$(get_wallpaper "08-long-task-titles.png")
echo "Wallpaper: 08-long-task-titles.png ($((SIZE/1024)) KB)"

echo ""
echo "============================================================"
echo "CLEANUP"
echo "============================================================"
api_call DELETE "/api/tasks" > /dev/null
api_call DELETE "/api/user" > /dev/null
echo "Test user deleted"

echo ""
echo "============================================================"
echo "TEST COMPLETE"
echo "============================================================"
echo ""
echo "Generated wallpapers in: $OUTPUT_DIR"
ls -la "$OUTPUT_DIR"/*.png 2>/dev/null | awk '{print "  " $NF " (" int($5/1024) " KB)"}'
echo ""
echo "Review each wallpaper to check achievement bar placement."
