# Data Flow Quick Reference

## 🎯 The 3 Core Flows

### 1️⃣ TASK CREATION (Local-First)
```
User types: "Meeting in 30 minutes"
├─→ Parse (LLM if online, local if offline) [0-2s]
├─→ Save to Android Room DB [<10ms] ✅
├─→ Show in UI immediately
└─→ Queue for background sync [deferred]

Network Status:
├─ Online:  Parse with Gemini → Rich metadata
└─ Offline: Local regex parse → Basic metadata

Sync (when online):
├─→ POST /api/sync
├─→ Backend creates in PostgreSQL
└─→ Android gets serverId mapping
```

### 2️⃣ TASK UPDATE (Local-First)
```
User: Mark task complete
├─→ Update Room DB [<10ms] ✅
├─→ Show completed immediately
└─→ Queue for sync

Sync (background):
├─→ POST /api/sync
└─→ Backend updates PostgreSQL
```

### 3️⃣ IMAGE UPLOAD (Network Required)
```
User: Upload custom wallpaper
├─→ Resize/compress image
├─→ POST /api/user/wallpaper [2-5s] ⚠️
├─→ Uploads to Supabase Storage
├─→ Saves URL to PostgreSQL
└─→ Future wallpapers use custom image

⚠️ MUST BE ONLINE - No offline support
```

---

## 🏗️ What Lives Where

### 📱 Android Device (Local)
```
Room Database:
├─ stars (tasks) with syncStatus
├─ sync_queue (pending operations)
├─ constellation_links
└─ orbital_relationships

Cache:
├─ Last wallpaper image
├─ Achievement data
└─ User preferences

Processing:
├─ UI updates (instant)
├─ Local fallback parser
└─ Background sync worker
```

### 🌐 Backend (Vercel + Supabase)
```
PostgreSQL:
├─ users (accounts, settings)
├─ tasks (actual task data)
├─ user_achievements
└─ indexes for performance

Supabase Storage:
└─ Custom wallpaper images

Redis Cache:
├─ Generated wallpapers (30min)
└─ Rate limit counters

External:
└─ Gemini AI (task parsing)
```

---

## ⚡ Speed Comparison

| Action | Local-First? | Perceived Speed | Backend Call |
|--------|--------------|-----------------|--------------|
| **Add Task** | ✅ Yes | Instant (<10ms) | Deferred sync |
| **Complete Task** | ✅ Yes | Instant (<10ms) | Deferred sync |
| **Edit Task** | ✅ Yes | Instant (<10ms) | Deferred sync |
| **Upload Image** | ❌ No | 2-5 seconds | Immediate ⚠️ |
| **Parse with LLM** | ⚠️ Fallback | 1-3 seconds | With fallback |
| **Get Wallpaper** | ⚠️ Cached | 1-2 seconds | With cache |

---

## 🧪 Test Commands

### Test Offline Task Creation:
```bash
# 1. Enable airplane mode on device
# 2. Open Cosmic Ocean app
# 3. Add task: "Test offline"
# 4. ✅ Task appears immediately
# 5. Check sync queue shows 1 pending
# 6. Disable airplane mode
# 7. ✅ Task syncs in 1-5 seconds
```

### Check Backend Tasks:
```sql
-- In Supabase SQL Editor
SELECT id, title, user_id, sync_status, created_at 
FROM tasks 
ORDER BY created_at DESC 
LIMIT 5;
```

### Check Sync Queue (Android):
```kotlin
// In Android Studio debugger
val pendingCount = syncManager.pendingCount.value
val syncState = syncManager.syncState.value
```

---

## 🔑 Key Takeaways

**Core Principle:**
> "Everything that can be local, IS local. Network is only for sync and heavy processing."

**What This Means:**
- ✅ Task CRUD works offline
- ✅ UI is always instant
- ✅ No data loss on poor network
- ⚠️ Rich features (LLM, uploads) need network

**The Trade-off:**
- ✅ Fast, reliable, offline-capable app
- ⚠️ Some features need internet (but have fallbacks)

---

*Quick reference for version 2.3.1*
