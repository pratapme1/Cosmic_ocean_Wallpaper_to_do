# Tasks for Tomorrow - 2026-01-06

## 🔴 PRIORITY 1: Fix Message Generation Timeout

**Issue:** v1.4.0 deployed successfully but message generation timing out at 15s limit

**Status:** Documented and ready for fix
**Estimated Time:** 30-45 minutes
**Files to Modify:** `backend/services/message-generator-llm.js`

---

## Fix Checklist

### Fix 1: Increase Timeout (15s → 25s) ⚡
**File:** `backend/services/message-generator-llm.js`
**Line:** 499

**Current Code:**
```javascript
setTimeout(() => reject(new Error('Claude timeout (15s)')), 15000);
```

**New Code:**
```javascript
setTimeout(() => reject(new Error('Claude timeout (25s)')), 25000);
```

**Risk:** LOW
**Expected Result:** 90% success rate

---

### Fix 2: Optimize 30-Day Query (Add LIMIT 500) 🎯
**File:** `backend/services/message-generator-llm.js`
**Lines:** 115-122

**Current Code:**
```javascript
const allTasksQuery = `
  SELECT *
  FROM tasks
  WHERE user_id = $1
  AND (archived = false OR archived IS NULL)
  AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
`;
```

**New Code:**
```javascript
const allTasksQuery = `
  SELECT *
  FROM tasks
  WHERE user_id = $1
  AND (archived = false OR archived IS NULL)
  AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
  ORDER BY completed_at DESC NULLS LAST
  LIMIT 500
`;
```

**Risk:** LOW (500 tasks is more than enough for stats)
**Expected Result:** Faster query execution

---

## Testing Plan

### 1. Local Testing
```bash
cd /home/vi/supernova/backend

# Test with real user (has 50+ tasks)
node -e "
require('dotenv').config();
const { generateAndCacheMessages } = require('./services/message-generator-llm');

async function test() {
  console.time('Total Time');
  try {
    const result = await generateAndCacheMessages('588d220e-5fea-4a66-a08c-4605568e8bcd');
    console.log('✅ Success:', result);
  } catch (error) {
    console.log('❌ Error:', error.message);
  }
  console.timeEnd('Total Time');
}

test();
"
```

**Expected Output:**
- Total Time: <20 seconds ✅
- Success message with count

---

### 2. Add Timing Logs (Optional but Recommended)

Add console.time() markers to measure each step:

```javascript
// In buildMessageContext()
console.time('[MessageGen] 30-day query');
const allTasksResult = await client.query(allTasksQuery, [userId]);
console.timeEnd('[MessageGen] 30-day query');

console.time('[MessageGen] Stats computation');
const stats = statsAggregator.computeStats(allTasks);
console.timeEnd('[MessageGen] Stats computation');

// In generateAndCacheMessages()
console.time('[MessageGen] Claude API call');
const result = await anthropic.messages.create(...);
console.timeEnd('[MessageGen] Claude API call');
```

---

### 3. Deploy to Production

```bash
# Commit changes
git add backend/services/message-generator-llm.js
git commit -m "Fix: Increase message timeout to 25s + optimize query

- Increase timeout from 15s → 25s
- Add LIMIT 500 to 30-day task query
- Fixes timeout issue discovered in v1.4.0

Resolves: PRODUCTION_ISSUE_v1.4.0_TIMEOUT.md"

# Push to GitHub (triggers auto-deploy)
git push origin main
```

---

### 4. Monitor Production

**Wait 5 minutes for deployment, then:**

```bash
# Check health endpoint
curl https://cosmic-ocean-api.vercel.app/api/health

# Monitor logs for timeout errors
vercel logs cosmic-ocean-api --since 1h | grep -i "timeout"

# Monitor logs for fallback usage
vercel logs cosmic-ocean-api --since 1h | grep -i "fallback"

# Check success rate
vercel logs cosmic-ocean-api --since 1h | grep "MessageGen.*Cached" | wc -l
```

**Success Criteria:**
- No "timeout" errors in logs
- "Cached X messages" logs appearing regularly
- No "Using fallback templates" warnings

---

## Rollback Plan (If Issues Occur)

```bash
# Revert the commit
git revert HEAD

# Push to trigger auto-deploy
git push origin main

# Verify rollback
curl https://cosmic-ocean-api.vercel.app/api/health
# Should show version going back
```

---

## Documentation Updates After Fix

### 1. Update CHANGELOG.md
Add to v1.4.0 section:
```markdown
### Fixed (2026-01-06)
- **Message generation timeout**: Increased from 15s → 25s
- **Query optimization**: Added LIMIT 500 to 30-day task query
- **Impact**: Message generation success rate improved from ~50% → >90%
```

### 2. Update STATUS.md
Change:
```markdown
**Status:** ✅ **PRODUCTION DEPLOYED** - ⚠️ **TIMEOUT ISSUE IDENTIFIED**
```

To:
```markdown
**Status:** ✅ **PRODUCTION DEPLOYED** - ✅ **ALL SYSTEMS OPERATIONAL**
```

### 3. Update ROADMAP.md
Check off the tasks:
```markdown
**Immediate Tasks (2026-01-06):**
- [x] Fix 1: Increase timeout 15s → 25s
- [x] Fix 2: Optimize 30-day query (add LIMIT 500)
- [x] Test & redeploy to production
- [x] Monitor success rate (target: >90%)
```

### 4. Close PRODUCTION_ISSUE_v1.4.0_TIMEOUT.md
Add at the top:
```markdown
**RESOLVED:** 2026-01-06
**Fix Version:** v1.4.1 (patch)
**Resolution:** Timeout increased to 25s + query optimized with LIMIT 500
```

---

## Version Bump Decision

**Question:** Should we bump to v1.4.1 (patch) or keep as v1.4.0?

**Recommendation:** Create v1.4.1 (patch release)
- **Reason:** This is a bug fix, not a feature addition
- **Semantic versioning:** Patch version for bug fixes

**If creating v1.4.1:**
```bash
# Update package.json
# "version": "1.4.0" → "1.4.1"

# Update server.js
# version: '1.4.0' → version: '1.4.1'

# Commit with version bump
git commit -m "Version bump: 1.4.1 - Message timeout fix"
```

---

## Summary

**Total Time:** 30-45 minutes
**Risk Level:** LOW
**User Impact:** POSITIVE (fixes degraded feature)
**Rollback Available:** YES

**Steps:**
1. Make 2 small code changes (timeout + query)
2. Test locally (verify <20s)
3. Commit & push (auto-deploys)
4. Monitor for 1 hour
5. Update documentation
6. Mark issue as resolved

---

**Created:** 2026-01-05 19:30 UTC
**Priority:** 🔴 HIGH (production issue)
**Owner:** Development Team
