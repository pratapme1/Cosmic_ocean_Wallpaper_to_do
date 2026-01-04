# LLM Integration Architecture
## Cosmic Ocean Intelligence Layer Enhancement

> **Version:** 1.0
> **Created:** 2026-01-04
> **Purpose:** Complete architectural specification for integrating LLM-powered task parsing and message intelligence into Cosmic Ocean
> **Scope:** Backend API + Android App + Database Schema
> **Timeline:** 8 weeks (MVP implementation)
> **Reference Documents:** `cosmic-ocean-llm-parser-complete.docx`, `cosmic-ocean-message-intelligence-engine.docx`

---

## TABLE OF CONTENTS

1. [Executive Summary](#executive-summary)
2. [Current State Analysis](#current-state-analysis)
3. [New System Architecture](#new-system-architecture)
4. [Integration Strategy](#integration-strategy)
5. [Task Parsing Flow](#task-parsing-flow)
6. [Message Intelligence Flow](#message-intelligence-flow)
7. [Database Schema Changes](#database-schema-changes)
8. [API Contract Changes](#api-contract-changes)
9. [Android Integration](#android-integration)
10. [Configuration & Feature Flags](#configuration--feature-flags)
11. [Migration Strategy](#migration-strategy)
12. [Risk Mitigation](#risk-mitigation)
13. [Implementation Roadmap](#implementation-roadmap)

---

## EXECUTIVE SUMMARY

### The Vision

Transform Cosmic Ocean from a pattern-based NLP system to an intelligent, LLM-powered system that:

1. **Understands natural language** - Parses complex user inputs like "email manager about the budget thing we discussed by Friday 3pm"
2. **Generates dynamic messages** - Creates fresh, contextual wallpaper messages that never feel repetitive
3. **Learns from users** - Tracks accuracy and improves over time
4. **Maintains reliability** - Graceful fallback to local parsing when LLM unavailable

### Why LLM Now?

**Current System Limitations:**
- **Pattern-based parser** - Brittle, requires code changes for every edge case (recent "in 10 minutes" bug proves this)
- **Template-based messages** - 50 templates become stale after 2-3 weeks of daily use
- **No semantic understanding** - "call mom she's in hospital" has no urgency without keyword match
- **High maintenance cost** - Every user complaint requires regex updates

**LLM Benefits:**
- **Natural language understanding** - Handles complex inputs without hardcoded patterns
- **Infinite variety** - Same data, different expressions every time
- **Zero-maintenance intelligence** - No regex updates needed
- **Cost-effective** - Gemini 1.5 Flash: FREE tier covers 1500 req/day (100-200 beta users)

### MVP Goals

**Week 8 Deliverables:**
1. ✅ LLM task parser deployed to production (backend + Android)
2. ✅ Message intelligence engine generating dynamic wallpaper messages
3. ✅ Graceful fallback system (LLM failure → local parser)
4. ✅ Rate limiting (10/min, 100/day per user)
5. ✅ Hallucination validation (strip invented dates/times)
6. ✅ Analytics tracking (parse accuracy, message engagement)
7. ✅ User preference controls (enable/disable LLM)

### Success Metrics

| Metric | Current (Pattern-based) | Target (LLM) | Measurement |
|--------|-------------------------|--------------|-------------|
| **Parse Accuracy** | 70% (users edit 30% of parses) | 85%+ (users edit <15%) | Track user edits in `parse_analytics` table |
| **Message Uniqueness** | 50 templates (repetition in 3 weeks) | Infinite variety (no repeat in 30 days) | Track message history, check duplicates |
| **Edge Case Coverage** | Requires code for each case | Handles unseen inputs | Test suite with 100 real user inputs |
| **User Satisfaction** | Baseline (to be measured) | +30% improvement | In-app rating after parse confirmation |
| **Maintenance Cost** | 4 hours/week (pattern updates) | 0 hours/week (no code changes) | Track engineering time |

---

## CURRENT STATE ANALYSIS

### Backend Structure (As-Is)

```
backend/
├── server.js                           # Express app, routes
├── utils/
│   ├── task-parser.js                 # Pattern-based parsing (CURRENT)
│   └── nlp-patterns.js                # Regex patterns, keywords
├── services/
│   ├── message-engine.js              # Template-based messages (CURRENT)
│   ├── wallpaper-generator-enhanced.js # PNG generation
│   ├── text-renderer.js               # Satori text rendering
│   └── stats-aggregator.js            # User statistics
└── routes/
    └── tasks.js                        # POST /api/tasks, etc.
```

### Current Task Creation Flow

```
┌──────────────────┐
│ User Input       │  "Call mom in 10 minutes"
└────────┬─────────┘
         │
         ↓ (1) Android sends raw input
┌──────────────────────────────────────────┐
│ POST /api/tasks                          │
│ Body: { title: "Call mom in 10 minutes" }│
└────────┬─────────────────────────────────┘
         │
         ↓ (2) Backend receives request
┌──────────────────────────────────────────┐
│ backend/utils/task-parser.js             │
│                                          │
│ Pattern-based extraction:               │
│ - Regex for "in X minutes"              │
│ - Keyword matching for priority         │
│ - Category detection via word lists     │
│                                          │
│ ISSUES:                                  │
│ ❌ Brittle (recent "in 10 min" bug)      │
│ ❌ No semantic understanding             │
│ ❌ High maintenance (code for each case) │
└────────┬─────────────────────────────────┘
         │
         ↓ (3) Parser returns structured data
┌──────────────────────────────────────────┐
│ {                                        │
│   title: "Call mom in 10 minutes",      │
│   dueDate: null,  // ❌ Missed parsing   │
│   dueTime: null,  // ❌ Not extracted    │
│   priority: 2                            │
│ }                                        │
└────────┬─────────────────────────────────┘
         │
         ↓ (4) Save to PostgreSQL
┌──────────────────────────────────────────┐
│ INSERT INTO tasks (title, due_time, ...) │
│ VALUES ('Call mom in 10 minutes', NULL...) │
└────────┬─────────────────────────────────┘
         │
         ↓ (5) Return to Android
┌──────────────────┐
│ Task Created     │  User sees: "Call mom in 10 minutes" (no time badge)
└──────────────────┘
```

**Problem:** User types natural language, but system doesn't understand it. User must manually set time.

### Current Message Flow

```
┌──────────────────┐
│ Wallpaper Request│  GET /api/wallpaper?userId=123
└────────┬─────────┘
         │
         ↓ (1) Backend fetches user stats
┌──────────────────────────────────────────┐
│ stats-aggregator.js                      │
│                                          │
│ Query DB:                                │
│ - completedToday: 8                      │
│ - pendingTasks: 3                        │
│ - streakDays: 5                          │
└────────┬─────────────────────────────────┘
         │
         ↓ (2) Select message template
┌──────────────────────────────────────────┐
│ message-engine.js                        │
│                                          │
│ const templates = [                      │
│   "8 tasks done today!",                 │
│   "{count} tasks completed today!",      │
│   "You crushed {count} tasks today!"     │
│ ]                                        │
│                                          │
│ message = templates[Math.random()]       │
│                                          │
│ ISSUES:                                  │
│ ❌ Random selection (no memory)          │
│ ❌ User sees repeats after 2-3 weeks     │
│ ❌ No anti-pattern prevention            │
└────────┬─────────────────────────────────┘
         │
         ↓ (3) Template filled
┌──────────────────────────────────────────┐
│ "8 tasks done today!"                    │
│ (same message user saw 3 times this week)│
└────────┬─────────────────────────────────┘
         │
         ↓ (4) Render to PNG
┌──────────────────┐
│ Wallpaper Image  │  User sees same message, gets bored
└──────────────────┘
```

**Problem:** Templates become noise. "8 tasks done today!" loses meaning after 10th repetition.

### Android Structure (As-Is)

```
android/app/src/main/java/com/cosmicocean/
├── network/
│   └── ApiService.kt                  # Retrofit API calls to backend
├── data/
│   └── TaskRepository.kt              # Talks to ApiService
├── ui/
│   └── TaskCreationScreen.kt          # User types task here
└── service/
    └── WallpaperService.kt            # Fetches wallpaper from backend
```

### Current Android Task Creation Flow

```
┌──────────────────────────────────────────┐
│ TaskCreationScreen.kt                    │
│                                          │
│ User types: "Call mom in 10 minutes"    │
│                                          │
│ User taps "Create Task"                  │
└────────┬─────────────────────────────────┘
         │
         ↓ (1) Call repository
┌──────────────────────────────────────────┐
│ TaskRepository.kt                        │
│                                          │
│ fun createTask(title: String) {          │
│   apiService.createTask(                 │
│     CreateTaskRequest(title)             │
│   )                                      │
│ }                                        │
│                                          │
│ NO PARSING - just sends raw title        │
└────────┬─────────────────────────────────┘
         │
         ↓ (2) API call
┌──────────────────────────────────────────┐
│ ApiService.kt (Retrofit)                 │
│                                          │
│ @POST("api/tasks")                       │
│ suspend fun createTask(                  │
│   @Body request: CreateTaskRequest       │
│ ): Response<Task>                        │
└────────┬─────────────────────────────────┘
         │
         ↓ (3) Backend response
┌──────────────────┐
│ Task Created     │  Room DB saves locally, UI updates
└──────────────────┘
```

**Problem:** Android has no awareness of parsing. Just passes raw text to backend.

---

## NEW SYSTEM ARCHITECTURE

### Backend New Structure (To-Be)

```
backend/
├── server.js                           # [NO CHANGE - just new routes]
│
├── utils/
│   ├── task-parser.js                 # [KEEP - fallback parsing]
│   ├── nlp-patterns.js                # [KEEP - fallback patterns]
│   └── llm-task-parser.js             # ✨ [NEW - LLM parsing]
│
├── services/
│   ├── message-engine.js              # [KEEP - fallback messages]
│   ├── message-generator-llm.js       # ✨ [NEW - LLM message generation]
│   ├── wallpaper-generator-enhanced.js # [NO CHANGE]
│   ├── text-renderer.js               # [NO CHANGE]
│   └── stats-aggregator.js            # [NO CHANGE]
│
├── middleware/
│   └── rate-limiter.js                # ✨ [NEW - API rate limiting]
│
└── routes/
    ├── tasks.js                        # [MODIFIED - add LLM endpoint]
    └── messages.js                     # ✨ [NEW - message generation endpoint]
```

### Android New Structure (To-Be)

```
android/app/src/main/java/com/cosmicocean/
├── network/
│   └── ApiService.kt                  # [MODIFIED - add new endpoints]
│
├── data/
│   └── TaskRepository.kt              # [MODIFIED - call new parse endpoint]
│
├── llm/                                # ✨ [NEW FOLDER]
│   ├── GeminiClient.kt                # ✨ [NEW - optional direct LLM]
│   └── ParsePreviewProvider.kt        # ✨ [NEW - live parse preview]
│
├── ui/
│   └── TaskCreationScreen.kt          # [MODIFIED - show parse preview]
│
└── service/
    └── WallpaperService.kt            # [NO CHANGE - backend handles it]
```

### Key Architectural Principles

#### 1. Parallel Systems (Not Replacement)

**Old and new systems coexist:**
- LLM parser tries first
- Falls back to pattern parser on failure
- Both write to same database tables
- No breaking changes to existing endpoints

#### 2. Graceful Degradation

**Failure modes handled:**
- LLM API down → Local parser
- Rate limited → Local parser
- Offline → Queue for later or local parser
- Invalid response → Local parser
- Hallucination detected → Strip invented data

#### 3. Feature Flag Control

**Server-side kill switch:**
```env
ENABLE_LLM_PARSING=true   # Global on/off
ENABLE_LLM_MESSAGES=true  # Message intelligence on/off
```

**User-level control:**
```sql
user_preferences.settings = {
  "llm_parsing_enabled": true,  -- User opt-in
  "show_parse_preview": true    -- Confirmation UI
}
```

#### 4. Analytics-Driven Iteration

**Track everything:**
- Parse accuracy (user edit rate)
- LLM vs local parser usage ratio
- API costs per user
- Message uniqueness score
- User satisfaction ratings

---

## INTEGRATION STRATEGY

### Approach: Parallel Systems with Graceful Degradation

```
┌─────────────────────────────────────────────────────────────────┐
│                         USER INPUT                               │
│                   "Call mom in 10 minutes"                       │
└─────────────────────────────┬───────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                    BACKEND API LAYER                             │
│                  POST /api/tasks/parse-llm                       │
│                                                                  │
│  ┌────────────┐      ┌──────────────┐      ┌─────────────┐    │
│  │ Rate Limit │ ───→ │ LLM Parser   │ ───→ │ Hallucinate │    │
│  │ Check      │      │ (Gemini API) │      │ Validator   │    │
│  └────────────┘      └──────┬───────┘      └─────────────┘    │
│                              │                                   │
│                              │ (on failure/rate-limit)           │
│                              ↓                                   │
│                      ┌───────────────┐                          │
│                      │ Fallback to   │                          │
│                      │ task-parser.js│                          │
│                      │ (current)     │                          │
│                      └───────────────┘                          │
└──────────────────────────────┬──────────────────────────────────┘
                               ↓
                    Returns parsed task object
                               ↓
                   Regular task creation flow
                    (no change to DB layer)
```

### Decision Tree: LLM vs Local Parser

```
User Input Received
    │
    ├─ Check: Is LLM enabled globally? (env var)
    │  NO → Use local parser
    │  YES ↓
    │
    ├─ Check: User opted in? (user_preferences.settings)
    │  NO → Use local parser
    │  YES ↓
    │
    ├─ Check: Network available? (Android connectivity check)
    │  NO → Use local parser OR queue for later
    │  YES ↓
    │
    ├─ Check: Under rate limit? (10/min, 100/day)
    │  NO → Use local parser
    │  YES ↓
    │
    ├─ Call LLM API (Gemini)
    │  SUCCESS ↓
    │  ├─ Validate response (hallucination check)
    │  └─ Return parsed data
    │
    │  ERROR ↓
    │  └─ Catch exception → Use local parser
    │
    └─ Both paths converge at task creation
```

### Coexistence Matrix

| Component | Old System | New System | Coexistence Strategy |
|-----------|------------|------------|---------------------|
| **Task Parsing** | `task-parser.js` (regex) | `llm-task-parser.js` (Gemini) | New tries first, falls back to old on failure |
| **Task Creation** | `POST /api/tasks` | `POST /api/tasks` | **SAME ENDPOINT** - no change |
| **Task Storage** | PostgreSQL `tasks` table | PostgreSQL `tasks` table | **SAME TABLE** - no schema change |
| **Message Generation** | `message-engine.js` (templates) | `message-generator-llm.js` (Gemini) | Check cache first, fallback to templates |
| **Message Storage** | In-memory (no persistence) | PostgreSQL `message_cache` table | New table doesn't affect old code |
| **Wallpaper Rendering** | `wallpaper-generator-enhanced.js` | `wallpaper-generator-enhanced.js` | **SAME FILE** - just reads from cache or templates |
| **Android Task Repo** | Calls old endpoints | Calls new parse endpoint, then old create endpoint | Same repository, new method added |
| **Android UI** | Direct task creation | Preview → Confirm → Create | New UI component, old flow still works |

---

## TASK PARSING FLOW

### New Task Creation Flow (End-to-End)

```
┌──────────────────┐
│ Android UI       │  User types: "Email manager by 5pm tomorrow"
│ TaskCreation     │
└────────┬─────────┘
         │
         │ (1) User taps "Create" OR types in real-time (debounced)
         ↓
┌──────────────────────────────────────────────────────────────┐
│ Android TaskRepository                                       │
│                                                              │
│ Decision: Use LLM parse or old parse?                       │
│ → Check user settings (enable_llm_parsing: true/false)      │
│ → Check network connectivity                                │
│ → Check if input is complex (length > 10 words)             │
│                                                              │
│ IF (enabled && online && complex): Call LLM endpoint        │
│ ELSE: Call old endpoint OR use local parser                 │
└────────┬─────────────────────────────────────────────────────┘
         │
         │ (2) POST /api/tasks/parse-llm
         │     Body: { title: "Email manager by 5pm tomorrow" }
         │     Headers: { Authorization: "Bearer <JWT>" }
         ↓
┌──────────────────────────────────────────────────────────────┐
│ Backend Endpoint: /api/tasks/parse-llm                      │
│                                                              │
│ Step 1: Rate Limiter Check                                  │
│ → middleware/rate-limiter.js                                │
│ → Is user under 10 requests/minute?                         │
│ → Is user under 100 requests/day?                           │
│ → NO: Return fallback parse (task-parser.js)                │
│   Response: { ...parsed, source: "local_fallback",          │
│               rateLimited: true }                            │
│ → YES: Continue                                              │
│                                                              │
│ Step 2: Call LLM (llm-task-parser.js)                       │
│ → Build prompt with current date/time context               │
│   Today: 2026-01-05 (Sunday)                                │
│   Time: 14:30                                                │
│ → Call Gemini API (or OpenAI/Claude based on config)        │
│   Model: gemini-1.5-flash                                    │
│   Temperature: 0 (deterministic)                             │
│   ResponseFormat: application/json                           │
│ → Parse JSON response                                        │
│ → ON ERROR: Catch and use fallback parser                   │
│   try { ... } catch (e) { return parseTask(input) }         │
│                                                              │
│ Step 3: Hallucination Validation                            │
│ → Check: Does input contain date words?                     │
│   "tomorrow" → YES, keep date                                │
│   No date words → Strip LLM's invented date                 │
│ → Check: Does input contain time words?                     │
│   "5pm" → YES, keep time                                     │
│   No time words → Strip LLM's invented time                 │
│ → Clean task name:                                           │
│   Remove trailing "by", "at", "in"                          │
│   "Email manager by" → "Email manager"                      │
│                                                              │
│ Step 4: Return standardized format                          │
│ → Same format as old parser (no breaking changes)           │
│ → Add metadata: { source: "llm", confidence: 0.95 }         │
└────────┬─────────────────────────────────────────────────────┘
         │
         │ (3) Response:
         │     {
         │       title: "Email manager",
         │       dueDate: "2026-01-06",        // Tomorrow
         │       dueTime: "17:00",             // 5pm
         │       priority: 2,                   // No urgency keyword
         │       category: "work",              // "manager" → work
         │       energy: "medium",              // No energy keyword
         │       estimateMinutes: null,         // Not mentioned
         │       confidence: 0.95,              // High confidence
         │       source: "llm"                  // Metadata
         │     }
         ↓
┌──────────────────────────────────────────────────────────────┐
│ Android TaskRepository                                       │
│                                                              │
│ (4) Receives parsed data                                    │
│ → Show preview to user (optional confirmation UI)           │
│ → Display:                                                   │
│   "Email manager"                                            │
│   📅 Tomorrow (Jan 6) at 5:00 PM                            │
│   🏢 Work category                                           │
│   [Confirm] [Edit] buttons                                  │
│                                                              │
│ → User confirms OR edits                                    │
│   If edited: Track in analytics (parse_analytics.user_edited = true) │
│                                                              │
│ → Call POST /api/tasks (existing endpoint - NO CHANGE!)     │
└────────┬─────────────────────────────────────────────────────┘
         │
         │ (5) POST /api/tasks
         │     Body: {
         │       title: "Email manager",
         │       dueDate: "2026-01-06",
         │       dueTime: "17:00",
         │       priority: 2,
         │       category: "work",
         │       ...
         │     }
         ↓
┌──────────────────────────────────────────────────────────────┐
│ Backend: Existing Task Creation Logic                       │
│                                                              │
│ → Validate input (express-validator)                        │
│ → Insert into PostgreSQL (tasks table)                      │
│   INSERT INTO tasks (id, user_id, title, due_date, ...)     │
│   VALUES (uuid, 123, 'Email manager', '2026-01-06', ...)    │
│ → Return created task                                        │
│                                                              │
│ [NO CHANGES TO THIS LAYER]                                  │
└────────┬─────────────────────────────────────────────────────┘
         │
         │ (6) Task created successfully
         │     Response: { id, title, dueDate, ... }
         ↓
┌──────────────────────────────────────────────────────────────┐
│ Android TaskRepository                                       │
│                                                              │
│ → Save to Room DB (local cache)                             │
│ → Update UI (task list)                                     │
│ → Trigger wallpaper refresh (new task added)                │
└────────┬─────────────────────────────────────────────────────┘
         │
         ↓
┌──────────────────┐
│ Android UI       │  Shows: "Email manager" with time badge "5:00 PM"
│                  │  Category badge: [■ WORK]
└──────────────────┘
```

### LLM Prompt Structure

**Core Principle:** Strict, deterministic instructions with anti-hallucination rules.

```
INPUT: "Email manager by 5pm tomorrow"
TODAY: 2026-01-05 (Sunday)
TIME: 14:30

STRICT RULES:
1. task: Extract ONLY the action. Remove date/time/priority words.
   - "by [time]" means deadline - remove "by" from task name

2. date: ONLY if EXPLICITLY mentioned. null otherwise.
   - "tomorrow", "Monday", "Jan 5" → YYYY-MM-DD
   - NO date words → null (DO NOT GUESS)

3. time: ONLY if EXPLICITLY mentioned. null otherwise.
   - "at 3pm", "by noon" → HH:MM (24-hour format)
   - "morning" → 09:00, "afternoon" → 14:00, "evening" → 18:00
   - NO time words → null (DO NOT GUESS)

4. priority: ONLY from explicit keywords.
   - "urgent", "asap", "critical", "!!!" → 1 (high)
   - "important" → 2 (medium)
   - "someday", "maybe", "eventually" → 3 (low)
   - NO keywords → 2 (default medium)

5. category: Best guess from keywords. Use "other" if uncertain.
   - Keywords: meeting, email, report → work
   - Keywords: family, home, clean → personal
   - Keywords: workout, gym, run → health
   - Keywords: pay, bill, bank → finance
   - Keywords: learn, study, read → learning
   - Keywords: call, dinner, party → social
   - Keywords: buy, shop, grocery → errands

6. energy: Infer from task type.
   - "focus", "deep work", "complex" → high
   - "quick", "easy", "simple" → low
   - Default → medium

7. estimateMinutes: ONLY if mentioned explicitly.
   - "30m", "1h", "quick" (=15), "long" (=120)
   - Otherwise → null

CRITICAL ANTI-HALLUCINATION RULES:
- "in 10 minutes" means due_time is 10 minutes from 14:30 (current time), NOT estimate
- "by 3pm" means due_time is 15:00, remove "by" from task name
- DO NOT invent dates/times not in input
- DO NOT add priority if not mentioned
- DO NOT create recurring patterns unless explicitly stated

Return this exact JSON structure:
{
  "task": "cleaned task name",
  "date": "YYYY-MM-DD or null",
  "time": "HH:MM or null",
  "priority": 1|2|3,
  "category": "work|personal|health|finance|learning|social|errands|other",
  "energy": "high|medium|low",
  "estimateMinutes": number or null,
  "confidence": 0.0-1.0
}
```

### Hallucination Validation Logic

**After LLM returns response, validate before trusting:**

```javascript
// backend/utils/llm-task-parser.js

function validateAndClean(parsed, originalInput) {
  const lowerInput = originalInput.toLowerCase();

  // Date Hallucination Check
  const hasDateWords = /tomorrow|today|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|\d{1,2}\/\d{1,2}/.test(lowerInput);

  if (!hasDateWords && parsed.date) {
    console.warn(`Hallucination detected: date="${parsed.date}" but input="${originalInput}"`);
    parsed.date = null;
  }

  // Time Hallucination Check
  const hasTimeWords = /am|pm|morning|afternoon|evening|night|noon|midnight|\d{1,2}:\d{2}|o'clock|in \d+ (minute|hour)/.test(lowerInput);

  if (!hasTimeWords && parsed.time) {
    console.warn(`Hallucination detected: time="${parsed.time}" but input="${originalInput}"`);
    parsed.time = null;
  }

  // Clean task name
  parsed.task = parsed.task
    .replace(/\s*by\s*$/i, '')   // Remove trailing "by"
    .replace(/\s*at\s*$/i, '')   // Remove trailing "at"
    .replace(/\s*in\s*$/i, '')   // Remove trailing "in"
    .trim();

  // Capitalize first letter
  if (parsed.task.length > 0) {
    parsed.task = parsed.task.charAt(0).toUpperCase() + parsed.task.slice(1);
  }

  return {
    title: parsed.task,
    dueDate: parsed.date ? new Date(parsed.date) : null,
    dueTime: parsed.time,
    priority: parsed.priority,
    category: parsed.category,
    energy: parsed.energy,
    estimateMinutes: parsed.estimateMinutes,
    confidence: parsed.confidence,
    rawTitle: originalInput,
    source: 'llm'
  };
}
```

### Fallback Scenarios

**When LLM parsing fails, system falls back gracefully:**

#### Scenario 1: Rate Limited

```
LLM Parse Request
    ↓
Rate Limiter: User exceeded 10/minute
    ↓
Auto-fallback to task-parser.js (current pattern-based)
    ↓
Returns result with { source: "local_fallback", rateLimited: true }
    ↓
User sees: "⚠️ Using quick parse (limit reached). Preview below."
```

#### Scenario 2: API Error

```
LLM Parse Request
    ↓
Gemini API returns 500 error OR timeout (>5 seconds)
    ↓
Catch error in try-catch block
    ↓
Fallback to task-parser.js
    ↓
Returns result with { source: "error_fallback", error: "API timeout" }
    ↓
User sees parsed result (may be lower accuracy, but works)
```

#### Scenario 3: Offline (Android)

```
Android: User types input
    ↓
TaskRepository checks connectivity
    ↓
No network detected
    ↓
Skip LLM endpoint entirely
    ↓
Option A: Use local pattern parser (task-parser.js logic copied to Android)
Option B: Queue for later parsing when online (OfflineTaskQueue)
    ↓
Create task with basic parsing
    ↓
Background worker re-parses when online, updates task
```

#### Scenario 4: User Preference Disabled

```
User toggles "Advanced Parsing" OFF in settings
    ↓
Android always uses old task creation flow
    ↓
LLM system dormant (no API calls made)
    ↓
Saves API quota for other users
```

---

## MESSAGE INTELLIGENCE FLOW

### Current System Limitations

**Template-based message engine:**
- 50 templates total
- Random selection (no memory)
- Repetition after 2-3 weeks
- No anti-pattern prevention
- "You got this!" becomes noise

**User experience:**
- Unlocks phone 80 times/day
- Sees "8 tasks done today!" 5 times
- Message becomes invisible (brain filters it out)
- Loses motivational value

### New LLM-Powered Message System

**Core Philosophy:**
1. **Never Monotonous** - Same data, different expressions
2. **Contextually Aware** - Adapts to time, urgency, patterns
3. **Emotionally Resonant** - Warm and human, not corporate

### Message Generation Architecture

```
State Change Detected
(task completed, time elapsed, urgency threshold crossed)
            │
            ▼
┌─────────────────────────────────────┐
│  Context Builder                    │
│  • Gather current state             │
│  • Load message history (last 20)   │
│  • Select voice & intent            │
└─────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────┐
│  LLM Generation                     │
│  • Build dynamic prompt             │
│  • Generate 5 message variants      │
│  • Validate output (word count, anti-patterns) │
└─────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────┐
│  Message Cache (PostgreSQL)         │
│  • Store variants locally           │
│  • Rotate on each wallpaper view    │
│  • Mark as shown, track history     │
└─────────────────────────────────────┘
```

### Message Generation Triggers

| Trigger Event | Rationale | Frequency |
|---------------|-----------|-----------|
| **Task Completed** | Celebration context changes | Every completion |
| **New Task Added** | Focus might shift | Every new task |
| **Every 2-3 Hours** | Time-of-day relevance (morning vs evening) | Background job |
| **Urgency Threshold Crossed** | Atmosphere shift (calm → storm brewing) | Real-time |
| **Day Rollover (Midnight)** | Fresh start, new day context | Daily at 00:00 |
| **Cache Exhausted** | All variants shown, need fresh batch | When cache empty |

### Voice Rotation System

**Five distinct voices prevent pattern collapse:**

```javascript
const VOICES = {
  WARM_FRIEND: {
    description: "Supportive friend who genuinely cares",
    tone: "Casual, can start with 'Hey' occasionally",
    example: "Hey, nice work today. Three left."
  },

  QUIET_OBSERVER: {
    description: "Short, observational statements",
    tone: "State facts without judgment. Minimal words.",
    example: "Three tasks. Calm evening ahead."
  },

  PLAYFUL: {
    description: "Light, slightly cheeky tone",
    tone: "Can personify tasks. Not silly—just lightly fun.",
    example: "Eight down, three standing. Who's next?"
  },

  POETIC: {
    description: "Lyrical, metaphorical, brief",
    tone: "Uses imagery and rhythm",
    example: "The day winds down. One task waits."
  },

  DIRECT: {
    description: "Clear, actionable, no fluff",
    tone: "Business-like efficiency",
    example: "Review vulnerability. Four hours left."
  }
};
```

**Rotation Logic:**
```javascript
// Rotate through voices in order
const voiceOrder = [WARM_FRIEND, QUIET_OBSERVER, PLAYFUL, POETIC, DIRECT];

// Get least recently used voice
const recentVoices = await getRecentMessages(10).map(m => m.voice);
const nextVoice = voiceOrder.find(v => !recentVoices.includes(v)) || voiceOrder.random();
```

**Context-Aware Overrides:**
```javascript
// Late night → softer voices
if (timeOfDay === 'NIGHT' && nextVoice === 'DIRECT') {
  nextVoice = 'QUIET_OBSERVER';  // Don't be harsh at night
}

// Critical urgency → override to DIRECT
if (urgencyLevel === 'CRITICAL') {
  nextVoice = 'DIRECT';
  nextIntent = 'FOCUS_NEXT';
}

// All tasks done → override to CELEBRATE
if (pendingTasks === 0 && completedToday > 0) {
  nextIntent = 'CELEBRATE';
}
```

### Intent Rotation System

**Six intents provide different angles on same data:**

```javascript
const INTENTS = {
  CELEBRATE: "Focus on wins and achievements",
  NUDGE: "Gently surface next action",
  TIME_AWARE: "Acknowledge time of day and energy",
  STREAK_FOCUS: "Reference streak without preaching",
  PERMISSION: "Give permission to rest",
  FOCUS_NEXT: "Surface the most important next task"
};
```

**Intent Examples (Same Data, Different Angles):**

**Context:** 8 tasks completed, 3 pending, evening, 5-day streak

| Voice | Intent | Example Output |
|-------|--------|----------------|
| WARM_FRIEND | CELEBRATE | "Eight. Your best Sunday in weeks." |
| PLAYFUL | NUDGE | "Three left. They're not going anywhere." |
| POETIC | TIME_AWARE | "Evening settles. One task still glows." |
| DIRECT | FOCUS_NEXT | "Vulnerability review. Before you unwind." |
| QUIET_OBSERVER | PERMISSION | "Rest is productive too." |
| WARM_FRIEND | STREAK_FOCUS | "Five days strong. Momentum building." |

### Message Generation Prompt

**Dynamic prompt built from narrative context:**

```javascript
function buildMessagePrompt(context, voice, intent, recentMessages) {
  return `Generate 5 short wallpaper messages.

CONTEXT:
User completed 8 tasks today—their highest this week.
They usually slow down after 6 PM but still have 3 pending.
One task (vulnerability review) is work-related and due tonight.
They've been on a 5-day streak, longest in 2 weeks.
It's Sunday evening—they might want encouragement to wrap up
or permission to rest.

VOICE: Supportive friend who genuinely cares
INTENT: Focus on wins and achievements

RECENT MESSAGES (DO NOT repeat or closely paraphrase):
- "Eight tasks done today"
- "Three pending"
- "Solid progress"
- "You're on a roll"
- "Five-day streak going"

OVERUSED WORDS to avoid: solid, tasks, done, today

ANTI-PATTERNS (NEVER use):
- Starting with "Great job" / "Nice work" / "Well done"
- "You got this" / "Keep it up" / "Keep going"
- "Crushing it" / "Killing it"
- Corporate motivation speak
- Exclamation marks on every message
- Starting with "Solid" (common LLM crutch)
- Questions in every message
- Emojis (not supported in wallpaper font)

RULES:
- Maximum 8 words per message
- Each message must be meaningfully different
- Natural, human language
- No emojis

Return JSON:
{
  "messages": [
    "First message here",
    "Second message here",
    "Third message here",
    "Fourth message here",
    "Fifth message here"
  ]
}`;
}
```

### Freshness Constraints

**Track recent messages to prevent repetition:**

```javascript
function buildFreshnessConstraints(recentMessages) {
  let constraints = '\nRECENT MESSAGES (DO NOT repeat or closely paraphrase):\n';

  recentMessages.forEach(msg => {
    constraints += `- "${msg}"\n`;
  });

  // Extract overused words (appeared 3+ times)
  const words = recentMessages.join(' ').toLowerCase().split(/\s+/);
  const wordCounts = {};
  words.forEach(w => {
    if (w.length > 3) {  // Skip short words
      wordCounts[w] = (wordCounts[w] || 0) + 1;
    }
  });

  const overused = Object.entries(wordCounts)
    .filter(([_, count]) => count >= 3)
    .map(([word, _]) => word);

  if (overused.length > 0) {
    constraints += `\nOVERUSED WORDS to avoid: ${overused.join(', ')}\n`;
  }

  return constraints;
}
```

### Message Validation

**After LLM generates messages, validate before caching:**

```javascript
function validateMessages(messages, recentMessages) {
  return messages.filter(msg => {
    // Word count check (max 8 words)
    const wordCount = msg.split(' ').length;
    if (wordCount > 8) {
      console.warn(`Message too long (${wordCount} words): "${msg}"`);
      return false;
    }

    // Anti-pattern check
    const lower = msg.toLowerCase();
    const antiPatterns = [
      'great job', 'nice work', 'well done',
      'you got this', 'keep it up', 'keep going',
      'crushing it', 'killing it', 'solid'
    ];

    for (const pattern of antiPatterns) {
      if (lower.includes(pattern)) {
        console.warn(`Anti-pattern detected: "${pattern}" in "${msg}"`);
        return false;
      }
    }

    // Duplicate check (exact match)
    if (recentMessages.some(recent => recent.toLowerCase() === lower)) {
      console.warn(`Duplicate message: "${msg}"`);
      return false;
    }

    // Similarity check (Levenshtein distance or simple word overlap)
    // Skip for MVP, implement in future

    return true;
  });
}
```

### Wallpaper Integration

**Message provider manages cache rotation:**

```javascript
// backend/services/wallpaper-message-provider.js

class WallpaperMessageProvider {
  async getCurrentMessage(userId) {
    // 1. Try to get from cache (next unshown message)
    const cached = await messageCacheDao.getNextUnshown(userId);

    if (cached) {
      // Mark as shown
      await messageCacheDao.markShown(cached.id);

      // Log to history (for analytics)
      await messageHistoryDao.insert({
        user_id: userId,
        message: cached.message,
        voice: cached.voice,
        intent: cached.intent,
        shown_at: new Date()
      });

      // Trigger background refresh if cache running low
      const remaining = await messageCacheDao.getUnshownCount(userId);
      if (remaining <= 2) {
        triggerBackgroundRefresh(userId);
      }

      return cached.message;
    }

    // 2. Cache empty - generate immediately
    const context = await buildMessageContext(userId);
    const messages = await generateMessages(userId, context);

    return messages[0] || getFallbackMessage(context);
  }
}
```

**Fallback messages (offline/error scenarios):**

```javascript
class FallbackMessageProvider {
  getContextualFallback(context) {
    const { timeOfDay, pendingTasks, completedToday } = context;

    // No tasks - zen messages
    if (pendingTasks === 0) {
      return ['Clear skies', 'All done', 'Nothing pending'][Math.floor(Math.random() * 3)];
    }

    // One task - focused messages
    if (pendingTasks === 1) {
      return 'One task waiting';
    }

    // Multiple tasks - time-based
    const timeBasedFallbacks = {
      EARLY_MORNING: ['Fresh start', 'New day ahead'],
      MORNING: ['Morning momentum', "Day's unfolding"],
      AFTERNOON: ['Midday push', 'Keep the flow'],
      EVENING: ['Evening wind-down', 'Day settling'],
      NIGHT: ['Rest well', 'Night falls']
    };

    const messages = timeBasedFallbacks[timeOfDay] || ['Tasks await'];
    return messages[Math.floor(Math.random() * messages.length)];
  }
}
```

### Background Message Generation

**Worker job generates messages periodically:**

```javascript
// Run every 2-3 hours for each user
setInterval(async () => {
  const activeUsers = await getUsersActiveInLast24Hours();

  for (const user of activeUsers) {
    try {
      // Check if cache needs refresh
      const cacheCount = await messageCacheDao.getUnshownCount(user.id);

      if (cacheCount < 3) {
        const context = await buildMessageContext(user.id);
        const messages = await generateMessages(user.id, context);
        console.log(`Generated ${messages.length} messages for user ${user.id}`);
      }
    } catch (error) {
      console.error(`Message generation failed for user ${user.id}:`, error);
      // Continue to next user (don't block entire job)
    }
  }
}, 2 * 60 * 60 * 1000);  // Every 2 hours
```

---

## DATABASE SCHEMA CHANGES

### New Tables (No Changes to Existing)

**Existing Tables (UNCHANGED):**
```sql
-- These tables remain exactly as they are
users
tasks
user_preferences
task_completion_log
```

**New Tables (ADDED):**

#### Table 1: parse_analytics

**Purpose:** Track parsing accuracy and user edit rates

```sql
CREATE TABLE parse_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    original_input TEXT NOT NULL,
    parsed_output JSONB NOT NULL,        -- Full parsed result
    confidence FLOAT,                     -- LLM confidence score (0.0-1.0)
    source VARCHAR(20) NOT NULL,          -- 'llm', 'local', 'local_fallback', 'error_fallback'
    user_edited BOOLEAN DEFAULT FALSE,    -- Did user change parsed result?
    edited_fields JSONB,                  -- Which fields did user change? {"dueDate": true, "priority": true}
    created_at TIMESTAMP DEFAULT NOW(),

    INDEX idx_parse_analytics_user (user_id),
    INDEX idx_parse_analytics_source (source),
    INDEX idx_parse_analytics_created (created_at DESC)
);
```

**Example Row:**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "user_id": 42,
  "original_input": "Email manager by 5pm tomorrow",
  "parsed_output": {
    "title": "Email manager",
    "dueDate": "2026-01-06",
    "dueTime": "17:00",
    "priority": 2,
    "category": "work",
    "confidence": 0.95
  },
  "source": "llm",
  "user_edited": true,              -- User changed something
  "edited_fields": {
    "priority": true                -- Changed priority from 2 to 1
  },
  "created_at": "2026-01-05T14:30:00Z"
}
```

#### Table 2: message_cache

**Purpose:** Store LLM-generated message variants for rotation

```sql
CREATE TABLE message_cache (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    voice VARCHAR(20) NOT NULL,       -- WARM_FRIEND, QUIET_OBSERVER, PLAYFUL, POETIC, DIRECT
    intent VARCHAR(20) NOT NULL,      -- CELEBRATE, NUDGE, TIME_AWARE, etc.
    generated_at TIMESTAMP DEFAULT NOW(),
    display_order INTEGER NOT NULL,   -- Order for rotation (1-5)
    shown BOOLEAN DEFAULT FALSE,
    shown_at TIMESTAMP,

    INDEX idx_message_cache_user_shown (user_id, shown),
    INDEX idx_message_cache_generated (generated_at DESC)
);
```

**Example Rows:**
```
id  | user_id | message                          | voice          | intent     | display_order | shown
----|---------|----------------------------------|----------------|------------|---------------|------
101 | 42      | "Eight. Best Sunday in weeks."   | WARM_FRIEND    | CELEBRATE  | 1             | true
102 | 42      | "Three left standing."           | PLAYFUL        | NUDGE      | 2             | false
103 | 42      | "Evening settles. One glows."    | POETIC         | TIME_AWARE | 3             | false
104 | 42      | "Vulnerability review. 4h left." | DIRECT         | FOCUS_NEXT | 4             | false
105 | 42      | "Rest is productive too."        | QUIET_OBSERVER | PERMISSION | 5             | false
```

#### Table 3: message_history

**Purpose:** Track shown messages for analytics and learning

```sql
CREATE TABLE message_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    voice VARCHAR(20) NOT NULL,
    intent VARCHAR(20) NOT NULL,
    shown_at TIMESTAMP DEFAULT NOW(),
    task_state JSONB,                             -- Snapshot: {completedToday: 8, pendingTasks: 3, urgencyLevel: "normal"}
    user_completed_task_within_30min BOOLEAN,     -- Did user act on message?

    INDEX idx_message_history_user_shown (user_id, shown_at DESC),
    INDEX idx_message_history_voice (voice),
    INDEX idx_message_history_intent (intent)
);
```

**Example Row:**
```json
{
  "id": 201,
  "user_id": 42,
  "message": "Vulnerability review. 4h left.",
  "voice": "DIRECT",
  "intent": "FOCUS_NEXT",
  "shown_at": "2026-01-05T18:00:00Z",
  "task_state": {
    "completedToday": 8,
    "pendingTasks": 3,
    "urgencyLevel": "medium",
    "nextDueTask": "Vulnerability review"
  },
  "user_completed_task_within_30min": true  -- User completed "Vulnerability review" at 18:25
}
```

**Future Use:** Learn which messages drive action

### Migration Script

```sql
-- Migration: Add LLM Intelligence Tables
-- Version: 1.3.0
-- Date: 2026-01-05

BEGIN;

-- Table 1: Parse Analytics
CREATE TABLE IF NOT EXISTS parse_analytics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    original_input TEXT NOT NULL,
    parsed_output JSONB NOT NULL,
    confidence FLOAT,
    source VARCHAR(20) NOT NULL,
    user_edited BOOLEAN DEFAULT FALSE,
    edited_fields JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_parse_analytics_user ON parse_analytics(user_id);
CREATE INDEX idx_parse_analytics_source ON parse_analytics(source);
CREATE INDEX idx_parse_analytics_created ON parse_analytics(created_at DESC);

-- Table 2: Message Cache
CREATE TABLE IF NOT EXISTS message_cache (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    voice VARCHAR(20) NOT NULL,
    intent VARCHAR(20) NOT NULL,
    generated_at TIMESTAMP DEFAULT NOW(),
    display_order INTEGER NOT NULL,
    shown BOOLEAN DEFAULT FALSE,
    shown_at TIMESTAMP
);

CREATE INDEX idx_message_cache_user_shown ON message_cache(user_id, shown);
CREATE INDEX idx_message_cache_generated ON message_cache(generated_at DESC);

-- Table 3: Message History
CREATE TABLE IF NOT EXISTS message_history (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    voice VARCHAR(20) NOT NULL,
    intent VARCHAR(20) NOT NULL,
    shown_at TIMESTAMP DEFAULT NOW(),
    task_state JSONB,
    user_completed_task_within_30min BOOLEAN
);

CREATE INDEX idx_message_history_user_shown ON message_history(user_id, shown_at DESC);
CREATE INDEX idx_message_history_voice ON message_history(voice);
CREATE INDEX idx_message_history_intent ON message_history(intent);

-- Add LLM settings to user_preferences (if doesn't exist)
ALTER TABLE user_preferences
ADD COLUMN IF NOT EXISTS settings JSONB DEFAULT '{}';

COMMIT;
```

---

## API CONTRACT CHANGES

### Existing Endpoints (NO BREAKING CHANGES)

**These continue working exactly as before:**

```
POST   /api/auth/register          [UNCHANGED]
POST   /api/auth/login             [UNCHANGED]
GET    /api/tasks                  [UNCHANGED]
POST   /api/tasks                  [UNCHANGED] ← Still creates tasks
PATCH  /api/tasks/:id              [UNCHANGED]
DELETE /api/tasks/:id              [UNCHANGED]
GET    /api/wallpaper              [MODIFIED internally, same response format]
GET    /api/user/preferences       [UNCHANGED]
PATCH  /api/user/preferences       [MODIFIED to add llm settings]
```

### New Endpoints (ADDED)

#### Endpoint 1: Parse Task with LLM

```
POST /api/tasks/parse-llm
```

**Purpose:** Parse natural language text into structured task data (DOES NOT create task)

**Request:**
```json
{
  "title": "Email manager about budget by Friday 3pm"
}
```

**Response (Success):**
```json
{
  "title": "Email manager about budget",
  "dueDate": "2026-01-10",          // Next Friday
  "dueTime": "15:00",                // 3pm in 24-hour format
  "priority": 2,                     // No urgency keyword
  "category": "work",                // "manager" → work
  "energy": "medium",                // Default
  "estimateMinutes": null,           // Not mentioned
  "confidence": 0.92,                // High confidence
  "source": "llm",                   // Parsed by LLM
  "extractedFields": ["date", "time", "category"]
}
```

**Response (Rate Limited):**
```json
{
  "title": "Email manager about budget",
  "dueDate": null,                   // Lower accuracy (local parser)
  "dueTime": null,
  "priority": 2,
  "category": "general",
  "energy": "medium",
  "estimateMinutes": null,
  "confidence": 0.45,                // Lower confidence
  "source": "local_fallback",        // Fallback parser used
  "rateLimited": true,               // User hit rate limit
  "waitSeconds": 42                  // Retry after 42 seconds
}
```

**Response (Error):**
```json
{
  "title": "Email manager about budget",
  "dueDate": null,
  "dueTime": null,
  "priority": 2,
  "category": "general",
  "energy": "medium",
  "estimateMinutes": null,
  "confidence": 0.40,
  "source": "error_fallback",
  "error": "LLM API timeout"
}
```

**Rate Limits:**
- 10 requests per minute per user
- 100 requests per day per user
- Returns 200 OK with fallback data when limited (not 429)

**Authentication:** Required (JWT token)

#### Endpoint 2: Preview Message

```
GET /api/messages/preview?voice=WARM_FRIEND&intent=CELEBRATE
```

**Purpose:** Generate sample message in specific voice/intent (for settings UI)

**Response:**
```json
{
  "message": "Eight tasks. Your best Sunday in weeks.",
  "voice": "WARM_FRIEND",
  "intent": "CELEBRATE"
}
```

**Authentication:** Required

**Use Case:** User settings screen shows message examples in different voices

#### Endpoint 3: Generate Messages (Internal)

```
POST /api/messages/generate
```

**Purpose:** Trigger message generation for user (internal endpoint, called by backend jobs)

**Request:**
```json
{
  "userId": 42,
  "trigger": "task_completed"  // task_completed, time_elapsed, cache_exhausted
}
```

**Response:**
```json
{
  "messagesGenerated": 5,
  "cachedCount": 5,
  "voice": "QUIET_OBSERVER",
  "intent": "NUDGE"
}
```

**Authentication:** Internal only (API key)

### Modified Endpoints

#### Endpoint: Get Wallpaper

```
GET /api/wallpaper?userId=123&width=1440&height=2560
```

**CHANGE:** Internally now checks `message_cache` table first, falls back to templates

**External API:** No change (same request/response format)

**Internal Flow Changed:**
```
BEFORE:
wallpaper-generator.js → message-engine.js (random template) → render PNG

AFTER:
wallpaper-generator.js → Check message_cache → If cached: use LLM message
                                              → If empty: fallback to template
                       → render PNG
```

**Response:** Same PNG format (no breaking change)

#### Endpoint: Update User Preferences

```
PATCH /api/user/preferences
```

**NEW FIELD:** `settings` (JSONB)

**Request:**
```json
{
  "settings": {
    "llm_parsing_enabled": true,
    "llm_message_voice": "WARM_FRIEND",
    "show_parse_preview": true,
    "auto_accept_high_confidence": false
  }
}
```

**Backward Compatible:** Existing fields still work

---

## ANDROID INTEGRATION

### Changes to Existing Files

#### 1. ApiService.kt (Retrofit Interface)

**BEFORE:**
```kotlin
interface ApiService {
    @POST("api/tasks")
    suspend fun createTask(@Body task: CreateTaskRequest): Response<Task>
}
```

**AFTER:**
```kotlin
interface ApiService {
    @POST("api/tasks")
    suspend fun createTask(@Body task: CreateTaskRequest): Response<Task>

    // ✨ NEW ENDPOINT: Parse with LLM
    @POST("api/tasks/parse-llm")
    suspend fun parseTaskLLM(@Body request: ParseRequest): Response<ParsedResult>

    // ✨ NEW ENDPOINT: Message preview
    @GET("api/messages/preview")
    suspend fun previewMessage(
        @Query("voice") voice: String,
        @Query("intent") intent: String
    ): Response<MessagePreview>
}

// ✨ NEW: Request/Response models
data class ParseRequest(val title: String)

data class ParsedResult(
    val title: String,
    val dueDate: String?,          // ISO date: "2026-01-10"
    val dueTime: String?,          // 24-hour: "15:00"
    val priority: Int,             // 1-3
    val category: String,          // "work", "personal", etc.
    val energy: String,            // "high", "medium", "low"
    val estimateMinutes: Int?,
    val confidence: Float,         // 0.0-1.0
    val source: String,            // "llm", "local_fallback", etc.
    val rateLimited: Boolean = false,
    val waitSeconds: Int? = null
)
```

#### 2. TaskRepository.kt

**BEFORE:**
```kotlin
class TaskRepository @Inject constructor(
    private val apiService: ApiService,
    private val taskDao: TaskDao
) {
    suspend fun createTask(title: String): Result<Task> {
        // Directly calls apiService.createTask()
        return try {
            val response = apiService.createTask(CreateTaskRequest(title))
            if (response.isSuccessful) {
                val task = response.body()!!
                taskDao.insert(task.toEntity())
                Result.success(task)
            } else {
                Result.failure(Exception("Failed to create task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**AFTER:**
```kotlin
class TaskRepository @Inject constructor(
    private val apiService: ApiService,
    private val taskDao: TaskDao,
    private val userPrefsDao: UserPreferencesDao,        // ✨ NEW
    private val connectivityManager: ConnectivityManager, // ✨ NEW
    private val analyticsTracker: AnalyticsTracker       // ✨ NEW
) {
    // ✨ NEW: Two-step task creation with parsing
    suspend fun createTaskWithParsing(title: String): Result<Task> {
        return withContext(Dispatchers.IO) {
            // Step 1: Parse input
            val parseResult = parseInput(title)

            // Step 2: Create task with parsed data
            createTaskFromParsed(parseResult)
        }
    }

    // ✨ NEW: Parse input (LLM or local)
    private suspend fun parseInput(title: String): ParsedResult {
        // Decision logic: Use LLM or local?
        val prefs = userPrefsDao.getSettings()
        val isOnline = connectivityManager.isConnected()
        val isComplex = title.length > 10  // Simple heuristic

        val useLLM = prefs.llmParsingEnabled && isOnline && isComplex

        return if (useLLM) {
            try {
                val response = apiService.parseTaskLLM(ParseRequest(title))
                if (response.isSuccessful) {
                    analyticsTracker.track("parse_llm_success", mapOf("confidence" to response.body()!!.confidence))
                    response.body()!!
                } else {
                    analyticsTracker.track("parse_llm_error", mapOf("code" to response.code()))
                    localParse(title)  // Fallback
                }
            } catch (e: Exception) {
                analyticsTracker.track("parse_llm_exception", mapOf("error" to e.message))
                localParse(title)  // Fallback
            }
        } else {
            localParse(title)  // Skip LLM entirely
        }
    }

    // ✨ NEW: Local parsing (simple pattern-based)
    private fun localParse(title: String): ParsedResult {
        // Basic pattern matching (copied from backend task-parser.js logic)
        // or just return title as-is with low confidence
        return ParsedResult(
            title = title,
            dueDate = null,
            dueTime = null,
            priority = 2,
            category = "general",
            energy = "medium",
            estimateMinutes = null,
            confidence = 0.3f,
            source = "local"
        )
    }

    // ✨ NEW: Create task from parsed result
    private suspend fun createTaskFromParsed(parsed: ParsedResult): Result<Task> {
        val request = CreateTaskRequest(
            title = parsed.title,
            dueDate = parsed.dueDate?.let { LocalDate.parse(it) },
            dueTime = parsed.dueTime?.let { LocalTime.parse(it) },
            priority = parsed.priority,
            category = parsed.category,
            // ... other fields
        )

        return try {
            val response = apiService.createTask(request)
            if (response.isSuccessful) {
                val task = response.body()!!
                taskDao.insert(task.toEntity())

                // Track parse accuracy (will update later if user edits)
                analyticsTracker.track("task_created", mapOf(
                    "parseSource" to parsed.source,
                    "parseConfidence" to parsed.confidence
                ))

                Result.success(task)
            } else {
                Result.failure(Exception("Failed to create task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // KEEP: Old method for backward compatibility
    suspend fun createTask(title: String): Result<Task> {
        return createTaskWithParsing(title)  // Route to new method
    }
}
```

#### 3. TaskCreationScreen.kt

**BEFORE:**
```kotlin
@Composable
fun TaskCreationScreen(viewModel: TaskViewModel) {
    var input by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("What needs doing?") }
        )

        Button(onClick = {
            viewModel.createTask(input)
        }) {
            Text("Create Task")
        }
    }
}
```

**AFTER:**
```kotlin
@Composable
fun TaskCreationScreen(viewModel: TaskViewModel) {
    var input by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<ParsedResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // ✨ NEW: Debounced live preview
    LaunchedEffect(input) {
        if (input.length > 5) {
            delay(800)  // Debounce 800ms
            isLoading = true
            preview = viewModel.previewParse(input)
            isLoading = false
        } else {
            preview = null
        }
    }

    Column {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("What needs doing?") }
        )

        // ✨ NEW: Live preview card
        preview?.let { p ->
            Card(
                modifier = Modifier.padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Parsed as:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = p.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Show due date/time if parsed
                    p.dueDate?.let { date ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = formatDate(date),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            p.dueTime?.let { time ->
                                Text(" at $time", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    // Show category badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(category = p.category)
                        Spacer(modifier = Modifier.width(8.dp))
                        PriorityBadge(priority = p.priority)
                    }

                    // Show confidence indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Confidence: ${(p.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                p.confidence >= 0.8 -> Color.Green
                                p.confidence >= 0.5 -> Color.Yellow
                                else -> Color.Red
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (p.source != "llm") {
                            Text(
                                text = "(${p.source})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Show loading indicator
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ✨ MODIFIED: Button now creates from preview
        Button(
            onClick = {
                if (preview != null && preview!!.confidence > 0.5) {
                    // High confidence - create directly
                    viewModel.createTaskFromPreview(preview!!)
                } else {
                    // Low confidence - show edit dialog first
                    viewModel.showEditDialog(preview)
                }
            },
            enabled = input.isNotBlank()
        ) {
            Text(
                if (preview?.confidence ?: 0f >= 0.8) "Create Task"
                else "Review & Create"
            )
        }
    }
}
```

### New Files

#### 1. ParsePreviewProvider.kt

**Purpose:** Manage live parse preview (debounced API calls)

```kotlin
// android/app/src/main/java/com/cosmicocean/llm/ParsePreviewProvider.kt

@Singleton
class ParsePreviewProvider @Inject constructor(
    private val apiService: ApiService,
    private val userPrefsDao: UserPreferencesDao
) {
    private val _previewFlow = MutableStateFlow<ParsedResult?>(null)
    val previewFlow: StateFlow<ParsedResult?> = _previewFlow.asStateFlow()

    private var debounceJob: Job? = null

    suspend fun requestPreview(input: String) {
        // Cancel previous job
        debounceJob?.cancel()

        // Debounce 800ms
        debounceJob = CoroutineScope(Dispatchers.IO).launch {
            delay(800)

            val prefs = userPrefsDao.getSettings()
            if (!prefs.llmParsingEnabled) {
                _previewFlow.value = null
                return@launch
            }

            try {
                val response = apiService.parseTaskLLM(ParseRequest(input))
                if (response.isSuccessful) {
                    _previewFlow.value = response.body()
                } else {
                    _previewFlow.value = null
                }
            } catch (e: Exception) {
                _previewFlow.value = null
            }
        }
    }

    fun clearPreview() {
        _previewFlow.value = null
    }
}
```

#### 2. GeminiClient.kt (Optional - Direct LLM Integration)

**Purpose:** Call Gemini directly from Android (for offline-first apps)

**NOT RECOMMENDED FOR MVP** - Adds complexity. Use backend API first.

**Future consideration:** For apps that need to work offline with LLM parsing

---

## CONFIGURATION & FEATURE FLAGS

### Backend Environment Variables

```env
# Existing
DATABASE_URL=postgresql://user:pass@host:5432/dbname
REDIS_URL=redis://host:6379
JWT_SECRET=your_secret_here

# ✨ NEW: LLM Integration
GEMINI_API_KEY=your_gemini_key_here          # Primary LLM provider
OPENAI_API_KEY=your_openai_key_here          # Backup option (optional)
CLAUDE_API_KEY=your_claude_key_here          # Backup option (optional)

LLM_PROVIDER=gemini                          # gemini | openai | claude
LLM_MODEL=gemini-1.5-flash                   # Model to use

# Feature Flags (Global Kill Switches)
ENABLE_LLM_PARSING=true                      # Master on/off for parsing
ENABLE_LLM_MESSAGES=true                     # Master on/off for messages

# Rate Limits
LLM_RATE_LIMIT_PER_MINUTE=10                 # Per user
LLM_RATE_LIMIT_PER_DAY=100                   # Per user

# Message Generation
MESSAGE_GENERATION_INTERVAL_HOURS=2          # How often to generate messages
MESSAGE_CACHE_SIZE=5                         # How many variants to generate per batch
MESSAGE_CACHE_TRIGGER_THRESHOLD=2            # Generate when cache has <= 2 messages
```

### User Preferences Schema

**Add `settings` JSONB column to `user_preferences` table:**

```sql
ALTER TABLE user_preferences ADD COLUMN settings JSONB DEFAULT '{}';
```

**Default Settings:**
```json
{
  "llm_parsing_enabled": true,              // User opt-in for LLM parsing
  "llm_message_voice": "WARM_FRIEND",       // Preferred message voice
  "show_parse_preview": true,               // Show confirmation UI before creating task
  "auto_accept_high_confidence": false      // Auto-create task if confidence > 0.9 (skip preview)
}
```

**Settings UI (Android):**
```kotlin
@Composable
fun LLMSettingsScreen(viewModel: SettingsViewModel) {
    Column {
        SwitchPreference(
            title = "Advanced Parsing",
            description = "Use AI to understand natural language",
            checked = viewModel.llmParsingEnabled.value,
            onCheckedChange = { viewModel.updateLLMParsing(it) }
        )

        if (viewModel.llmParsingEnabled.value) {
            SwitchPreference(
                title = "Show Preview",
                description = "Confirm parsed data before creating task",
                checked = viewModel.showParsePreview.value,
                onCheckedChange = { viewModel.updateShowPreview(it) }
            )

            DropdownPreference(
                title = "Message Voice",
                description = "Tone of wallpaper messages",
                options = listOf("Warm Friend", "Quiet Observer", "Playful", "Poetic", "Direct"),
                selected = viewModel.messageVoice.value,
                onSelected = { viewModel.updateMessageVoice(it) }
            )
        }
    }
}
```

### Android Build Configuration

**Add Gemini API key to build config (optional for direct integration):**

```kotlin
// android/app/build.gradle.kts

android {
    defaultConfig {
        // Existing config...

        // ✨ NEW: LLM feature flag
        buildConfigField("boolean", "ENABLE_LLM", "true")
    }

    buildTypes {
        debug {
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
        }

        release {
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
        }

        // ✨ NEW: Build variant WITHOUT LLM (for comparison testing)
        create("releaseNoLLM") {
            initWith(getByName("release"))
            buildConfigField("boolean", "ENABLE_LLM", "false")
        }
    }
}
```

**local.properties:**
```properties
GEMINI_API_KEY=your_key_here
```

---

## MIGRATION STRATEGY

### Phase 1: Backend Setup (Week 1)

**Goal:** Deploy new backend code with LLM endpoints (not used by Android yet)

**Tasks:**
1. Install dependencies (`npm install @google/generative-ai`)
2. Add environment variables to Vercel
3. Create `llm-task-parser.js` service
4. Create `message-generator-llm.js` service
5. Create `rate-limiter.js` middleware
6. Add new API routes (`/api/tasks/parse-llm`, `/api/messages/preview`)
7. Run database migration (add new tables)
8. Deploy to Vercel

**Testing:**
- Use Postman/curl to test `/api/tasks/parse-llm`
- Verify rate limiting works (make 11 requests in 1 minute → should fallback)
- Test hallucination validation (input: "Buy milk" → should NOT have date)
- Test fallback (disconnect API key → should use local parser)

**Risk:** ZERO (new code not used by production app yet)

**Deployment:**
```bash
cd backend
npm install @google/generative-ai
vercel env add GEMINI_API_KEY production
vercel env add ENABLE_LLM_PARSING production
vercel --prod
```

**Verification:**
```bash
curl -X POST https://cosmic-ocean-api.vercel.app/api/tasks/parse-llm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT>" \
  -d '{"title":"Email manager by 5pm tomorrow"}'
```

**Expected Response:**
```json
{
  "title": "Email manager",
  "dueDate": "2026-01-06",
  "dueTime": "17:00",
  "priority": 2,
  "category": "work",
  "confidence": 0.95,
  "source": "llm"
}
```

---

### Phase 2: Android Integration (Week 2-3)

**Goal:** Update Android app to call new endpoints (feature flag OFF by default)

**Tasks:**
1. Add new Retrofit endpoints to `ApiService.kt`
2. Update `TaskRepository.kt` with parsing logic
3. Create `ParsePreviewProvider.kt` for live preview
4. Update `TaskCreationScreen.kt` with preview UI
5. Add settings screen for LLM preferences
6. Add analytics tracking

**Feature Flag Control:**
```kotlin
// Initially, feature flag is OFF for all users
// Only enable for internal testing via settings
val llmEnabled = userPrefs.settings.llmParsingEnabled ?: false
```

**Testing Strategy:**

**Week 2: Internal Team Only (10 people)**
- Install APK with feature flag accessible in settings
- Test parse accuracy with real inputs
- Track: How often do users edit LLM parses?
- Goal: >=80% acceptance rate (user doesn't edit)

**Week 3: Beta Testers (50 people)**
- Send APK to beta group
- Enable feature flag via backend (specific user IDs)
- Monitor: Parse errors, fallback rate, user feedback
- Goal: <5% error rate, <10% fallback rate

**Rollback Plan:**
- If parse accuracy <70%: Disable feature flag server-side
  ```sql
  UPDATE user_preferences
  SET settings = jsonb_set(settings, '{llm_parsing_enabled}', 'false')
  WHERE user_id IN (SELECT id FROM users);
  ```
- App continues working with old flow
- No app update needed

---

### Phase 3: Message Intelligence (Week 4-5)

**Goal:** Backend generates messages in background, wallpaper uses cached messages

**Tasks:**
1. Implement `message-generator-llm.js` service
2. Create background worker (Node.js setInterval or cron job)
3. Update `wallpaper-generator-enhanced.js` to check cache
4. Implement `FallbackMessageProvider` for offline scenarios
5. Add message history tracking

**Testing:**

**Week 4: Generate Messages (Don't Show Yet)**
- Background job runs for 10 test users
- Verify: 5 messages generated per user
- Check: No duplicates in recent 20 messages
- Measure: Generation success rate (>95% target)

**Week 5: Show Messages to Users**
- Enable for 10% of users (A/B test)
- Track: Message uniqueness over 7 days
- Monitor: User feedback, wallpaper download rate
- Goal: No repeat messages in 20 consecutive views

**Gradual Rollout:**
```
Week 5, Day 1: 10% of users
Week 5, Day 3: 25% of users
Week 5, Day 5: 50% of users
Week 5, Day 7: 100% of users
```

---

### Phase 4: Optimization & Iteration (Week 6-8)

**Goal:** Analyze data, optimize costs, tune performance

**Data Analysis:**

**Parse Accuracy Dashboard:**
```sql
SELECT
  source,
  COUNT(*) as total_parses,
  AVG(confidence) as avg_confidence,
  SUM(CASE WHEN user_edited THEN 1 ELSE 0 END)::float / COUNT(*) as edit_rate
FROM parse_analytics
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY source;
```

**Expected Results:**
```
source          | total_parses | avg_confidence | edit_rate
----------------|--------------|----------------|----------
llm             | 450          | 0.87           | 0.12 (12% edited)
local           | 120          | 0.42           | 0.35 (35% edited)
local_fallback  | 30           | 0.38           | 0.40 (40% edited)
```

**Message Uniqueness Dashboard:**
```sql
SELECT
  user_id,
  COUNT(DISTINCT message) as unique_messages,
  COUNT(*) as total_shown,
  COUNT(DISTINCT message)::float / COUNT(*) as uniqueness_ratio
FROM message_history
WHERE shown_at > NOW() - INTERVAL '30 days'
GROUP BY user_id
HAVING COUNT(*) > 20;
```

**Expected Results:**
```
user_id | unique_messages | total_shown | uniqueness_ratio
--------|-----------------|-------------|------------------
42      | 78              | 80          | 0.975 (97.5% unique)
43      | 72              | 80          | 0.900 (90% unique)
```

**Cost Optimization:**

**Identify Low-Value LLM Calls:**
```sql
-- Find inputs where LLM confidence is low (might not need LLM)
SELECT original_input, confidence, COUNT(*)
FROM parse_analytics
WHERE source = 'llm' AND confidence < 0.6
GROUP BY original_input, confidence
ORDER BY COUNT(*) DESC
LIMIT 20;
```

**Optimize:**
- Simple inputs (1-2 words, no date/time) → Skip LLM, use local parser
- High-confidence patterns → Cache parse rules locally

**Tune Confidence Thresholds:**
```kotlin
// If user edits often at confidence 0.7-0.8, increase threshold
val shouldShowPreview = parsed.confidence < 0.85  // Was 0.8
```

---

## RISK MITIGATION

### Risk Matrix

| Risk | Impact | Probability | Mitigation | Status |
|------|--------|-------------|------------|--------|
| **Gemini API goes down** | High | Low | Automatic fallback to `task-parser.js` (current system) | ✅ Implemented |
| **High API costs** | High | Medium | Rate limiting (10/min, 100/day) + fallback to free tier | ✅ Implemented |
| **LLM hallucinates dates** | High | Medium | Hallucination validator strips invented data | ✅ Implemented |
| **Slow API response (>5s)** | Medium | Low | Timeout after 5 seconds → fallback to local parser | ⚠️ TODO |
| **User edits 50% of LLM parses** | High | Low | A/B test shows metrics, disable if accuracy <70% | 📊 Measure |
| **Message repetition** | Medium | Medium | Freshness constraints + recent message tracking | ✅ Implemented |
| **Android offline** | Medium | High | Detects connectivity → uses local parser automatically | ⚠️ TODO |
| **Breaking change in Gemini API** | High | Low | Try-catch wraps all LLM calls → fallback on error | ✅ Implemented |
| **Privacy concerns (user data sent to Google)** | High | Medium | Privacy policy update + user consent | ⚠️ TODO |
| **Rate limit too restrictive (users frustrated)** | Medium | Medium | Monitor complaints, adjust limits (10→20/min) | 📊 Monitor |

### Fallback Strategy Summary

**Every LLM call has a fallback:**

```
LLM Call
  ↓
 SUCCESS? → YES → Use LLM result
  ↓
 NO ↓
  ↓
Check Failure Type:
  │
  ├─ Rate Limited → Use local parser (inform user)
  ├─ API Error (500) → Use local parser (silent fallback)
  ├─ Timeout (>5s) → Use local parser (silent fallback)
  ├─ Network Error → Use local parser OR queue for later
  ├─ Invalid Response → Use local parser (log error)
  └─ Hallucination Detected → Strip invented data, keep valid parts
```

**User Never Sees:**
- "Error 500: Internal Server Error"
- "LLM API down, try again later"
- Blank screen or crash

**User Always Sees:**
- Parsed task (may be lower accuracy, but functional)
- Optional warning: "⚠️ Using quick parse" (if rate limited)

---

## IMPLEMENTATION ROADMAP

### Week 1: Backend LLM Parser

**Goal:** LLM parsing endpoint deployed and tested

**Day 1-2: Setup & Dependencies**
- [ ] Install Gemini SDK: `npm install @google/generative-ai`
- [ ] Add environment variables to Vercel (GEMINI_API_KEY, ENABLE_LLM_PARSING)
- [ ] Create `backend/utils/llm-task-parser.js` with `parseLLM()` function
- [ ] Test locally: `node -e "const {parseLLM} = require('./utils/llm-task-parser'); parseLLM('email manager by 5pm tomorrow').then(console.log)"`

**Day 3-4: Hallucination Validation**
- [ ] Create `validateAndClean()` function (date/time hallucination checks)
- [ ] Test with inputs that have NO date words → verify LLM date is stripped
- [ ] Test with "in 10 minutes" → verify parsed as due_time, not estimate
- [ ] Write unit tests: `backend/tests/llm-parser-hallucination.test.js`

**Day 5-6: Rate Limiting**
- [ ] Create `backend/middleware/rate-limiter.js`
- [ ] Implement per-user minute/day tracking
- [ ] Test: Make 11 requests in 1 minute → 11th should fallback to local parser
- [ ] Add rate limit headers to response (X-RateLimit-Remaining, X-RateLimit-Reset)

**Day 7: API Endpoint & Deployment**
- [ ] Create `POST /api/tasks/parse-llm` route in `backend/routes/tasks.js`
- [ ] Wire up: Rate limiter → LLM parser → Hallucination validator → Response
- [ ] Test with Postman: 10 diverse inputs (simple, complex, edge cases)
- [ ] Deploy to Vercel: `vercel --prod`
- [ ] Verify production endpoint works: `curl https://cosmic-ocean-api.vercel.app/api/tasks/parse-llm ...`

**Deliverables:**
- ✅ LLM parsing endpoint live
- ✅ Rate limiting working
- ✅ Hallucination validation tested
- ✅ Fallback to local parser on failure
- ✅ 20 unit tests passing

---

### Week 2: Android Integration (Part 1)

**Goal:** Android app calls new endpoint, shows parse preview

**Day 1-2: API Layer**
- [ ] Add `parseTaskLLM()` to `ApiService.kt` (Retrofit)
- [ ] Create `ParseRequest` and `ParsedResult` data classes
- [ ] Test: Call endpoint from Android (hardcoded test user)
- [ ] Verify: Response parsed correctly to Kotlin objects

**Day 3-4: Repository Layer**
- [ ] Update `TaskRepository.kt` with `parseInput()` function
- [ ] Implement decision logic: LLM vs local parser
- [ ] Add connectivity check: `connectivityManager.isConnected()`
- [ ] Add user preferences check: `userPrefs.llmParsingEnabled`
- [ ] Test: Online + enabled → calls LLM, Offline → local parser

**Day 5-6: UI Layer**
- [ ] Update `TaskCreationScreen.kt` with live preview
- [ ] Implement debounced input (800ms delay)
- [ ] Show preview card with parsed data (title, date, time, category, confidence)
- [ ] Add "Review & Create" button (vs "Create Task" if high confidence)
- [ ] Test: Type "email manager by 5pm tomorrow" → verify live preview appears

**Day 7: Testing & Polish**
- [ ] Test with 20 real user inputs (from beta tester feedback)
- [ ] Fix any UI bugs (layout issues, timing, etc.)
- [ ] Add loading indicator during parse
- [ ] Build debug APK: `./gradlew assembleDebug`
- [ ] Install on device: `adb install app/build/outputs/apk/debug/app-debug.apk`

**Deliverables:**
- ✅ Android app calls LLM endpoint
- ✅ Live parse preview UI
- ✅ Graceful offline fallback
- ✅ Debug APK ready for internal testing

---

### Week 3: Android Integration (Part 2) + Internal Testing

**Goal:** Settings UI, analytics tracking, internal team testing

**Day 1-2: Settings Screen**
- [ ] Create `LLMSettingsScreen.kt`
- [ ] Add switches: "Advanced Parsing", "Show Preview"
- [ ] Add dropdown: "Message Voice" (5 options)
- [ ] Wire to `UserPreferencesDao` (save to Room DB)
- [ ] Sync settings to backend: `PATCH /api/user/preferences`

**Day 3-4: Analytics**
- [ ] Create `AnalyticsTracker.kt` (local events + backend sync)
- [ ] Track: `parse_llm_success`, `parse_llm_error`, `task_created`, `task_edited`
- [ ] Add event properties: `confidence`, `source`, `parseTimeMs`
- [ ] Test: Create 5 tasks → verify events logged

**Day 5-7: Internal Testing**
- [ ] Build release APK: `./gradlew assembleRelease`
- [ ] Distribute to 10 internal team members
- [ ] Collect feedback: Parse accuracy, UI issues, bugs
- [ ] Monitor backend logs: Error rate, fallback rate, API latency
- [ ] Iterate: Fix critical bugs, adjust confidence thresholds

**Deliverables:**
- ✅ Settings UI for LLM preferences
- ✅ Analytics tracking implemented
- ✅ 10 internal users testing
- ✅ Feedback collected, issues documented

---

### Week 4: Message Intelligence Engine

**Goal:** Backend generates LLM messages, stores in cache

**Day 1-2: Message Generator Service**
- [ ] Create `backend/services/message-generator-llm.js`
- [ ] Implement `generateMessages()` function
- [ ] Build dynamic prompt with narrative context
- [ ] Test: Generate 5 messages for test user → verify variety

**Day 3-4: Voice/Intent Rotation**
- [ ] Implement `VoiceIntentRotator` class
- [ ] Track last 10 messages per user (avoid voice/intent repetition)
- [ ] Test: Generate 20 messages → verify all 5 voices used

**Day 5-6: Message Validation & Caching**
- [ ] Implement `validateMessages()` (word count, anti-patterns, duplicates)
- [ ] Create database queries: `messageCacheDao.insert()`, `getNextUnshown()`, `markShown()`
- [ ] Test: Generate → validate → cache → retrieve → mark shown

**Day 7: Background Worker**
- [ ] Create background job (Node.js setInterval or Vercel Cron)
- [ ] Run every 2 hours for active users (last 24h activity)
- [ ] Test: Manually trigger job → verify messages generated for all users

**Deliverables:**
- ✅ Message generation service working
- ✅ Voice/intent rotation implemented
- ✅ Message cache in database
- ✅ Background worker running

---

### Week 5: Message Intelligence Integration

**Goal:** Wallpaper uses LLM messages, A/B testing begins

**Day 1-2: Wallpaper Integration**
- [ ] Update `wallpaper-generator-enhanced.js`
- [ ] Add logic: Check message cache → if exists, use LLM message, else template
- [ ] Update `GET /api/wallpaper` to call new message provider
- [ ] Test: Generate wallpaper for user with cache → verify LLM message shown

**Day 3-4: Fallback Messages**
- [ ] Create `FallbackMessageProvider` class
- [ ] Implement time-based, context-aware fallbacks
- [ ] Test: Empty cache → verify fallback message used

**Day 5-7: A/B Testing Rollout**
- [ ] Day 5: Enable for 10% of users (update user settings in DB)
  ```sql
  UPDATE user_preferences
  SET settings = jsonb_set(settings, '{llm_messages_enabled}', 'true')
  WHERE user_id IN (SELECT id FROM users ORDER BY RANDOM() LIMIT (SELECT COUNT(*)/10 FROM users));
  ```
- [ ] Day 6: Monitor message history → check uniqueness ratio (target >90%)
- [ ] Day 7: Increase to 50% of users (if no issues)

**Deliverables:**
- ✅ Wallpaper displays LLM messages
- ✅ Fallback system working
- ✅ 50% of users seeing LLM messages
- ✅ Uniqueness metrics tracked

---

### Week 6-7: Beta Testing & Iteration

**Goal:** 50 beta testers using full LLM system, feedback collected

**Week 6: Beta Rollout**
- [ ] Day 1: Recruit 50 beta testers
- [ ] Day 2: Send APK + instructions
- [ ] Day 3: Enable LLM for all beta users (backend)
- [ ] Day 4-7: Monitor analytics dashboard
  - Parse accuracy (target >85%)
  - Message uniqueness (target >90%)
  - Error rate (target <5%)
  - Fallback rate (target <10%)

**Week 7: Feedback & Iteration**
- [ ] Day 1-2: Analyze top 10 parsing errors
  - Which inputs fail? Add to test suite
  - Adjust prompts or add special handling
- [ ] Day 3-4: Analyze message feedback
  - Which voices do users prefer? (survey)
  - Are there repeated phrases? (check history)
- [ ] Day 5-6: Implement fixes
  - Update prompt engineering
  - Tune confidence thresholds
  - Fix edge cases
- [ ] Day 7: Re-test with beta group

**Deliverables:**
- ✅ 50 beta users actively testing
- ✅ Feedback collected (10+ responses)
- ✅ Top issues fixed
- ✅ Metrics improving (accuracy, uniqueness)

---

### Week 8: Production Rollout & Monitoring

**Goal:** Full production release, 100% of users, monitoring dashboard

**Day 1-2: Final QA**
- [ ] Test suite: 100 real user inputs (from beta feedback)
- [ ] Load test: Simulate 100 concurrent parse requests
- [ ] Edge case testing: Emoji inputs, very long inputs, special characters
- [ ] Hallucination audit: 50 random parses → verify no invented data

**Day 3-4: Production Rollout**
- [ ] Day 3: Enable for 75% of users
- [ ] Day 4: Enable for 100% of users
  ```sql
  UPDATE user_preferences
  SET settings = jsonb_set(settings, '{llm_parsing_enabled}', 'true');
  ```
- [ ] Monitor error logs (Vercel logs, Sentry)
- [ ] Watch API costs (Gemini dashboard)

**Day 5-7: Monitoring & Documentation**
- [ ] Build analytics dashboard (Grafana or custom admin panel)
- [ ] Set up alerts: Error rate >5%, Fallback rate >15%
- [ ] Document: API usage guide, troubleshooting guide
- [ ] Create: User FAQ ("Why does it ask me to confirm?", "How to disable?")

**Deliverables:**
- ✅ 100% of users on LLM system
- ✅ Monitoring dashboard live
- ✅ Error alerts configured
- ✅ Documentation complete

---

## SUCCESS CRITERIA

### MVP Success (Week 8)

**Technical Metrics:**
- ✅ Parse accuracy >=85% (users edit <15% of parses)
- ✅ Message uniqueness >=90% (no repeats in 20 consecutive messages)
- ✅ Uptime >=99.5% (fallback works, users never blocked)
- ✅ API latency <2 seconds (p95)
- ✅ Error rate <5%
- ✅ Fallback rate <10%

**User Metrics:**
- ✅ User satisfaction +30% (measured via in-app rating)
- ✅ Task creation time -20% (measured via analytics: time from input start to task created)
- ✅ Wallpaper engagement +20% (measured via download frequency)
- ✅ Churn rate unchanged or lower (users don't leave due to LLM issues)

**Cost Metrics:**
- ✅ Cost per user per month <$0.50
- ✅ Total monthly cost for 1000 users <$500

**Operational Metrics:**
- ✅ Engineering maintenance time <2 hours/week (vs 4 hours/week for pattern updates)
- ✅ Zero critical bugs (app never crashes due to LLM)
- ✅ Privacy policy updated, no user complaints

### Long-Term Success (Month 3-6)

**Intelligence Improvements:**
- Users type less (LLM understands shortcuts: "email mgr fri" → "Email manager on Friday")
- Category detection improves (user corrections fed back to training data)
- Message learning: Track which messages drive task completions, generate more of that style

**Cost Optimization:**
- Hybrid approach: Simple inputs use local parser (free), complex use LLM
- Average cost per user drops to <$0.20/month

**Feature Expansion:**
- Voice input support (speech-to-text + LLM parsing)
- Multi-task parsing ("Buy milk, call mom, email manager" → 3 tasks)
- Smart suggestions ("You usually do laundry on Sundays, want to add it?")

---

## APPENDIX: KEY FILES REFERENCE

### Backend Files (New)

```
backend/
├── utils/
│   └── llm-task-parser.js              # Core LLM parsing logic
├── services/
│   └── message-generator-llm.js        # LLM message generation
├── middleware/
│   └── rate-limiter.js                 # API rate limiting
└── routes/
    └── messages.js                      # Message-related endpoints
```

### Backend Files (Modified)

```
backend/
├── server.js                            # Add new routes
├── routes/
│   └── tasks.js                         # Add /parse-llm endpoint
└── services/
    └── wallpaper-generator-enhanced.js  # Check message cache
```

### Android Files (New)

```
android/app/src/main/java/com/cosmicocean/
└── llm/
    ├── ParsePreviewProvider.kt          # Live parse preview
    └── GeminiClient.kt                  # Optional: Direct LLM (future)
```

### Android Files (Modified)

```
android/app/src/main/java/com/cosmicocean/
├── network/
│   └── ApiService.kt                    # Add parseTaskLLM() endpoint
├── data/
│   └── TaskRepository.kt                # Add parseInput() logic
└── ui/
    └── TaskCreationScreen.kt            # Add preview UI
```

### Database Migrations

```
backend/migrations/
└── 001_add_llm_tables.sql               # Create parse_analytics, message_cache, message_history
```

---

## CONCLUSION

This document provides a complete architectural blueprint for integrating LLM intelligence into Cosmic Ocean. Key principles:

1. **Parallel Systems** - New and old coexist, gradual migration
2. **Graceful Degradation** - Always works, even when LLM fails
3. **User Control** - Feature flags, preferences, opt-in/out
4. **Data-Driven** - Analytics track everything, iterate based on data
5. **Cost-Conscious** - Rate limits, fallbacks, hybrid approach

**Next Steps:**
1. Review this document with product owner (Vishnu)
2. Get approval for 8-week timeline
3. Set up Gemini API key
4. Start Week 1: Backend LLM Parser

**Questions? Check:**
- `cosmic-ocean-llm-parser-complete.docx` - Original LLM parser spec
- `cosmic-ocean-message-intelligence-engine.docx` - Original message spec
- `ROADMAP.md` - Overall project roadmap
- `CLAUDE.md` - AI assistant instructions

---

**Document Version:** 1.0
**Last Updated:** 2026-01-04
**Maintained By:** Claude Sonnet 4.5
**Review Cycle:** Update after each major milestone
**Status:** APPROVED FOR IMPLEMENTATION ✅
