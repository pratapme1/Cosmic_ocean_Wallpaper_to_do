# Cosmic Ocean - Android UI/UX Design System

> **Purpose**: Complete design specifications for Android devices. Ensures wallpaper renders correctly across all screen sizes without content breaking or being hidden.

---

# PART 1: SCREEN SPECIFICATIONS

## 1.1 Android Device Categories

| Category | Screen Size | Resolution | Aspect Ratio | Density | Example Devices |
|----------|-------------|------------|--------------|---------|-----------------|
| Compact | 5.0" - 5.8" | 1080 x 2160 | 18:9 | 400-420 dpi | Pixel 4a, Galaxy A series |
| Standard | 5.8" - 6.4" | 1080 x 2340 | 19.5:9 | 400-440 dpi | Pixel 7, Galaxy S23 |
| Large | 6.4" - 6.9" | 1080 x 2400 | 20:9 | 390-420 dpi | Pixel 7 Pro, Galaxy S23+ |
| XLarge | 6.7" - 6.9" | 1440 x 3088 | 19.3:9 | 500-560 dpi | Galaxy S23 Ultra, Pixel 8 Pro |
| Foldable Outer | 6.2" | 904 x 2316 | 23.1:9 | 390 dpi | Galaxy Fold outer |
| Foldable Inner | 7.6" | 1812 x 2176 | 6:5 | 373 dpi | Galaxy Fold inner |

## 1.2 Wallpaper Canvas Dimensions

**Rule: Always render at device's native resolution**

```kotlin
fun getWallpaperDimensions(context: Context): Dimensions {
    val displayMetrics = context.resources.displayMetrics
    
    // Get actual screen dimensions
    val width = displayMetrics.widthPixels
    val height = displayMetrics.heightPixels
    
    // For wallpaper, we need to account for parallax scrolling
    // Standard: 2x width for home screen parallax
    val wallpaperWidth = width * 2
    val wallpaperHeight = height
    
    return Dimensions(wallpaperWidth, wallpaperHeight)
}
```

**Lock Screen vs Home Screen:**

| Context | Width | Height | Parallax |
|---------|-------|--------|----------|
| Lock Screen | 1x device width | Device height | None |
| Home Screen | 2x device width | Device height | Horizontal scroll |

**Recommended Base Resolutions for Assets:**

| Asset Type | Resolution | Format | Notes |
|------------|------------|--------|-------|
| Theme Background | 2880 x 5120 | JPEG (85%) | 16:9 at 4x, scales down |
| Particle Sprites | 64 x 64 → 256 x 256 | PNG (alpha) | Multiple sizes |
| Celebration Sprites | 512 x 512 | PNG (alpha) | Detailed animations |
| UI Icons | 24dp, 32dp, 48dp | Vector (XML) | All densities |

---

# PART 2: SAFE ZONES & LAYOUT GRID

## 2.1 System UI Safe Zones

Every Android screen has areas occupied by system UI that we must avoid:

```
┌──────────────────────────────────────────────────────────────┐
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ STATUS BAR ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│ 24-48dp
├──────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────┐ │
│ │                                                          │ │
│ │                    CAMERA CUTOUT                         │ │ 0-32dp
│ │              (punch hole / notch area)                   │ │
│ │                                                          │ │
│ └──────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                                                              │
│                    LOCK SCREEN CLOCK                         │ 80-140dp
│                    (system rendered)                         │
│                                                              │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                                                              │
│                                                              │
│                    SAFE CONTENT AREA                         │
│                    (our wallpaper scene)                     │
│                                                              │
│                                                              │
│                                                              │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                    TASK DISPLAY ZONE                         │
│                    (our UI overlay)                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓ NAVIGATION/GESTURE BAR ▓▓▓▓▓▓▓▓▓▓▓▓▓▓│ 20-48dp
└──────────────────────────────────────────────────────────────┘
```

## 2.2 Safe Zone Calculations

```kotlin
object SafeZones {
    
    data class SafeZoneInsets(
        val top: Int,           // Status bar + camera cutout
        val clockZone: Int,     // Lock screen clock area
        val bottom: Int,        // Navigation/gesture bar
        val left: Int,          // Edge insets
        val right: Int          // Edge insets
    )
    
    fun calculate(context: Context, windowInsets: WindowInsets): SafeZoneInsets {
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        
        // Get system insets
        val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val displayCutout = windowInsets.displayCutout
        
        // Status bar height (typically 24-48dp)
        val statusBarHeight = systemBars.top
        
        // Camera cutout (additional space if present)
        val cutoutHeight = displayCutout?.safeInsetTop ?: 0
        
        // Navigation bar (20-48dp depending on gesture/button nav)
        val navBarHeight = systemBars.bottom
        
        // Lock screen clock zone (estimate - varies by OEM)
        // Typically 80-140dp from top of safe area
        val clockZoneHeight = (120 * density).toInt()
        
        return SafeZoneInsets(
            top = maxOf(statusBarHeight, cutoutHeight),
            clockZone = clockZoneHeight,
            bottom = navBarHeight,
            left = systemBars.left,
            right = systemBars.right
        )
    }
    
    // Content should not be placed in these zones
    fun getAvoidZones(screenHeight: Int, insets: SafeZoneInsets): List<Zone> {
        return listOf(
            Zone("status_bar", 0, insets.top),
            Zone("clock_area", insets.top, insets.top + insets.clockZone),
            Zone("nav_bar", screenHeight - insets.bottom, screenHeight)
        )
    }
}
```

## 2.3 Lock Screen Clock Variations by OEM

Different manufacturers render the lock screen clock differently:

| OEM | Clock Position | Clock Height | Notes |
|-----|----------------|--------------|-------|
| Stock Android / Pixel | Top center | ~100dp | Large time, small date below |
| Samsung One UI | Top left or center | ~120dp | Customizable position |
| Xiaomi MIUI | Top center | ~90dp | Compact style |
| OnePlus OxygenOS | Top center | ~110dp | Medium size |
| OPPO ColorOS | Top left | ~100dp | Date beside time |

**Our Strategy:** Reserve 140dp from top of safe area for clock zone (covers all cases).

## 2.4 Responsive Layout Grid

```
Screen Width: 100%
Margins: 24dp left/right (compact) | 32dp left/right (large)
Gutter: 16dp between elements

┌────────────────────────────────────────────────────────────┐
│ 24dp │                    CONTENT                    │ 24dp │
├────────────────────────────────────────────────────────────┤
│      │ ┌────────────────────────────────────────────┐ │      │
│      │ │                                            │ │      │
│      │ │          FULL-WIDTH ELEMENT                │ │      │
│      │ │                                            │ │      │
│      │ └────────────────────────────────────────────┘ │      │
│      │                    16dp                        │      │
│      │ ┌──────────────────┐ 16dp ┌──────────────────┐ │      │
│      │ │   HALF-WIDTH     │      │   HALF-WIDTH     │ │      │
│      │ └──────────────────┘      └──────────────────┘ │      │
└────────────────────────────────────────────────────────────┘
```

**Breakpoints:**

| Screen Width | Category | Margins | Max Content Width |
|--------------|----------|---------|-------------------|
| < 360dp | Compact | 16dp | 100% - 32dp |
| 360-400dp | Standard | 24dp | 100% - 48dp |
| 400-600dp | Large | 32dp | 100% - 64dp |
| > 600dp | XLarge/Tablet | 48dp | 504dp (capped) |

---

# PART 3: LAYOUT ZONES (Percentage-Based)

## 3.1 Vertical Zone Distribution

Using percentages ensures consistent layout across all screen sizes:

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│                      SYSTEM ZONE                             │  ~8%
│                 (status bar + cutout)                        │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                      CLOCK ZONE                              │  ~12%
│                 (lock screen clock)                          │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                                                              │
│                                                              │
│                     SCENE ZONE                               │  ~40%
│                 (pure wallpaper beauty)                      │
│                 (garden elements live here)                  │
│                                                              │
│                                                              │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│ ░░░░░░░░░░░░░░ TRANSITION GRADIENT ░░░░░░░░░░░░░░░░░░░░░░░░ │  ~5%
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                      TASK ZONE                               │  ~28%
│                 (task list/one thing)                        │
│                 (readable, semi-transparent bg)              │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│                   INTERACTION ZONE                           │  ~7%
│                 (quick add, done for today)                  │
│                                                              │
├──────────────────────────────────────────────────────────────┤
│▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ NAVIGATION ZONE ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓│  ~5%
└──────────────────────────────────────────────────────────────┘

TOTAL: 100%
```

## 3.2 Zone Calculations

```kotlin
data class LayoutZones(
    val systemZone: ZoneRect,
    val clockZone: ZoneRect,
    val sceneZone: ZoneRect,
    val transitionZone: ZoneRect,
    val taskZone: ZoneRect,
    val interactionZone: ZoneRect,
    val navigationZone: ZoneRect
)

fun calculateZones(screenHeight: Int, safeInsets: SafeZoneInsets): LayoutZones {
    // Fixed zones (system-dependent)
    val systemZoneHeight = safeInsets.top
    val navZoneHeight = safeInsets.bottom
    
    // Available height for our content
    val availableHeight = screenHeight - systemZoneHeight - navZoneHeight
    
    // Percentage-based zones
    val clockZoneHeight = (availableHeight * 0.12f).toInt()
    val sceneZoneHeight = (availableHeight * 0.40f).toInt()
    val transitionZoneHeight = (availableHeight * 0.05f).toInt()
    val taskZoneHeight = (availableHeight * 0.28f).toInt()
    val interactionZoneHeight = (availableHeight * 0.07f).toInt()
    
    // Calculate Y positions
    var currentY = systemZoneHeight
    
    val clockZone = ZoneRect(0, currentY, screenWidth, currentY + clockZoneHeight)
    currentY += clockZoneHeight
    
    val sceneZone = ZoneRect(0, currentY, screenWidth, currentY + sceneZoneHeight)
    currentY += sceneZoneHeight
    
    val transitionZone = ZoneRect(0, currentY, screenWidth, currentY + transitionZoneHeight)
    currentY += transitionZoneHeight
    
    val taskZone = ZoneRect(0, currentY, screenWidth, currentY + taskZoneHeight)
    currentY += taskZoneHeight
    
    val interactionZone = ZoneRect(0, currentY, screenWidth, currentY + interactionZoneHeight)
    
    return LayoutZones(
        systemZone = ZoneRect(0, 0, screenWidth, systemZoneHeight),
        clockZone = clockZone,
        sceneZone = sceneZone,
        transitionZone = transitionZone,
        taskZone = taskZone,
        interactionZone = interactionZone,
        navigationZone = ZoneRect(0, screenHeight - navZoneHeight, screenWidth, screenHeight)
    )
}
```

## 3.3 Device-Specific Examples

### Standard Phone (1080 x 2400, 20:9)

```
Total Height: 2400px
Status Bar: 72px (3%)
Nav Bar: 48px (2%)
Available: 2280px

┌─────────────────────────────────────┐
│          Status Bar (72px)          │
├─────────────────────────────────────┤
│                                     │
│        Clock Zone (274px)           │  12%
│                                     │
├─────────────────────────────────────┤
│                                     │
│                                     │
│                                     │
│        Scene Zone (912px)           │  40%
│                                     │
│                                     │
│                                     │
├─────────────────────────────────────┤
│      Transition (114px)             │  5%
├─────────────────────────────────────┤
│                                     │
│        Task Zone (638px)            │  28%
│                                     │
├─────────────────────────────────────┤
│      Interaction Zone (160px)       │  7%
├─────────────────────────────────────┤
│          Nav Bar (48px)             │
└─────────────────────────────────────┘
```

### Compact Phone (1080 x 2160, 18:9)

```
Total Height: 2160px
Status Bar: 66px
Nav Bar: 48px
Available: 2046px

┌─────────────────────────────────────┐
│          Status Bar (66px)          │
├─────────────────────────────────────┤
│        Clock Zone (246px)           │  12%
├─────────────────────────────────────┤
│                                     │
│        Scene Zone (818px)           │  40%
│                                     │
├─────────────────────────────────────┤
│      Transition (102px)             │  5%
├─────────────────────────────────────┤
│                                     │
│        Task Zone (573px)            │  28%
│                                     │
├─────────────────────────────────────┤
│      Interaction Zone (143px)       │  7%
├─────────────────────────────────────┤
│          Nav Bar (48px)             │
└─────────────────────────────────────┘
```

### Foldable Inner (1812 x 2176, ~6:5)

```
Total Height: 2176px
Status Bar: 72px
Nav Bar: 48px
Available: 2056px

Note: Wider aspect ratio means more horizontal space.
Consider two-column layout for foldables when unfolded.
```

---

# PART 4: TYPOGRAPHY SYSTEM

## 4.1 Type Scale

Based on Material Design 3, optimized for lock screen readability:

| Token | Size (sp) | Line Height | Weight | Tracking | Use |
|-------|-----------|-------------|--------|----------|-----|
| display-large | 32 | 40 | 400 | -0.25 | Celebration text |
| display-medium | 28 | 36 | 400 | 0 | "Done for Today" |
| headline-large | 24 | 32 | 600 | 0 | One Thing task title |
| headline-medium | 20 | 28 | 500 | 0 | Section headers |
| title-large | 18 | 26 | 500 | 0 | Task title (list mode) |
| title-medium | 16 | 24 | 500 | 0.15 | Secondary task info |
| body-large | 16 | 24 | 400 | 0.5 | Descriptions |
| body-medium | 14 | 20 | 400 | 0.25 | Time estimates |
| label-large | 14 | 20 | 500 | 0.1 | Buttons, chips |
| label-medium | 12 | 16 | 500 | 0.5 | Labels, captions |
| label-small | 10 | 14 | 500 | 0.5 | "+N more", hints |

## 4.2 Font Family

```kotlin
object Typography {
    
    // Primary font - clean, readable
    val fontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold)
    )
    
    // Fallback to system font
    val systemFallback = FontFamily.SansSerif
}
```

## 4.3 Responsive Typography

Font sizes should scale with device density but have min/max bounds:

```kotlin
fun calculateFontSize(baseSp: Float, density: Float, screenWidth: Int): Float {
    // Scale factor based on screen width
    val scaleFactor = when {
        screenWidth < 360 -> 0.9f    // Compact: slightly smaller
        screenWidth < 400 -> 1.0f    // Standard: base size
        screenWidth < 600 -> 1.05f   // Large: slightly larger
        else -> 1.1f                  // XLarge: larger
    }
    
    val scaledSize = baseSp * scaleFactor
    
    // Enforce min/max bounds
    val minSize = baseSp * 0.85f
    val maxSize = baseSp * 1.2f
    
    return scaledSize.coerceIn(minSize, maxSize)
}
```

## 4.4 Text Rendering for Readability

```kotlin
object TextRenderer {
    
    fun createTaskTextPaint(
        textSize: Float,
        color: Int,
        glowColor: Int,
        weight: FontWeight = FontWeight.Medium
    ): List<Paint> {
        
        // Layer 1: Glow/shadow for contrast
        val glowPaint = Paint().apply {
            this.color = glowColor
            this.textSize = textSize
            this.typeface = Typeface.create(Typography.fontFamily, weight.weight)
            this.isAntiAlias = true
            this.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        
        // Layer 2: Subtle shadow for depth
        val shadowPaint = Paint().apply {
            this.color = Color.BLACK
            this.alpha = 80
            this.textSize = textSize
            this.typeface = Typeface.create(Typography.fontFamily, weight.weight)
            this.isAntiAlias = true
            this.setShadowLayer(4f, 2f, 2f, Color.argb(100, 0, 0, 0))
        }
        
        // Layer 3: Main text
        val mainPaint = Paint().apply {
            this.color = color
            this.textSize = textSize
            this.typeface = Typeface.create(Typography.fontFamily, weight.weight)
            this.isAntiAlias = true
            this.isSubpixelText = true
        }
        
        return listOf(glowPaint, shadowPaint, mainPaint)
    }
    
    fun drawTextWithGlow(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paints: List<Paint>
    ) {
        // Draw in order: glow, shadow, main
        paints.forEach { paint ->
            canvas.drawText(text, x, y, paint)
        }
    }
}
```

---

# PART 5: COLOR SYSTEM

## 5.1 Cosmic Theme Palette

| Token | Calm | Attention | Urgent | Critical | Done |
|-------|------|-----------|--------|----------|------|
| bg-primary | #0A1628 | #14102A | #1E0A32 | #280A14 | #0A1E14 |
| bg-secondary | #1A0A28 | #2A1432 | #2A1A14 | #3A0A0A | #143A28 |
| accent-primary | #6B8AFF | #9B7AFF | #FFB74D | #FF5252 | #4CAF50 |
| accent-secondary | #4A6FE3 | #7B5ADF | #FF9800 | #FF1744 | #81C784 |
| glow | #6B8AFF40 | #9B7AFF50 | #FFB74D60 | #FF525270 | #4CAF5050 |
| particle | #FFFFFF | #E0CFFF | #FFE0B2 | #FFCDD2 | #C8E6C9 |
| text-primary | #FFFFFF | #FFFFFF | #FFFFFF | #FFFFFF | #FFFFFF |
| text-secondary | #FFFFFFB3 | #FFFFFFB3 | #FFFFFFCC | #FFFFFFE6 | #FFFFFFB3 |

## 5.2 Ocean Theme Palette

| Token | Calm | Attention | Urgent | Critical | Done |
|-------|------|-----------|--------|----------|------|
| bg-primary | #0A2832 | #0A3246 | #1E3A3A | #2A1E28 | #1E3228 |
| bg-secondary | #143C4A | #1E4A5A | #2A4A46 | #3A2832 | #284A32 |
| accent-primary | #4DD0E1 | #00BCD4 | #FFB74D | #FF7043 | #66BB6A |
| accent-secondary | #26C6DA | #00ACC1 | #FFA726 | #FF5722 | #81C784 |
| glow | #4DD0E140 | #00BCD450 | #FFB74D60 | #FF704370 | #66BB6A50 |
| particle | #E0F7FA | #B2EBF2 | #FFE0B2 | #FFCCBC | #C8E6C9 |
| text-primary | #FFFFFF | #FFFFFF | #FFFFFF | #FFFFFF | #FFFFFF |
| text-secondary | #FFFFFFB3 | #FFFFFFB3 | #FFFFFFCC | #FFFFFFE6 | #FFFFFFB3 |

## 5.3 Color Transition System

```kotlin
object ColorTransition {
    
    // Transition between urgency states over 30 seconds
    private const val TRANSITION_DURATION_MS = 30000L
    
    fun lerpColor(from: Int, to: Int, progress: Float): Int {
        val fromA = Color.alpha(from)
        val fromR = Color.red(from)
        val fromG = Color.green(from)
        val fromB = Color.blue(from)
        
        val toA = Color.alpha(to)
        val toR = Color.red(to)
        val toG = Color.green(to)
        val toB = Color.blue(to)
        
        return Color.argb(
            (fromA + (toA - fromA) * progress).toInt(),
            (fromR + (toR - fromR) * progress).toInt(),
            (fromG + (toG - fromG) * progress).toInt(),
            (fromB + (toB - fromB) * progress).toInt()
        )
    }
}
```

## 5.4 Contrast Requirements (WCAG)

| Text Type | Minimum Ratio | Our Implementation |
|-----------|---------------|-------------------|
| Primary text | 4.5:1 | White (#FFFFFF) on 50% black overlay = 10.5:1 ✓ |
| Secondary text | 3:1 | White 70% on 50% black overlay = 7.4:1 ✓ |
| Large text (24sp+) | 3:1 | Always exceeds ✓ |

---

# PART 6: SPACING SYSTEM

## 6.1 Spacing Scale (4dp Base)

| Token | Value | Use |
|-------|-------|-----|
| space-1 | 4dp | Hairline gaps |
| space-2 | 8dp | Tight spacing, icon margins |
| space-3 | 12dp | Related element spacing |
| space-4 | 16dp | Standard padding |
| space-5 | 20dp | Medium gaps |
| space-6 | 24dp | Section margins |
| space-8 | 32dp | Large gaps |
| space-12 | 48dp | Major sections |

## 6.2 Component Spacing

### Task Row

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  16dp  ┌────┐  12dp  ┌──────────────────────┐  8dp  ┌──────┐  16dp │
│        │ ●  │        │    Task title...     │       │~20m  │       │
│        │    │        │                      │       │      │       │
│        └────┘        └──────────────────────┘       └──────┘       │
│        24x24                                         auto           │
│                                                                      │
│  Height: 56dp                                                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### One Thing Mode

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│                            24dp                                      │
│                        RIGHT NOW                                     │
│                           16dp                                       │
│                     Submit report                                    │
│                           12dp                                       │
│                         ~20 min                                      │
│                           24dp                                       │
│                          [✓]                                         │
│                        (48x48)                                       │
│                           24dp                                       │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

# PART 7: COMPONENT SPECIFICATIONS

## 7.1 Checkbox Component

**Touch Target: 48dp x 48dp (minimum)**
**Visual Size: 24dp diameter**

```
UNCHECKED:          FILLED (urgent):      CHECKED:
   ○                    ●                    ●✓
   │                    │                    │
2dp stroke          Fill + glow          Fill + checkmark
text-secondary      accent + 8dp blur    accent + white ✓
```

## 7.2 Chip Component

**Height: 36dp**
**Padding: 12dp horizontal**
**Corner Radius: 18dp (full round)**

```
UNSELECTED:                    SELECTED:
┌─────────────────────┐       ┌─────────────────────┐
│   ○   Today        │       │   ●   Today        │
└─────────────────────┘       └─────────────────────┘
Border: 1dp, 30% white         Background: accent 20%
                               Border: 2dp, accent
```

## 7.3 Quick Add Button

**Height: 48dp**
**Corner Radius: 24dp**

```
COLLAPSED:
┌──────────────────────────────────────────────────────────────┐
│  16dp  [+]  8dp  Add task...                          16dp  │
└──────────────────────────────────────────────────────────────┘

EXPANDED:
┌──────────────────────────────────────────────────────────────┐
│  ┌──────────────────────────────────────────────────────┐   │
│  │ What needs doing?                                    │   │
│  └──────────────────────────────────────────────────────┘   │
│  When?  [Today] [Tomorrow] [This week] [Someday]            │
│  How long?  [~5m] [~15m] [~30m] [~1h] [~2h+]               │
└──────────────────────────────────────────────────────────────┘
Animation: 250ms ease-out, expand from bottom
```

---

# PART 8: ANIMATION SPECIFICATIONS

## 8.1 Timing Curves

```kotlin
object AnimationCurves {
    val standard = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    val accelerate = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
    val decelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val breathe = CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)
    val bounce = CubicBezierEasing(0.68f, -0.55f, 0.265f, 1.55f)
}
```

## 8.2 Duration Standards

| Animation Type | Duration | Easing | Use |
|----------------|----------|--------|-----|
| Micro-interaction | 100ms | standard | Tap feedback |
| Button state | 150ms | decelerate | Press/release |
| Checkbox fill | 200ms | bounce | Task completion |
| Content reveal | 250ms | decelerate | Quick add expand |
| Task slide | 400ms | decelerate | List reorder |
| Celebration (small) | 1000ms | custom | Shooting star |
| Celebration (large) | 2500ms | custom | Whale breach |
| Color transition | 30000ms | linear | Urgency shift |
| Breathing | 1500-10000ms | sine | Background pulse |

## 8.3 Breathing Animation

```kotlin
fun calculateBreathPhase(timeMs: Long, cycleDurationMs: Long): Float {
    val phase = (timeMs % cycleDurationMs) / cycleDurationMs.toFloat()
    return sin(phase * 2f * PI.toFloat()) * 0.5f + 0.5f  // 0 to 1
}

fun calculateScale(phase: Float, intensity: Float): Float {
    // intensity: 0.02 (calm) to 0.08 (critical)
    return 1f + (phase - 0.5f) * 2f * intensity
}
```

---

# PART 9: PARTICLE SPECIFICATIONS

## 9.1 Particle Parameters by Urgency

### Stars (Cosmic Theme)

| Parameter | Calm | Attention | Urgent | Critical |
|-----------|------|-----------|--------|----------|
| Count | 30 | 50 | 70 | 100 |
| Size range | 1-3dp | 2-4dp | 2-5dp | 3-6dp |
| Twinkle speed | 3-5s | 2-4s | 1-3s | 0.5-2s |
| Drift speed | 0.5dp/s | 1dp/s | 2dp/s | 3dp/s |

### Bubbles (Ocean Theme)

| Parameter | Calm | Attention | Urgent | Critical |
|-----------|------|-----------|--------|----------|
| Count | 20 | 35 | 50 | 80 |
| Size range | 4-12dp | 6-16dp | 8-20dp | 10-24dp |
| Rise speed | 20dp/s | 35dp/s | 50dp/s | 70dp/s |
| Wobble | 5dp | 8dp | 12dp | 16dp |

## 9.2 Particle Zone Weights

Particles less dense in task area to maintain readability:

| Zone | Particle Density |
|------|------------------|
| Clock zone | 30% |
| Scene zone | 100% |
| Transition | 50% |
| Task zone | 20% |
| Interaction | 10% |

---

# PART 10: ACCESSIBILITY

## 10.1 Touch Targets

| Element | Minimum | Our Size |
|---------|---------|----------|
| Checkbox | 48dp | 48dp ✓ |
| Task row | 48dp height | 56dp ✓ |
| Quick add | 48dp height | 48dp ✓ |
| Chips | 48dp | 48dp (with padding) ✓ |

## 10.2 Screen Reader Support

```kotlin
@Composable
fun TaskRow(task: Task, onComplete: () -> Unit) {
    Row(
        modifier = Modifier.semantics {
            contentDescription = buildString {
                append("Task: ${task.title}. ")
                if (task.isOverdue) append("Overdue. ")
                task.dueDate?.let { append("Due ${formatDueDate(it)}. ") }
                append("Double tap to mark complete.")
            }
        }
    )
}
```

## 10.3 Reduced Motion

```kotlin
fun shouldReduceMotion(context: Context): Boolean {
    return Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
}
```

---

# PART 11: PERFORMANCE

## 11.1 Frame Rate Targets

| Device Tier | Normal | Battery Saver |
|-------------|--------|---------------|
| High-end | 60 FPS | 30 FPS |
| Mid-range | 30 FPS | 20 FPS |
| Low-end | 20 FPS | 15 FPS |

## 11.2 Memory Budget

| Asset Type | Budget |
|------------|--------|
| Background bitmap | 10 MB |
| Particle textures | 2 MB |
| Celebration sprites | 5 MB (load on demand) |
| **Total runtime** | **50 MB target** |

---

# PART 12: TESTING CHECKLIST

## Device Matrix (Minimum)

| Category | Device | Resolution |
|----------|--------|------------|
| Compact | Pixel 4a | 1080x2340 |
| Standard | Pixel 7 | 1080x2400 |
| Large | Pixel 7 Pro | 1440x3120 |
| Budget | Galaxy A13 | 1080x2408 |
| Foldable | Galaxy Fold | 1812x2176 |
| Notch | Pixel 3 XL | 1440x2960 |

## Visual Checks

- [ ] Task text never overlaps system clock
- [ ] Task text never hidden by nav bar
- [ ] All text readable on all urgency states
- [ ] Particles don't obscure task text
- [ ] Animations smooth (no jank)
- [ ] Quick add keyboard doesn't cover input

---

# QUICK REFERENCE

## Safe Zone Template
```
Status Bar:     24-48dp (use WindowInsets)
Clock Zone:     140dp reserved
Scene Zone:     40% of available
Transition:     5% of available
Task Zone:      28% of available
Interaction:    7% of available
Nav Bar:        20-48dp (use WindowInsets)
```

## Typography Quick Reference
```
Task Title (List):    18sp, Medium
Task Title (One):     24sp, SemiBold
Time Estimate:        14sp, Regular
Labels:               12sp, Medium
```

## Color Quick Reference (Cosmic)
```
Calm Background:      #0A1628
Urgent Background:    #1E0A32
Critical Background:  #280A14
Accent (Calm):        #6B8AFF
Accent (Urgent):      #FFB74D
Accent (Critical):    #FF5252
```

## Animation Quick Reference
```
Micro:        100ms
Standard:     200-300ms
Content:      400ms
Celebration:  1000-2500ms
Breathing:    1500-10000ms
Color shift:  30000ms
```

---

*This document ensures visual consistency across all Android devices.*
*Version: 1.0*
*Last updated: December 2024*
