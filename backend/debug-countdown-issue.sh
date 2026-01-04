#!/bin/bash
# Debug: Why countdown not showing in API wallpapers

API_URL="http://localhost:3000"

# Login
TOKEN=$(curl -s -X POST $API_URL/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"epic7-test@example.com","password":"testpass123"}' | jq -r '.accessToken')

echo "🔍 Debugging Fix #6 Countdown Issue"
echo "===================================="
echo ""

# Fetch tasks from DB
echo "Step 1: Check task data from database"
echo "--------------------------------------"
curl -s $API_URL/api/tasks \
  -H "Authorization: Bearer $TOKEN" | jq '.[] | select(.title | contains("meeting")) | {
    id,
    title,
    due_date,
    due_time,
    estimate_minutes
  }' | head -20

echo ""
echo "Step 2: Check how wallpaper generator receives tasks"
echo "-----------------------------------------------------"
echo "The issue is likely:"
echo "  1. due_date and due_time are separate fields in DB"
echo "  2. Countdown function expects a single Date object"
echo "  3. Need to combine due_date + due_time before passing to getLiveCountdown()"
echo ""
echo "Let's verify the task structure..."

# Show full task object
curl -s $API_URL/api/tasks \
  -H "Authorization: Bearer $TOKEN" | jq '.[0]' | head -40
