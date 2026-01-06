# Text Rendering Implementation Plan - v1.3.5

> **Created:** 2026-01-06
> **Epic:** 9.1 - Smart Text Rendering & Collision Detection
> **Priority:** P1 - User Experience Critical
> **Duration:** 6 hours (estimated)
> **Version Target:** v1.3.5

---

## TABLE OF CONTENTS

1. [Problem Statement](#problem-statement)
2. [Solution Overview](#solution-overview)
3. [Technical Architecture](#technical-architecture)
4. [Implementation Phases](#implementation-phases)
5. [Testing Strategy](#testing-strategy)
6. [Success Criteria](#success-criteria)

---

## PROBLEM STATEMENT

### Issue 1: Background Obscures Stars

**Current Behavior:**
```kotlin
// CosmicCanvas.kt:717-724
drawRect(
    color = Color.Black.copy(alpha = 0.6f),  // ← 60% opacity blocks star glow
    topLeft = labelPos,
    size = Size(labelWidth + 12f, labelHeight + 6f)
)
```

**Problems:**
- Black background (60% opacity) covers star glow
- Reduces visual appeal and star visibility
- Makes it harder to see star colors (urgency indicators)

**User Impact:**
- Stars look dimmer and less attractive
- Color coding less effective
- Overall visual quality degraded

---

### Issue 2: Adjacent Labels Overlap

**Current Behavior:**
```kotlin
// LabelPositioning.kt only checks screen edges
if (x + labelWidth + padding > screenWidth) {
    // Move left
}
// ❌ NO check for other labels!
// ❌ NO check for nearby stars!
```

**Problems:**
- Labels overlap when stars are close together
- Overlapping text becomes unreadable
- No spatial awareness between labels

**User Impact:**
- Can't read task names when stars are adjacent
- Critical for same-priority stars (same color)
- Confusing UX when multiple tasks exist

**Example:**
```
Star 1 (red): "Email manager about project update"
              ┌────────────────────────────┐
              │ Email manager about pr     │ ← Label 1
              └────────────────────────────┘
                   ┌────────────────────────────┐
                   │ Call client for feedback   │ ← Label 2 (OVERLAPS!)
                   └────────────────────────────┘
Star 2 (red): "Call client for feedback"
```

**Root Cause:**
- `LabelPositioning.kt:calculateLabelPosition()` has no concept of "other labels exist"
- Treats each label independently
- No collision detection algorithm

---

## SOLUTION OVERVIEW

### Core Approach: Smart Collision Detection

**Key Principles:**
1. **Label-to-Label Collision Detection** - Prevent text box overlaps
2. **Label-to-Star Collision Detection** - Don't obscure nearby stars
3. **Intelligent Truncation** - Limit max width, show "..." for overflow
4. **Tap-to-Expand** - Full text visible in edit modal (already implemented!)
5. **Reduced Opacity** - 60% → 30% background (see stars through labels)
6. **Vertical Staggering** - Offset labels vertically when stars are horizontally close

### Visual Before/After

**BEFORE (Current):**
```
    ●  ┌───────────────────────────────┐ ← Blocks star glow (60% opacity)
       │ Email manager about proj...   │
       └───────────────────────────────┘
          ┌───────────────────────────────┐ ← OVERLAPS!
    ●     │ Call client for feedback      │
          └───────────────────────────────┘
```

**AFTER (v1.3.5):**
```
    ●  ┌──────────────────────┐ ← Lighter (30% opacity)
       │ Email manager abo... │
       └──────────────────────┘
                                    ← Staggered vertically
    ●                    ┌──────────────────────┐
                         │ Call client for fe...│
                         └──────────────────────┘
```

---

## TECHNICAL ARCHITECTURE

### New Components

#### 1. LabelCollisionDetector.kt

**Purpose:** Spatial collision detection for labels and stars

**Key Methods:**
```kotlin
object LabelCollisionDetector {

    /**
     * Calculate safe label positions avoiding all collisions
     * Uses spatial hashing for O(n) performance
     */
    fun calculateSafePositions(
        stars: List<Star>,
        labelSizes: Map<String, Size>,  // star.id → label size
        screenWidth: Float,
        screenHeight: Float
    ): Map<String, Offset>  // star.id → label position

    /**
     * Check if two rectangles overlap
     */
    private fun rectsOverlap(
        rect1: RectF,
        rect2: RectF,
        buffer: Float = 10f  // Minimum gap between elements
    ): Boolean

    /**
     * Check if label would obscure a star
     */
    private fun labelObscuresStar(
        labelRect: RectF,
        starX: Float,
        starY: Float,
        starRadius: Float
    ): Boolean

    /**
     * Find alternative position avoiding collisions
     * Try: right → left → top → bottom
     */
    private fun findSafePosition(
        preferredPos: Offset,
        labelSize: Size,
        occupiedAreas: List<RectF>,
        starX: Float,
        starY: Float,
        screenWidth: Float,
        screenHeight: Float
    ): Offset
}
```

**Algorithm:**
```
FOR each star in order of creation:
  1. Get preferred label position (LabelPositioning.kt)
  2. Check collision with existing labels
  3. Check collision with ALL stars (including nearby ones)
  4. IF collision detected:
       - Try right side
       - Try left side
       - Try above
       - Try below
       - Apply vertical offset (+40px) if still collision
  5. Store final position in map
```

**Performance Optimization:**
- Spatial hashing: Divide screen into grid cells
- Only check labels/stars in nearby cells
- O(n) instead of O(n²) for large star counts

---

#### 2. Updated LabelPositioning.kt

**Changes:**
```kotlin
// OLD: Independent positioning
fun calculateLabelPosition(...): Offset

// NEW: Collision-aware positioning
fun calculateLabelPosition(
    ...,
    existingLabels: List<RectF>,  // NEW: All existing label rectangles
    allStars: List<Star>,          // NEW: All stars (to avoid obscuring)
    bufferZone: Float = 10f        // NEW: Minimum gap
): Offset
```

**Integration:**
```kotlin
// In CosmicCanvas.kt:
val labelPositions = LabelCollisionDetector.calculateSafePositions(
    stars = stars,
    labelSizes = stars.associate { it.id to measureLabelSize(it.title) },
    screenWidth = size.width,
    screenHeight = size.height
)

stars.forEach { star ->
    val labelPos = labelPositions[star.id] ?: defaultPosition
    drawStarLabel(star, textMeasurer, labelPos, ...)
}
```

---

### Modified Components

#### CosmicCanvas.kt Changes

**1. Reduce Background Opacity (Line 718):**
```kotlin
// BEFORE:
drawRect(color = Color.Black.copy(alpha = 0.6f), ...)

// AFTER:
drawRect(color = Color.Black.copy(alpha = 0.3f), ...)  // ✅ 50% reduction
```

**2. Smart Truncation (Line 691-699):**
```kotlin
// BEFORE:
val maxLabelWidth = (screenWidth * 0.4f).toInt()  // 40% of screen

// AFTER:
val maxLabelWidth = min(
    (screenWidth * 0.3f).toInt(),  // 30% of screen (smaller)
    calculateMaxLabelWidth(star, allStars)  // Dynamic based on nearby stars
)

// Add ellipsis for overflow
val displayText = if (star.title.length > 25) {
    "${star.title.take(22)}..."  // Truncate at 25 chars
} else {
    star.title
}
```

**3. Collision-Aware Rendering (Line 464-468):**
```kotlin
// BEFORE:
stars.forEach { star ->
    drawStarLabel(star, textMeasurer, size.width, size.height)
}

// AFTER:
// Calculate all label positions ONCE (before drawing)
val labelPositions = LabelCollisionDetector.calculateSafePositions(
    stars = stars,
    labelSizes = stars.associate { it.id to measureLabelSize(it.title) },
    screenWidth = size.width,
    screenHeight = size.height
)

// Draw labels with collision-free positions
stars.forEach { star ->
    val labelPos = labelPositions[star.id]
    if (labelPos != null) {
        drawStarLabel(star, textMeasurer, labelPos, size.width, size.height)
    }
}
```

**4. Update drawStarLabel Signature:**
```kotlin
// BEFORE:
fun DrawScope.drawStarLabel(
    star: Star,
    textMeasurer: TextMeasurer,
    screenWidth: Float,
    screenHeight: Float
)

// AFTER:
fun DrawScope.drawStarLabel(
    star: Star,
    textMeasurer: TextMeasurer,
    labelPos: Offset,  // NEW: Pre-calculated position
    maxWidth: Int      // NEW: Dynamic width constraint
)
```

---

## IMPLEMENTATION PHASES

### Phase 1: Collision Detection Engine (2 hours)

**Deliverables:**
- `LabelCollisionDetector.kt` file created
- Spatial hashing grid implementation
- Collision detection algorithm working
- Unit tests for overlap detection

**Files Created:**
- `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/utils/LabelCollisionDetector.kt`

**Test Cases:**
```kotlin
@Test
fun testRectsOverlap_NoCollision() {
    val rect1 = RectF(0f, 0f, 100f, 50f)
    val rect2 = RectF(150f, 0f, 250f, 50f)
    assertFalse(rectsOverlap(rect1, rect2))
}

@Test
fun testRectsOverlap_WithCollision() {
    val rect1 = RectF(0f, 0f, 100f, 50f)
    val rect2 = RectF(50f, 0f, 150f, 50f)  // Overlaps 50-100
    assertTrue(rectsOverlap(rect1, rect2))
}

@Test
fun testLabelObscuresStar() {
    val labelRect = RectF(100f, 100f, 200f, 150f)
    val starX = 150f
    val starY = 125f  // Star center inside label
    val starRadius = 30f
    assertTrue(labelObscuresStar(labelRect, starX, starY, starRadius))
}
```

---

### Phase 2: Smart Positioning (2 hours)

**Deliverables:**
- `LabelPositioning.kt` updated with collision awareness
- Vertical staggering algorithm
- Buffer zone implementation
- Integration with CosmicCanvas.kt

**Files Modified:**
- `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/utils/LabelPositioning.kt`
- `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/ui/components/CosmicCanvas.kt`

**Algorithm Details:**

**Vertical Staggering:**
```kotlin
fun calculateVerticalOffset(
    star: Star,
    allStars: List<Star>,
    horizontalThreshold: Float = 150f  // Stars closer than 150px
): Float {
    // Find stars horizontally close
    val nearbyStars = allStars.filter { other ->
        other != star &&
        abs(other.particle.x - star.particle.x) < horizontalThreshold
    }

    // Stagger vertically based on index
    val index = nearbyStars.indexOf(star)
    return index * 40f  // 40px per level
}
```

**Buffer Zones:**
```kotlin
// Minimum gap between elements
const val LABEL_TO_LABEL_BUFFER = 10f   // 10px gap between labels
const val LABEL_TO_STAR_BUFFER = 50f    // 50px gap from stars
const val EDGE_PADDING = 20f            // 20px from screen edges
```

---

### Phase 3: Visual Polish (1 hour)

**Deliverables:**
- Background opacity reduced to 30%
- Smart truncation with "..." ellipsis
- Verify edit modal shows full text (already implemented)
- Visual consistency check

**Files Modified:**
- `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/ui/components/CosmicCanvas.kt` (opacity)
- `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/ui/components/CosmicCanvas.kt` (truncation)

**Truncation Logic:**
```kotlin
fun truncateTaskName(taskName: String, maxChars: Int = 25): String {
    return if (taskName.length > maxChars) {
        "${taskName.take(maxChars - 3)}..."
    } else {
        taskName
    }
}
```

**Opacity Change:**
```kotlin
// Before: 0.6f (60% opacity)
// After:  0.3f (30% opacity)
drawRect(
    color = Color.Black.copy(alpha = 0.3f),  // ← 50% reduction
    topLeft = labelPos,
    size = Size(labelWidth + 12f, labelHeight + 6f)
)
```

---

### Phase 4: Testing (1 hour)

**Test Scenarios:**

**1. Low Density (2-3 stars):**
- Stars spread across screen
- ✅ Labels don't overlap
- ✅ Stars visible through labels

**2. Medium Density (5-7 stars):**
- Some stars adjacent
- ✅ Vertical staggering works
- ✅ No label-to-label overlap
- ✅ No label-to-star overlap

**3. High Density (10+ stars):**
- Many stars close together
- ✅ All labels visible
- ✅ Performance acceptable (<16ms render)
- ✅ No visual glitches

**4. Long Task Names:**
- Task name: "Email manager about project status update for Q4 deliverables"
- ✅ Truncated to ~25 chars
- ✅ Shows "Email manager about pr..."
- ✅ Full text in edit modal

**5. Edge Cases:**
- Stars in corners (top-left, bottom-right)
- All stars same priority (all red)
- Stars very close together (<100px)

**Files Modified:**
- None (manual testing on device)

**Test Checklist:**
```
[ ] 2 stars (opposite sides) → No overlap
[ ] 3 stars (triangle) → Vertical stagger works
[ ] 5 stars (same priority) → All labels readable
[ ] 10 stars (random) → Performance acceptable
[ ] Long task name (50+ chars) → Truncation working
[ ] Edit modal → Full text visible
[ ] Background opacity → Stars visible (30%)
[ ] Corner stars → Labels don't go off-screen
```

---

## TESTING STRATEGY

### Unit Tests

**LabelCollisionDetector.kt:**
```kotlin
class LabelCollisionDetectorTest {
    @Test
    fun rectsOverlap_NoCollision() { /* ... */ }

    @Test
    fun rectsOverlap_WithCollision() { /* ... */ }

    @Test
    fun rectsOverlap_WithBuffer() { /* ... */ }

    @Test
    fun labelObscuresStar_Inside() { /* ... */ }

    @Test
    fun labelObscuresStar_Outside() { /* ... */ }

    @Test
    fun findSafePosition_RightSideWorks() { /* ... */ }

    @Test
    fun findSafePosition_LeftSideFallback() { /* ... */ }

    @Test
    fun calculateSafePositions_TwoStars() { /* ... */ }

    @Test
    fun calculateSafePositions_TenStars() { /* ... */ }
}
```

### Integration Tests

**Device Testing:**
```
1. Create 2 stars (P1) with names: "Email manager", "Call client"
2. Position them 100px apart
3. ✅ Verify: Labels don't overlap
4. ✅ Verify: Stars visible through labels

5. Create 5 stars (mixed priorities)
6. Position randomly
7. ✅ Verify: All labels readable
8. ✅ Verify: No visual clutter

9. Create star with long name: "Email manager about project status update for Q4 deliverables"
10. ✅ Verify: Truncated to ~25 chars with "..."
11. Tap star
12. ✅ Verify: Edit modal shows full text
```

### Performance Tests

**Render Time:**
```kotlin
// Acceptable: < 16ms (60fps)
// Target: < 8ms (120fps)

fun measureRenderTime(starCount: Int) {
    val start = System.nanoTime()
    LabelCollisionDetector.calculateSafePositions(stars, ...)
    val elapsed = (System.nanoTime() - start) / 1_000_000f
    println("Render time ($starCount stars): ${elapsed}ms")
}

// Expected results:
// 2 stars:  < 1ms
// 5 stars:  < 2ms
// 10 stars: < 5ms
// 20 stars: < 10ms
```

---

## SUCCESS CRITERIA

### Visual Quality

- [ ] ✅ Background opacity reduced to 30%
- [ ] ✅ Stars clearly visible through labels
- [ ] ✅ No label-to-label overlap (100% elimination)
- [ ] ✅ No label-to-star overlap (obscuring nearby stars)
- [ ] ✅ Text always readable (contrast preserved)

### Functional Requirements

- [ ] ✅ Long task names truncated with "..." (max 25 chars)
- [ ] ✅ Full text visible in edit modal (existing feature)
- [ ] ✅ Vertical staggering works for adjacent stars
- [ ] ✅ Labels don't go off-screen (edge detection)
- [ ] ✅ Performance acceptable (< 16ms render time)

### User Experience

- [ ] ✅ Can distinguish same-priority stars by task name
- [ ] ✅ Visual clutter eliminated
- [ ] ✅ Readability improved (95% vs 60%)
- [ ] ✅ Stars look better (30% opacity vs 60%)
- [ ] ✅ No frustrating overlaps

### Code Quality

- [ ] ✅ `LabelCollisionDetector.kt` follows Kotlin conventions
- [ ] ✅ Unit tests cover edge cases
- [ ] ✅ Performance optimized (spatial hashing)
- [ ] ✅ Code documented with comments
- [ ] ✅ No regressions in existing features

---

## DEPLOYMENT PLAN

### Version Bump

**build.gradle:**
```gradle
defaultConfig {
    versionCode 9      // Was 8
    versionName "1.3.5"  // Was "1.3.4"
}
```

### APK Build

```bash
cd /home/vi/supernova/android
./gradlew clean assembleRelease
cp app/build/outputs/apk/release/app-release.apk \
   /home/vi/supernova/cosmic-ocean-v1.3.5-smart-labels.apk
```

### Installation

```bash
adb install -r /home/vi/supernova/cosmic-ocean-v1.3.5-smart-labels.apk
```

### Testing Checklist

```
[ ] Install APK on device
[ ] Create 2 stars → Verify no overlap
[ ] Create 5 stars → Verify staggering
[ ] Create 10 stars → Verify performance
[ ] Long task name → Verify truncation
[ ] Tap star → Verify full text in modal
[ ] Visual check → Stars visible through labels (30% opacity)
[ ] Regression check → Drag, complete, archive still work
```

---

## FILES TO CREATE/MODIFY

### New Files (1)

1. **LabelCollisionDetector.kt** (NEW)
   - Location: `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/utils/LabelCollisionDetector.kt`
   - Lines: ~250
   - Purpose: Collision detection engine

### Modified Files (3)

1. **CosmicCanvas.kt** (MODIFY)
   - Location: `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/ui/components/CosmicCanvas.kt`
   - Changes:
     - Line 691-699: Smart truncation logic
     - Line 718: Opacity 0.6 → 0.3
     - Line 464-468: Collision-aware label rendering
   - Lines Changed: ~30

2. **LabelPositioning.kt** (MODIFY)
   - Location: `/home/vi/supernova/android/app/src/main/java/com/cosmicocean/utils/LabelPositioning.kt`
   - Changes:
     - Add collision-aware positioning
     - Add vertical staggering
     - Add buffer zone checks
   - Lines Changed: ~50

3. **build.gradle** (MODIFY)
   - Location: `/home/vi/supernova/android/app/build.gradle`
   - Changes:
     - versionCode: 8 → 9
     - versionName: "1.3.4" → "1.3.5"
   - Lines Changed: 2

---

## ESTIMATED TIME BREAKDOWN

| Phase | Duration | Tasks |
|-------|----------|-------|
| Phase 1: Collision Engine | 2 hours | LabelCollisionDetector.kt, unit tests |
| Phase 2: Smart Positioning | 2 hours | LabelPositioning.kt, CosmicCanvas.kt integration |
| Phase 3: Visual Polish | 1 hour | Opacity, truncation, final tweaks |
| Phase 4: Testing | 1 hour | Device testing, edge cases |
| **TOTAL** | **6 hours** | |

---

## RISKS & MITIGATION

### Risk 1: Performance Degradation

**Risk:** Collision detection slows render time (>16ms)
**Impact:** Dropped frames, laggy UI
**Mitigation:**
- Use spatial hashing (O(n) vs O(n²))
- Cache label positions when stars don't move
- Profile with 20+ stars

### Risk 2: Complex Edge Cases

**Risk:** Labels still overlap in rare scenarios
**Impact:** User frustration, unclear task names
**Mitigation:**
- Exhaustive testing (2, 5, 10, 20 stars)
- Fallback: Move label far away (top/bottom edges)
- Ultimate fallback: Stack labels vertically

### Risk 3: Truncation Too Aggressive

**Risk:** Task names too short, lose context
**Impact:** User can't identify tasks
**Mitigation:**
- Allow 25 chars (was 40% screen = ~30-40 chars)
- Dynamic truncation based on nearby stars
- Full text always in edit modal

---

## NEXT STEPS

**Immediate:**
1. ✅ Create this implementation plan
2. ✅ Update STATUS.md with Epic 9.1
3. ✅ Update ROADMAP.md with Epic 9.1
4. 📋 Get user approval to proceed
5. 📋 Start Phase 1: Collision Detection Engine

**After Completion:**
1. Update CHANGELOG.md with v1.3.5
2. Create deployment verification document
3. Mark Epic 9.1 complete in ROADMAP.md

---

**Created:** 2026-01-06 18:45 UTC
**Owner:** Vishnu (Product) + Claude (Implementation)
**Status:** 📋 **READY TO IMPLEMENT** (Awaiting approval)
