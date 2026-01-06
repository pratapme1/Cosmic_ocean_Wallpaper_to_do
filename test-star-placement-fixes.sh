#!/bin/bash

# Star Placement Fixes - Device Testing Script
# Date: 2026-01-05
# Purpose: Install APK and guide through manual testing

set -e

ADB="/home/vi/Android/Sdk/platform-tools/adb"
APK="/home/vi/supernova/cosmic-ocean-v1.3.2-star-placement-fixes.apk"
PACKAGE="com.cosmicocean"

echo "============================================"
echo "Star Placement Fixes - Device Testing"
echo "============================================"
echo ""

# Check if APK exists
if [ ! -f "$APK" ]; then
    echo "❌ ERROR: APK not found at $APK"
    exit 1
fi

echo "✅ APK found: $(ls -lh $APK | awk '{print $5}')"
echo ""

# Wait for device
echo "Waiting for device connection..."
echo "(Connect your phone via USB and enable USB debugging)"
echo ""

$ADB wait-for-device
echo "✅ Device connected!"
echo ""

# Get device info
DEVICE_MODEL=$($ADB shell getprop ro.product.model)
ANDROID_VERSION=$($ADB shell getprop ro.build.version.release)
SCREEN_SIZE=$($ADB shell wm size | grep -oP '\d+x\d+')

echo "📱 Device Info:"
echo "   Model: $DEVICE_MODEL"
echo "   Android: $ANDROID_VERSION"
echo "   Screen: $SCREEN_SIZE"
echo ""

# Uninstall old version if exists
echo "Removing old version (if exists)..."
$ADB uninstall $PACKAGE 2>/dev/null || echo "   No previous version found"
echo ""

# Install APK
echo "Installing APK..."
$ADB install -r "$APK"

if [ $? -eq 0 ]; then
    echo "✅ APK installed successfully!"
else
    echo "❌ Installation failed!"
    exit 1
fi

echo ""
echo "============================================"
echo "Manual Testing Guide"
echo "============================================"
echo ""
echo "The app is now installed. Follow these tests:"
echo ""

echo "🧪 TEST 1: Label Width Truncation (30 seconds)"
echo "   1. Open the app and tap + to create a task"
echo "   2. Enter: 'This is a very long task name that should be truncated with ellipsis'"
echo "   3. Save the task"
echo "   4. VERIFY: Label shows 'This is a very long tas...' (truncated)"
echo "   5. VERIFY: Label doesn't extend off screen edges"
echo ""
read -p "Press ENTER when Test 1 complete..."

echo ""
echo "🧪 TEST 2: Zone Forces - P1 (Urgent) → Bottom (2 minutes)"
echo "   1. Create 3 tasks with urgency P1 (urgent):"
echo "      - 'Call client ASAP' (tap P1/RED urgency)"
echo "      - 'Fix critical bug' (tap P1/RED urgency)"
echo "      - 'Submit report now' (tap P1/RED urgency)"
echo "   2. Wait 60 seconds and observe"
echo "   3. VERIFY: All 3 P1 stars are RED color (🔴)"
echo "   4. VERIFY: All 3 P1 stars are LARGEST size (26px)"
echo "   5. Wait another 60 seconds (total 2 minutes)"
echo "   6. VERIFY: P1 stars have drifted toward BOTTOM of screen (80%+)"
echo ""
read -p "Press ENTER when Test 2 complete..."

echo ""
echo "🧪 TEST 3: Zone Forces - P3 (Low Priority) → Top (2 minutes)"
echo "   1. Create 3 tasks with urgency P3 (low priority):"
echo "      - 'Review goals next week' (tap P3/BLUE urgency)"
echo "      - 'Clean up old files' (tap P3/BLUE urgency)"
echo "      - 'Read documentation' (tap P3/BLUE urgency)"
echo "   2. Wait 60 seconds and observe"
echo "   3. VERIFY: All 3 P3 stars are BLUE color (🔵)"
echo "   4. VERIFY: All 3 P3 stars are SMALLEST size (18px)"
echo "   5. Wait another 60 seconds (total 2 minutes)"
echo "   6. VERIFY: P3 stars have floated toward TOP of screen (0-20%)"
echo ""
read -p "Press ENTER when Test 3 complete..."

echo ""
echo "🧪 TEST 4: Collision & Clustering (30 seconds)"
echo "   1. Double-tap the SAME SPOT 10 times to create 10 tasks in one location"
echo "   2. All tasks will start overlapping/clustered"
echo "   3. Wait 10 seconds"
echo "   4. VERIFY: Stars visibly separating (not overlapping)"
echo "   5. Wait another 20 seconds (total 30 sec)"
echo "   6. VERIFY: Stars have distributed evenly (no clustering)"
echo "   7. VERIFY: Minimum 60-80px spacing between stars"
echo ""
read -p "Press ENTER when Test 4 complete..."

echo ""
echo "============================================"
echo "Testing Complete!"
echo "============================================"
echo ""
echo "📊 Summary of Fixes:"
echo "   ✅ Label width: Limited to 40% screen with ellipsis"
echo "   ✅ Zone forces: 2x faster (5.5 px/sec, visible in ~2 min)"
echo "   ✅ Collision: 40% faster separation (267 px/sec)"
echo "   ✅ Clustering: 50% less gravitational pull"
echo ""
echo "📝 Next Steps:"
echo "   1. If all tests passed → Ready for release!"
echo "   2. If any test failed → Document issue and investigate"
echo ""
echo "Collect device logs (if needed):"
echo "   $ADB logcat -d > /home/vi/supernova/testing/device-logs-$(date +%Y%m%d-%H%M%S).txt"
echo ""
