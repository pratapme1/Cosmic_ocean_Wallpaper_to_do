# Bug Report: Due Time and Yesterday Parsing Issues

**Date:** 2026-01-06
**Version:** v1.5.9
**Priority:** HIGH
**Status:** OPEN

---

## Summary

Despite multiple fixes (v1.5.5 through v1.5.9), users still experiencing issues with:
1. Due time calculations for "in X minutes" tasks
2. "Yesterday" date parsing and OVERDUE display

---

## Issues Reported

### Issue 1: "in X minutes" Due Time Calculation
**Input:** "record in 39 minutes"
**Expected:** Due time ~39 minutes from creation, correct countdown on wallpaper
**Actual:** User reports ~8 minute countdown with ~30m tag (inconsistent)

**Root Cause Investigation Needed:**
- LLM time calculation may still be incorrect
- Timezone conversion between LLM response and storage
- Countdown calculation in wallpaper renderer

### Issue 2: "Yesterday" Not Showing Properly
**Input:** "missed alert yesterday"
**Expected:** Task shows as OVERDUE in wallpaper
**Actual:** User reports not seeing expected behavior

**Root Cause Investigation Needed:**
- LLM parsing of "yesterday" keyword
- Date storage in database
- OVERDUE section rendering logic

---

## Fixes Already Attempted

| Version | Fix | Result |
|---------|-----|--------|
| v1.5.5 | Fixed "now" keyword UTC→IST | Partial |
| v1.5.6 | Added "yesterday" to dateWords | Partial |
| v1.5.7 | Added explicit "yesterday" example in LLM prompt | Partial |
| v1.5.8 | Fixed "in X minutes" time math, JSON extraction | Partial |
| v1.5.9 | Fixed midnight rollover (00:00 = tomorrow) | Partial |

---

## Files Involved

### Backend
- `/backend/server.js` - Task creation, date/time handling (lines 551-655)
- `/backend/utils/llm-task-parser.js` - LLM prompt and parsing
- `/backend/services/text-renderer.js` - Wallpaper countdown calculation (lines 360-400)

### Key Code Sections

**LLM Time Parsing (llm-task-parser.js:74-85):**
```javascript
3. dueTime: ONLY if EXPLICITLY mentioned. Otherwise null.
   - "in X minutes/hours" → ADD X to ${currentTime} and return result as HH:MM
     * EXAMPLE: If currentTime is 23:21 and input is "in 39 minutes" → 00:00
```

**Countdown Calculation (text-renderer.js:371-388):**
```javascript
// FIX v1.5.1: due_time is stored in user's LOCAL timezone, not UTC
const tempUtc = new Date(`${dateStr}T${task.due_time}Z`);
// Calculate timezone offset and adjust
fullDueDate = new Date(tempUtc.getTime() + offsetMs);
```

---

## Investigation Steps for Tomorrow

### Step 1: Add Comprehensive Logging
Add debug logging to track the full data flow:
```
User Input → LLM Response → DB Storage → DB Query → Wallpaper Render
```

### Step 2: Test Each Component Independently
1. Test LLM parsing locally with exact user inputs
2. Query database to verify stored values
3. Test countdown calculation with known values

### Step 3: Verify User's Exact Inputs
Get the EXACT task inputs from user:
- What text did they type?
- What time did they create it?
- What does the wallpaper show?

### Step 4: Check Android App
- Is Android sending correct timezone?
- Is Android displaying API response correctly?

---

## Test Cases Needed

```javascript
// Test 1: "in X minutes" near midnight
Input: "task in 39 minutes" at 23:21 IST
Expected: due_date = 2026-01-07, due_time = 00:00

// Test 2: "yesterday"
Input: "missed alert yesterday" on 2026-01-06
Expected: due_date = 2026-01-05, shows OVERDUE

// Test 3: "now"
Input: "call now" at 23:30 IST
Expected: due_date = 2026-01-06, due_time = 23:30
```

---

## Potential Root Causes

1. **Timezone Mismatch:** Server (UTC) vs User (IST) vs LLM context
2. **Date Object Conversion:** PostgreSQL DATE type vs JavaScript Date
3. **LLM Inconsistency:** Claude Haiku may give different results for same input
4. **Countdown Math:** Offset calculation may be incorrect for edge cases

---

## Notes

- User timezone: Asia/Kolkata (IST = UTC+5:30)
- Server runs in UTC (Vercel)
- LLM receives user's local time in context
- Database stores DATE as string (type parser override)
- Database stores TIME as string (HH:MM:SS)

---

## Next Session Action Items

- [ ] Get exact user inputs that fail
- [ ] Add comprehensive logging to trace data flow
- [ ] Test with user's exact scenarios
- [ ] Fix identified issues
- [ ] Verify with user before marking resolved
