# Claude Instructions - Cosmic Ocean Project

> **Last Updated:** 2026-01-02
> **Read Time:** 3 minutes
> **Purpose:** Core AI assistant instructions

---

## ⚠️ CRITICAL: FILE MANAGEMENT FOR AI AGENTS

**This is the ONLY instruction file for AI agents.**

**DO NOT:**
- ❌ Create duplicate files (claude.md, ai-instructions.md, etc.)
- ❌ Create "quick reference" variants
- ❌ Split into multiple instruction files

**DO:**
- ✅ Update THIS file only when instructions change
- ✅ Alert user if you find duplicate instruction files

---

## 🚀 AUTOMATIC WORKFLOW (No Manual Triggering)

**EVERY SESSION - You AUTOMATICALLY:**
1. Read **[STATUS.md](STATUS.md)** first
2. Read **[ROADMAP.md](ROADMAP.md)** to identify next task
3. Read **[DECISIONS.md](DECISIONS.md)** for context

**EVERY TASK - You AUTOMATICALLY:**

When user says **ANY** of these:
- "Implement [feature]"
- "Create [component]"
- "Build [system]"
- "Fix [bug]"
- "Test [code]"
- "Review [implementation]"
- "Update [docs]"

You **IMMEDIATELY and AUTOMATICALLY** (without asking):

### Step 1: Auto-Detect Task Type
```javascript
// Your internal logic:
if (mentions Android/UI/Component/Feature) → android-dev-agent
if (mentions API/endpoint/backend/server) → backend-dev-agent
if (mentions test/testing/verify) → testing-agent
if (mentions review/check/audit) → review-agent
if (mentions docs/STATUS/ROADMAP) → doc-agent
if (mentions PWA/cosmic-ocean/analyze) → pwa-reference-agent
```

### Step 2: Auto-Load Agent Files
```bash
# You automatically read (NO user prompt needed):
Read .agents/[detected-agent].md

# For Android features, you ALWAYS read:
Read .agents/pwa-reference-agent.md  # First
Read .agents/android-dev-agent.md    # Then
```

### Step 3: Auto-Execute Workflow
```
Follow the agent's workflow steps automatically
Generate outputs in agent's format automatically
Update docs automatically (via doc-agent)
```

**NO MANUAL ACTIVATION REQUIRED. JUST DO IT.**

---

## 🎯 PROJECT OVERVIEW

**Product:** Task app where your lock screen wallpaper IS your task list

**Three Components:**
1. **PWA (cosmic-ocean/)** - Reference implementation - **READ ONLY** ❌
2. **Android (android/)** - Active development - **WORK HERE** ✅
3. **Backend (backend/)** - API development - **WORK HERE** ✅

**Your Mission:** Replicate PWA features in Android app + complete backend API

---

## 🚫 CRITICAL RULES - WHAT NOT TO DO

### Rule 1: DO NOT Modify PWA
- PWA (cosmic-ocean/) is **FROZEN** - reference only
- Study it, replicate in Android, but **DO NOT CHANGE IT**
- Exception: Critical bugs with explicit user approval

### Rule 2: DO NOT Implement Abandoned Pivot
- `cosmic-ocean/docs/PIVOT.md` = "Informative Ocean" pivot
- This was **ABANDONED** (see [DECISIONS.md](DECISIONS.md) D-001)
- Treat as historical context only

### Rule 3: DO NOT Skip PWA Reference
- Always read PWA code before implementing in Android
- Must understand **behavior**, not just feature name
- Goal: Behavioral parity with PWA

### Rule 4: DO NOT Make Architectural Changes Without Approval
- Ask before changing project structure
- Ask before making strategic decisions
- Ask before deviating from PWA reference

---

## ✅ AUTOMATIC EXECUTION RULES

**These rules are AUTOMATIC. You execute them without asking:**

### Rule 1: Auto-Read Session Context
```
On EVERY new conversation:
→ Automatically read STATUS.md
→ Automatically read ROADMAP.md
→ Automatically read DECISIONS.md
```

### Rule 2: Auto-Detect and Load Agents
```
When user mentions a task:
→ Automatically detect task type
→ Automatically read agent file(s) from .agents/
→ Automatically follow agent workflow
```

### Rule 3: Auto-Execute Multi-Agent Pipeline
```
For Android features (ALWAYS do this automatically):
1. Auto-read .agents/pwa-reference-agent.md → Analyze PWA
2. Auto-read .agents/android-dev-agent.md → Implement
3. Auto-read .agents/testing-agent.md → Test
4. Auto-read .agents/review-agent.md → Review
5. Auto-read .agents/doc-agent.md → Update docs

NO user command needed. Just do it.
```

### Rule 4: Auto-Generate Agent Outputs
```
Automatically create files in:
→ testing/reports/pwa-analysis/
→ testing/reports/test-report-*.md
→ testing/reports/reviews/review-*.md
→ .summaries/session-*.md

Automatically update:
→ STATUS.md
→ ROADMAP.md
```

**IMPORTANT:** User should NEVER have to say "use agents" or "activate agents".
You detect task type and execute agent workflows AUTOMATICALLY.

---

## 🤖 AUTO-DETECTION EXAMPLES (How It Works)

**User says:** "Implement Tutorial System"
**You automatically do:**
```
1. Detect: Android feature (Epic 0, Task 6)
2. Read: .agents/pwa-reference-agent.md
3. Read: cosmic-ocean/src/ui/TutorialController.ts
4. Create: testing/reports/pwa-analysis/tutorial-analysis.md
5. Read: .agents/android-dev-agent.md
6. Implement: TutorialManager.kt, TutorialOverlay.kt
7. Read: .agents/testing-agent.md
8. Test: Create testing/reports/test-report-tutorial.md
9. Read: .agents/review-agent.md
10. Review: Create testing/reports/reviews/review-tutorial.md
11. Read: .agents/doc-agent.md
12. Update: STATUS.md, ROADMAP.md
```

**User says:** "Create wallpaper generation endpoint"
**You automatically do:**
```
1. Detect: Backend API task (Epic 2)
2. Read: .agents/backend-dev-agent.md
3. Implement: routes, controllers, services
4. Read: .agents/testing-agent.md
5. Test: Create testing/reports/test-report-wallpaper.md
6. Read: .agents/review-agent.md
7. Review: Create testing/reports/reviews/review-wallpaper.md
8. Read: .agents/doc-agent.md
9. Update: STATUS.md, ROADMAP.md
```

**User says:** "What's next?"
**You automatically do:**
```
1. Read: STATUS.md
2. Read: ROADMAP.md
3. Identify: Next task from roadmap
4. Report: Task details and which agents will handle it
```

**NO manual activation. NO asking "should I use agents?". JUST DO IT.**

---

## 📋 TASK COMPLETION CHECKLIST

Before marking ANY task complete:

- [ ] PWA reference code reviewed
- [ ] Android implementation matches PWA behavior
- [ ] Build succeeds (no errors)
- [ ] Feature tested on device/emulator
- [ ] [STATUS.md](STATUS.md) updated
- [ ] [ROADMAP.md](ROADMAP.md) checkbox marked `[x]`
- [ ] No regressions in existing features

---

## 🔍 QUICK REFERENCE

### When Working on Android Features

| Step | Action |
|------|--------|
| 1 | Find PWA file: `cosmic-ocean/src/[feature].ts` |
| 2 | Study implementation (data structures, logic, edge cases) |
| 3 | Create equivalent: `android/app/src/main/kotlin/[feature].kt` |
| 4 | Test behavioral parity (same inputs → same outputs) |

### When Working on Backend

| Step | Action |
|------|--------|
| 1 | Check [ROADMAP.md](ROADMAP.md) Epic 2 remaining tasks |
| 2 | Reference PRD requirements |
| 3 | Support Android client needs first |

### Key PWA Reference Files

| Feature | PWA File | Lines |
|---------|----------|-------|
| Tutorial | `cosmic-ocean/src/ui/TutorialController.ts` | 750 |
| Help | `cosmic-ocean/src/ui/HelpOverlay.ts` | 429 |
| Backup | `cosmic-ocean/src/data/BackupManager.ts` | 308 |
| Delete | `cosmic-ocean/src/interactions/DeleteController.ts` | 558 |
| Trophy | `cosmic-ocean/src/ui/TrophyDisplay.ts` | 362 |

---

## 📚 ESSENTIAL DOCUMENTS

| Need | Document |
|------|----------|
| Current state? | [STATUS.md](STATUS.md) |
| Next task? | [ROADMAP.md](ROADMAP.md) |
| Why this decision? | [DECISIONS.md](DECISIONS.md) |
| Detailed workflow? | [WORKFLOW.md](WORKFLOW.md) |
| All docs index? | [DOCUMENTS_INDEX.md](DOCUMENTS_INDEX.md) |
| Design system? | [ANDROID_UI_UX_DESIGN_SYSTEM.md](ANDROID_UI_UX_DESIGN_SYSTEM.md) |

---

## 🎨 CODING STANDARDS

### Android (Kotlin)
```kotlin
// Jetpack Compose for UI
// Material Design 3 components
// Coroutines for async work
// Follow existing patterns in android/ folder
```

### Backend (Node.js)
```javascript
// Express.js + async/await
// JWT authentication middleware
// Input validation with express-validator
// Follow existing patterns in backend/ folder
```

### Documentation
```markdown
// Update STATUS.md daily
// Update ROADMAP.md task checkboxes
// Document decisions in DECISIONS.md
```

---

## 🚨 WHEN TO ASK USER

**Always Ask Before:**
- Changing project structure
- Making architectural decisions
- Deviating from PWA reference
- Skipping a task in ROADMAP.md
- Modifying PWA code (cosmic-ocean/)

**Can Decide Yourself:**
- Implementation details within Android
- Code organization within a file
- Variable/function naming
- Adding utility functions

---

## 💡 SESSION WORKFLOW

### Start (5 min)
1. Read [STATUS.md](STATUS.md)
2. Read next task in [ROADMAP.md](ROADMAP.md)
3. Check [DECISIONS.md](DECISIONS.md) for updates

### During Work
1. Study PWA reference code
2. Implement in Android
3. Test incrementally
4. Document issues

### End (5 min)
1. Update [STATUS.md](STATUS.md) with progress
2. Update [ROADMAP.md](ROADMAP.md) checkboxes
3. Document blockers or decisions

---

## 🎯 SUCCESS DEFINITION

**Android App:**
- 100% feature parity with PWA
- 60fps with 100+ tasks
- Tutorial guides new users
- No crashes

**Backend:**
- API documented (OpenAPI)
- Offline sync working
- Redis caching operational
- Wallpaper generation <1 second

**Overall:**
- Lock screen shows ONE task
- Beautiful wallpaper (3 themes)
- Ready for Google Play Store

---

## 🎯 REMEMBER

**Goal:** Android app that perfectly replicates PWA behavior + complete backend API

**Strategy:** PWA as blueprint → Implement in Android → Test parity → Ship

**Focus:** Quality over speed. Behavioral parity over quick features.

**Timeline:** ~17 weeks (Epic 0: 2 weeks, Epic 1: 10-14 weeks, Backend: 1 week)

---

**Owner:** Vishnu (Product) + Claude (AI Assistant)
**Format:** Concise reference (250 lines)
**For Details:** See [WORKFLOW.md](WORKFLOW.md), [ROADMAP.md](ROADMAP.md), [STATUS.md](STATUS.md)
