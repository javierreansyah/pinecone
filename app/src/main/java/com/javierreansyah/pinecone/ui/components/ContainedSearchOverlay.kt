package com.javierreansyah.pinecone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_backward
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Mic
import com.javierreansyah.pinecone.R

@Composable
fun ContainedSearchOverlay(
    textFieldState: TextFieldState,
    placeholderText: String,
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    onVoiceResult: ((String) -> Unit)? = null,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchBarState = rememberContainedSearchBarState(initialValue = SearchBarValue.Expanded)

    val launchVoiceSearch = rememberVoiceSearchLauncher { spokenText ->
        textFieldState.edit { replace(0, length, spokenText) }
        onVoiceResult?.invoke(spokenText)
        keyboardController?.hide()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }

    PredictiveBackOverlay(
        onClose = {
            focusManager.clearFocus()
            keyboardController?.hide()
            onClose()
        },
        modifier = modifier,
        backgroundColor = backgroundColor,
    ) { dismiss ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            SearchBarDefaults.InputField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester),
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                colors = SearchBarDefaults.inputFieldColors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    onSearch(textFieldState.text.toString())
                },
                placeholder = {
                    Text(
                        text = placeholderText,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            dismiss()
                        },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Chevron_backward,
                            contentDescription = stringResource(R.string.action_back),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp),
                    ) {
                        val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                        val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                        AnimatedContent(
                            targetState = textFieldState.text.isNotEmpty(),
                            transitionSpec = {
                                fadeIn(animationSpec = effectsSpec) + scaleIn(
                                    initialScale = 0.85f,
                                    animationSpec = spatialSpec,
                                ) togetherWith fadeOut(animationSpec = effectsSpec) + scaleOut(
                                    targetScale = 0.85f,
                                    animationSpec = spatialSpec,
                                )
                            },
                            label = "searchTrailingIcon",
                        ) { hasQuery ->
                            if (hasQuery) {
                                IconButton(
                                    onClick = {
                                        textFieldState.edit { replace(0, length, "") }
                                        onClear?.invoke()
                                    },
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Outlined.Close,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            } else {
                                IconButton(onClick = { launchVoiceSearch() }) {
                                    Icon(
                                        imageVector = MaterialSymbols.Outlined.Mic,
                                        contentDescription = stringResource(R.string.action_voice_search),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                },
            )

            content()
        }
    }
}
