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

## Code Examples

### ViewModel Setup with Camera State
```kotlin
class CarConfigViewModel : ViewModel() {
    private val _selectedPart = MutableStateFlow("")
    val selectedPart: StateFlow<String> = _selectedPart.asStateFlow()
    
    private val _cameraPos = MutableStateFlow(Float3(x = 0f, y = 2f, z = 5f))
    val cameraPos: StateFlow<Float3> = _cameraPos.asStateFlow()
    
    private val _cameraTarget = MutableStateFlow(Float3(x = 0f, y = 1f, z = 0f))
    val cameraTarget: StateFlow<Float3> = _cameraTarget.asStateFlow()
    
    // Camera animation progress [0, 1]
    private val _cameraLerpFactor = MutableStateFlow(0f)
    val cameraLerpFactor: StateFlow<Float> = _cameraLerpFactor.asStateFlow()
    
    fun selectPart(partName: String, targetPos: Float3, targetLookAt: Float3) {
        _selectedPart.value = partName
        viewModelScope.launch {
            animateCamera(targetPos, targetLookAt)
        }
    }
    
    private suspend fun animateCamera(target: Float3, lookAt: Float3) {
        val startPos = _cameraPos.value
        val startTarget = _cameraTarget.value
        val durationMs = 400
        val startTime = System.currentTimeMillis()
        
        while (true) {
            val elapsed = System.currentTimeMillis() - startTime
            val t = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
            
            // Smooth easing: cubic ease-out
            val eased = 1f - (1f - t).let { it * it * it }
            
            _cameraPos.value = Float3(
                x = startPos.x + (target.x - startPos.x) * eased,
                y = startPos.y + (target.y - startPos.y) * eased,
                z = startPos.z + (target.z - startPos.z) * eased
            )
            _cameraTarget.value = Float3(
                x = startTarget.x + (lookAt.x - startTarget.x) * eased,
                y = startTarget.y + (lookAt.y - startTarget.y) * eased,
                z = startTarget.z + (lookAt.z - startTarget.z) * eased
            )
            _cameraLerpFactor.value = eased
            
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
    val selectedPart by viewModel.selectedPart.collectAsState()
    val cameraPos by viewModel.cameraPos.collectAsState()
    val cameraTarget by viewModel.cameraTarget.collectAsState()
    
    var sceneView by remember { mutableStateOf<SceneView?>(null) }
    var carModel by remember { mutableStateOf<ModelInstance?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // 3D Sceneview
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
        
        // Update camera position in onFrame callback
        LaunchedEffect(cameraPos, cameraTarget) {
            sceneView?.onFrame = {
                sceneView?.cameraNode?.apply {
                    position = cameraPos
                    lookAt(cameraTarget)
                }
            }
        }
        
        // Highlight selected part by modifying material
        LaunchedEffect(selectedPart) {
            carModel?.let { model ->
                // Clear previous highlights
                model.getChildren<Node>().forEach { node ->
                    restoreMaterial(node)
                }
                
                // Highlight selected node
                val selectedNode = model.getChildByName(selectedPart)
                selectedNode?.let { node ->
                    applyHighlightMaterial(node)
                }
            }
        }
        
        // Part selector UI overlay
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(carParts) { part ->
                PartButton(
                    part = part,
                    isSelected = selectedPart == part.nodeName,
                    onClick = {
                        viewModel.selectPart(
                            part.nodeName,
                            part.cameraPos,
                            part.cameraLookAt
                        )
                    }
                )
            }
        }
    }
}

// Helper to apply emissive highlight to a node
private fun applyHighlightMaterial(node: Node) {
    node.renderableInstance?.material?.let { material ->
        // Modify or clone material
        material.setParameter("emissiveFactor", FloatArray(4) { 1f })
        material.setParameter("emissiveColor", Color(0xFFFF6B6B)) // Red highlight
    }
}

private fun restoreMaterial(node: Node) {
    // Restore default material (you may need to cache originals)
    node.renderableInstance?.material?.let { material ->
        material.setParameter("emissiveFactor", FloatArray(4) { 0f })
    }
}
```

### Part Definition Data Class
```kotlin
data class CarPart(
    val nodeName: String,           // Must match 3D model node name
    val displayName: String,
    val cameraPos: Float3,          // Camera position to frame this part
    val cameraLookAt: Float3,       // Where camera looks at
    @DrawableRes val iconRes: Int
)

val carParts = listOf(
    CarPart(
        nodeName = "Engine_Mesh",
        displayName = "Engine",
        cameraPos = Float3(x = 0.5f, y = 1.5f, z = 1.2f),
        cameraLookAt = Float3(x = 0.5f, y = 1f, z = 0f),
        iconRes = R.drawable.ic_engine
    ),
    CarPart(
        nodeName = "Suspension_Front",
        displayName = "Suspension",
        cameraPos = Float3(x = 0.8f, y = 0.2f, z = 0.8f),
        cameraLookAt = Float3(x = 0.8f, y = 0.5f, z = 0f),
        iconRes = R.drawable.ic_suspension
    )
    // ... more parts
)
```

## Performance Optimization Tips

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
*To be populated once build.gradle is created:*
- Build APK: `./gradlew assembleDebug`
- Run tests: `./gradlew testDebug`
- Single test file: `./gradlew testDebug --tests com.example.carconfigurator.viewmodel.CarConfigViewModelTest`
- Lint: `./gradlew lint`
- Build release: `./gradlew assembleRelease`
- Run on device: `./gradlew installDebug && adb shell am start -n com.example.carconfigurator/.MainActivity`

## Key Dependencies to Expect
- `io.github.sceneview:sceneview` (or latest version)
- `androidx.compose.ui:ui`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `org.jetbrains.kotlinx:kotlinx-coroutines-core` (for StateFlow)
- `androidx.compose.foundation:foundation` (for LazyRow, etc.)

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
