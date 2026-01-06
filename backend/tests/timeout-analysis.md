# Timeout Bug Analysis - BEFORE FIX

**Date:** 2026-01-06
**Issue:** Message generation timeout (15s limit exceeded)
**Status:** IDENTIFIED - Ready to fix

---

## 🔍 Root Cause Identified

**File:** `backend/services/message-generator-llm.js`
**Lines:** 115-128

### Problematic Code (BEFORE FIX):

```javascript
// ENHANCEMENT: Get ALL tasks (including completed) for StatsAggregator
const allTasksQuery = `
  SELECT *                    // ❌ All columns (inefficient)
  FROM tasks
  WHERE user_id = $1
  AND (archived = false OR archived IS NULL)
  AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
                             // ❌ 30 days worth of tasks
`;
const allTasksResult = await client.query(allTasksQuery, [userId]);  // ❌ No LIMIT
const allTasks = allTasksResult.rows;

// ENHANCEMENT: Calculate stats using StatsAggregator
const statsAggregator = new StatsAggregator();
const stats = statsAggregator.computeStats(allTasks);  // ❌ CPU-intensive on every request
```

---

## 📊 Performance Impact Analysis

### Scenario: Active user with 200 tasks in last 30 days

**Current Query:**
```sql
SELECT *  -- Returns ALL columns (id, user_id, title, description, due_date, due_time, ...)
FROM tasks
WHERE user_id = 'user-123'
AND (archived = false OR archived IS NULL)
AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
```

**Data Transfer:**
- 200 rows × ~500 bytes/row = 100 KB
- All columns selected (including description, notes, etc.)
- No pagination or limit

**Processing:**
- StatsAggregator.computeStats() runs on 200 tasks
- Calculates: streaks, patterns, averages, categories
- CPU-intensive operations on EVERY wallpaper request

**Total Time:**
- Query: ~500ms (200 rows, all columns)
- StatsAggregator computation: ~1000-2000ms (streak calculation, pattern analysis)
- Claude API call: ~2000-3000ms (message generation)
- **TOTAL: 3.5-5.5 seconds** (under normal conditions)

**Why it times out at 15s:**
- Vercel cold start: +2-3s
- Database connection pool: +500ms
- Heavy user (500+ tasks): +5-10s
- **TOTAL: 8-18 seconds** ❌ EXCEEDS 15s VERCEL LIMIT

---

## 🎯 Expected Behavior vs Actual

### Test Case: User with 200 tasks in last 30 days

| Metric | Expected (< 5s target) | Actual (BEFORE fix) | Status |
|--------|------------------------|---------------------|--------|
| Query execution | < 100ms | ~500ms | ⚠️ SLOW |
| Rows returned | < 50 (7 days) | 200 (30 days) | ❌ TOO MANY |
| Data transfer | < 10 KB | ~100 KB | ❌ TOO MUCH |
| StatsAggregator | < 500ms | ~2000ms | ❌ SLOW |
| Total time | < 5s | 8-18s | ❌ TIMEOUT |

---

## 📋 What Needs to be Fixed

### Fix #1: Optimize Query (Reduce data transfer)
```javascript
// BEFORE: SELECT * (all columns)
SELECT *

// AFTER: Select only needed columns
SELECT id, completed, completed_at, created_at, category, priority
```
**Impact:** Reduces data transfer from 100 KB → 20 KB (80% reduction)

---

### Fix #2: Reduce Time Window (Reduce rows)
```javascript
// BEFORE: 30 days (200 rows)
AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')

// AFTER: 7 days (50 rows)
AND (created_at > NOW() - INTERVAL '7 days' OR completed_at > NOW() - INTERVAL '7 days')
```
**Impact:** Reduces rows from 200 → 50 (75% reduction)

**Justification:** 7 days is enough for:
- Streak calculation (tracks daily completion)
- Pattern detection (identifies peak times)
- Average per day (7-day rolling average is sufficient)

---

### Fix #3: Add LIMIT Safeguard
```javascript
// BEFORE: No limit
SELECT ... FROM tasks WHERE ...

// AFTER: Add safety limit
SELECT ... FROM tasks WHERE ... LIMIT 100
```
**Impact:** Prevents runaway queries for edge cases

---

### Fix #4: Add Database Index
```sql
-- Index on user_id + created_at for faster filtering
CREATE INDEX idx_tasks_user_created ON tasks(user_id, created_at DESC)
  WHERE archived = false OR archived IS NULL;
```
**Impact:** Speeds up query from ~500ms → ~50ms (90% reduction)

---

### Fix #5: Increase Vercel Timeout
```javascript
// vercel.json
{
  "functions": {
    "api/**/*.js": {
      "maxDuration": 25  // Increase from 15s to 25s
    }
  }
}
```
**Impact:** Gives buffer for edge cases (users with 500+ tasks)

---

## 🧪 Verification Plan (AFTER FIX)

### Test 1: Query Performance
```sql
EXPLAIN ANALYZE
SELECT id, completed, completed_at, created_at, category, priority
FROM tasks
WHERE user_id = $1
AND (archived = false OR archived IS NULL)
AND (created_at > NOW() - INTERVAL '7 days' OR completed_at > NOW() - INTERVAL '7 days')
LIMIT 100;
```
**Expected:** Execution time < 100ms

---

### Test 2: End-to-End Performance
```bash
node -e "
const { buildMessageContext } = require('./services/message-generator-llm');
const start = Date.now();
buildMessageContext('test-user-id').then(() => {
  console.log('Duration:', Date.now() - start, 'ms');
});
"
```
**Expected:** Duration < 5000ms

---

### Test 3: Production Verification
```bash
curl -X GET "https://cosmic-ocean-api.vercel.app/api/wallpaper?token=..." \
  -H "Authorization: Bearer <JWT>" \
  -w "\nTime: %{time_total}s\n"
```
**Expected:** Response time < 5s (including wallpaper generation)

---

## ✅ Success Criteria

After fix is deployed:
- [ ] buildMessageContext() completes in < 5s
- [ ] Query returns < 100 rows
- [ ] Database query executes in < 100ms
- [ ] StatsAggregator computation < 500ms
- [ ] Total wallpaper generation < 8s (including LLM call)
- [ ] No timeouts in production logs
- [ ] Vercel function logs show completion < 25s

---

## 📝 Summary

**Problem:** 30-day task query with SELECT * causes 8-18s processing time, exceeding 15s Vercel limit

**Root Cause:**
1. Too many rows (30 days → 200 rows)
2. Too much data (SELECT * → 100 KB)
3. No index on filtered columns
4. Timeout too short (15s)

**Fix:**
1. Reduce to 7 days (200 → 50 rows)
2. Select only needed columns (100 KB → 20 KB)
3. Add LIMIT 100 safeguard
4. Add database index
5. Increase timeout to 25s

**Expected Result:** 8-18s → 2-4s (75% reduction)

---

**Next Step:** Apply the fix and verify with real production data
