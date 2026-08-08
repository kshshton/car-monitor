# 3D Car Configurator - Copilot Instructions

## Project Overview
A **3D Car Configurator** Android app for monitoring environmental sensors integrated into a vehicle. Built with Kotlin, Jetpack Compose, and Sceneview, it provides an interactive 3D visualization of where 6 different environmental sensors are located and what they monitor. Users select sensors from a bottom menu, triggering smooth camera animations to frame each sensor location and visual highlighting on the 3D model.

## Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **3D Engine**: Sceneview (Google Filament-based, supports .glb/.gltf models)
- **Architecture**: MVVM (Model-View-ViewModel) + StateFlow
- **Min API**: TBD (define in build.gradle.kts; typically 26+ for Compose support)

## Architecture Overview

```
┌─────────────────────────────────────────────┐
│  3D Model (.glb) - Loaded from assets/      │
│  └─ Nodes: Engine_Mesh, Suspension_Front... │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│  ViewModel (StateFlow)                      │
│  • selectedPart: StateFlow<String>           │
│  • cameraTarget: StateFlow<Vec3>             │
│  • cameraPos: StateFlow<Vec3>                │
│  • Logic: Lerp interpolation, material props │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│  Compose UI                                 │
│  ┌──────────────────────────────────────┐   │
│  │ Sceneview Composable (3D Renderer)   │   │
│  │ • Renders car model                  │   │
│  │ • onFrame: updates camera via Lerp   │   │
│  │ • Emissive material on selected node │   │
│  └──────────────────────────────────────┘   │
│  ┌──────────────────────────────────────┐   │
│  │ LazyRow (UI Overlay)                 │   │
│  │ [Engine] [Suspension] [Wheels] [Body]│   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

## Core Functional Requirements

1. **3D Model Loading**
   - Load compressed `.glb` file from `assets/` directory (car with 6 sensor locations)
   - Parse and identify sensor mesh nodes by name (e.g., "Air_Filter_Mesh", "Dashboard_Vent_Mesh")
   - Cache model to avoid repeated loads

2. **Sensor Selection (UI)**
   - Bottom menu (LazyRow) with buttons for each of 6 environmental sensors
   - Button click updates `selectedPartId` StateFlow in ViewModel with sensor ID
   - UI reflects selection state with visual highlighting
   - Reference: complete sensor list in repository sensor definitions

3. **Camera Animation**
   - Smooth camera position interpolation (Lerp) when user selects a sensor
   - Each sensor has a predefined camera position and look-at point (from repository sensor definitions)
   - Animate over ~400ms using smooth easing (cubic ease-out)
   - Position data includes exact coordinates for all 6 sensors

4. **Sensor Highlighting**
   - Modify selected sensor node's material: adjust EmissiveFactor/EmissiveColor
   - Visual feedback shows which sensor location is currently being viewed
   - Restore original material when deselected

## Code Standards & Patterns

### State Management
```kotlin
// Prefer StateFlow over mutableStateOf for ViewModel state
val selectedPart: StateFlow<String> = _selectedPart.asStateFlow()
private val _selectedPart = MutableStateFlow("")

// Compose observes StateFlow with collectAsState()
val selectedPart by viewModel.selectedPart.collectAsState()
```

### 3D Calculations
- Always comment **3D position/camera math**: specify coordinate system, interpolation details
- Use `Float3` for positions, document world-space vs. local-space conversions
- Lerp formula: `current = start + (target - start) * t` where `t ∈ [0, 1]`

### Naming Conventions
- **ViewModel properties**: `selectedPartId`, `cameraPos`, `cameraTarget` (camelCase, descriptive)
- **Composables**: `CarConfiguratorScreen`, `SensorButton` (PascalCase)
- **3D Node references**: Match model export names exactly (e.g., "Air_Filter_Mesh", "Dashboard_Vent_Mesh")
- **Sensor IDs**: Match sensor model codes (e.g., "pms5003_01", "ens160_01", "scd41_01")
- **Material/Color variables**: `highlightEmissiveColor`, `defaultMaterial` (clear intent)

### Compose Structure
- Extract complex Composables (>150 lines) into separate functions
- Use `remember { }` for expensive object creation (e.g., camera interpolation state)
- Hoist state to ViewModel for cross-screen concerns

## Code Examples

### ViewModel Setup with Camera State
```kotlin
class CarConfigViewModel : ViewModel() {
    private val _selectedPartId = MutableStateFlow("")
    val selectedPartId: StateFlow<String> = _selectedPartId.asStateFlow()
    
    private val _cameraPos = MutableStateFlow(Position(x = 0f, y = 1.5f, z = 2f))
    val cameraPos: StateFlow<Position> = _cameraPos.asStateFlow()
    
    private val _cameraTarget = MutableStateFlow(Position(x = 0f, y = 1f, z = 0f))
    val cameraTarget: StateFlow<Position> = _cameraTarget.asStateFlow()
    
    fun selectPart(part: CarPart) {
        _selectedPartId.value = part.id
        viewModelScope.launch {
            animateCamera(part.cameraPosition, part.cameraTarget)
        }
    }
    
    private suspend fun animateCamera(target: Position, lookAt: Position) {
        val startPos = _cameraPos.value
        val startTarget = _cameraTarget.value
        val durationMs = 400
        val startTime = System.currentTimeMillis()
        
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            
            // Smooth easing: cubic ease-out
            val eased = 1f - (1f - t).let { it * it * it }
            
            _cameraPos.value = Position(
                x = startPos.x + (target.x - startPos.x) * eased,
                y = startPos.y + (target.y - startPos.y) * eased,
                z = startPos.z + (target.z - startPos.z) * eased
            )
            _cameraTarget.value = Position(
                x = startTarget.x + (lookAt.x - startTarget.x) * eased,
                y = startTarget.y + (lookAt.y - startTarget.y) * eased,
                z = startTarget.z + (lookAt.z - startTarget.z) * eased
            )
            
            if (t >= 1f) break
            delay(16) // ~60fps
        }
    }
}
```

### SceneView Composable with Material Highlighting
```kotlin
@Composable
fun CarConfiguratorScreen(viewModel: CarConfigViewModel) {
    val selectedPartId by viewModel.selectedPartId.collectAsState()
    val cameraPos by viewModel.cameraPos.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()
    
    var sceneView by remember { mutableStateOf<SceneView?>(null) }
    var carModel by remember { mutableStateOf<ModelInstance?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 3D Sceneview - renders the car with sensors
        SceneViewComposable(
            modifier = Modifier.fillMaxSize(),
            onSceneViewCreated = { sv ->
                sceneView = sv
                // Load car model from assets
                sv.loadModel("models/car_model.glb") { model ->
                    carModel = model
                }
            }
        )
        
        // Update camera position based on selected sensor
        LaunchedEffect(cameraPos, cameraTarget) {
            sceneView?.onFrame = {
                sceneView?.cameraNode?.apply {
                    position = cameraPos.toFloat3()
                    lookAt(cameraTarget.toFloat3())
                }
            }
        }
        
        // Highlight selected sensor node
        LaunchedEffect(selectedPartId) {
            carModel?.let { model ->
                // Clear previous highlights
                model.getChildren<Node>().forEach { node ->
                    restoreMaterial(node)
                }
                
                // Find and highlight selected sensor
                carParts.find { it.id == selectedPartId }?.let { part ->
                    val sensorNode = model.getChildByName(part.nodeName)
                    sensorNode?.let { node ->
                        applyHighlightMaterial(node)
                    }
                }
            }
        }
        
        // Sensor selector UI overlay (bottom)
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(carParts) { part ->
                SensorButton(
                    part = part,
                    isSelected = selectedPartId == part.id,
                    onClick = {
                        viewModel.selectPart(part)
                    }
                )
            }
        }
    }
}

private fun Position.toFloat3() = Float3(x, y, z)

private fun applyHighlightMaterial(node: Node) {
    node.renderableInstance?.material?.let { material ->
        material.setParameter("emissiveFactor", FloatArray(4) { 1f })
        material.setParameter("emissiveColor", Color(0xFFFF6B6B)) // Red highlight
    }
}

private fun restoreMaterial(node: Node) {
    node.renderableInstance?.material?.let { material ->
        material.setParameter("emissiveFactor", FloatArray(4) { 0f })
    }
}
```

### Part Definition Data Class
```kotlin
data class CarPart(
    val id: String,                 // Unique identifier (sensor model ID)
    val displayName: String,
    val nodeName: String,           // Must match 3D model node name exactly
    val cameraPosition: Position,   // Camera position to frame this part
    val cameraTarget: Position      // Where camera looks at
)

data class Position(val x: Float, val y: Float, val z: Float)
```

**Car Parts Reference** (defined in repository sensor definitions):
The app configures monitoring for 6 environmental sensors, each mapped to specific car locations:

| Sensor | Display Name | Node Name | Purpose |
|--------|--------------|-----------|---------|
| PMS5003 | Air & Particulate Filter | `Air_Filter_Mesh` | Air quality, particulate matter |
| ENS160 | VOC & eCO2 Sensor | `Dashboard_Vent_Mesh` | Volatile organic compounds, CO2 equiv. |
| SCD41 | Cabin CO2 Sensor | `Center_Console_Mesh` | CO2 levels in cabin |
| SEN0441 | Formaldehyde Sensor | `Rear_Seats_Mesh` | Off-gassing detection |
| BME280 | Barometric & Temp Sensor | `Engine_Manifold_Mesh` | Pressure, temp in engine bay |
| DS18B20 | Outdoor Temp Sensor | `Front_Bumper_Radiator_Mesh` | External ambient temperature |

See repository sensor definitions for complete camera position coordinates for each part.

## Development Workflow

**Typical Feature Implementation Flow**:

1. **Adding a New Sensor**
   - Define `CarPart` in `model/CarPart.kt` with exact 3D node name
   - Add button to `SensorButton` LazyRow in `CarConfiguratorScreen.kt`
   - Add animation target (camera position + lookAt) to `CarConfigViewModel.animateCamera()`
   - Test on device to verify model node name is correct

2. **Fixing Camera Animation**
   - Edit interpolation math in `CarConfigViewModel.animateCamera()`; test with `./gradlew testDebug`
   - Verify easing function is smooth; if jerky, check that animation duration is >300ms
   - Use Device Frame Rate Inspection in Android Profiler to detect 60 fps drops during animation

3. **Highlighting Logic Changes**
   - Modify material application in Composable or extract to utility function
   - Always test: (a) highlighting works, (b) previous highlight clears, (c) no memory leaks during repeated switches
   - Run Logcat + Memory Profiler to verify material instances are released

4. **Model/Asset Updates**
   - Export new `.glb` with meaningful node names (no spaces, use underscores)
   - Replace `assets/models/car_model.glb` with new version
   - Run app, print node names to verify they match `CarPart` definitions
   - If node names changed, update all `CarPart.nodeName` values

**PRs & Code Review**:
- Always include a brief description of 3D coordinate math if modifying camera logic
- Attach before/after screenshots/GIFs of sensor highlighting behavior
- Request review from someone who tested on a physical device (emulator gaps can hide bugs)

### 3D Rendering
- **Model loading**: Load `.glb` model once in `remember { }` block, not on every recomposition
- **Node hierarchy caching**: Cache `model.getChildren()` lookups to avoid repeated traversal
- **Material mutations**: Reuse material instances instead of creating new ones; clone only when necessary
- **Texture memory**: Use compressed textures in `.glb`; check that model file size is <10MB
- **LOD (Level of Detail)**: If model is complex, consider using Sceneview's built-in LOD support

### Compose Updates
- **Collect StateFlow efficiently**: Use `collectAsState()` only in Composables; avoid collecting in LaunchedEffect without guard
- **Camera updates**: Batch camera position + target updates in a single ViewModel state if possible
- **Recomposition scope**: Keep `SceneView { }` Composable separate from heavy UI; minimize shared state
- **Remember expensive objects**: Use `remember { }` for SceneView instance, camera interpolator

### Memory
- **Asset loading**: Don't load models in init; defer to user action or LaunchedEffect
- **Material cleanup**: Dispose old material instances when switching parts to avoid leaks
- **Coroutine scope**: Always use `viewModelScope.launch()` for ViewModel coroutines; avoid GlobalScope

## Build & Test Commands

**When build.gradle.kts is created, use these commands:**
- **Build debug APK**: `./gradlew assembleDebug`
- **Run all tests**: `./gradlew testDebug`
- **Run single test file**: `./gradlew testDebugUnitTest --tests com.example.carconfigurator.viewmodel.CarConfigViewModelTest`
- **Run UI/instrumentation tests**: `./gradlew connectedAndroidTest`
- **Run lint**: `./gradlew lint`
- **Build release APK**: `./gradlew assembleRelease`
- **Install and run on device**: `./gradlew installDebug && adb shell am start -n com.example.carconfigurator/.MainActivity`
- **Continuous build/test**: `./gradlew testDebug --watch` (if using AGP 8.1+)

## Testing Strategy

**Unit Tests** (ViewModel, data models, camera math):
- Place in `app/src/test/java/` 
- Test `CarConfigViewModel` camera interpolation with various easing functions
- Mock Sceneview interactions; avoid rendering in unit tests
- Test sensor-to-mesh node mappings with hardcoded test fixtures

**Instrumentation Tests** (Compose UI, Sceneview rendering):
- Place in `app/src/androidTest/java/`
- Use Compose testing (androidx.compose.ui:ui-test-junit4)
- Verify sensor button clicks update ViewModel state
- Test 3D model loading and material highlighting (requires emulator or device)
- Use test fixtures for `.glb` model to avoid asset bloat

**Manual Testing Checklist**:
1. Launch app and verify car model loads without freezing
2. Click each sensor button; confirm smooth camera animation (~400ms)
3. Verify selected sensor node highlights with correct material/color
4. Verify previous sensor highlight is cleared when selecting new one
5. Test on minimum API level target device

## Critical Files & Patterns

**Must-Watch Files** (highest-velocity, most error-prone):
- `CarConfigViewModel.kt` — Camera animation logic; bugs here block UI
- `CarConfiguratorScreen.kt` — Sceneview integration; easy to break rendering pipeline
- `CarPart.kt` (or constants file) — Sensor-to-mesh mappings must match `.glb` node names exactly; typos cause silent failures
- Material application logic — Ensure materials are cloned before mutation; original materials can affect other nodes

**Key Patterns**:
- All 3D math is Float-based; use `Float3` consistently, not separate x/y/z parameters
- Camera animations must clamp interpolation `t` to [0, 1] using `coerceIn()` to prevent overshoot
- Sceneview model loading is async; always check for null before accessing model nodes
- StateFlow emissions in ViewModel trigger Compose recomposition; keep ViewModel state minimal to avoid excessive redraws

## Key Dependencies to Expect
- `io.github.sceneview:sceneview` (or latest version)
- `androidx.compose.ui:ui`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` (for StateFlow)
- `androidx.compose.foundation:foundation` (for LazyRow, etc.)

## Dependency & Version Management

**Important** — Before upgrading any dependency:
1. Check Sceneview GitHub releases for breaking changes (3D rendering APIs are volatile)
2. Verify Jetpack Compose version aligns with Android Gradle Plugin version (AGP 8.x → Compose 1.6+)
3. Test on emulator and device after major version bumps; Sceneview + Compose interactions can regress silently

**Kotlin Version Strategy**:
- Use latest stable Kotlin version compatible with Compose and Sceneview
- Kotlin coroutines must be >= 1.7 for StateFlow stability in recomposition

**BOM (Bill of Materials)** Approach (recommended):
```gradle.kts
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    // ...
}
```

## File Structure (when implemented)
```
app/
├── src/main/
│   ├── kotlin/com/example/carmonitor/
│   │   ├── ui/
│   │   │   ├── screen/CarConfiguratorScreen.kt
│   │   │   └── component/SensorButton.kt
│   │   ├── viewmodel/CarConfigViewModel.kt
│   │   ├── model/
│   │   │   └── CarPart.kt  # Sensor IDs, node names, camera positions
│   │   ├── util/
│   │   │   ├── CameraInterpolator.kt
│   │   │   └── EasingFunctions.kt
│   │   └── MainActivity.kt
│   ├── assets/
│   │   └── models/
│   │       └── car_model.glb  # Compressed 3D model with sensor mesh nodes
│   └── AndroidManifest.xml
├── src/test/java/  # Unit tests (ViewModel, camera math)
├── src/androidTest/java/  # Instrumentation tests (UI, rendering)
├── build.gradle.kts
├── settings.gradle.kts
├── .github/
│   ├── workflows/  # CI/CD (build, test on PR)
│   └── copilot-instructions.md
└── README.md
```

## Common Gotchas & Debugging

**Critical Issues** (highest impact, hardest to debug):

1. **3D Model Node Names Must Match Exactly**
   - If `.glb` node is named `Air_Filter_Mesh` but code looks for `air_filter_mesh`, the model loads but highlighting silently fails
   - Always print loaded node names during model init: `model.getChildren().forEach { println(it.name) }`
   - Keep a reference document of all node names in a constants file (e.g., `ModelNodeNames.kt`)

2. **Camera Animation Clipping**
   - Forget to `coerceIn(0f, 1f)` interpolation `t`? Camera overshoots and jumps off-screen
   - Always clamp: `val eased = (elapsed / durationMs).coerceIn(0f, 1f)`

3. **Material Mutation Affects All Instances**
   - Modifying `node.material` directly can affect sibling nodes if they share material references
   - Clone before mutation: `val newMaterial = node.material?.copy() ?: Material()` (API depends on Sceneview version)
   - Reset to original on deselection; don't leave highlighted nodes

4. **StateFlow Collection Deadlock**
   - Using `.collect()` in LaunchedEffect without proper scope can cause recomposition cycles
   - Always use `collectAsState()` in Composables: `val selected by viewModel.selectedPartId.collectAsState()`
   - If using LaunchedEffect, guard with a key: `LaunchedEffect(selectedPartId) { ... }`

5. **3D Model Coordinate System**
   - Verify `.glb` export coordinate system before hardcoding camera positions
   - Most exports default to Y-up (OpenGL); some are Z-up. Wrong system = wrong camera framing
   - Load model in preview tool (e.g., Babylon.js Viewer) to confirm axis orientation

6. **Asset Packaging & Loading**
   - `.glb` must be in `src/main/assets/` (not `res/raw/`)
   - Sceneview may cache model; to force reload during dev: delete app data or restart process
   - Compressed `.glb` should be <10MB; if larger, strip unused data from 3D model source

7. **Emulator Performance**
   - Sceneview rendering is GPU-intensive; emulator can be slow or unstable
   - Test on physical device for accurate performance; emulator is fine for logic verification
   - If emulator crashes with Sceneview: reduce texture resolution in `.glb` or use software rendering

**Debugging Tips**:
- Add logging to `LaunchedEffect` blocks: Log which sensor was selected, interpolation `t` values, final camera position
- Use Android Profiler (Memory, GPU) to detect material/model leaks during repeated sensor switches
- Enable verbose Sceneview logging: `SceneView.LOG_LEVEL = Logger.DEBUG`
- Check Logcat for Filament (underlying 3D engine) errors; they appear as "Filament: ..." messages
