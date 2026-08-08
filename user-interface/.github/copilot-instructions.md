# 3D Car Configurator - Copilot Instructions

## Project Overview
A **3D Car Configurator** Android app built with Kotlin, Jetpack Compose, and Sceneview. Users interact with a 3D car model, selecting different parts (engine, suspension, wheels, body) with smooth camera animations and visual highlighting.

## Technology Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **3D Engine**: Sceneview (Google Filament-based, supports .glb/.gltf models)
- **Architecture**: MVVM (Model-View-ViewModel) + StateFlow
- **Min API**: TBD (define in build.gradle)

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
   - Load compressed `.glb` file from `assets/` directory
   - Parse and identify sub-nodes by name (e.g., "Engine_Mesh", "Suspension_Front")
   - Cache model to avoid repeated loads

2. **Part Selection (UI)**
   - Bottom menu (LazyRow) with buttons for each car part
   - Button click updates `selectedPart` StateFlow in ViewModel
   - UI reflects selection state with highlighting

3. **Camera Animation**
   - Smooth camera position interpolation (Lerp) when user selects a part
   - Each part has a target camera position and look-at point
   - Animate over ~300-500ms using onFrame callback

4. **Part Highlighting**
   - Modify selected node's material: adjust EmissiveFactor/EmissiveColor
   - Or swap material entirely to show selection
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
- **ViewModel properties**: `selectedPart`, `cameraPos`, `targetNode` (camelCase, descriptive)
- **Composables**: `CarConfiguratorScreen`, `PartSelector` (PascalCase)
- **3D Node references**: Match model export names exactly (e.g., "Engine_Mesh", "Body_01")
- **Material/Color variables**: `highlightEmissiveColor`, `defaultMaterial` (clear intent)

### Compose Structure
- Extract complex Composables (>150 lines) into separate functions
- Use `remember { }` for expensive object creation (e.g., camera interpolation state)
- Hoist state to ViewModel for cross-screen concerns

## Build & Test Commands
*To be populated once build.gradle is created:*
- Build APK: `./gradlew assembleDebug`
- Run tests: `./gradlew testDebug`
- Lint: `./gradlew lint`
- Build release: `./gradlew assembleRelease`

## Key Dependencies to Expect
- `io.github.sceneview:sceneview` (or latest version)
- `androidx.compose.ui:ui`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` (for StateFlow)

## File Structure (when implemented)
```
app/
├── src/main/
│   ├── kotlin/com/example/carconfigurator/
│   │   ├── ui/
│   │   │   ├── screen/CarConfiguratorScreen.kt
│   │   │   └── component/PartSelector.kt
│   │   ├── viewmodel/CarConfigViewModel.kt
│   │   ├── model/CarPart.kt
│   │   └── util/CameraInterpolator.kt
│   └── assets/
│       └── models/
│           └── car_model.glb
├── build.gradle.kts
└── AndroidManifest.xml
```

## Common Gotchas
- **Sceneview model loading**: Ensure `.glb` is in `assets/` (not `res/raw/`) for proper loading
- **Camera transitions**: Clamp Lerp `t` to [0, 1]; use `coerceIn()` for safety
- **Material mutations**: Clone materials before modifying to avoid affecting unselected nodes
- **StateFlow collection**: Always use `collectAsState()` in Compose, not `.collect()` in LaunchedEffect
- **3D coordinates**: Verify car model's coordinate system (Y-up vs Z-up) on first load
