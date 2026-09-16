package com.seasonyuu.fnmusic.core.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import com.kyant.backdrop.Backdrop
import kotlin.math.*
import kotlinx.coroutines.currentCoroutineContext

enum class LiquidMenuTransition { Attached, Detached }

sealed interface LiquidMenuAnchorShape {
    data object Capsule : LiquidMenuAnchorShape
    data class RoundedRectangle(val radius: Dp) : LiquidMenuAnchorShape {
        init { require(radius.value.isFinite() && radius.value >= 0f) }
    }
}

/** Register the visible surface separately from the trigger's touch target. */
class LiquidMenuAnchorScope internal constructor(
    val toggle: () -> Unit,
    private val registerSurface: (LiquidMenuAnchorShape) -> Modifier,
    val foregroundModifier: Modifier,
) {
    fun surfaceModifier(shape: LiquidMenuAnchorShape = LiquidMenuAnchorShape.Capsule): Modifier =
        registerSurface(shape)
}

sealed interface LiquidMenuEntry {
    val id: String
}

data class LiquidMenuItem(
    override val id: String,
    val text: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val icon: (@Composable () -> Unit)? = null,
) : LiquidMenuEntry

data class LiquidMenuDivider(override val id: String) : LiquidMenuEntry

private class MenuRegistration(
    val expanded: State<Boolean>,
    val items: State<List<LiquidMenuEntry>>,
    val onDismiss: State<() -> Unit>,
    val onSelect: State<(String) -> Unit>,
    val backdrop: State<Backdrop>,
    val locals: State<CompositionLocalContext>,
) {
    var anchor by mutableStateOf(Rect.Zero)
    var surface by mutableStateOf(Rect.Zero)
    var foreground by mutableStateOf(Rect.Zero)
    var shape by mutableStateOf<LiquidMenuAnchorShape>(LiquidMenuAnchorShape.Capsule)
    var layer: GraphicsLayer? = null
    var transition by mutableStateOf(LiquidMenuTransition.Detached)
    var hidden by mutableStateOf(false)
    val focus = FocusRequester()
}

private class MenuSession(val owner: MenuRegistration) {
    var dismissed by mutableStateOf(false)

    fun dismiss() {
        if (!dismissed) {
            dismissed = true
            owner.onDismiss.value()
        }
    }

    fun select(id: String) {
        if (dismissed || !owner.expanded.value) return
        if (owner.items.value.none { it.id == id && it is LiquidMenuItem && it.enabled }) return
        dismiss()
        owner.onSelect.value(id)
    }
}

private class MenuHostState {
    var active by mutableStateOf<MenuSession?>(null)
    var origin by mutableStateOf(Offset.Zero)
}

private val LocalMenuHost = staticCompositionLocalOf<MenuHostState?> { null }

/** Place once above navigation chrome, outside all backdrop recording layers. */
@Composable
fun LiquidMenuHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val host = remember { MenuHostState() }
    BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { host.origin = it.positionInWindow() }
    ) {
        CompositionLocalProvider(LocalMenuHost provides host) {
            Box(
                Modifier.fillMaxSize()
                    .then(if (host.active != null) Modifier.clearAndSetSemantics {} else Modifier)
            ) {
                content()
            }
            host.active?.let { session ->
                key(session) {
                    CompositionLocalProvider(session.owner.locals.value) {
                        MenuOverlay(
                            host,
                            session,
                            Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                        )
                    }
                }
            }
        }
    }
}

/** The trigger owns its click handling; its layout is retained while the liquid replaces it. */
@Composable
fun LiquidMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    backdrop: Backdrop,
    items: List<LiquidMenuEntry>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    transition: LiquidMenuTransition = LiquidMenuTransition.Detached,
    trigger: @Composable LiquidMenuAnchorScope.(toggle: () -> Unit) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    require(items.map { it.id }.distinct().size == items.size) {
        "LiquidMenu item IDs must be unique"
    }
    val host = checkNotNull(LocalMenuHost.current) { "LiquidMenu requires LiquidMenuHost" }
    val expandedState = rememberUpdatedState(expanded)
    val itemState = rememberUpdatedState(items)
    val dismissState = rememberUpdatedState(onDismissRequest)
    val selectState = rememberUpdatedState(onSelect)
    val backdropState = rememberUpdatedState(backdrop)
    val locals = rememberUpdatedState(currentCompositionLocalContext)
    val owner = remember {
        MenuRegistration(expandedState, itemState, dismissState, selectState, backdropState, locals)
    }
    val foregroundLayer = rememberGraphicsLayer()
    val hiddenLayer = rememberGraphicsLayer()
    SideEffect { owner.transition = transition; owner.layer = foregroundLayer }
    val toggle = { if (expanded) host.active?.dismiss() else onExpandedChange(true); Unit }
    val scope = LiquidMenuAnchorScope(
        toggle,
        { shape -> Modifier.onGloballyPositioned {
            owner.surface = it.boundsInWindow()
            owner.shape = shape
        } },
        if (transition == LiquidMenuTransition.Attached) Modifier
            .onPlaced { owner.foreground = it.boundsInWindow() }
            .drawWithContent {
                foregroundLayer.record { this@drawWithContent.drawContent() }
                drawLayer(foregroundLayer)
            } else Modifier,
    )
    LaunchedEffect(expanded) {
        if (expanded) {
            if (host.active?.owner !== owner) {
                host.active?.let {
                    it.dismiss()
                    it.owner.hidden = false
                }
                host.active = MenuSession(owner)
            } else host.active?.dismissed = false
        }
    }
    DisposableEffect(host, owner) {
        onDispose {
            if (host.active?.owner === owner) host.active = null
            owner.hidden = false
            owner.layer = null
        }
    }
    Box(
        modifier
            .onGloballyPositioned { if (it.isAttached) owner.anchor = it.boundsInWindow() }
            .focusRequester(owner.focus)
            .drawWithContent {
                if (owner.hidden) hiddenLayer.record { this@drawWithContent.drawContent() }
                else drawContent()
            }
            .then(if (owner.hidden) Modifier.clearAndSetSemantics {} else Modifier)
    ) {
        scope.trigger(toggle)
    }
}

@Composable
private fun MenuOverlay(host: MenuHostState, session: MenuSession, window: Size) {
    val owner = session.owner
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val inset = WindowInsets.safeDrawing.union(WindowInsets.ime)
    val d = density.density
    val safe =
        Rect(
            (inset.getLeft(density, direction) + 12 * d).coerceAtMost(window.width / 2),
            (inset.getTop(density) + 12 * d).coerceAtMost(window.height / 2),
            (window.width - inset.getRight(density, direction) - 12 * d).coerceAtLeast(
                window.width / 2
            ),
            (window.height - inset.getBottom(density) - 12 * d).coerceAtLeast(window.height / 2),
        )
    val currentAnchor = owner.anchor.translate(-host.origin)
    val currentSurface = owner.surface.translate(-host.origin)
    val rendering = rememberMenuRendering()
    // Freeze geometry during a session; moving anchors close instead of being chased.
    val anchor = remember { currentAnchor }
    val surface = remember { currentSurface }
    val full = rendering == MenuRendering.Shader &&
        owner.transition == LiquidMenuTransition.Attached && !surface.isEmpty && !owner.foreground.isEmpty
    val startWindow = remember { window }
    val startRadius = when (val shape = owner.shape) {
        LiquidMenuAnchorShape.Capsule -> min(surface.width, surface.height) / 2
        is LiquidMenuAnchorShape.RoundedRectangle -> with(density) { shape.radius.toPx() }
    }
    val entries = owner.items.value
    val hasSelection = entries.any { it is LiquidMenuItem && it.selected }
    val itemPadding = 12.dp
    val itemSpacing = 12.dp
    val iconSize = 24.dp
    val selectionSize = 16.dp
    val textInset = 24.dp + itemPadding * 2 + iconSize + itemSpacing +
        (if (hasSelection) selectionSize + itemSpacing else 0.dp)
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelLarge
    val width = min(200 * d, safe.width)
    val heights =
        entries.map { entry ->
            if (entry is LiquidMenuItem) {
                val layout =
                    measurer.measure(
                        entry.text,
                        style,
                        constraints = Constraints(maxWidth = max(1, (width - with(density) { textInset.toPx() }).toInt())),
                    )
                max(48 * d, layout.size.height + 24 * d)
            } else 9 * d
        }
    val naturalHeight = heights.sum() + 24 * d + max(0, entries.size - 1) * 2 * d
    val target = if (full) menuDestination(anchor, Size(width, naturalHeight), safe)
        else menuDetachedDestination(anchor, Size(width, naturalHeight), safe, 8 * d)
    val scrollable = naturalHeight > target.height + .5f
    val progress = remember { Animatable(0f) }
    val open = owner.expanded.value && !session.dismissed
    val scroll = rememberScrollState()
    var hover by remember { mutableStateOf<String?>(null) }
    var touch by remember { mutableStateOf(Offset(-1f, -1f)) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dragAnimation = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(dragOffset, dragging) {
        if (dragging) dragAnimation.snapTo(dragOffset)
        else dragAnimation.animateTo(Offset.Zero, spring(.5f, 300f, Offset.VisibilityThreshold))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(open, full) {
        if (anchor.isEmpty || target.isEmpty) {
            session.dismiss()
            owner.hidden = false
            if (host.active === session) host.active = null
            return@LaunchedEffect
        }
        owner.hidden = full
        val noMotion = currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f
        if (noMotion) progress.snapTo(if (open) 1f else 0f)
        else if (!full) progress.animateTo(if (open) 1f else 0f, tween(160))
        else
            progress.animateTo(
                if (open) 1f else 0f,
                spring(
                    dampingRatio = 16f / (2f * sqrt(120f)),
                    stiffness = 120f,
                    visibilityThreshold = .001f,
                ),
                initialVelocity = if (open) progress.velocity else min(progress.velocity, -2.5f),
            )
        if (!open) {
            owner.hidden = false
            if (host.active === session) host.active = null
            runCatching { owner.focus.requestFocus() }
        }
    }
    // A moved/removed anchor must not leave an orphaned modal surface behind.
    LaunchedEffect(window, currentAnchor, currentSurface) {
        if (window != startWindow || currentAnchor != anchor || (full && currentSurface != surface)) {
            session.dismiss()
            owner.hidden = false
            if (host.active === session) host.active = null
        }
    }
    BackHandler { session.dismiss() }
    val raw = progress.value
    val detachedOffset = Offset(0f, (if (target.center.y >= anchor.center.y) -8f else 8f) * d * (1f - raw))
    val blobs = if (full) menuBlobs(surface, target, raw, d, startRadius)
        else MenuBlobs(Rect.Zero, target.translate(detachedOffset), 0f, min(32 * d, min(target.width, target.height) / 2), 0f)
    val pressProgress by androidx.compose.animation.core.animateFloatAsState(
        if (dragging) 1f else 0f,
        spring(.5f, 300f, .001f),
        label = "menu-press",
    )
    val deformation = liquidDragDeformation(blobs.body.size, dragAnimation.value, pressProgress, 4 * d)
    val half = Offset(blobs.body.width / 2 * deformation.scale.x, blobs.body.height / 2 * deformation.scale.y)
    val center = blobs.body.center + deformation.translation
    val moved = blobs.copy(body = Rect(center - half, center + half))
    val hoveredIndex = entries.indexOfFirst { it.id == hover }
    LaunchedEffect(hover, scrollable) {
        if (scrollable && hoveredIndex >= 0) {
            val top = 12 * d + heights.take(hoveredIndex).sum() + hoveredIndex * 2 * d
            val bottom = top + heights[hoveredIndex]
            val next =
                when {
                    top < scroll.value -> top - 12 * d
                    bottom > scroll.value + target.height -> bottom - target.height + 12 * d
                    else -> scroll.value.toFloat()
                }
            scroll.animateScrollTo(next.roundToInt().coerceIn(0, scroll.maxValue))
        }
    }
    val highlightTop by
        androidx.compose.animation.core.animateFloatAsState(
            if (hoveredIndex < 0) 12 * d
            else 12 * d + heights.take(hoveredIndex).sum() + hoveredIndex * 2 * d - scroll.value,
            androidx.compose.animation.core.tween(150),
            label = "menu-highlight-top",
        )
    val highlightHeight by
        androidx.compose.animation.core.animateFloatAsState(
            heights.getOrNull(hoveredIndex) ?: 48 * d,
            androidx.compose.animation.core.tween(150),
            label = "menu-highlight-height",
        )
    val highlightAlpha by
        androidx.compose.animation.core.animateFloatAsState(
            if (hoveredIndex >= 0) .12f else 0f,
            label = "menu-highlight-alpha",
        )
    val highlightColor = MaterialTheme.colorScheme.onSurface
    fun choose(id: String) {
        if (progress.value >= .8f && owner.expanded.value) session.select(id)
    }
    fun hit(position: Offset): String? {
        if (
            position.x < 0 ||
                position.x > target.width ||
                position.y < 0 ||
                position.y > target.height
        )
            return null
        var y = 12 * d - scroll.value
        entries.forEachIndexed { index, entry ->
            if (
                position.y >= y &&
                    position.y < y + heights[index] &&
                    entry is LiquidMenuItem &&
                    entry.enabled
            )
                return entry.id
            y += heights[index] + 2 * d
        }
        return null
    }
    Box(Modifier.fillMaxSize().testTag("liquid-menu-overlay").semantics { paneTitle = "菜单" }) {
        Box(
            Modifier.matchParentSize()
                .testTag("liquid-menu-dismiss")
                .pointerInput(session) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        session.dismiss()
                    }
                }
                .semantics {
                    onClick("关闭菜单") {
                        session.dismiss()
                        true
                    }
                }
        )
        LiquidMenuSurface(
            owner.backdrop.value, moved, touch = touch, rendering = rendering,
            opacity = if (full) 1f else raw.coerceIn(0f, 1f),
        )
        if (full) {
            val foregroundAlpha = menuForegroundAlpha(surface, moved.body)
            val foreground = owner.foreground.translate(-host.origin)
            val scale = min(moved.body.width / surface.width, moved.body.height / surface.height)
            val topLeft = moved.body.center + (foreground.topLeft - surface.center) * scale
            val position = IntOffset(floor(topLeft.x).toInt(), floor(topLeft.y).toInt())
            Box(Modifier.offset { position }
                .size(with(density) { foreground.width.toDp() }, with(density) { foreground.height.toDp() })
                .testTag("liquid-menu-foreground")
                .graphicsLayer {
                    alpha = foregroundAlpha
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = topLeft.x - position.x
                    translationY = topLeft.y - position.y
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind { owner.layer?.let { drawLayer(it) } })
        }
        if (raw > (if (full) .3f else 0f)) {
            // Request focus only after content exists, including zero-duration transitions.
            LaunchedEffect(open, raw >= .8f) { if (open && raw >= .8f) focus.requestFocus() }
            val body = moved.body
            val alpha = if (full) ((raw - .3f) / .4f).coerceIn(0f, 1f) else raw.coerceIn(0f, 1f)
            Column(
                Modifier.offset { IntOffset(body.left.roundToInt(), body.top.roundToInt()) }
                    .size(
                        with(density) { target.width.toDp() },
                        with(density) { target.height.toDp() },
                    )
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = body.width / target.width.coerceAtLeast(1f)
                        scaleY = body.height / target.height.coerceAtLeast(1f)
                        this.alpha = alpha
                    }
                    .clip(RoundedCornerShape(32.dp))
                    .drawBehind {
                        drawRoundRect(
                            highlightColor.copy(alpha = highlightAlpha),
                            Offset(12 * d, highlightTop),
                            Size((target.width - 24 * d).coerceAtLeast(0f), highlightHeight),
                            CornerRadius(24 * d),
                        )
                    }
                    .testTag("liquid-menu-content")
                    .onPlaced { contentCoordinates = it }
                    .focusRequester(focus)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false
                        else
                            when (event.key) {
                                Key.Escape -> {
                                    session.dismiss()
                                    true
                                }
                                Key.DirectionDown,
                                Key.DirectionUp,
                                Key.Tab -> {
                                    val enabled =
                                        entries.filterIsInstance<LiquidMenuItem>().filter {
                                            it.enabled
                                        }
                                    if (enabled.isNotEmpty()) {
                                        val at = enabled.indexOfFirst { it.id == hover }
                                        val next =
                                            if (
                                                event.key == Key.DirectionDown ||
                                                    (event.key == Key.Tab && !event.isShiftPressed)
                                            )
                                                (at + 1).mod(enabled.size)
                                            else
                                                (if (at < 0) enabled.size - 1 else at - 1).mod(
                                                    enabled.size
                                                )
                                        hover = enabled[next].id
                                    }
                                    true
                                }
                                Key.Enter,
                                Key.NumPadEnter,
                                Key.Spacebar -> {
                                    (hover
                                            ?: entries
                                                .filterIsInstance<LiquidMenuItem>()
                                                .firstOrNull { it.enabled }
                                                ?.id)
                                        ?.let(::choose)
                                    true
                                }
                                else -> false
                            }
                    }
                    .focusable()
                    .then(
                        if (!scrollable)
                            Modifier.pointerInput(entries, open) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    var outside = false
                                    var canceled = false
                                    var position = down.position
                                    hover = hit(position)
                                    val start = contentCoordinates?.localToWindow(down.position) ?: (body.topLeft + down.position + host.origin)
                                    dragging = true
                                    dragOffset = Offset.Zero
                                    touch = start - host.origin
                                    try {
                                        do {
                                            val event = awaitPointerEvent()
                                            val change =
                                                event.changes.firstOrNull { it.id == down.id }
                                                    ?: break
                                            canceled =
                                                canceled ||
                                                    change.isConsumed ||
                                                    event.changes.size > 1
                                            position = change.position
                                            val out =
                                                position.x !in 0f..target.width ||
                                                    position.y !in 0f..target.height
                                            outside = outside || out
                                            // Window coordinates avoid feeding our own scale/translation back into the drag.
                                            val pointer = contentCoordinates?.localToWindow(position)
                                                ?: (body.topLeft + position + host.origin)
                                            dragOffset = pointer - start
                                            touch = pointer - host.origin
                                            hover = hit(position)
                                            event.changes.forEach { it.consume() }
                                        } while (change.pressed)
                                        if (!outside && !canceled) hit(position)?.let(::choose)
                                    } finally {
                                        hover = null
                                        touch = Offset(-1f, -1f)
                                        dragging = false
                                        dragOffset = Offset.Zero
                                    }
                                }
                            }
                        else Modifier
                    )
                    .verticalScroll(scroll, enabled = scrollable)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    if (entry is LiquidMenuDivider)
                        Box(
                            Modifier.fillMaxWidth().height(with(density) { heights[index].toDp() }),
                            contentAlignment = Alignment.Center,
                        ) {
                            HorizontalDivider()
                        }
                    else if (entry is LiquidMenuItem) {
                        val active = entry.enabled && raw >= .8f && open
                        Row(
                            Modifier.fillMaxWidth()
                                .height(with(density) { heights[index].toDp() })
                                .clip(RoundedCornerShape(24.dp))
                                .testTag("liquid-menu-item-${entry.id}")
                                .then(
                                    if (scrollable)
                                        Modifier.clickable(enabled = active) { choose(entry.id) }
                                    else Modifier
                                )
                                .semantics(mergeDescendants = true) {
                                    selected = entry.selected
                                    role = Role.Button
                                    if (!active) disabled()
                                    onClick {
                                        if (active) choose(entry.id)
                                        active
                                    }
                                }
                                .padding(horizontal = itemPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                        ) {
                            val color =
                                if (entry.destructive) MaterialTheme.colorScheme.error
                                else FnTextPrimary
                            CompositionLocalProvider(
                                LocalContentColor provides
                                    color.copy(alpha = if (entry.enabled) 1f else .38f)
                            ) {
                                if (hasSelection) {
                                    // Reserve a leading selection gutter so every item stays aligned.
                                    Box(Modifier.size(selectionSize), contentAlignment = Alignment.Center) {
                                        if (entry.selected) Icon(Icons.Rounded.Check, null, Modifier.size(selectionSize))
                                    }
                                }
                                Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                                    entry.icon?.invoke()
                                }
                                Text(entry.text, modifier = Modifier.weight(1f), style = style, color = LocalContentColor.current)
                            }
                        }
                    }
                }
            }
        }
    }
}
