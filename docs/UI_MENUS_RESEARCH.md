# Meta Spatial SDK UI & Menu Patterns Research

## Source: UISetSample from Meta-Spatial-SDK-Samples/Showcases

---

## Architecture Overview

### Key Components

1. **PanelRegistry** - Central registration of all UI panels
2. **PanelNavigator** - Controls panel visibility and positioning
3. **NavigationView** - Main navigation menu with selectable items
4. **Layout Composables** - Individual UI screens (Buttons, Sliders, etc.)

---

## Panel Registration Pattern

```kotlin
// PanelRegistry.kt
class PanelRegistry {
    fun initialPanelRegistration(): List<PanelRegistration> {
        return listOf(
            panelRegistration(PanelRegistrationIds.PANEL_NAVIGATOR) {
                NavigationView(PanelNavigator())
            },
            panelRegistration(
                PanelRegistrationIds.PANEL_BUTTONS_LAYOUT,
                layoutWidth = 1136f,
                layoutHeight = 569f,
            ) {
                ButtonsLayout()
            },
            // ... more panels
        )
    }

    private fun panelRegistration(
        registrationId: Int,
        layoutWidth: Float? = null,
        layoutHeight: Float? = null,
        content: @Composable () -> Unit,
    ): PanelRegistration {
        return PanelRegistration(registrationId) { _ ->
            config {
                layoutWidthInDp = layoutWidth ?: default
                layoutHeightInDp = layoutHeight ?: default
                layerConfig = LayerConfig()
                enableTransparent = true
                includeGlass = false
            }
            composePanel { setContent { content() } }
        }
    }
}
```

### Key Takeaways:
- Use `PanelRegistration` with unique IDs
- Configure panel dimensions with `layoutWidthInDp` / `layoutHeightInDp`
- Enable transparency with `enableTransparent = true`
- Use `composePanel` for Jetpack Compose content

---

## Navigation Pattern

### NavigationView.kt - Grid-based Menu

```kotlin
@Composable
fun NavigationView(panelNavigator: PanelNavigator) {
    val allViewIds = NavigationUiItem.entries.map { it.panelRegistrationIds }.flatten()
    var selectedView by remember { mutableStateOf(initialView) }

    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        itemsIndexed(items) { index, item ->
            TextTileButton(
                icon = { Icon(SpatialIcons.Regular.CategoryAll, "") },
                label = item.label,
                secondaryLabel = item.secondaryLabel,
                onSelectionChange = { selected ->
                    if (selected) {
                        panelNavigator.setPanelsVisible(
                            item.panelRegistrationIds,
                            allViewIds.filter { it !in item.panelRegistrationIds }
                        )
                        selectedView = item
                    }
                },
                selected = selectedView == item,
            )
        }
    }
}
```

### Key Takeaways:
- Use `LazyVerticalGrid` for grid layouts
- `TextTileButton` from UISet for selectable menu items
- Track selection state with `mutableStateOf`
- Use `PanelNavigator` to show/hide panels

---

## Panel Navigator - Visibility & Positioning

```kotlin
class PanelNavigator {
    fun setPanelsVisible(registrationIds: List<Int>, otherIds: List<Int>) {
        // Get reference panel for positioning
        val navigatorPanel = Query.where { has(Panel.id) }
            .eval()
            .first { it.getComponent<Panel>().panelRegistrationId == PANEL_NAVIGATOR }

        // Set visibility on all panels
        Query.where { has(Panel.id) }
            .eval()
            .filter { ... }
            .forEach {
                val id = it.getComponent<Panel>().panelRegistrationId
                it.setComponent(Visible(registrationIds.contains(id)))

                // Position relative to navigator
                if (id == registrationIds.first()) {
                    val transform = navigatorPanel.getComponent<Transform>().transform
                    transform.t.z += 0.5f  // Depth offset
                    transform.t.y += 0.8f  // Vertical offset
                    transform.q = transform.q.times(Quaternion(-30f, 0f, 0f))  // Rotation
                    it.setComponent(Transform(transform))
                }
            }
    }
}
```

### Key Takeaways:
- Use `Query.where { has(Panel.id) }` to find panels
- Control visibility with `Visible` component
- Position panels relative to each other using `Transform`
- Apply rotation with `Quaternion`

---

## UISet Button Components

### Available Button Types:
```kotlin
// Primary - High emphasis action
PrimaryButton(label = "Label", onClick = { })
PrimaryCircleButton(icon = { Icon(...) }, onClick = { })
PrimaryIconButton(icon = { Icon(...) }, onClick = { })

// Secondary - Medium emphasis
SecondaryButton(label = "Label", onClick = { })
SecondaryCircleButton(...)
SecondaryIconButton(...)

// Borderless - No background
BorderlessButton(label = "Label", onClick = { })
BorderlessCircleButton(...)
BorderlessIconButton(...)

// Destructive - Delete/End actions
DestructiveButton(label = "Label", onClick = { })
DestructiveCircleButton(...)
DestructiveIconButton(...)

// TextTileButton - Selectable tile with icon and labels
TextTileButton(
    icon = { Icon(...) },
    label = "Main Label",
    secondaryLabel = "Description",
    onSelectionChange = { selected -> },
    selected = isSelected
)
```

### Icons:
```kotlin
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.icons.regular.*

// Usage
Icon(SpatialIcons.Regular.CategoryAll, "")
Icon(SpatialIcons.Regular.Chat, "")
Icon(SpatialIcons.Regular.MoreHorizontal, "")
```

---

## Activity Setup

```kotlin
class ImmersiveActivity : AppSystemActivity() {
    private val panelRegistry = PanelRegistry()

    override fun registerFeatures(): List<SpatialFeature> {
        return listOf(
            VRFeature(this),
            ComposeFeature()  // Required for Compose UI
        )
    }

    override fun registerPanels(): List<PanelRegistration> {
        return panelRegistry.initialPanelRegistration()
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.enableHolePunching(true)  // Better panel rendering
        scene.enablePassthrough(true)    // MR mode
        scene.setReferenceSpace(ReferenceSpace.LOCAL)
    }
}
```

---

## Scaffold Pattern

```kotlin
@Composable
fun PanelScaffold(
    title: String? = null,
    padding: PaddingValues = PaddingValues(24.dp),
    content: @Composable () -> Unit
) {
    Column(Modifier.padding(padding)) {
        if (title != null) {
            Text(title, style = SpatialTheme.typography.headline1Strong)
            Spacer(Modifier.size(24.dp))
        }
        content()
    }
}
```

---

## Best Practices Summary

### Panel Design
1. Use consistent panel registration IDs
2. Configure appropriate dimensions for each panel
3. Enable transparency for MR experiences
4. Position panels relative to user/other panels

### Navigation
1. Use grid layouts for menu items
2. Track selection state in Composable
3. Show/hide panels via PanelNavigator
4. Use TextTileButton for selectable items

### Styling
1. Use `SpatialTheme.typography` for text styles
2. Use `SpatialTheme.colorScheme` for colors
3. Use `SpatialIcons` for consistent iconography
4. Apply appropriate button types based on action importance

### Performance
1. Use `LazyVerticalGrid` for large item lists
2. Enable `userScrollEnabled = false` for fixed grids
3. Use `remember` for state that shouldn't recompose

---

## Recommended for MetaPetz

### Menu Structure:
1. **Main Panel** - Pet interaction/info (existing)
2. **Options Panel** - Pet selection grid (existing)
3. **Settings Panel** - New panel for settings/customization
4. **Stats Panel** - Detailed pet statistics

### UISet Components to Use:
- `PrimaryButton` - Feed, Play, Care actions
- `SecondaryButton` - Secondary actions
- `TextTileButton` - Pet selection cards
- `SpatialTheme` - Consistent styling
- `PanelScaffold` - Structured layouts

### Navigation Pattern:
- Use `PanelNavigator` pattern for showing/hiding panels
- Position panels relative to main panel
- Track panel visibility state
