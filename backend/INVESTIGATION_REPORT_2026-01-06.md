# Task Creation Bug Investigation Report
**Date:** 2026-01-06
**Investigator:** Claude Sonnet 4.5
**Issues:** Multiple bugs in task creation with LLM/NLP parsing

---

## Executive Summary

Three critical bugs discovered affecting ALL task creation:

1. **Task titles not cleaned** - Raw input stored instead of parsed clean title
2. **"now" tasks get WRONG due_date** - Yesterday's date instead of null or today
3. **"tomorrow" tasks have NULL due_date** - Should have tomorrow's date
4. **Priority mismatch** - LLM-parsed priority not stored correctly

---

## Evidence: User "timezone@gmail.com" (ID: e87c9221-5e14-4e77-8281-60179f999532)

### Test Case 1: "call mom now"

**Created:** 2026-01-06 08:32:35 IST

#### LLM Parser Output (from logs):
```json
{
  "input": "call mom now",
  "title": "Call mom",        ← CLEANED (removed "now")
  "dueDate": null,             ← Correct
  "dueTime": null,             ← Correct
  "priority": 1,               ← URGENT (detected)
  "category": "personal",
  "source": "llm"
}
```

#### Backend Log (line 644):
```
[Task Created] userId=e87c9221..., title="Call mom", priority=1, category=personal
```

#### Database Record:
```json
{
  "id": "14a4d524-0ccc-41c1-82fc-d54776ea46bb",
  "title": "call mom now",     ← WRONG: Raw title, not cleaned
  "raw_title": "call mom now",
  "due_date": "2026-01-05",    ← WRONG: YESTERDAY! (task created on Jan 6)
  "due_time": "08:40:08",      ← WRONG: Where did this come from?
  "priority": 3,               ← WRONG: Should be 1 (urgent)
  "created_at": "2026-01-06T03:02:35.561Z"
}
```

**Discrepancies:**
| Field | LLM Output | Backend Log | Database | Status |
|-------|------------|-------------|----------|--------|
| title | "Call mom" | "Call mom" | "call mom now" | ❌ WRONG |
| priority | 1 | 1 | 3 | ❌ WRONG |
| due_date | null | (not logged) | 2026-01-05 | ❌ WRONG |
| due_time | null | (not logged) | 08:40:08 | ❌ WRONG |

---

### Test Case 2: "update resume tomorrow"

**Created:** 2026-01-06 08:34:14 IST

#### Database Record:
```json
{
  "title": "update resume tomorrow ",  ← WRONG: Not cleaned
  "raw_title": "update resume tomorrow ",
  "due_date": null,                    ← WRONG: Should be 2026-01-07
  "due_time": null,
  "priority": 2
}
```

**Expected:**
- title: "update resume" (cleaned)
- due_date: 2026-01-07 (tomorrow)

---

### Test Case 3: "talk to Anand in an hour"

**Created:** 2026-01-06 08:33:17 IST

#### Database Record:
```json
{
  "title": "talk to Anand in an hour ",  ← WRONG: Not cleaned
  "raw_title": "talk to Anand in an hour ",
  "due_date": null,                      ← WRONG: Should be 2026-01-06
  "due_time": "15:03:00",                ← Correct time
  "priority": 2,
  "completed": true
}
```

**Expected:**
- title: "talk to Anand" (cleaned)
- due_date: 2026-01-06 (today)
- due_time: 15:03:00 (correct)

---

## Code Analysis

### Backend Task Creation Flow

```
POST /api/tasks
  ↓
511: Extract req.body.rawTitle, req.body.priority
  ↓
540: parsed = await parseLLM(rawTitle, userTimezone)
  ↓
542: title = parsed.title  ← SHOULD set cleaned title
  ↓
557: calculatedPriority = priority !== undefined ? priority : (parsed.priority || 2)
     ← If Android sends priority, it OVERRIDES LLM
  ↓
570-582: CATCH BLOCK (if parsing fails):
  title = inputText  ← Sets RAW title
  calculatedPriority = 2
  ↓
585-602: Extract due_date and due_time from parsed.dueDate
  ↓
605-637: INSERT INTO tasks (title, raw_title, priority, due_date, due_time...)
  VALUES ($1, $2, $3, $4, $5...)
  ↓
644: console.log(`[Task Created] title="${title}", priority=${calculatedPriority}`)
     ← Logs VARIABLES, not database result
  ↓
646: res.json(result.rows[0])  ← Return database record to Android
```

### Android Request (TaskRepository.kt:142-150)

```kotlin
apiService.createTask(
    mapOf(
        "rawTitle" to star.title,  // Only sends rawTitle
        "x" to star.particle.x.toString(),
        "y" to star.particle.y.toString(),
        "is_recurring" to star.isRecurring.toString(),
        "echo_interval" to (star.echoInterval?.name ?: ""),
        "is_subtask" to star.isSubtask.toString()
        // Does NOT send: title, priority, due_date
    )
)
```

---

## Hypothesis: Silent Error in Parsing

### Theory 1: Catch Block is Executing

If `parseLLM()` throws an error AFTER logging "Parsed task" but BEFORE returning:
- Line 569: Log shows correct parsed values
- Then exception occurs
- Line 570: Catch block executes
- Line 573: `title = inputText` (raw title)
- Line 582: `calculatedPriority = 2` (default)

**Problem:** Database has `priority = 3`, not `2`. So catch block is NOT executing.

### Theory 2: parseLLM() Returns Wrong Data

The log at line 569 shows what the `parsed` object APPEARS to be, but maybe the actual object has different data?

**Test needed:** Add logging of `parsed.title` and `parsed.priority` IMMEDIATELY after line 542 and 557.

### Theory 3: Multiple Task Creations

Maybe the task was created multiple times:
1. First creation: Failed/wrong data → Database record
2. Second creation: Success → Logged but not saved?

**Evidence against:** Only ONE task in database with this raw_title and timestamp.

### Theory 4: Database Constraint/Trigger

Database might have triggers copying data or overriding values.

**Evidence against:** Only foreign key triggers found, no custom triggers.

---

## Why "call mom now" Has due_date = Yesterday

### Observation:
```
Database: due_date = 2026-01-05 (yesterday)
          due_time = 08:40:08
Created:  2026-01-06 08:32:35 IST
```

The due_time (08:40:08) is ~8 minutes AFTER the creation time (08:32:35).

### Hypothesis:
1. Something generated a Date object with time 08:40:08 IST
2. parseDateTimeForDB() was called with this Date
3. Function uses UTC extraction (line 72-75)
4. Date in IST at 08:40 = Date in UTC at 03:10 (Jan 6)
5. But somehow the UTC date extraction gave Jan 5?

### Test Needed:
Recreate the scenario with detailed logging in parseDateTimeForDB().

---

## Why "tomorrow" Tasks Have NULL due_date

### Expected Behavior:
- Input: "update resume tomorrow"
- LLM should return: `dueDate: "2026-01-07T00:00:00Z"`
- Backend should store: `due_date = '2026-01-07'`

### Actual Behavior:
- Database: `due_date = null`

### Hypothesis:
LLM is not returning dueDate for "tomorrow" tasks.

### Test Needed:
Check LLM logs for "tomorrow" task parsing.

---

## Root Cause Summary

| Bug | Symptom | Suspected Cause |
|-----|---------|-----------------|
| Title not cleaned | "call mom now" instead of "Call mom" | `title` variable overridden in catch block OR parseLLM returns wrong data |
| Wrong priority | 3 instead of 1 | Android sending `priority` field OR catch block OR parseLLM returns wrong data |
| Wrong due_date | 2026-01-05 instead of null | parseDateTimeForDB timezone bug OR unexpected Date object |
| NULL due_date for "tomorrow" | null instead of 2026-01-07 | LLM not parsing "tomorrow" |

---

## Next Steps

1. **Add detailed logging** to pinpoint where data gets corrupted:
   ```javascript
   542: title = parsed.title;
   +    console.log(`[DEBUG] After parsing: title="${title}", parsed.title="${parsed.title}"`);

   557: calculatedPriority = priority !== undefined ? priority : (parsed.priority || 2);
   +    console.log(`[DEBUG] Priority: req.body.priority=${priority}, parsed.priority=${parsed.priority}, calculated=${calculatedPriority}`);

   616: title,  // In INSERT values
   +    console.log(`[DEBUG] INSERT values: title="${title}", priority=${calculatedPriority}, due_date=${dueDateForDB}`);
   ```

2. **Test LLM parser directly** with timezone context to see actual output

3. **Check if Android is sending additional fields** we're not aware of

4. **Reproduce the bug** with controlled input and full logging

---

## Questions for User

1. Do you see the wallpaper showing "call mom now" or "Call mom"?
2. What does the wallpaper show for due time/overdue status?
3. Can you create a new task "test tomorrow" and share the immediate response?

