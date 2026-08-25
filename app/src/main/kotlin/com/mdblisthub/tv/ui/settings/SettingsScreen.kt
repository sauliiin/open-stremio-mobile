package com.mdblisthub.tv.ui.settings

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.mdblisthub.tv.BuildConfig
import com.mdblisthub.tv.R
import com.mdblisthub.tv.core.data.DataGraph
import com.mdblisthub.tv.core.data.repository.SimklLinkState
import com.mdblisthub.tv.core.model.HubThemeVariant
import com.mdblisthub.tv.core.model.LibraryProvider
import com.mdblisthub.tv.core.model.TraktAccount
import com.mdblisthub.tv.core.model.TraktLinkFailure
import com.mdblisthub.tv.core.model.TraktLinkState
import com.mdblisthub.tv.core.ui.theme.HubColors
import com.mdblisthub.tv.core.ui.theme.HubDimens
import com.mdblisthub.tv.core.ui.theme.HubTokens
import com.mdblisthub.tv.core.ui.component.HubGlassCard
import com.mdblisthub.tv.core.ui.component.HubScreenHeading
import com.mdblisthub.tv.core.ui.component.HubSectionLabel
import com.mdblisthub.tv.core.ui.component.HubSettingRow
import com.mdblisthub.tv.core.ui.component.HubToggle
import com.mdblisthub.tv.ui.component.HubButton
import com.mdblisthub.tv.ui.addons.AddonsScreen
import com.mdblisthub.tv.ui.hubViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

val ALL_LANGUAGES = listOf(
    "pt" to "Português (Brasil)",
    "pt-pt" to "Português (Portugal)",
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "it" to "Italiano",
    "de" to "Deutsch",
    "ru" to "Русский",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
    "ar" to "العربية",
    "hi" to "हिन्दी",
    "tr" to "Türkçe",
    "pl" to "Polski",
    "nl" to "Nederlands",
    "hr" to "Hrvatski",
    "sr" to "Српски",
    "bs" to "Bosanski"
)

data class SettingsUiState(
    val language: String = "en",
    val subtitleAutoDownload: Boolean = true,
    val subtitleLanguage: String = "pt",
    val subtitleColor: String = "white",
    val subtitleTextOpacity: Int = 100,
    val subtitleBackgroundEnabled: Boolean = false,
    val subtitleBackgroundOpacity: Int = 40,
    val amoledMode: Boolean = false,
    val autotrailer: Boolean = false,
    val introEnabled: Boolean = false,
    val audioLanguage: String = "en",
    val libraryProvider: LibraryProvider = LibraryProvider.MDBLIST,
    val dimUnwatchedEpisodes: Boolean = false,
    val traktAccount: TraktAccount? = null,
    /** False when the build ships no Trakt client id — see `ApiConfig`. */
    val traktConfigured: Boolean = false,
    val simklLinked: Boolean = false,
    val googleEmail: String? = null,
    val mdblistLinked: Boolean = false,
    val mdblistOnly: Boolean = false,
)

class SettingsViewModel(private val graph: DataGraph) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(traktConfigured = graph.traktAuth.configured))
    val state = _state.asStateFlow()

    /** Non-null only while the device-link overlay is up. */
    private val _traktLink = MutableStateFlow<TraktLinkState?>(null)
    val traktLink = _traktLink.asStateFlow()
    private val _simklLink = MutableStateFlow<SimklLinkState?>(null)
    val simklLink = _simklLink.asStateFlow()

    private var linkJob: Job? = null

    init {
        viewModelScope.launch {
            // Nested rather than one call: `combine` has typed overloads up to
            // five flows, and this needs seven.
            combine(
                graph.uiPreferences.language,
                graph.uiPreferences.subtitleAutoDownload,
                graph.uiPreferences.subtitleLanguage,
                graph.uiPreferences.subtitleColor,
                graph.uiPreferences.audioLanguage,
            ) { lang, subAuto, subLang, subColor, audioLang ->
                SettingsUiState(
                    language = lang,
                    subtitleAutoDownload = subAuto,
                    subtitleLanguage = subLang,
                    subtitleColor = subColor,
                    audioLanguage = audioLang,
                )
            }
                .combine(graph.uiPreferences.libraryProvider) { partial, provider ->
                    partial.copy(libraryProvider = provider)
                }
                .combine(graph.uiPreferences.dimUnwatchedEpisodes) { partial, dim ->
                    partial.copy(dimUnwatchedEpisodes = dim)
                }
                .combine(graph.traktAuth.account) { partial, account ->
                    partial.copy(
                        traktAccount = account,
                        traktConfigured = graph.traktAuth.configured,
                    )
                }
                .combine(graph.auth.googleAccount) { partial, google ->
                    partial.copy(googleEmail = google?.email)
                }
                .combine(graph.auth.mdblistLinked) { partial, linked ->
                    partial.copy(mdblistLinked = linked)
                }
                .combine(graph.auth.isMdblistOnly) { partial, mdblistOnly ->
                    partial.copy(mdblistOnly = mdblistOnly)
                }
                .combine(graph.simklTokenStore.linked) { partial, linked ->
                    partial.copy(simklLinked = linked)
                }
                .combine(graph.uiPreferences.subtitleTextOpacity) { partial, value ->
                    partial.copy(subtitleTextOpacity = value)
                }
                .combine(graph.uiPreferences.subtitleBackgroundEnabled) { partial, value ->
                    partial.copy(subtitleBackgroundEnabled = value)
                }
                .combine(graph.uiPreferences.subtitleBackgroundOpacity) { partial, value ->
                    partial.copy(subtitleBackgroundOpacity = value)
                }
                .combine(graph.uiPreferences.amoledMode) { partial, value ->
                    partial.copy(amoledMode = value)
                }
                .combine(graph.uiPreferences.autotrailer) { partial, value ->
                    partial.copy(autotrailer = value)
                }
                .combine(graph.uiPreferences.introEnabled) { partial, value ->
                    partial.copy(introEnabled = value)
                }
                .collect { _state.value = it }
        }
    }

    /**
     * Picking Trakt with no account linked opens the link flow instead of
     * saving a setting that could not mean anything yet — the switch is only
     * a switch once there is something on the other side of it.
     */
    fun setLibraryProvider(provider: LibraryProvider) {
        viewModelScope.launch {
            if (provider == LibraryProvider.TRAKT && _state.value.traktAccount == null) {
                beginTraktLink()
                return@launch
            }
            if (provider == LibraryProvider.SIMKL && !_state.value.simklLinked) {
                beginSimklLink()
                return@launch
            }
            graph.switchLibraryProvider(provider)
            refreshLibraryRows()
        }
    }

    fun beginTraktLink() {
        if (linkJob?.isActive == true) return
        if (!graph.traktAuth.configured) {
            _traktLink.value = TraktLinkState.Failed(TraktLinkFailure.MISSING_CREDENTIALS)
            return
        }

        _traktLink.value = TraktLinkState.Requesting
        linkJob = viewModelScope.launch {
            graph.traktAuth.startLink().fold(
                onSuccess = { code ->
                    graph.traktAuth.poll(code).collect { linkState ->
                        _traktLink.value = linkState
                        if (linkState is TraktLinkState.Linked) {
                            // Connecting *is* the request to use Trakt; making
                            // the user then pick the option they just went
                            // through a device flow for would be asking twice.
                            graph.switchLibraryProvider(LibraryProvider.TRAKT)
                            refreshLibraryRows()
                            // Deliberately left on screen rather than closed
                            // after a beat. This is the only moment the flow
                            // ever confirms it worked, and a device link is
                            // long enough — open a browser, type a code — that
                            // the viewer is often not looking at the phone
                            // when it lands. A confirmation nobody is looking
                            // at is the same as no confirmation, so it waits
                            // to be dismissed instead.
                        }
                    }
                },
                onFailure = {
                    _traktLink.value = TraktLinkState.Failed(TraktLinkFailure.UNAVAILABLE)
                },
            )
        }
    }

    fun dismissTraktLink() {
        linkJob?.cancel()
        linkJob = null
        _traktLink.value = null
    }

    fun unlinkTrakt() {
        viewModelScope.launch {
            graph.unlinkTrakt()
            refreshLibraryRows()
        }
    }

    fun beginSimklLink() {
        if (linkJob?.isActive == true) return
        _simklLink.value = SimklLinkState.Requesting
        linkJob = viewModelScope.launch {
            runCatching { graph.simklAuth.start() }
                .onSuccess { pin ->
                    graph.simklAuth.poll(pin).collect { linkState ->
                        _simklLink.value = linkState
                        if (linkState is SimklLinkState.Linked) {
                            graph.switchLibraryProvider(LibraryProvider.SIMKL)
                            refreshLibraryRows()
                            delay(LINKED_VISIBLE_MS)
                            _simklLink.value = null
                        }
                    }
                }
                .onFailure { _simklLink.value = SimklLinkState.Failed }
        }
    }

    fun dismissSimklLink() {
        linkJob?.cancel()
        linkJob = null
        _simklLink.value = null
    }

    fun unlinkSimkl() {
        viewModelScope.launch {
            graph.unlinkSimkl()
            refreshLibraryRows()
        }
    }

    /**
     * On the graph's scope, not this ViewModel's: the point of refreshing here
     * is that the change has already landed by the time the user navigates
     * back to Home, and a scope that dies when Settings closes would cancel
     * exactly the work that makes that true.
     */
    private fun refreshLibraryRows() {
        graph.scope.launch {
            graph.homeFeeds.refresh()
            graph.playback.refreshResumePoints()
        }
    }

    fun setLanguage(lang: String) = viewModelScope.launch { graph.uiPreferences.saveLanguage(lang) }
    fun toggleSubtitleAutoDownload() = viewModelScope.launch { graph.uiPreferences.saveSubtitleAutoDownload(!_state.value.subtitleAutoDownload) }
    fun setSubtitleLanguage(lang: String) = viewModelScope.launch { graph.uiPreferences.saveSubtitleLanguage(lang) }
    fun setSubtitleColor(color: String) = viewModelScope.launch { graph.uiPreferences.saveSubtitleColor(color) }
    fun setSubtitleTextOpacity(value: Int) = viewModelScope.launch { graph.uiPreferences.saveSubtitleTextOpacity(value) }
    fun toggleSubtitleBackground() = viewModelScope.launch {
        graph.uiPreferences.saveSubtitleBackgroundEnabled(!_state.value.subtitleBackgroundEnabled)
    }
    fun setSubtitleBackgroundOpacity(value: Int) = viewModelScope.launch {
        graph.uiPreferences.saveSubtitleBackgroundOpacity(value)
    }
    fun setAudioLanguage(lang: String) = viewModelScope.launch { graph.uiPreferences.saveAudioLanguage(lang) }
    fun toggleDimUnwatchedEpisodes() = viewModelScope.launch { graph.uiPreferences.saveDimUnwatchedEpisodes(!_state.value.dimUnwatchedEpisodes) }

    // The bottom nav dropped its own theme-cycle button once this section
    // existed to pick one deliberately instead — see BottomNavBar.
    fun setTheme(target: HubThemeVariant) {
        HubColors.apply(target)
        viewModelScope.launch { graph.uiPreferences.saveTheme(target) }
    }

    fun toggleAmoledMode() {
        val enabled = !_state.value.amoledMode
        HubColors.applyAmoledMode(enabled)
        viewModelScope.launch { graph.uiPreferences.saveAmoledMode(enabled) }
    }

    fun toggleAutotrailer() = viewModelScope.launch {
        graph.uiPreferences.saveAutotrailer(!_state.value.autotrailer)
    }

    fun toggleIntroEnabled() = viewModelScope.launch {
        graph.uiPreferences.saveIntroEnabled(!_state.value.introEnabled)
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            graph.scheduler.onSignedOut()
            graph.auth.signOut()
            onDone()
        }
    }

    private companion object {
        /** Long enough to read "connected as @you" before the overlay closes. */
        const val LINKED_VISIBLE_MS = 1_600L
    }
}

private enum class SettingsDestination(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    ROOT(R.string.settings_title, R.string.settings_root_subtitle),
    ACCOUNT(R.string.settings_page_account, R.string.settings_page_account_description),
    APPEARANCE(R.string.settings_page_appearance, R.string.settings_page_appearance_description),
    LIBRARY(R.string.settings_page_library, R.string.settings_page_library_description),
    SUBTITLES(R.string.settings_page_subtitles, R.string.settings_page_subtitles_description),
    PLAYBACK(R.string.settings_page_playback, R.string.settings_page_playback_description),
    ADDONS(R.string.menu_addons, R.string.settings_page_addons_description),
    ABOUT(R.string.settings_page_about, R.string.settings_page_about_description),
}

private val SETTINGS_DESTINATIONS = SettingsDestination.entries.filterNot {
    it == SettingsDestination.ROOT
}

private fun SettingsDestination.icon(): ImageVector = when (this) {
    SettingsDestination.ROOT -> Icons.Default.Search
    SettingsDestination.ACCOUNT -> Icons.Default.AccountCircle
    SettingsDestination.APPEARANCE -> Icons.Default.Palette
    SettingsDestination.LIBRARY -> Icons.AutoMirrored.Filled.LibraryBooks
    SettingsDestination.SUBTITLES -> Icons.Default.Subtitles
    SettingsDestination.PLAYBACK -> Icons.Default.PlayCircle
    SettingsDestination.ADDONS -> Icons.Default.Extension
    SettingsDestination.ABOUT -> Icons.Default.Info
}

@Composable
fun SettingsScreen(
    graph: DataGraph,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val viewModel = hubViewModel { SettingsViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val traktLink by viewModel.traktLink.collectAsStateWithLifecycle()
    val simklLink by viewModel.simklLink.collectAsStateWithLifecycle()

    var destination by rememberSaveable { mutableStateOf(SettingsDestination.ROOT) }
    var query by rememberSaveable { mutableStateOf("") }
    var subtitlePickerOpen by remember { mutableStateOf(false) }
    var audioPickerOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= HubTokens.Breakpoint.tablet
        val effectiveDestination = if (tablet && destination == SettingsDestination.ROOT) {
            SettingsDestination.APPEARANCE
        } else {
            destination
        }

        BackHandler {
            when {
                traktLink != null -> viewModel.dismissTraktLink()
                simklLink != null -> viewModel.dismissSimklLink()
                subtitlePickerOpen -> subtitlePickerOpen = false
                audioPickerOpen -> audioPickerOpen = false
                confirmSignOut -> confirmSignOut = false
                !tablet && destination != SettingsDestination.ROOT -> destination = SettingsDestination.ROOT
                else -> onBack()
            }
        }

        if (tablet) {
            Row(Modifier.fillMaxSize()) {
                SettingsNavigationPane(
                    selected = effectiveDestination,
                    query = query,
                    onQueryChange = { query = it },
                    onSelect = { destination = it },
                    modifier = Modifier
                        .width(326.dp)
                        .fillMaxHeight(),
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(HubTokens.Space.hairline)
                        .background(HubColors.Border.copy(alpha = 0.72f)),
                )
                SettingsPagePane(
                    graph = graph,
                    destination = effectiveDestination,
                    state = state,
                    viewModel = viewModel,
                    showBack = false,
                    onBack = {},
                    onOpenSubtitlePicker = { subtitlePickerOpen = true },
                    onOpenAudioPicker = { audioPickerOpen = true },
                    onRequestSignOut = { confirmSignOut = true },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "settings-page",
            ) { target ->
                if (target == SettingsDestination.ROOT) {
                    SettingsNavigationPane(
                        selected = null,
                        query = query,
                        onQueryChange = { query = it },
                        onSelect = { destination = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SettingsPagePane(
                        graph = graph,
                        destination = target,
                        state = state,
                        viewModel = viewModel,
                        showBack = true,
                        onBack = { destination = SettingsDestination.ROOT },
                        onOpenSubtitlePicker = { subtitlePickerOpen = true },
                        onOpenAudioPicker = { audioPickerOpen = true },
                        onRequestSignOut = { confirmSignOut = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (subtitlePickerOpen) {
        LanguagePickerOverlay(
            title = stringResource(R.string.settings_subtitle_default_lang),
            languages = ALL_LANGUAGES,
            selectedCode = state.subtitleLanguage,
            onSelect = { viewModel.setSubtitleLanguage(it); subtitlePickerOpen = false },
        )
    }
    if (audioPickerOpen) {
        LanguagePickerOverlay(
            title = stringResource(R.string.settings_audio_preferred_lang),
            languages = ALL_LANGUAGES,
            selectedCode = state.audioLanguage,
            onSelect = { viewModel.setAudioLanguage(it); audioPickerOpen = false },
        )
    }
    traktLink?.let { link ->
        TraktLinkOverlay(
            state = link,
            onRetry = viewModel::beginTraktLink,
            onDismiss = viewModel::dismissTraktLink,
        )
    }
    simklLink?.let { link ->
        SimklLinkOverlay(
            state = link,
            onRetry = viewModel::beginSimklLink,
            onDismiss = viewModel::dismissSimklLink,
        )
    }
    if (confirmSignOut) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { confirmSignOut = false }) {
            HubGlassCard(
                modifier = Modifier.widthIn(max = 430.dp),
                strong = true,
            ) {
                Column(
                    modifier = Modifier.padding(HubTokens.Space.xxl),
                    verticalArrangement = Arrangement.spacedBy(HubTokens.Space.lg),
                ) {
                    Text(
                        text = stringResource(R.string.settings_sign_out_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = HubColors.Text,
                    )
                    Text(
                        text = stringResource(R.string.settings_sign_out_description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = HubColors.TextDim,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.sm)) {
                        HubButton(
                            text = stringResource(R.string.menu_exit),
                            primary = true,
                            onClick = {
                                confirmSignOut = false
                                viewModel.signOut(onSignOut)
                            },
                        )
                        HubButton(
                            text = stringResource(R.string.home_cancel),
                            onClick = { confirmSignOut = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsNavigationPane(
    selected: SettingsDestination?,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = SETTINGS_DESTINATIONS.filter { destination ->
        val title = stringResource(destination.titleRes)
        val description = stringResource(destination.descriptionRes)
        query.isBlank() || title.contains(query, ignoreCase = true) ||
            description.contains(query, ignoreCase = true)
    }

    Column(
        modifier = modifier.padding(
            start = HubDimens.ScreenPaddingHorizontal,
            end = HubDimens.ScreenPaddingHorizontal,
            top = HubDimens.ScreenPaddingVertical * 2,
        ),
        verticalArrangement = Arrangement.spacedBy(HubTokens.Space.lg),
    ) {
        HubScreenHeading(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_root_subtitle),
        )
        SettingsSearchField(query = query, onQueryChange = onQueryChange)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
            contentPadding = PaddingValues(bottom = HubTokens.Size.contentBottomClearance),
        ) {
            items(filtered, key = { it.name }) { destination ->
                HubGlassCard(strong = selected == destination) {
                    HubSettingRow(
                        title = stringResource(destination.titleRes),
                        description = stringResource(destination.descriptionRes),
                        leading = {
                            Icon(
                                imageVector = destination.icon(),
                                contentDescription = null,
                                tint = HubColors.AccentSoft,
                                modifier = Modifier.size(23.dp),
                            )
                        },
                        trailing = {
                            Icon(
                                imageVector = if (selected == destination) {
                                    Icons.Default.CheckCircle
                                } else {
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                                },
                                contentDescription = null,
                                tint = if (selected == destination) HubColors.Accent else HubColors.TextFaint,
                            )
                        },
                        onClick = { onSelect(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(HubTokens.Radius.lg)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = HubColors.Text),
        cursorBrush = SolidColor(HubColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HubColors.Surface.copy(alpha = HubTokens.Opacity.glass))
            .border(1.dp, HubColors.Border, shape)
            .padding(horizontal = HubTokens.Space.lg, vertical = 14.dp),
        decorationBox = { field ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = HubColors.TextFaint)
                Box(Modifier.weight(1f)) {
                    if (query.isBlank()) {
                        Text(
                            text = stringResource(R.string.settings_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = HubColors.TextFaint,
                        )
                    }
                    field()
                }
            }
        },
    )
}

@Composable
private fun SettingsPagePane(
    graph: DataGraph,
    destination: SettingsDestination,
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenSubtitlePicker: () -> Unit,
    onOpenAudioPicker: () -> Unit,
    onRequestSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination == SettingsDestination.ADDONS) {
        AddonsScreen(graph = graph, onBack = onBack)
        return
    }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = HubDimens.ScreenPaddingHorizontal,
            end = HubDimens.ScreenPaddingHorizontal,
            top = HubDimens.ScreenPaddingVertical * 2,
            bottom = HubTokens.Size.contentBottomClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(HubTokens.Space.xl),
    ) {
        item(key = "header-${destination.name}") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showBack) {
                    SettingsIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.login_mdblist_only_back),
                        onClick = onBack,
                    )
                }
                HubScreenHeading(
                    title = stringResource(destination.titleRes),
                    subtitle = stringResource(destination.descriptionRes),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (destination) {
            SettingsDestination.APPEARANCE -> {
                item(key = "appearance-theme") {
                    ModernSettingsGroup(stringResource(R.string.settings_section_theme)) {
                        Text(
                            text = stringResource(R.string.settings_theme_variant),
                            style = MaterialTheme.typography.titleMedium,
                            color = HubColors.Text,
                            modifier = Modifier.padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.sm),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
                        ) {
                            listOf(
                                HubThemeVariant.NORMAL to stringResource(R.string.menu_theme_normal),
                                HubThemeVariant.CYBERPUNK to stringResource(R.string.menu_theme_cyberpunk),
                                HubThemeVariant.NETFLIXY to stringResource(R.string.menu_theme_netflixy),
                                HubThemeVariant.PRIMEFLY to stringResource(R.string.menu_theme_primefly),
                            ).forEach { (variant, label) ->
                                ThemePreviewChoice(
                                    variant = variant,
                                    label = label,
                                    selected = HubColors.variant == variant,
                                    onClick = { viewModel.setTheme(variant) },
                                )
                            }
                        }
                    }
                }
                item(key = "appearance-language") {
                    ModernSettingsGroup(stringResource(R.string.settings_section_interface)) {
                        HubSettingRow(
                            title = stringResource(R.string.settings_language),
                            description = ALL_LANGUAGES.firstOrNull { it.first == state.language }?.second ?: state.language,
                            leading = {
                                Icon(Icons.Default.Language, contentDescription = null, tint = HubColors.AccentSoft)
                            },
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
                        ) {
                            listOf("pt", "en", "es").forEach { code ->
                                val label = ALL_LANGUAGES.first { it.first == code }.second
                                HubButton(
                                    text = label,
                                    primary = state.language == code,
                                    onClick = { viewModel.setLanguage(code) },
                                )
                            }
                        }
                    }
                }
                item(key = "appearance-options") {
                    ModernSettingsGroup(stringResource(R.string.settings_appearance_options)) {
                        HubSettingRow(
                            title = stringResource(R.string.settings_amoled_mode),
                            description = stringResource(R.string.settings_amoled_mode_description),
                            trailing = {
                                HubToggle(state.amoledMode, { viewModel.toggleAmoledMode() })
                            },
                            onClick = viewModel::toggleAmoledMode,
                        )
                        SettingsDivider()
                        HubSettingRow(
                            title = stringResource(R.string.settings_autotrailer),
                            description = stringResource(R.string.settings_autotrailer_pip_description),
                            trailing = {
                                HubToggle(state.autotrailer, { viewModel.toggleAutotrailer() })
                            },
                            onClick = viewModel::toggleAutotrailer,
                        )
                        SettingsDivider()
                        HubSettingRow(
                            title = stringResource(R.string.settings_intro_enabled),
                            description = stringResource(R.string.settings_intro_enabled_description),
                            trailing = {
                                HubToggle(state.introEnabled, { viewModel.toggleIntroEnabled() })
                            },
                            onClick = viewModel::toggleIntroEnabled,
                        )
                    }
                }
            }

            SettingsDestination.LIBRARY -> item(key = "library-options") {
                ModernSettingsGroup(stringResource(R.string.settings_section_library)) {
                    HubSettingRow(
                        title = stringResource(R.string.settings_library_provider),
                        description = stringResource(
                            when (state.libraryProvider) {
                                LibraryProvider.MDBLIST -> R.string.settings_library_mdblist
                                LibraryProvider.TRAKT -> R.string.settings_library_trakt
                                LibraryProvider.SIMKL -> R.string.settings_library_provider_simkl
                            },
                        ),
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
                    ) {
                        HubButton(
                            text = stringResource(R.string.settings_library_mdblist),
                            primary = state.libraryProvider == LibraryProvider.MDBLIST,
                            onClick = { viewModel.setLibraryProvider(LibraryProvider.MDBLIST) },
                        )
                        HubButton(
                            text = stringResource(R.string.settings_library_trakt),
                            primary = state.libraryProvider == LibraryProvider.TRAKT,
                            onClick = { viewModel.setLibraryProvider(LibraryProvider.TRAKT) },
                        )
                        HubButton(
                            text = stringResource(R.string.settings_library_simkl),
                            primary = state.libraryProvider == LibraryProvider.SIMKL,
                            onClick = { viewModel.setLibraryProvider(LibraryProvider.SIMKL) },
                        )
                    }
                    SettingsDivider()
                    HubSettingRow(
                        title = stringResource(R.string.settings_dim_unwatched_episodes),
                        description = stringResource(
                            if (state.dimUnwatchedEpisodes) R.string.settings_on else R.string.settings_off,
                        ),
                        trailing = {
                            HubToggle(
                                checked = state.dimUnwatchedEpisodes,
                                onCheckedChange = { viewModel.toggleDimUnwatchedEpisodes() },
                            )
                        },
                        onClick = viewModel::toggleDimUnwatchedEpisodes,
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = state.traktAccount?.handle ?: stringResource(R.string.settings_trakt_not_connected),
                        description = stringResource(
                            if (state.traktAccount == null) R.string.settings_trakt_connect else R.string.settings_trakt_disconnect,
                        ),
                        trailing = {
                            HubButton(
                                text = stringResource(
                                    if (state.traktAccount == null) R.string.settings_trakt_connect else R.string.settings_trakt_disconnect,
                                ),
                                primary = state.traktAccount == null,
                                onClick = if (state.traktAccount == null) viewModel::beginTraktLink else viewModel::unlinkTrakt,
                            )
                        },
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = stringResource(
                            if (state.simklLinked) R.string.settings_simkl_connected else R.string.settings_simkl_not_connected,
                        ),
                        description = stringResource(
                            if (state.simklLinked) R.string.settings_simkl_disconnect else R.string.settings_simkl_connect,
                        ),
                        trailing = {
                            HubButton(
                                text = stringResource(
                                    if (state.simklLinked) R.string.settings_simkl_disconnect else R.string.settings_simkl_connect,
                                ),
                                primary = !state.simklLinked,
                                onClick = if (state.simklLinked) viewModel::unlinkSimkl else viewModel::beginSimklLink,
                            )
                        },
                    )
                }
            }

            SettingsDestination.SUBTITLES -> item(key = "subtitle-options") {
                ModernSettingsGroup(stringResource(R.string.settings_section_subtitles)) {
                    HubSettingRow(
                        title = stringResource(R.string.settings_subtitle_auto_download),
                        description = stringResource(
                            if (state.subtitleAutoDownload) R.string.settings_on else R.string.settings_off,
                        ),
                        trailing = {
                            HubToggle(
                                checked = state.subtitleAutoDownload,
                                onCheckedChange = { viewModel.toggleSubtitleAutoDownload() },
                            )
                        },
                        onClick = viewModel::toggleSubtitleAutoDownload,
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = stringResource(R.string.settings_subtitle_default_lang),
                        description = ALL_LANGUAGES.firstOrNull { it.first == state.subtitleLanguage }?.second,
                        trailing = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HubColors.TextFaint)
                        },
                        onClick = onOpenSubtitlePicker,
                    )
                    SettingsDivider()
                    HubSettingRow(title = stringResource(R.string.settings_subtitle_color))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
                    ) {
                        listOf(
                            "yellow" to Color.Yellow,
                            "white" to Color.White,
                            "red" to Color.Red,
                            "blue" to Color.Blue,
                            "black" to Color.Black,
                        ).forEach { (code, color) ->
                            SubtitleColorChoice(
                                color = color,
                                selected = state.subtitleColor == code,
                                onClick = { viewModel.setSubtitleColor(code) },
                            )
                        }
                    }
                    SettingsDivider()
                    OpacitySetting(
                        label = stringResource(R.string.settings_subtitle_text_opacity),
                        value = state.subtitleTextOpacity,
                        onValueChange = viewModel::setSubtitleTextOpacity,
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = stringResource(R.string.settings_subtitle_background),
                        description = stringResource(if (state.subtitleBackgroundEnabled) R.string.settings_on else R.string.settings_off),
                        trailing = {
                            HubToggle(state.subtitleBackgroundEnabled, { viewModel.toggleSubtitleBackground() })
                        },
                        onClick = viewModel::toggleSubtitleBackground,
                    )
                    if (state.subtitleBackgroundEnabled) {
                        OpacitySetting(
                            label = stringResource(R.string.settings_subtitle_background_opacity),
                            value = state.subtitleBackgroundOpacity,
                            onValueChange = viewModel::setSubtitleBackgroundOpacity,
                        )
                    }
                }
            }

            SettingsDestination.PLAYBACK -> item(key = "playback-options") {
                ModernSettingsGroup(stringResource(R.string.settings_section_player)) {
                    HubSettingRow(
                        title = stringResource(R.string.settings_audio_preferred_lang),
                        description = ALL_LANGUAGES.firstOrNull { it.first == state.audioLanguage }?.second,
                        trailing = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HubColors.TextFaint)
                        },
                        onClick = onOpenAudioPicker,
                    )
                }
            }

            SettingsDestination.ACCOUNT -> item(key = "account-options") {
                ModernSettingsGroup(stringResource(R.string.settings_page_account)) {
                    HubSettingRow(
                        title = stringResource(R.string.settings_account_google),
                        description = state.googleEmail ?: stringResource(
                            if (state.mdblistOnly) R.string.settings_account_local_session else R.string.settings_status_not_connected,
                        ),
                        leading = {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = HubColors.AccentSoft)
                        },
                        trailing = { SettingsStatusBadge(state.googleEmail != null) },
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = "MDBList",
                        description = stringResource(
                            if (state.mdblistLinked) R.string.settings_status_connected else R.string.settings_status_not_connected,
                        ),
                        leading = {
                            Icon(Icons.AutoMirrored.Filled.LibraryBooks, contentDescription = null, tint = HubColors.AccentSoft)
                        },
                        trailing = { SettingsStatusBadge(state.mdblistLinked) },
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = stringResource(R.string.menu_exit),
                        description = stringResource(R.string.settings_sign_out_description),
                        leading = {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = HubColors.Rotten)
                        },
                        trailing = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = HubColors.Rotten)
                        },
                        onClick = onRequestSignOut,
                    )
                }
            }

            SettingsDestination.ABOUT -> item(key = "about-options") {
                ModernSettingsGroup(stringResource(R.string.settings_page_about)) {
                    HubSettingRow(
                        title = "OmniStream",
                        description = stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        leading = {
                            Icon(Icons.Default.Info, contentDescription = null, tint = HubColors.AccentSoft)
                        },
                    )
                    SettingsDivider()
                    HubSettingRow(
                        title = stringResource(R.string.settings_open_source_title),
                        description = stringResource(R.string.settings_open_source_description),
                    )
                }
            }

            SettingsDestination.ADDONS -> Unit

            SettingsDestination.ROOT -> Unit
        }
    }
}

@Composable
private fun ModernSettingsGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(HubTokens.Space.sm)) {
        HubSectionLabel(text = title, modifier = Modifier.padding(horizontal = HubTokens.Space.xs))
        HubGlassCard(content = content)
    }
}

@Composable
private fun ThemePreviewChoice(
    variant: HubThemeVariant,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val preview = when (variant) {
        HubThemeVariant.NORMAL -> R.drawable.theme_normal_preview
        HubThemeVariant.CYBERPUNK -> R.drawable.theme_cyberpunk_preview
        HubThemeVariant.NETFLIXY -> R.drawable.theme_netflixy_preview
        HubThemeVariant.PRIMEFLY -> R.drawable.theme_primefly_preview
    }
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HubColors.Surface)
            .border(if (selected) 3.dp else 1.dp, if (selected) HubColors.Accent else HubColors.Border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(preview),
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(THEME_PREVIEW_ASPECT_RATIO)
                .clip(RoundedCornerShape(12.dp)),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = HubColors.Text,
            modifier = Modifier.padding(vertical = 8.dp),
            maxLines = 1,
        )
    }
}

private const val THEME_PREVIEW_ASPECT_RATIO = 720f / 1600f

@Composable
private fun SubtitleColorChoice(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) HubColors.Accent else HubColors.Border,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .background(color, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        )
    }
}

@Composable
private fun OpacitySetting(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = HubTokens.Space.lg, vertical = HubTokens.Space.sm),
        verticalArrangement = Arrangement.spacedBy(HubTokens.Space.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = HubColors.Text)
            Text("${value.coerceIn(0, 100)}%", style = MaterialTheme.typography.bodyLarge, color = HubColors.AccentSoft)
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            factory = { context ->
                android.widget.SeekBar(context).apply {
                    max = 100
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) onValueChange(progress)
                        }
                        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
                    })
                }
            },
            update = { if (it.progress != value) it.progress = value.coerceIn(0, 100) },
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(HubTokens.Space.hairline)
            .background(HubColors.Border.copy(alpha = 0.7f)),
    )
}

@Composable
private fun SettingsIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(HubTokens.Size.touchTarget)
            .clip(RoundedCornerShape(HubTokens.Radius.lg))
            .background(HubColors.SurfaceStrong.copy(alpha = HubTokens.Opacity.glass))
            .border(1.dp, HubColors.Border, RoundedCornerShape(HubTokens.Radius.lg))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = HubColors.Text)
    }
}

@Composable
private fun SettingsStatusBadge(connected: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(HubTokens.Radius.full))
            .background(
                if (connected) HubColors.Fresh.copy(alpha = 0.16f) else HubColors.SurfaceStrong,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(
                if (connected) R.string.settings_status_connected else R.string.settings_status_not_connected,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (connected) HubColors.Fresh else HubColors.TextFaint,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun LegacySettingsScreen(graph: DataGraph, onBack: () -> Unit) {
    val viewModel = hubViewModel { SettingsViewModel(graph) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val traktLink by viewModel.traktLink.collectAsStateWithLifecycle()

    var subtitlePickerOpen by remember { mutableStateOf(false) }
    var audioPickerOpen by remember { mutableStateOf(false) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        when {
            traktLink != null -> viewModel.dismissTraktLink()
            subtitlePickerOpen -> subtitlePickerOpen = false
            audioPickerOpen -> audioPickerOpen = false
            else -> onBack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Pinned outside the LazyColumn on purpose: the list's initial focus
        // lands on the first focusable card below, and Compose's focus-driven
        // scroll-into-view then shifts the whole list up to seat that card
        // near the top — taking this non-focusable title with it, off the
        // top of the screen, before the user ever presses a key. A fixed
        // header can't be scrolled away by a focus change it isn't part of.
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displayLarge,
            color = HubColors.Text,
            modifier = Modifier
                .padding(horizontal = HubDimens.ScreenPaddingHorizontal)
                .padding(
                    top = if (isLandscape) 8.dp else HubDimens.ScreenPaddingVertical * 2,
                    bottom = if (isLandscape) 6.dp else 18.dp,
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = HubDimens.ScreenPaddingHorizontal,
                end = HubDimens.ScreenPaddingHorizontal,
                bottom = if (isLandscape) 8.dp else HubDimens.ScreenPaddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 12.dp else 22.dp),
        ) {
        item(key = "interface") {
            SettingsCard(title = stringResource(R.string.settings_section_interface)) {
                SettingsRow(label = stringResource(R.string.settings_language)) {
                    val langs = listOf(
                        "pt" to stringResource(R.string.lang_pt),
                        "en" to stringResource(R.string.lang_en),
                        "es" to stringResource(R.string.lang_es),
                    )
                    langs.forEach { (code, name) ->
                        HubButton(
                            text = name,
                            primary = state.language == code,
                            onClick = { viewModel.setLanguage(code) }
                        )
                    }
                }
            }
        }

        item(key = "theme") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val doubleTapHint = stringResource(R.string.settings_theme_double_tap_hint)
            SettingsCard(title = stringResource(R.string.settings_section_theme)) {
                SettingsRow(label = stringResource(R.string.settings_theme_variant)) {
                    val themes = listOf(
                        HubThemeVariant.NORMAL to stringResource(R.string.menu_theme_normal),
                        HubThemeVariant.CYBERPUNK to stringResource(R.string.menu_theme_cyberpunk),
                        HubThemeVariant.NETFLIXY to stringResource(R.string.menu_theme_netflixy),
                        HubThemeVariant.PRIMEFLY to stringResource(R.string.menu_theme_primefly),
                    )
                    themes.forEach { (variant, name) ->
                        HubButton(
                            text = name,
                            primary = HubColors.variant == variant,
                            onClick = {
                                viewModel.setTheme(variant)
                                // Netflixy and Primefly are the two themes
                                // where a tap previews instead of opening —
                                // see PosterCard's requireDoubleTapToOpen.
                                // Nothing else on the screen says so, so the
                                // first thing picking either does is explain it.
                                if (variant == HubThemeVariant.NETFLIXY || variant == HubThemeVariant.PRIMEFLY) {
                                    android.widget.Toast.makeText(context, doubleTapHint, android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                }
            }
        }

        item(key = "library") {
            SettingsCard(title = stringResource(R.string.settings_section_library)) {
                SettingsRow(label = stringResource(R.string.settings_library_provider)) {
                    val providers = listOf(
                        LibraryProvider.MDBLIST to stringResource(R.string.settings_library_mdblist),
                        LibraryProvider.TRAKT to stringResource(R.string.settings_library_trakt),
                        LibraryProvider.SIMKL to stringResource(R.string.settings_library_simkl),
                    )
                    providers.forEach { (provider, name) ->
                        HubButton(
                            text = name,
                            primary = state.libraryProvider == provider,
                            onClick = { viewModel.setLibraryProvider(provider) },
                        )
                    }
                }

                SettingsRow(label = stringResource(R.string.settings_dim_unwatched_episodes)) {
                    HubButton(
                        text = stringResource(
                            if (state.dimUnwatchedEpisodes) R.string.settings_on else R.string.settings_off,
                        ),
                        primary = state.dimUnwatchedEpisodes,
                        onClick = viewModel::toggleDimUnwatchedEpisodes,
                    )
                }

                SettingsRow(
                    label = state.traktAccount?.handle
                        ?: stringResource(R.string.settings_trakt_not_connected),
                ) {
                    if (state.traktAccount == null) {
                        HubButton(
                            text = stringResource(R.string.settings_trakt_connect),
                            primary = true,
                            onClick = viewModel::beginTraktLink,
                        )
                    } else {
                        HubButton(
                            text = stringResource(R.string.settings_trakt_disconnect),
                            onClick = viewModel::unlinkTrakt,
                        )
                    }
                }

                SettingsRow(
                    label = stringResource(
                        if (state.simklLinked) R.string.settings_simkl_connected else R.string.settings_simkl_not_connected,
                    ),
                ) {
                    HubButton(
                        text = stringResource(
                            if (state.simklLinked) R.string.settings_simkl_disconnect else R.string.settings_simkl_connect,
                        ),
                        primary = !state.simklLinked,
                        onClick = if (state.simklLinked) viewModel::unlinkSimkl else viewModel::beginSimklLink,
                    )
                }
            }
        }

        item(key = "subtitles") {
            SettingsCard(title = stringResource(R.string.settings_section_subtitles)) {
                SettingsRow(label = stringResource(R.string.settings_subtitle_auto_download)) {
                    HubButton(
                        text = stringResource(
                            if (state.subtitleAutoDownload) R.string.settings_on else R.string.settings_off,
                        ),
                        primary = state.subtitleAutoDownload,
                        onClick = viewModel::toggleSubtitleAutoDownload
                    )
                }
                
                SettingsRow(label = stringResource(R.string.settings_subtitle_default_lang)) {
                    val currentName = ALL_LANGUAGES.find { it.first == state.subtitleLanguage }?.second ?: state.subtitleLanguage
                    HubButton(
                        text = currentName,
                        primary = true,
                        onClick = { subtitlePickerOpen = true }
                    )
                }

                SettingsRow(label = stringResource(R.string.settings_subtitle_color)) {
                    val colors = listOf(
                        "yellow" to stringResource(R.string.color_yellow),
                        "white" to stringResource(R.string.color_white),
                        "red" to stringResource(R.string.color_red),
                        "blue" to stringResource(R.string.color_blue)
                    )
                    colors.forEach { (code, name) ->
                        HubButton(
                            text = name,
                            primary = state.subtitleColor == code,
                            onClick = { viewModel.setSubtitleColor(code) }
                        )
                    }
                }
            }
        }

        item(key = "player") {
            SettingsCard(title = stringResource(R.string.settings_section_player)) {
                SettingsRow(label = stringResource(R.string.settings_audio_preferred_lang)) {
                    val currentName = ALL_LANGUAGES.find { it.first == state.audioLanguage }?.second ?: state.audioLanguage
                    HubButton(
                        text = currentName,
                        primary = true,
                        onClick = { audioPickerOpen = true }
                    )
                }
            }
        }

        item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (subtitlePickerOpen) {
        LanguagePickerOverlay(
            title = stringResource(R.string.settings_subtitle_default_lang),
            languages = ALL_LANGUAGES,
            selectedCode = state.subtitleLanguage,
            onSelect = { viewModel.setSubtitleLanguage(it); subtitlePickerOpen = false },
        )
    }

    if (audioPickerOpen) {
        LanguagePickerOverlay(
            title = stringResource(R.string.settings_audio_preferred_lang),
            languages = ALL_LANGUAGES,
            selectedCode = state.audioLanguage,
            onSelect = { viewModel.setAudioLanguage(it); audioPickerOpen = false },
        )
    }

    traktLink?.let { link ->
        TraktLinkOverlay(
            state = link,
            onRetry = viewModel::beginTraktLink,
            onDismiss = viewModel::dismissTraktLink,
        )
    }
    }
}


@Composable
private fun SettingsCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HubColors.Surface.copy(alpha = 0.65f))
            .border(1.dp, HubColors.Border, RoundedCornerShape(14.dp))
            .padding(if (compact) 12.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = HubColors.Text)
        content()
    }
}

@Composable
private fun SettingsRow(label: String, content: @Composable () -> Unit) {
    val compact = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HubColors.TextDim)
        // Scrollable, not just wrapped in spacedBy: a phone-width card is
        // narrower than four option buttons laid out at their natural
        // width, and this row used to just let the last one run off the
        // edge — theme, with four options, and subtitle colour, with four
        // more, both did it before this scrolled instead of clipping.
        Row(
            modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
private fun LanguagePickerOverlay(
    title: String,
    languages: List<Pair<String, String>>,
    selectedCode: String,
    onSelect: (String) -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onSelect(selectedCode) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = HubTokens.Opacity.scrim))
                .padding(HubTokens.Space.lg),
            contentAlignment = Alignment.Center,
        ) {
            HubGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .fillMaxHeight(0.82f),
                strong = true,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(HubTokens.Space.xxl),
                    verticalArrangement = Arrangement.spacedBy(HubTokens.Space.lg),
                ) {
                    Text(title, style = MaterialTheme.typography.headlineLarge, color = HubColors.Text)
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(HubTokens.Space.sm),
                        contentPadding = PaddingValues(bottom = HubTokens.Space.lg),
                    ) {
                        items(languages) { (code, name) ->
                            HubButton(
                                text = name,
                                primary = code == selectedCode,
                                onClick = { onSelect(code) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
