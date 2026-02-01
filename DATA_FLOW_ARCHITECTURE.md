# Cosmic Ocean - Complete Data Flow Architecture

> **Version:** 2.3.1  
> **Date:** 2026-02-02  
> **Purpose:** Document exactly what happens when you upload images, create tasks, or update tasks

---

## 📊 Overview: 3 Types of Operations

| Operation | Local-First? | Backend Dependency | Offline Behavior |
|-----------|--------------|-------------------|------------------|
| **Image Upload** | ❌ NO | ✅ Required | Fails - requires network |
| **Task Creation** | ✅ YES | ⚠️ Sync happens later | ✅ Saves locally, syncs when online |
| **Task Update** | ✅ YES | ⚠️ Sync happens later | ✅ Saves locally, syncs when online |

---

## 🖼️ Flow 1: Image/Custom Wallpaper Upload

### When: User uploads custom wallpaper image

```
USER ACTION: Select image from gallery
    ↓
Android (MainActivity.kt)
├─→ Compress & resize image (ImageUtils.kt)
├─→ Create multipart request
├─→ NetworkModule.getApi().uploadWallpaper(image)
    ↓
NETWORK REQUIRED ❌ (Must be online)
    ↓
POST /api/user/wallpaper (backend/routes/user.js)
├─→ Save to Supabase Storage
├─→ Get public URL
├─→ Update user.custom_wallpaper_path in PostgreSQL
├─→ Invalidate cache
    ↓
RESPONSE: { success: true, url: "..." }
    ↓
Android (MainActivity.kt)
├─→ Show "Wallpaper uploaded!" toast
├─→ Trigger wallpaper refresh
    ↓
GET /api/wallpaper (next wallpaper request)
├─→ Backend sees custom_wallpaper_path
├─→ Uses custom image instead of generating
```

### ⚠️ Backend Dependencies:
- **Supabase Storage**: Stores the actual image file
- **PostgreSQL**: Stores the `custom_wallpaper_path` reference
- **Network**: Required for upload (NO offline support)

### 🔴 If Offline:
- Upload **FAILS immediately**
- User sees error message
- Must retry when online

---

## 📝 Flow 2: Task Creation (with LLM Parsing)

### When: User types "Email manager in 10 minutes"

### Step 1: NLP Parsing (Immediate)

```
USER INPUT: "Email manager in 10 minutes"
    ↓
Android (TaskRepository.kt)
├─→ Check network available?
    ↓
IF ONLINE:
├─→ POST /api/tasks/parse-llm
    ├─→ Backend (server.js:455)
    ├─→ parseLLM() with Gemini API
    ├─→ Returns: { title, dueDate, dueTime, priority, ... }
    ↓
IF OFFLINE:
├─→ Local fallback parser
├─→ Basic regex extraction
├─→ Returns: { title, priority: 2, ... }
    ↓
PARSED RESULT:
{
  title: "Email manager",
  dueDate: "2026-02-02",
  dueTime: "10:00",
  priority: 2,
  source: "llm" | "local_fallback"
}
```

### Step 2: Task Creation (Local-First)

```
PARSED DATA
    ↓
Android (TaskRepository.kt)
├─→ Generate localId: "star-1769970517500"
├─→ Create Star object with parsed fields
├─→ Save to Room DB (syncStatus = "pending")
├─→ ✅ UI updates immediately (via Flow)
    ↓
QUEUE TO SYNC
├─→ SyncManager.queueCreate(localTaskId, data)
├─→ Stores in sync_queue table
    ↓
TRIGGER SYNC (debounced 1s)
├─→ IF ONLINE:
    ├─→ POST /api/sync
    ├─→ Backend generates UUID: "550e8400-e29b-..."
    ├─→ Creates task in PostgreSQL
    ├─→ Returns mapping: {clientId, serverId}
    ├─→ Android updates local record with serverId
    ├─→ Mark as "synced"
    ↓
├─→ IF OFFLINE:
    ├─→ Stays in sync_queue
    ├─→ Retries when network returns
```

### ✅ Backend Dependencies:
- **LLM Parsing** (Optional): `/api/tasks/parse-llm` - Falls back to local if offline
- **Sync** (Deferred): `/api/sync` - Happens in background when online
- **Storage**: PostgreSQL stores the actual task data

### ✅ If Offline:
- **Task saves locally** ✅
- **UI updates immediately** ✅
- **Parse uses fallback** ✅
- **Sync queues for later** ✅
- **Zero data loss** ✅

---

## ✏️ Flow 3: Task Update (Complete/Archive/Edit)

### When: User marks task complete or edits it

```
USER ACTION: Mark task complete
    ↓
Android (TaskRepository.kt)
├─→ Update Room DB immediately
├─→ Set syncStatus = "pending"
├─→ ✅ UI updates immediately
    ↓
QUEUE TO SYNC
├─→ SyncManager.queueUpdate(localTaskId, data)
├─→ Data: { completed: true, completed_at: ... }
├─→ Stores in sync_queue table
    ↓
TRIGGER SYNC (debounced 1s, throttled 5s)
├─→ IF ONLINE:
    ├─→ POST /api/sync
    ├─→ Backend finds task by serverId
    ├─→ Applies update to PostgreSQL
    ├─→ Last-write-wins: checks timestamps
    ├─→ Returns success
    ├─→ Android marks as "synced"
    ↓
├─→ IF OFFLINE:
    ├─→ Stays in sync_queue
    ├─→ Retries with exponential backoff
```

### ✅ Backend Dependencies:
- **Sync only**: `/api/sync` - Deferred background operation
- **No immediate backend call** on update

### ✅ If Offline:
- **Update saves locally** ✅
- **UI shows completed** ✅
- **Syncs when online** ✅

---

## 🔄 Flow 4: Wallpaper Generation

### When: App requests wallpaper or periodic update

```
TRIGGER: User opens app / Periodic update
    ↓
Android
├─→ Check wallpaper mode: "generated" or "custom"
    ↓
IF CUSTOM MODE:
├─→ Use locally stored custom image
├─→ No backend call
    ↓
IF GENERATED MODE:
├─→ GET /api/wallpaper (with auth token)
    ├─→ Backend fetches tasks from PostgreSQL
    ├─→ Generates wallpaper image (Satori + Canvas)
    ├─→ Returns PNG image
    ├─→ Android caches and displays
    ↓
IF OFFLINE:
├─→ Use locally cached wallpaper
├─→ Or generate locally (limited)
```

### ⚠️ Backend Dependencies:
- **Generated Mode**: Requires backend call
- **Custom Mode**: No backend call (uses uploaded image)

### ✅ Offline Support:
- **Custom wallpaper**: ✅ Works offline
- **Generated wallpaper**: ⚠️ Uses last cached version

---

## 🏗️ Architecture Dependencies Map

### **1. Supabase PostgreSQL (Primary Database)**
```
Stores:
├── users (id, email, theme, timezone, custom_wallpaper_path)
├── tasks (id, user_id, title, due_date, completed, x, y, ...)
├── user_achievements (achievement tracking)
└── Indexes: idx_tasks_user_status, idx_tasks_user_due
```

### **2. Supabase Storage (File Storage)**
```
Stores:
└── Custom wallpaper images
    └── Path: wallpaper-uploads/{userId}/{filename}.jpg
```

### **3. Redis Cache (Upstash)**
```
Caches:
├── Generated wallpapers (30 min TTL)
├── User session data
└── Rate limit counters
```

### **4. External APIs**
```
├── Gemini AI (Google) - LLM task parsing
│   └── Endpoint: /api/tasks/parse-llm
├── Vercel - Hosting platform
└── Serverless Functions - Cold start capable
```

### **5. Android Local Database (Room)**
```
Local SQLite Database:
├── stars (tasks) - localId, serverId, title, syncStatus, ...
├── constellation_links - relationships
├── orbital_relationships - parent-child
├── sync_queue - pending operations
└── trophies - achievement cache
```

---

## 📡 Network Call Summary

### **Immediate Network Calls (Must be online)**
1. **Auth**: Login, Register, Token refresh
2. **Image Upload**: Custom wallpaper upload
3. **LLM Parsing**: Task parsing (has fallback)

### **Deferred Network Calls (Queue for later)**
1. **Task Create**: Queued to sync
2. **Task Update**: Queued to sync
3. **Task Delete**: Queued to sync
4. **Task Complete**: Queued to sync

### **Optional Network Calls**
1. **Wallpaper**: Can use cached/generated locally
2. **Achievements**: Can cache locally

---

## 🧠 Key Design Decisions

### **Why Some Things Require Network:**
1. **Image Upload**: Can't store large images locally efficiently
2. **LLM Parsing**: Requires Gemini API (but has local fallback)
3. **Wallpaper Generation**: Heavy processing done server-side

### **Why Tasks Are Local-First:**
1. **Instant feedback**: User sees task immediately
2. **Offline work**: Can use app on airplane mode
3. **No data loss**: Queue persists until synced
4. **Battery efficient**: Batched sync, not per-task

### **Conflict Resolution:**
- **Last-write-wins** based on server timestamps
- **Smart merging**: Preserves local-only fields (positions)
- **User choice**: Dialog shown for stale data conflicts

---

## 🔍 Testing the Flows

### **Test Task Creation Offline:**
```
1. Turn on airplane mode
2. Add task: "Test offline task tomorrow 3pm"
3. ✅ Should appear immediately in app
4. Check sync queue: 1 pending operation
5. Turn off airplane mode
6. Wait 1-5 seconds
7. ✅ Task syncs to backend
8. Check server: Task has proper UUID
```

### **Test Image Upload:**
```
1. Go to settings → Wallpaper
2. Select "Custom" mode
3. Upload image
4. ✅ Must be online to upload
5. Backend stores in Supabase Storage
6. Future wallpapers use uploaded image
```

---

## 📊 Performance Characteristics

| Operation | Local Latency | Network Latency | Total Time |
|-----------|---------------|-----------------|------------|
| Task Create | < 10ms | Deferred | ~0ms perceived |
| Task Update | < 10ms | Deferred | ~0ms perceived |
| Image Upload | N/A | 2-5s | 2-5s |
| LLM Parse | < 50ms (fallback) | 1-3s | 1-3s (with fallback) |
| Wallpaper | N/A | 1-2s | 1-2s |
| Sync | N/A | 200-500ms | Background |

---

## 🎯 Summary

**The architecture balances:**
- ✅ **Speed**: Local-first = instant UI updates
- ✅ **Reliability**: Queue ensures no data loss
- ✅ **Offline support**: Work without network
- ✅ **Rich features**: LLM, wallpaper generation require network but have fallbacks

**Dependencies are minimized for core functionality** (task CRUD) while allowing **rich network-enhanced features** (LLM parsing, server-generated wallpapers).

---

*Version 2.3.1 - Local-First Architecture with PostgreSQL Backend*
