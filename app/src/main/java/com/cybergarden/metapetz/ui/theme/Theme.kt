package com.cybergarden.metapetz.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.meta.spatial.uiset.theme.SpatialColorScheme
import com.meta.spatial.uiset.theme.darkSpatialColorScheme
import com.meta.spatial.uiset.theme.lightSpatialColorScheme

@Composable
fun getPanelTheme(): SpatialColorScheme =
    if (isSystemInDarkTheme()) darkSpatialColorScheme() else lightSpatialColorScheme()
