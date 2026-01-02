# Supernova Backend API Documentation

> **Version:** 1.0.0
> **Base URL:** `https://api.supernova.app` (Production) | `http://localhost:3000` (Development)
> **Last Updated:** 2026-01-02

---

## Table of Contents

1. [Overview](#overview)
2. [Authentication](#authentication)
3. [Rate Limiting](#rate-limiting)
4. [Error Handling](#error-handling)
5. [API Endpoints](#api-endpoints)
   - [Authentication Endpoints](#authentication-endpoints)
   - [User Endpoints](#user-endpoints)
   - [Task Endpoints](#task-endpoints)
   - [Sync Endpoints](#sync-endpoints)
   - [Wallpaper Endpoint](#wallpaper-endpoint)
   - [Utility Endpoints](#utility-endpoints)
6. [Data Models](#data-models)
7. [Caching Strategy](#caching-strategy)
8. [Code Examples](#code-examples)

---

## Overview

The Supernova API is a RESTful API that powers the Supernova task management application. It supports:

- **JWT-based authentication** with access and refresh tokens
- **Offline-first sync** with conflict resolution
- **Dynamic wallpaper generation** with task overlays
- **Real-time task prioritization** based on context

### Key Features

| Feature | Description |
|---------|-------------|
| Authentication | JWT tokens with 7-day access / 30-day refresh |
| Sync | Offline-first with last-write-wins conflict resolution |
| Wallpaper | Dynamic PNG generation with Redis caching |
| Themes | 3 themes: cosmic, ocean, fantasy |
| Priorities | Auto-calculated based on due dates |

---

## Authentication

### Token Types

| Token | Expiry | Purpose |
|-------|--------|---------|
| Access Token | 7 days | API access via `Authorization: Bearer <token>` |
| Refresh Token | 30 days | Obtain new access tokens |
| Wallpaper Token | 365 days | Widget-only access to wallpaper endpoint |

### Authentication Header

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Token Payload Structure

**Access Token:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "iat": 1672531200,
  "exp": 1673136000
}
```

**Wallpaper Token:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "scope": "wallpaper",
  "iat": 1672531200,
  "exp": 1704067200
}
```

---

## Rate Limiting

All endpoints are rate-limited to prevent abuse:

| Limiter | Limit | Window | Applied To |
|---------|-------|--------|------------|
| Global | 100 req | 1 minute | All endpoints (except /health) |
| Auth | 10 req | 1 minute | /api/auth/* |
| Wallpaper | 20 req | 1 hour | /api/wallpaper |
| Task Creation | 30 req | 1 minute | POST /api/tasks |

### Rate Limit Headers

```http
RateLimit-Limit: 100
RateLimit-Remaining: 95
RateLimit-Reset: 1672531260
```

### Rate Limit Exceeded Response

```json
{
  "error": "Too many requests, please try again later"
}
```
**Status Code:** `429 Too Many Requests`

---

## Error Handling

### Error Response Format

```json
{
  "error": {
    "message": "Descriptive error message",
    "code": "ERROR_CODE",
    "timestamp": "2024-01-01T00:00:00.000Z",
    "path": "/api/endpoint",
    "method": "POST",
    "details": {}
  }
}
```

### Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Invalid request data |
| `AUTHENTICATION_ERROR` | 401 | Missing or invalid token |
| `AUTHORIZATION_ERROR` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Resource not found |
| `CONFLICT` | 409 | Resource already exists |
| `RATE_LIMIT` | 429 | Too many requests |
| `DATABASE_ERROR` | 500 | Database operation failed |
| `CACHE_ERROR` | 500 | Redis operation failed |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

### Validation Error Format

```json
{
  "errors": [
    {
      "type": "field",
      "value": "invalid-email",
      "msg": "Invalid email format",
      "path": "email",
      "location": "body"
    }
  ]
}
```

---

## API Endpoints

---

## Authentication Endpoints

### POST /api/auth/register

Register a new user account.

**Rate Limit:** 10 req/min

**Request:**
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!",
  "theme": "cosmic",
  "timezone": "America/New_York",
  "resolution": "1170x2532"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| email | string | Yes | Valid email address |
| password | string | Yes | Minimum 8 characters |
| theme | string | No | `cosmic`, `ocean`, or `fantasy` (default: cosmic) |
| timezone | string | No | IANA timezone (default: UTC) |
| resolution | string | No | Screen resolution WxH (default: 1170x2532) |

**Response (201 Created):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "wallpaperToken": "550e8400-e29b-41d4-a716-446655440000",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "theme": "cosmic",
    "timezone": "America/New_York",
    "resolution": "1170x2532"
  }
}
```

**Errors:**
- `400` - Validation error (invalid email/password format)
- `409` - Email already registered

---

### POST /api/auth/login

Authenticate user and receive tokens.

**Rate Limit:** 10 req/min

**Request:**
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| email | string | Yes | Registered email address |
| password | string | Yes | Account password |

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "wallpaperToken": "550e8400-e29b-41d4-a716-446655440000",
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "email": "user@example.com",
    "theme": "cosmic",
    "timezone": "UTC",
    "resolution": "1170x2532",
    "done_for_today": false
  }
}
```

**Errors:**
- `401` - Invalid email or password

---

### POST /api/auth/refresh

Exchange refresh token for new access token.

**Rate Limit:** 10 req/min

**Request:**
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| refreshToken | string | Yes | Valid refresh token |

**Response (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs..."
}
```

**Errors:**
- `400` - Missing refresh token
- `401` - Invalid or expired refresh token

---

### POST /api/auth/wallpaper-token

Generate a new wallpaper token (invalidates old one).

**Auth Required:** Yes

**Request:**
```http
POST /api/auth/wallpaper-token
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "wallpaperToken": "new-uuid-token"
}
```

**Errors:**
- `401` - Not authenticated

---

## User Endpoints

### GET /api/user

Get current user profile.

**Auth Required:** Yes

**Request:**
```http
GET /api/user
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "user@example.com",
  "theme": "cosmic",
  "resolution": "1170x2532",
  "display_mode": "one_thing",
  "timezone": "UTC",
  "setup_complete": false,
  "done_for_today": false,
  "done_for_today_at": null,
  "wallpaper_token": "token-uuid",
  "created_at": "2024-01-01T00:00:00.000Z",
  "updated_at": "2024-01-01T00:00:00.000Z"
}
```

**Errors:**
- `404` - User not found

---

### PATCH /api/user

Update user settings.

**Auth Required:** Yes

**Request:**
```http
PATCH /api/user
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "theme": "ocean",
  "resolution": "1080x1920",
  "display_mode": "all_tasks",
  "timezone": "Europe/London",
  "setup_complete": true,
  "done_for_today": false
}
```

| Field | Type | Required | Validation |
|-------|------|----------|------------|
| theme | string | No | `cosmic`, `ocean`, or `fantasy` |
| resolution | string | No | Format: `WxH` (e.g., `1080x1920`) |
| display_mode | string | No | `one_thing` or `all_tasks` |
| timezone | string | No | Valid IANA timezone |
| setup_complete | boolean | No | - |
| done_for_today | boolean | No | - |

**Response (200 OK):**
Returns updated user object (same format as GET /api/user)

**Side Effects:**
- Invalidates all wallpaper caches for user

**Errors:**
- `400` - Validation error or no valid fields provided
- `404` - User not found

---

### GET /api/user/export

Export all user data (GDPR compliance).

**Auth Required:** Yes

**Request:**
```http
GET /api/user/export
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "exportedAt": "2024-01-01T12:00:00.000Z",
  "version": "1.0",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "theme": "cosmic",
    "resolution": "1170x2532",
    "display_mode": "one_thing",
    "timezone": "UTC",
    "setup_complete": true,
    "created_at": "2024-01-01T00:00:00.000Z",
    "updated_at": "2024-01-01T00:00:00.000Z"
  },
  "tasks": [
    {
      "id": "uuid",
      "title": "Task title",
      "priority": 2
    }
  ],
  "stats": [
    {
      "week_start": "2024-01-01",
      "app_opens": 50,
      "tasks_completed": 25
    }
  ]
}
```

---

### DELETE /api/user

Delete user account and all associated data (GDPR compliance).

**Auth Required:** Yes

**Request:**
```http
DELETE /api/user
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "Account deleted successfully. All associated data has been removed."
}
```

**Side Effects:**
- Cascading delete removes all tasks and stats

---

### GET /api/user/stats/weekly

Get weekly statistics for last 8 weeks.

**Auth Required:** Yes

**Request:**
```http
GET /api/user/stats/weekly
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "weeks": [
    {
      "id": "uuid",
      "user_id": "uuid",
      "week_start": "2024-01-01",
      "app_opens": 10,
      "widget_interactions": 5,
      "tasks_created": 3,
      "tasks_completed": 2,
      "tasks_completed_via_widget": 1,
      "created_at": "2024-01-01T00:00:00.000Z",
      "updated_at": "2024-01-01T00:00:00.000Z"
    }
  ],
  "trends": {
    "appOpenTrend": 25,
    "widgetCompletionRate": 50
  }
}
```

| Trend Field | Description |
|-------------|-------------|
| appOpenTrend | % change in app opens from previous week (null if insufficient data) |
| widgetCompletionRate | % of tasks completed via widget |

---

### POST /api/user/done-for-today

Mark user as done for the day (shows celebration wallpaper).

**Auth Required:** Yes

**Request:**
```http
POST /api/user/done-for-today
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "doneForToday": true,
  "doneAt": "2024-01-01T18:30:00.000Z"
}
```

**Side Effects:**
- Sets `done_for_today = true`
- Invalidates wallpaper cache
- Resets automatically at midnight

---

### GET /api/user/stats/graduation

Get graduation metrics showing app usage reduction over 6 months.

**Auth Required:** Yes

**Request:**
```http
GET /api/user/stats/graduation
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "months": [
    {
      "month": "2024-01-01T00:00:00.000Z",
      "total_app_opens": 50,
      "total_widget_interactions": 20,
      "total_tasks_completed": 15,
      "total_tasks_completed_via_widget": 10
    }
  ],
  "graduation": {
    "score": 35,
    "message": "You're making progress. The app is becoming invisible infrastructure.",
    "widgetCompletionRate": 67
  }
}
```

| Score Range | Message |
|-------------|---------|
| > 50 | "Amazing! You're graduating from the app..." |
| > 25 | "You're making progress..." |
| > 0 | "Good start! Keep using the widget..." |
| 0 | "Use the widget more..." |

---

## Task Endpoints

### GET /api/tasks

List all active tasks for user, sorted by priority score.

**Auth Required:** Optional (guest mode supported)

**Request:**
```http
GET /api/tasks?location=home&time=morning
Authorization: Bearer <access_token>
```

| Query Param | Type | Description |
|-------------|------|-------------|
| location | string | Context for location-based prioritization |
| time | string | Time context: `morning`, `afternoon`, `evening` |

**Response (200 OK):**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "user_id": "uuid",
    "title": "Buy groceries",
    "due_date": "2024-01-15",
    "due_time": "14:30",
    "estimate_minutes": 30,
    "priority": 2,
    "completed": false,
    "completed_at": null,
    "snoozed_until": null,
    "context_location": "grocery_store",
    "context_time": "afternoon",
    "energy_required": "low",
    "times_rescheduled": 0,
    "original_due_date": null,
    "x": 100.5,
    "y": 200.5,
    "is_subtask": false,
    "is_recurring": false,
    "echo_interval": null,
    "archived": false,
    "created_at": "2024-01-01T00:00:00.000Z",
    "updated_at": "2024-01-01T00:00:00.000Z",
    "score": 450
  }
]
```

**Notes:**
- Excludes completed tasks
- Excludes tasks snoozed until future
- Sorted by calculated priority score (descending)

---

### GET /api/tasks/:id

Get single task by ID.

**Auth Required:** Optional

**Request:**
```http
GET /api/tasks/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <access_token>
```

**Response (200 OK):**
Single task object (same structure as GET /api/tasks)

**Errors:**
- `404` - Task not found

---

### POST /api/tasks

Create a new task with natural language parsing.

**Auth Required:** Yes
**Rate Limit:** 30 req/min

**Request:**
```http
POST /api/tasks
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "rawTitle": "Buy groceries tomorrow at 3pm in 30m"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| rawTitle | string | Yes* | Natural language task input |
| title | string | Yes* | Alternative to rawTitle (plain title) |
| priority | integer | No | 1-3 (auto-calculated if not provided) |
| context_location | string | No | Location context |
| context_time | string | No | Time context |
| x | number | No | Spatial coordinate |
| y | number | No | Spatial coordinate |
| is_subtask | boolean | No | Whether this is a subtask |
| is_recurring | boolean | No | Whether this recurs |
| echo_interval | string | No | Recurrence interval (e.g., "1d", "1w") |

*One of `rawTitle` or `title` is required

**Natural Language Parsing:**

| Input Pattern | Extracted |
|---------------|-----------|
| `in 30m` or `30min` | estimate_minutes: 30 |
| `in 1h` or `1.5h` | estimate_minutes: 60 or 90 |
| `tomorrow` | due_date: tomorrow |
| `next Monday` | due_date: next Monday |
| `at 3pm` | due_time: 15:00 |

**Response (201 Created):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": "uuid",
  "title": "Buy groceries",
  "estimate_minutes": 30,
  "priority": 2,
  "due_date": "2024-01-02",
  "due_time": "15:00",
  "created_at": "2024-01-01T00:00:00.000Z",
  "updated_at": "2024-01-01T00:00:00.000Z"
}
```

**Side Effects:**
- Sets `done_for_today = false`
- Invalidates wallpaper cache

**Priority Auto-Calculation:**

| Due Date | Priority |
|----------|----------|
| Overdue | P1 (critical) |
| Due within 2h | P2 (normal) |
| Future | P3 (low priority) |

---

### PATCH /api/tasks/:id

Update task fields.

**Auth Required:** Optional

**Request:**
```http
PATCH /api/tasks/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "title": "Updated task title",
  "completed": true,
  "source": "widget"
}
```

| Field | Type | Description |
|-------|------|-------------|
| title | string | Task title |
| priority | integer | 1-3 priority level |
| estimate_minutes | integer | Time estimate in minutes |
| due_date | string | ISO date (YYYY-MM-DD) |
| due_time | string | Time (HH:MM) |
| completed | boolean | Mark as complete |
| snoozed_until | string | ISO datetime for snooze |
| context_location | string | Location context |
| context_time | string | Time context |
| energy_required | string | `low`, `medium`, `high` |
| x | number | Spatial coordinate |
| y | number | Spatial coordinate |
| is_subtask | boolean | Subtask flag |
| is_recurring | boolean | Recurring flag |
| echo_interval | string | Recurrence interval |
| archived | boolean | Archive flag |
| source | string | `widget` for tracking |

**Automatic Fields:**
- `completed_at` set to NOW() when `completed = true`
- `archived_at` set to NOW() when `archived = true`
- `updated_at` always set to NOW()

**Response (200 OK):**
Updated task object

**Side Effects:**
- When `completed = true`: increments tasks_completed stat
- When `source = "widget"`: increments tasks_completed_via_widget
- Invalidates wallpaper cache

**Errors:**
- `404` - Task not found

---

### POST /api/tasks/:id/snooze

Snooze task until tomorrow.

**Auth Required:** Optional

**Request:**
```http
POST /api/tasks/550e8400-e29b-41d4-a716-446655440000/snooze
Authorization: Bearer <access_token>
```

**Response (200 OK):**
Updated task object with:
- `due_date` → tomorrow
- `snoozed_until` → tomorrow
- `original_due_date` → preserved (if first snooze)
- `times_rescheduled` → incremented

**Side Effects:**
- Invalidates wallpaper cache

---

### DELETE /api/tasks/:id

Delete a single task.

**Auth Required:** Optional

**Request:**
```http
DELETE /api/tasks/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "success": true
}
```

**Side Effects:**
- Invalidates wallpaper cache

**Errors:**
- `404` - Task not found

---

### DELETE /api/tasks

Delete all tasks for user.

**Auth Required:** Optional

**Request:**
```http
DELETE /api/tasks
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "success": true,
  "message": "All tasks deleted"
}
```

**Side Effects:**
- Invalidates wallpaper cache

---

## Sync Endpoints

### POST /api/sync

Synchronize offline changes with server.

**Auth Required:** Yes

**Conflict Resolution:** Last-Write-Wins

**Request:**
```http
POST /api/sync
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "lastSyncAt": 1672531200000,
  "pendingChanges": [
    {
      "type": "create",
      "clientId": "client-uuid-1",
      "timestamp": 1672531300000,
      "data": {
        "title": "New task from offline",
        "priority": 2
      }
    },
    {
      "type": "update",
      "clientId": "client-uuid-2",
      "timestamp": 1672531400000,
      "data": {
        "id": "existing-task-uuid",
        "completed": true
      }
    },
    {
      "type": "delete",
      "clientId": "client-uuid-3",
      "timestamp": 1672531500000,
      "data": {
        "id": "task-to-delete-uuid"
      }
    }
  ]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| lastSyncAt | integer | No | Unix timestamp of last sync (ms) |
| pendingChanges | array | No | Array of offline changes |

**Change Object:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| type | string | Yes | `create`, `update`, or `delete` |
| action | string | No | Legacy alias for `type` |
| clientId | string | Yes | Client-generated UUID |
| timestamp | integer | Yes | Unix timestamp (ms) |
| data | object | Yes | Change payload |

**Response (200 OK):**
```json
{
  "syncedAt": 1672531600000,
  "tasks": [
    {
      "id": "uuid",
      "title": "Task title",
      "updated_at": "2024-01-01T00:00:00.000Z"
    }
  ],
  "results": {
    "applied": 3,
    "rejected": 1,
    "conflicts": []
  },
  "conflicts": [
    {
      "clientId": "client-uuid-2",
      "reason": "stale_data",
      "serverData": {
        "id": "existing-task-uuid",
        "title": "Server version",
        "updated_at": "2024-01-01T00:00:00.000Z"
      }
    }
  ]
}
```

**Conflict Reasons:**

| Reason | Description |
|--------|-------------|
| `already_exists` | Task with same ID or title exists |
| `not_found` | Task doesn't exist (for update/delete) |
| `stale_data` | Server version is newer |
| `server_error` | Processing error |

**Errors:**
- `400` - Validation error
- `401` - Unauthorized

---

### GET /api/sync/status

Get sync status and metadata.

**Auth Required:** Yes

**Request:**
```http
GET /api/sync/status
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "taskCount": 42,
  "lastModified": 1672531600000,
  "serverTime": 1672531700000
}
```

| Field | Type | Description |
|-------|------|-------------|
| taskCount | integer | Total active tasks for user |
| lastModified | integer | Timestamp of most recent task update (null if no tasks) |
| serverTime | integer | Current server timestamp |

---

## Wallpaper Endpoint

### GET /api/wallpaper

Generate and return wallpaper PNG image.

**Auth Required:** Yes (JWT in Authorization header)
**Rate Limit:** 20 req/hour

**Request:**
```http
GET /api/wallpaper?theme=cosmic&resolution=1080x1920&enhanced=true
Authorization: Bearer <access_token>
```

| Query Param | Type | Default | Description |
|-------------|------|---------|-------------|
| theme | string | cosmic | `cosmic`, `ocean`, or `fantasy` |
| resolution | string | 1080x1920 | Format: `WxH` |
| enhanced | string | true | Use enhanced generator |
| timestamp | integer | - | Animation frame timestamp (skips cache) |

**Response (200 OK):**
```http
Content-Type: image/png
X-Cache: HIT | MISS
X-Fallback: true (if fallback used)

[PNG image binary data]
```

**Urgency Levels:**

| Level | Condition | Visual Effect |
|-------|-----------|---------------|
| clear | done_for_today OR no tasks | Celebration colors |
| critical | Has overdue tasks | Red tones |
| urgent | Has tasks due today | Orange tones |
| attention | Tasks due within 48h | Yellow/orange tones |
| calm | No immediate deadlines | Blue/teal tones |

**Theme Color Palettes:**

| Theme | Clear | Calm | Attention | Urgent | Critical |
|-------|-------|------|-----------|--------|----------|
| cosmic | cyan | blue | orange | orange | red |
| ocean | teal | deep_blue | gold | gold | red |
| fantasy | gold | purple | orange | orange | red |

**Caching:**
- Cache Key: `wp:{userId}:{theme}:{resolution}:one_thing`
- TTL: 1 hour (3600 seconds)
- Invalidated on: task changes, settings changes, done_for_today changes
- Bypassed when: `timestamp` parameter provided

**Errors:**
- Returns fallback wallpaper on generation error
- `401` - Invalid/missing token
- `500` - Complete failure (JSON error response)

---

## Utility Endpoints

### GET /api/health

Health check endpoint.

**Auth Required:** No
**Rate Limited:** No

**Request:**
```http
GET /api/health
```

**Response (200 OK):**
```json
{
  "status": "ok",
  "mode": "postgres"
}
```

| Mode | Description |
|------|-------------|
| postgres | PostgreSQL database connected |
| mock | In-memory mock database |

---

### POST /api/done-for-today

Mark done for today (legacy endpoint).

**Auth Required:** Optional

**Request:**
```http
POST /api/done-for-today
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "success": true
}
```

---

### POST /api/metrics/app-open

Track app open event for analytics.

**Auth Required:** Optional

**Request:**
```http
POST /api/metrics/app-open
Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "success": true
}
```

**Tracking:**
- Increments `app_opens` in weekly user_stats

---

## Data Models

### User

```typescript
interface User {
  id: string;                    // UUID
  email: string;                 // Unique email
  password_hash: string;         // Bcrypt hash (never returned)
  theme: 'cosmic' | 'ocean' | 'fantasy';
  resolution: string;            // "WxH" format
  display_mode: 'one_thing' | 'all_tasks';
  timezone: string;              // IANA timezone
  setup_complete: boolean;
  done_for_today: boolean;
  done_for_today_at: string | null;  // ISO datetime
  wallpaper_token: string;       // UUID
  created_at: string;            // ISO datetime
  updated_at: string;            // ISO datetime
}
```

### Task

```typescript
interface Task {
  id: string;                    // UUID
  user_id: string;               // UUID (FK to users)
  title: string;                 // Required
  due_date: string | null;       // YYYY-MM-DD
  due_time: string | null;       // HH:MM
  estimate_minutes: number | null;
  priority: number;              // 1 (critical), 2 (normal), 3 (low)
  completed: boolean;
  completed_at: string | null;   // ISO datetime
  snoozed_until: string | null;  // ISO datetime
  context_location: string | null;
  context_time: string | null;   // 'morning' | 'afternoon' | 'evening'
  energy_required: string | null; // 'low' | 'medium' | 'high'
  decay_prompted: boolean;
  original_due_date: string | null; // First due date before snoozes
  times_rescheduled: number;
  x: number | null;              // Spatial coordinate
  y: number | null;              // Spatial coordinate
  is_subtask: boolean;
  is_recurring: boolean;
  echo_interval: string | null;  // '1d', '1w', etc.
  archived: boolean;
  archived_at: string | null;    // ISO datetime
  created_at: string;            // ISO datetime
  updated_at: string;            // ISO datetime
  score?: number;                // Calculated priority score
}
```

### UserStats

```typescript
interface UserStats {
  id: string;                    // UUID
  user_id: string;               // UUID (FK to users)
  week_start: string;            // YYYY-MM-DD (Monday)
  app_opens: number;
  widget_interactions: number;
  tasks_created: number;
  tasks_completed: number;
  tasks_completed_via_widget: number;
  created_at: string;            // ISO datetime
  updated_at: string;            // ISO datetime
}
```

---

## Caching Strategy

### Redis Cache Configuration

| Setting | Value |
|---------|-------|
| Enabled | When `REDIS_URL` or `REDIS_ENABLED=true` |
| Max Reconnect Attempts | 5 |
| Reconnect Delay | 2 seconds |
| Circuit Breaker Threshold | 5 failures |
| Circuit Breaker Timeout | 30 seconds |

### Cache Keys

```
wp:{userId}:{theme}:{resolution}:{displayMode}
```

Example:
```
wp:550e8400-e29b-41d4-a716-446655440000:cosmic:1080x1920:one_thing
```

### Cache Invalidation Triggers

| Trigger | Action |
|---------|--------|
| Task created | Invalidate user wallpapers |
| Task updated | Invalidate user wallpapers |
| Task deleted | Invalidate user wallpapers |
| User settings changed | Invalidate user wallpapers |
| done_for_today changed | Invalidate user wallpapers |
| Midnight reset | Clear all wallpapers |

### TTL Values

| Cache Type | TTL |
|------------|-----|
| Wallpaper | 1 hour (3600s) |

---

## Code Examples

### JavaScript/TypeScript

#### Register and Login

```javascript
const API_BASE = 'https://api.supernova.app';

// Register
async function register(email, password) {
  const response = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password, theme: 'cosmic' })
  });
  return response.json();
}

// Login
async function login(email, password) {
  const response = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });
  return response.json();
}
```

#### Create and Manage Tasks

```javascript
// Create task with natural language
async function createTask(accessToken, rawTitle) {
  const response = await fetch(`${API_BASE}/api/tasks`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ rawTitle })
  });
  return response.json();
}

// Get all tasks
async function getTasks(accessToken) {
  const response = await fetch(`${API_BASE}/api/tasks`, {
    headers: { 'Authorization': `Bearer ${accessToken}` }
  });
  return response.json();
}

// Complete task
async function completeTask(accessToken, taskId) {
  const response = await fetch(`${API_BASE}/api/tasks/${taskId}`, {
    method: 'PATCH',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ completed: true })
  });
  return response.json();
}
```

#### Sync Offline Changes

```javascript
async function syncChanges(accessToken, lastSyncAt, pendingChanges) {
  const response = await fetch(`${API_BASE}/api/sync`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ lastSyncAt, pendingChanges })
  });
  return response.json();
}

// Usage
const result = await syncChanges(token, lastSync, [
  {
    type: 'create',
    clientId: crypto.randomUUID(),
    timestamp: Date.now(),
    data: { title: 'New task' }
  }
]);

if (result.conflicts.length > 0) {
  // Handle conflicts
  result.conflicts.forEach(conflict => {
    console.log(`Conflict: ${conflict.reason}`, conflict.serverData);
  });
}
```

#### Download Wallpaper

```javascript
async function downloadWallpaper(accessToken, theme, resolution) {
  const response = await fetch(
    `${API_BASE}/api/wallpaper?theme=${theme}&resolution=${resolution}`,
    { headers: { 'Authorization': `Bearer ${accessToken}` } }
  );

  const cacheHit = response.headers.get('X-Cache') === 'HIT';
  const blob = await response.blob();

  return { blob, cacheHit };
}
```

### cURL Examples

```bash
# Register
curl -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# Login
curl -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# Create task
curl -X POST http://localhost:3000/api/tasks \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"rawTitle":"Buy milk tomorrow at 3pm in 30m"}'

# Get tasks
curl http://localhost:3000/api/tasks \
  -H "Authorization: Bearer <TOKEN>"

# Complete task
curl -X PATCH http://localhost:3000/api/tasks/<TASK_ID> \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"completed": true}'

# Snooze task
curl -X POST http://localhost:3000/api/tasks/<TASK_ID>/snooze \
  -H "Authorization: Bearer <TOKEN>"

# Get wallpaper
curl "http://localhost:3000/api/wallpaper?theme=cosmic&resolution=1080x1920" \
  -H "Authorization: Bearer <TOKEN>" \
  -o wallpaper.png

# Sync changes
curl -X POST http://localhost:3000/api/sync \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "lastSyncAt": 1672531200000,
    "pendingChanges": [{
      "type": "create",
      "clientId": "unique-id",
      "timestamp": 1672531300000,
      "data": {"title": "Synced task"}
    }]
  }'

# Export user data
curl http://localhost:3000/api/user/export \
  -H "Authorization: Bearer <TOKEN>"

# Delete account
curl -X DELETE http://localhost:3000/api/user \
  -H "Authorization: Bearer <TOKEN>"
```

### Kotlin (Android)

```kotlin
import retrofit2.http.*
import retrofit2.Response

interface SupernovaApi {
    @POST("api/auth/login")
    suspend fun login(@Body credentials: LoginRequest): Response<AuthResponse>

    @GET("api/tasks")
    suspend fun getTasks(@Header("Authorization") token: String): Response<List<Task>>

    @POST("api/tasks")
    suspend fun createTask(
        @Header("Authorization") token: String,
        @Body task: CreateTaskRequest
    ): Response<Task>

    @PATCH("api/tasks/{id}")
    suspend fun updateTask(
        @Header("Authorization") token: String,
        @Path("id") taskId: String,
        @Body updates: TaskUpdate
    ): Response<Task>

    @POST("api/sync")
    suspend fun sync(
        @Header("Authorization") token: String,
        @Body syncRequest: SyncRequest
    ): Response<SyncResponse>

    @GET("api/wallpaper")
    suspend fun getWallpaper(
        @Header("Authorization") token: String,
        @Query("theme") theme: String,
        @Query("resolution") resolution: String
    ): Response<ResponseBody>
}
```

---

## Environment Variables

```bash
# Required
DATABASE_URL=postgresql://user:pass@host:5432/dbname
JWT_SECRET=your-secure-jwt-secret
JWT_REFRESH_SECRET=your-secure-refresh-secret

# Optional
POSTGRES_URL=postgresql://...  # Alternative to DATABASE_URL
DB_SSL=true                    # Enable SSL for database
REDIS_URL=redis://localhost:6379
REDIS_ENABLED=true
PORT=3000
NODE_ENV=development|test|production
```

---

## API Summary

| Endpoint | Method | Auth | Rate Limit | Description |
|----------|--------|------|------------|-------------|
| `/api/auth/register` | POST | No | 10/min | Register new user |
| `/api/auth/login` | POST | No | 10/min | Login user |
| `/api/auth/refresh` | POST | No | 10/min | Refresh access token |
| `/api/auth/wallpaper-token` | POST | Yes | 100/min | Generate wallpaper token |
| `/api/user` | GET | Yes | 100/min | Get user profile |
| `/api/user` | PATCH | Yes | 100/min | Update user settings |
| `/api/user` | DELETE | Yes | 100/min | Delete account |
| `/api/user/export` | GET | Yes | 100/min | Export all data |
| `/api/user/stats/weekly` | GET | Yes | 100/min | Get weekly stats |
| `/api/user/stats/graduation` | GET | Yes | 100/min | Get graduation metrics |
| `/api/user/done-for-today` | POST | Yes | 100/min | Mark done for today |
| `/api/tasks` | GET | Optional | 100/min | List all tasks |
| `/api/tasks` | POST | Yes | 30/min | Create task |
| `/api/tasks` | DELETE | Optional | 100/min | Delete all tasks |
| `/api/tasks/:id` | GET | Optional | 100/min | Get single task |
| `/api/tasks/:id` | PATCH | Optional | 100/min | Update task |
| `/api/tasks/:id` | DELETE | Optional | 100/min | Delete task |
| `/api/tasks/:id/snooze` | POST | Optional | 100/min | Snooze task |
| `/api/sync` | POST | Yes | 100/min | Sync offline changes |
| `/api/sync/status` | GET | Yes | 100/min | Get sync status |
| `/api/wallpaper` | GET | Yes | 20/hr | Generate wallpaper |
| `/api/done-for-today` | POST | Optional | 100/min | Mark done (legacy) |
| `/api/metrics/app-open` | POST | Optional | 100/min | Track app open |
| `/api/health` | GET | No | None | Health check |

**Total Endpoints:** 24

---

*Documentation generated on 2026-01-02*
