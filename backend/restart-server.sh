#!/bin/bash
# Restart server with new code

echo "🔄 Restarting server with Fix #6 code..."

# Kill old server
pkill -f "node.*server.js"
sleep 2

# Start new server
PORT=3000 node server.js > /tmp/server-fix6.log 2>&1 &
sleep 3

# Check health
curl -s http://localhost:3000/api/health | jq .

echo ""
echo "✅ Server restarted"
echo "📋 Log file: /tmp/server-fix6.log"
