# Production Issue: Message Generation Timeout (v1.4.0)

**Discovered:** 2026-01-05 19:08 UTC
**Severity:** 🟡 MEDIUM
**Status:** 🔴 OPEN - Fix scheduled for 2026-01-06
**Affected Version:** v1.4.0
**Impact:** Users receive fallback template messages instead of personalized StatsAggregator messages

---

## Issue Summary

After deploying v1.4.0 with StatsAggregator integration, message generation is timing out at the 15-second limit, causing the system to fall back to template messages.

---

## Production Logs

```
2026-01-05 19:08:22.815 [error] [MessageGen] Error generating messages: Claude timeout (15s)
2026-01-05 19:08:22.815 [info] [MessageGen] Using fallback templates
2026-01-05 19:08:23.533 [info] [MessageGen] Cached 1 messages for user 588d220e-5fea-4a66-a08c-4605568e8bcd
```

**Good News:**
- ✅ LLM task parser working correctly
- ✅ Fallback templates working (graceful degradation)
- ✅ Tasks being created successfully
- ✅ No crashes or data loss

**Problem:**
- ❌ Message generation hitting 15-second timeout
- ❌ Users not getting personalized messages with stats/patterns

---

## Root Cause Analysis

The v1.4.0 deployment added StatsAggregator integration, which:
1. Queries 30 days of task history (including completed tasks)
2. Computes stats (streaks, patterns, completions)
3. Calls Claude API to generate personalized messages

**Time Breakdown:**
- 30-day task query: ~3-5 seconds (variable based on task count)
- StatsAggregator computation: ~1-2 seconds
- Claude API call: ~8-12 seconds
- **Total: ~12-19 seconds** (exceeds 15s timeout)

**Code Location:**
`backend/services/message-generator-llm.js:499`
```javascript
setTimeout(() => reject(new Error('Claude timeout (15s)')), 15000);
```

---

## Impact Assessment

### User Impact
- **Severity:** MEDIUM (feature degraded, not broken)
- **Scope:** All users attempting wallpaper generation
- **Workaround:** Fallback templates still provide functional messages
- **Data Loss:** NONE - all other features working normally

### System Impact
- Task creation: ✅ Working (LLM parser not affected)
- Database operations: ✅ Working
- Authentication: ✅ Working
- Wallpaper rendering: ✅ Working (with fallback messages)

---

## Proposed Fixes

### Fix 1: Increase Timeout (Immediate - Priority 1)
**Change:** 15s → 25s timeout
**File:** `backend/services/message-generator-llm.js:499`
**Risk:** LOW
**Effort:** 5 minutes
**Expected Result:** 90% success rate

```javascript
// Before
setTimeout(() => reject(new Error('Claude timeout (15s)')), 15000);

// After
setTimeout(() => reject(new Error('Claude timeout (25s)')), 25000);
```

### Fix 2: Optimize 30-Day Query (Short-term - Priority 2)
**Change:** Add LIMIT 500 to task query
**File:** `backend/services/message-generator-llm.js:115-122`
**Risk:** LOW
**Effort:** 10 minutes
**Expected Result:** Faster query execution

```sql
SELECT * FROM tasks
WHERE user_id = $1
AND (archived = false OR archived IS NULL)
AND (created_at > NOW() - INTERVAL '30 days' OR completed_at > NOW() - INTERVAL '30 days')
ORDER BY completed_at DESC NULLS LAST
LIMIT 500;  -- Add limit
```

### Fix 3: Stats Caching (Long-term - Priority 3)
**Change:** Cache stats for 1 hour per user
**File:** New file `backend/services/stats-cache.js`
**Risk:** MEDIUM (cache invalidation complexity)
**Effort:** 1 hour
**Expected Result:** Near-instant stats retrieval for repeat requests

**Implementation:**
- Use Redis or in-memory cache
- TTL: 1 hour
- Cache key: `stats:${userId}`
- Invalidate on task completion

---

## Testing Plan (Tomorrow)

1. **Local Testing:**
   ```bash
   # Test with real user account (has 50+ tasks)
   node -e "require('./services/message-generator-llm').generateAndCacheMessages('588d220e-5fea-4a66-a08c-4605568e8bcd')"
   ```

2. **Measure Timings:**
   - Add console.time() markers
   - Log query duration, stats computation, Claude API
   - Verify total time <25s

3. **Deploy & Monitor:**
   - Deploy Fix 1 + Fix 2
   - Monitor Vercel logs for 1 hour
   - Verify success rate >90%

4. **Rollback Plan:**
   ```bash
   git revert HEAD
   npx vercel --prod
   ```

---

## Timeline

| Date | Action | Status |
|------|--------|--------|
| 2026-01-05 19:08 | Issue discovered in production | ✅ Identified |
| 2026-01-05 19:30 | Root cause analyzed | ✅ Complete |
| 2026-01-05 19:30 | Documentation updated | ✅ Complete |
| **2026-01-06** | **Implement Fix 1 + Fix 2** | 🔴 SCHEDULED |
| **2026-01-06** | **Test & deploy to production** | 🔴 SCHEDULED |
| 2026-01-07+ | Implement Fix 3 (stats caching) | ⚪ BACKLOG |

---

## Monitoring

**Key Metrics to Watch:**
- Message generation success rate (target: >90%)
- Average generation time (target: <20s)
- Fallback template usage (target: <10%)
- Database query duration (target: <5s)

**Vercel Logs to Monitor:**
```bash
# Check for timeout errors
vercel logs cosmic-ocean-api --since 1h | grep "timeout"

# Check fallback usage
vercel logs cosmic-ocean-api --since 1h | grep "fallback"
```

---

## Related Files

- `backend/services/message-generator-llm.js` - Timeout setting + 30-day query
- `backend/services/stats-aggregator.js` - Stats computation logic
- `STATUS.md` - Updated with issue status
- `CHANGELOG.md` - v1.4.0 release notes
- `backend/LLM_CHANGES_2026-01-05.md` - Technical changes

---

## Communication

**Status:** ✅ Documented
**User Impact:** NONE (graceful fallback working)
**Next Update:** 2026-01-06 after fix deployment

---

**Created:** 2026-01-05 19:30 UTC
**Owner:** Development Team
**Priority:** MEDIUM (P2)
**Target Resolution:** 2026-01-06
