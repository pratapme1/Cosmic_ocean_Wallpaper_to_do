# Agent Workflow Architecture

## Purpose
Define how agents coordinate QA and release work across sessions with clear hooks, status tracking, and handoffs.

## Roles
- **Planner**: Creates/updates plans and acceptance criteria
- **Executor**: Runs tests/builds, collects evidence
- **Reviewer**: Audits evidence, validates outputs

## Single Source of Truth
- Status file: `docs/STATUS_TRACKING.md`
- QA workflow: `docs/QA_WORKFLOW_MULTISESSION.md`
- Test plan: `docs/QA_TEST_PLAN.md`

## Required Hooks
1. **Start Hook**
   - Read `docs/STATUS_TRACKING.md`
   - Identify next `In Progress` item
   - Log new session entry

2. **Before Action Hook**
   - Confirm scope and dependencies
   - Check last completed step
   - Verify test environment

3. **After Action Hook**
   - Record result + evidence paths
   - Update status for that item

4. **Handoff Hook**
   - Add a short summary of what was done
   - Mark next item as `Ready`
   - Note blockers if any

## Execution Rules
- Never skip the status file
- Every action must have evidence or a clear rationale
- If blocked, log reason and assign a follow-up

## Status Labels
- `Ready`
- `In Progress`
- `Blocked`
- `Done`

## Session Log Format
Each session entry in `docs/STATUS_TRACKING.md` should include:
- Date
- Agent
- Task
- Result
- Evidence

