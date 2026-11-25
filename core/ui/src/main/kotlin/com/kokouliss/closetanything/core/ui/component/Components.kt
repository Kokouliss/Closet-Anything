package com.kokouliss.closetanything.core.ui.component

import android.net.ConnectivityManager
import android.net.Network
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kokouliss.closetanything.core.ui.R
import com.kokouliss.closetanything.core.ui.theme.Motion

/** What a screen shows while it waits. A screen reader hears "Loading" once. */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    val loading = stringResource(R.string.core_ui_loading)
    Box(
        modifier.fillMaxSize().clearAndSetSemantics {
            contentDescription = loading
            liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** Centered icon, title and explanation for a screen that has nothing to show yet. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    // Centred while it fits; scrolls once large text makes it taller than the screen.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(icon, null, Modifier.size(40.dp), MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            actions()
        }
    }
}

/** Menu entry colours for an action that deletes something: red, as Material asks. */
@Composable
fun destructiveMenuItemColors(): MenuItemColors = MenuDefaults.itemColors(
    textColor = MaterialTheme.colorScheme.error,
    leadingIconColor = MaterialTheme.colorScheme.error,
)

/** A circle with the first letter of [name]. Decorative: the name is always shown next to it. */
@Composable
fun InitialAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** True while the phone has no way to the internet. Changes made then wait on the phone. */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    return produceState(false) {
        val network = context.getSystemService(ConnectivityManager::class.java)
        value = network.activeNetwork == null
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                value = false
            }

            override fun onLost(network: Network) {
                value = true
            }
        }
        network.registerDefaultNetworkCallback(callback)
        awaitDispose { network.unregisterNetworkCallback(callback) }
    }.value
}

/** The logo of the app on the dark tile of its launcher icon. Decorative: the name is shown next to it. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.core_ui_logo_closet),
        contentDescription = null,
        modifier = modifier.background(LogoTile, LogoTileShape),
    )
}

// The ink of the AnyKind logos and the corners of their icon tile, 24 of 100.
internal val LogoTile = Color(0xFF171A21)
internal val LogoTileShape = RoundedCornerShape(percent = 24)

/** Gives a little under the finger. [source] is the one the click listens to. */
@Composable
fun Modifier.pressScale(source: InteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) Motion.PRESS_SCALE else 1f, Motion.momentum(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A floating button that pops in and out, not on and off. */
@Composable
fun PopIn(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible, enter = scaleIn(Motion.momentum()) + fadeIn(), exit = scaleOut() + fadeOut()) { content() }
}

/** The loader while [loading], then [content] fades in over it. A screen that opens loaded just shows. */
@Composable
fun LoadingCrossfade(loading: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Crossfade(loading, modifier, tween(Motion.LONG), label = "loaded") { if (it) LoadingBox() else content() }
}

/** The logo of AnyKind, the collection this app belongs to. Its letters take the colour of the text around it. */
@Composable
fun AnyKindWordmark(modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(painterResource(R.drawable.core_ui_logo_anykind_mark), contentDescription = "AnyKind")
        Image(
            painter = painterResource(R.drawable.core_ui_logo_anykind_letters),
            contentDescription = null,
            colorFilter = ColorFilter.tint(LocalContentColor.current),
        )
    }
}

/** The widest a page of content gets, so rows do not stretch across a tablet. Centred past that. */
fun Modifier.pageWidth(): Modifier = fillMaxWidth().wrapContentWidth().widthIn(max = 840.dp).fillMaxWidth()

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        AppIcon(AppIcons.ArrowBack, stringResource(R.string.core_ui_back))
    }
}
