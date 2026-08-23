package com.mdblisthub.tv.ui.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.mdblisthub.tv.R
import com.mdblisthub.tv.core.data.DataGraph
import com.mdblisthub.tv.core.model.HubThemeVariant
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.ui.component.HubButton
import kotlinx.coroutines.launch

private data class WelcomeTheme(val variant: HubThemeVariant, val label: Int, val preview: Int)

@Composable
fun WelcomeScreen(graph: DataGraph, onContinue: () -> Unit) {
    val themes = listOf(
        WelcomeTheme(HubThemeVariant.NORMAL, R.string.menu_theme_normal, R.drawable.theme_normal_preview),
        WelcomeTheme(HubThemeVariant.CYBERPUNK, R.string.menu_theme_cyberpunk, R.drawable.theme_cyberpunk_preview),
        WelcomeTheme(HubThemeVariant.NETFLIXY, R.string.menu_theme_netflixy, R.drawable.theme_netflixy_preview),
        WelcomeTheme(HubThemeVariant.PRIMEFLY, R.string.menu_theme_primefly, R.drawable.theme_primefly_preview),
    )
    var selected by remember { mutableStateOf(HubThemeVariant.NORMAL) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineLarge, color = HubColors.Text)
        Text(
            stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = HubColors.TextDim,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(themes, key = { it.variant.name }) { theme ->
                val active = selected == theme.variant
                Column(
                    Modifier
                        .width(170.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(HubColors.Surface)
                        .border(if (active) 3.dp else 1.dp, if (active) HubColors.Accent else HubColors.Border, RoundedCornerShape(20.dp))
                        .clickable {
                            selected = theme.variant
                            HubColors.apply(theme.variant)
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painterResource(theme.preview),
                        contentDescription = stringResource(theme.label),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(THEME_PREVIEW_ASPECT_RATIO)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Text(stringResource(theme.label), color = HubColors.Text, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
        Box(Modifier.padding(top = 24.dp)) {
            HubButton(
                text = stringResource(R.string.welcome_continue),
                primary = true,
                onClick = {
                    scope.launch {
                        graph.uiPreferences.saveTheme(selected)
                        graph.uiPreferences.saveSetupCompleted(true)
                        onContinue()
                    }
                },
            )
        }
    }
}

private const val THEME_PREVIEW_ASPECT_RATIO = 720f / 1600f
