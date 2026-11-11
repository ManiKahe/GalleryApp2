package com.gallery.app

import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.roundToInt

data class Photo(
    val uri: Uri,
    val width: Int,
    val height: Int,
    val date: Long
)

private data class GridEntry(
    val photo: Photo? = null,
    val label: String? = null,
    val id: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        setContent {
            GalleryTheme { GalleryApp() }
        }
    }
}

@Composable
private fun GalleryTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    val colors = if (dark) {
        darkColorScheme(
            background = Color(0xFF11120F),
            surface = Color(0xFF11120F),
            onBackground = Color(0xFFF1F0E9),
            onSurface = Color(0xFFF1F0E9),
            primary = Color(0xFFF1F0E9),
            onPrimary = Color(0xFF171813),
            surfaceVariant = Color(0xFF22231F),
            onSurfaceVariant = Color(0xFFA7A69D),
            outline = Color(0xFF383932)
        )
    } else {
        lightColorScheme(
            background = Color(0xFFF4F3EE),
            surface = Color(0xFFF4F3EE),
            onBackground = Color(0xFF191A16),
            onSurface = Color(0xFF191A16),
            primary = Color(0xFF191A16),
            onPrimary = Color(0xFFF4F3EE),
            surfaceVariant = Color(0xFFE9E8E1),
            onSurfaceVariant = Color(0xFF686960),
            outline = Color(0xFFD6D5CD)
        )
    }

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
@Composable
private fun GalleryApp() {
    val context = LocalContext.current
    val view = LocalView.current
    val statusBarBackground = MaterialTheme.colorScheme.background
val prefs = remember {
        context.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE)
    }

    var permissionGranted by remember { mutableStateOf(hasImagePermission(context)) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    SideEffect {
        context.findActivity()?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            val lightSystemBars = selectedIndex < 0 && statusBarBackground.luminance() > 0.5f
            controller.isAppearanceLightStatusBars = lightSystemBars
            controller.isAppearanceLightNavigationBars = lightSystemBars
        }
    }
    val thumbnailBounds = remember { mutableStateMapOf<Uri, Rect>() }
    val readyThumbnails = remember { mutableStateMapOf<Uri, Boolean>() }
    var transitionRect by remember { mutableStateOf<Rect?>(null) }
    var closing by remember { mutableStateOf(false) }
    var closingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val transitionProgress = remember { Animatable(1f) }
    val transitionProgressState = transitionProgress.asState()
    val transitionEasing = remember {
        androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
    }
    val reverseTransitionEasing = androidx.compose.animation.core.Easing { fraction ->
        1f - transitionEasing.transform(1f - fraction)
    }
    var horizontal by remember {
        mutableStateOf(prefs.getBoolean("horizontal_scroll", false))
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasImagePermission(context)
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            loading = true
            photos = withContext(Dispatchers.IO) { loadPhotos(context) }
            loading = false
        }
    }

    if (!permissionGranted) {
        PermissionScreen {
            permissionLauncher.launch(imagePermissions())
        }
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PhotoGrid(
            photos = photos,
            loading = loading,
            horizontal = horizontal,
            gridState = gridState,
            hiddenPhotoUri = null,
            onPhotoBounds = { photo, rect ->
                if (thumbnailBounds[photo.uri] != rect) {
                    thumbnailBounds[photo.uri] = rect
                }
            },
            onPhotoReady = { photo ->
                if (readyThumbnails[photo.uri] != true) {
                    readyThumbnails[photo.uri] = true
                }
            },
            onContextMenuStart = { _, _ -> },
            onContextMenuMove = { _, _ -> },
            onContextMenuEnd = { _ -> },
            onContextMenuCancel = {},            onOrientationChange = {
                horizontal = it
                prefs.edit().putBoolean("horizontal_scroll", it).apply()
            },
            onPhotoClick = { photo ->
                val index = photos.indexOf(photo)
                if (index >= 0) {
                    scope.launch {
                        closing = false
                        closingPhotoUri = null
                        transitionRect = thumbnailBounds[photo.uri]
                        transitionProgress.snapTo(0f)
                        selectedIndex = index
                        withFrameNanos { }
                        withFrameNanos { }
                        transitionProgress.animateTo(
                            1f,
                            tween(durationMillis = 280, easing = transitionEasing)
                        )
                    }
                }
            }
        )

        if (selectedIndex >= 0 && photos.isNotEmpty()) {
            PhotoViewer(
                photos = photos,
                initialPage = selectedIndex,
                horizontal = horizontal,
                sourceRect = transitionRect,
                transitionProgress = transitionProgressState,
                closing = closing,
                onClose = { photo ->
                    if (!closing) {
                        closing = true
                        scope.launch {
                            val targetGridIndex = makeEntries(photos)
                                .indexOfFirst { it.photo?.uri == photo.uri }
                            if (targetGridIndex >= 0) {
                                val layout = gridState.layoutInfo
                                val itemHeight = layout.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
                                val centerOffset = if (itemHeight > 0) {
                                    -((layout.viewportSize.height - itemHeight) / 2)
                                } else 0
                                gridState.scrollToItem(targetGridIndex, centerOffset)
                            }
                            kotlinx.coroutines.delay(100)
                            selectedIndex = -1
                            closing = false
                        }
                    }
                }
            )
        }


    }
}

private tailrec fun Context.findActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun hasImagePermission(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun imagePermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun loadPhotos(context: Context): List<Photo> {
    val result = mutableListOf<Photo>()
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT,
        MediaStore.Images.Media.DATE_TAKEN,
        MediaStore.Images.Media.DATE_ADDED
    )

    context.contentResolver.query(
        collection, projection, null, null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

        while (cursor.moveToNext()) {
            val taken = cursor.getLong(takenCol)
            val added = cursor.getLong(addedCol)
            result.add(
                Photo(
                    ContentUris.withAppendedId(collection, cursor.getLong(idCol)),
                    cursor.getInt(widthCol),
                    cursor.getInt(heightCol),
                    if (taken > 0) taken else added * 1000L
                )
            )
        }
    }
    return result
}

private fun dateLabel(timestamp: Long): String {
    val today = Calendar.getInstance()
    val day = Calendar.getInstance().apply { timeInMillis = timestamp }

    if (today.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    ) return "TODAY"

    today.add(Calendar.DAY_OF_YEAR, -1)
    if (today.get(Calendar.YEAR) == day.get(Calendar.YEAR) &&
        today.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR)
    ) return "YESTERDAY"

    return SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        .format(Date(timestamp))
}

private fun makeEntries(photos: List<Photo>): List<GridEntry> =
    buildList {
        photos.groupBy { dateLabel(it.date) }.forEach { (label, group) ->
            add(GridEntry(label = label, id = "header_$label"))
            group.forEach { add(GridEntry(photo = it, id = it.uri.toString())) }
        }
    }

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            "YOUR LIBRARY",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.8.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Photos,\nkept together.",
            fontSize = 32.sp,
            lineHeight = 37.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1.1).sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Allow access to view the images stored on this device.",
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(3.dp),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 13.dp)
        ) {
            Text("Allow photo access", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
@Suppress("UNUSED_PARAMETER")
@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    loading: Boolean,
    horizontal: Boolean,
    gridState: LazyGridState,
    hiddenPhotoUri: Uri?,
    onPhotoBounds: (Photo, Rect) -> Unit,
    onPhotoReady: (Photo) -> Unit,
    onContextMenuStart: (Photo, Offset) -> Unit,
    onContextMenuMove: (Photo, Offset) -> Unit,
    onContextMenuEnd: (Photo) -> Unit,
    onContextMenuCancel: () -> Unit,
    onOrientationChange: (Boolean) -> Unit,
    onPhotoClick: (Photo) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE)
    }
    var columns by remember {
        mutableIntStateOf(prefs.getInt("grid_columns", 3).coerceIn(2, 7))
    }
    val entries = remember(photos) { makeEntries(photos) }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 20.dp, top = 18.dp, bottom = 17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "YOUR LIBRARY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp,
                    color = muted
                )
                Text(
                    "Photos",
                    fontSize = 31.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.1).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    photos.size.toString(),
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "ITEMS",
                    fontSize = 9.sp,
                    letterSpacing = 1.3.sp,
                    color = muted
                )
            }

            Spacer(Modifier.width(14.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f))
                    .clickable { onOrientationChange(!horizontal) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (horizontal) "↔" else "↕",
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
            }
        }

        when {
            loading -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 1.5.dp
                        )
                        Text(
                            "Loading library",
                            fontSize = 12.sp,
                            color = muted
                        )
                    }
                }
            }

            photos.isEmpty() -> {
                Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 34.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            "YOUR LIBRARY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.7.sp,
                            color = muted
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No photos yet",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(7.dp))
                        Text(
                            "Images saved on this device will appear here.",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                        )
                    }
                }
            }

            else -> {
                val gridModifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var pinchAccumulator = 1f
                            awaitFirstDown(requireUnconsumed = false)
                            var previousDistance = 0f

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                if (pressed.size >= 2) {
                                    val a = pressed[0].position
                                    val b = pressed[1].position
                                    val dx = a.x - b.x
                                    val dy = a.y - b.y
                                    val distance = sqrt(dx * dx + dy * dy)

                                    if (previousDistance > 0f) {
                                        pinchAccumulator *= distance / previousDistance

                                        if (pinchAccumulator > 1.35f) {
                                            columns = (columns - 1).coerceAtLeast(2)
                                            prefs.edit().putInt("grid_columns", columns).apply()
                                            pinchAccumulator = 1f
                                        } else if (pinchAccumulator < 0.74f) {
                                            columns = (columns + 1).coerceAtMost(7)
                                            prefs.edit().putInt("grid_columns", columns).apply()
                                            pinchAccumulator = 1f
                                        }
                                    }

                                    previousDistance = distance
                                    pressed.forEach { it.consume() }
                                } else {
                                    previousDistance = 0f
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }

                val gridContent: androidx.compose.foundation.lazy.grid.LazyGridScope.() -> Unit = {
                    items(
                        items = entries,
                        key = { it.id },
                        span = {
                            if (it.photo == null) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                        },
                        contentType = { if (it.photo == null) "date" else "photo" }
                    ) { entry ->
                        val photo = entry.photo

                        if (photo == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, end = 8.dp, top = 17.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (entry.label ?: "").uppercase(Locale.getDefault()),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.15.sp,
                                    color = muted
                                )
                                Spacer(Modifier.width(13.dp))
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f))
                                )
                            }
                        } else {
                            var itemBounds by remember(photo.uri) {
                                mutableStateOf(Rect.Zero)
                            }
                            val request = remember(photo.uri) {
                                ImageRequest.Builder(context)
                                    .data(photo.uri)
                                    .size(256, 256)
                                    .precision(Precision.INEXACT)
                                    .crossfade(false)
                                    .build()
                            }

                            AsyncImage(
                                model = request,
                                contentDescription = "Photo",
                                onSuccess = { onPhotoReady(photo) },
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        alpha = if (photo.uri == hiddenPhotoUri) 0f else 1f
                                    }
                                    .onGloballyPositioned {
                                        itemBounds = it.boundsInRoot()
                                        onPhotoBounds(photo, itemBounds)
                                    }
                                    .pointerInput(photo.uri) {
                                        detectTapGestures(
                                            onTap = { onPhotoClick(photo) }
                                        )
                                    }
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = gridModifier,
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    content = gridContent
                )
            }
        }
    }
}
@Composable
private fun PhotoViewer(
    photos: List<Photo>,
    initialPage: Int,
    horizontal: Boolean,
    sourceRect: Rect? = null,
    transitionProgress: State<Float>,
    closing: Boolean,
    onClose: (Photo) -> Unit
) {
    val horizontalState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    val verticalState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    val currentPage = if (horizontal) horizontalState.currentPage else verticalState.currentPage
    val progress = transitionProgress.value.coerceIn(0f, 1f)
    val currentPhoto = photos[currentPage]

    BackHandler {
        onClose(currentPhoto)
    }

    val viewerAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (closing) 0f else 1f,
        animationSpec = tween(80),
        label = "viewerFade"
    ).value

    val controlsAlpha = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (progress >= 0.999f) 1f else 0f,
        animationSpec = tween(180),
        label = "controlsAlpha"
    ).value

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(if (closing) Color.Transparent else Color.Black)
            .graphicsLayer { alpha = viewerAlpha },
        contentAlignment = Alignment.TopStart
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidth = with(density) { maxWidth.toPx() }
        val screenHeight = with(density) { maxHeight.toPx() }

        val imageWidth = currentPhoto.width.coerceAtLeast(1).toFloat()
        val imageHeight = currentPhoto.height.coerceAtLeast(1).toFloat()
        val fit = minOf(screenWidth / imageWidth, screenHeight / imageHeight)

        val endWidth = imageWidth * fit
        val endHeight = imageHeight * fit
        val endLeft = (screenWidth - endWidth) / 2f
        val endTop = (screenHeight - endHeight) / 2f

        val start = sourceRect ?: Rect(endLeft, endTop, endLeft + endWidth, endTop + endHeight)

        val left = start.left + (endLeft - start.left) * progress
        val top = start.top + (endTop - start.top) * progress
        val width = start.width + (endWidth - start.width) * progress
        val height = start.height + (endHeight - start.height) * progress

        val showPager = progress >= 0.999f

        if (horizontal) {
            HorizontalPager(
                state = horizontalState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (showPager) 1f else 0f
                },
                beyondViewportPageCount = 0
            ) { page ->
                ZoomablePhoto(photos[page])
            }
        } else {
            VerticalPager(
                state = verticalState,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    alpha = if (showPager) 1f else 0f
                },
                beyondViewportPageCount = 0
            ) { page ->
                ZoomablePhoto(photos[page])
            }
        }

        if (!showPager) {
            AsyncImage(
                model = currentPhoto.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(
                        width = with(density) { width.coerceAtLeast(1f).toDp() },
                        height = with(density) { height.coerceAtLeast(1f).toDp() }
                    )
                    .graphicsLayer {
                        translationX = left
                        translationY = top
                        clip = true
                        shape = RoundedCornerShape(1.dp)
                    }
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 18.dp, vertical = 7.dp)
                .graphicsLayer { alpha = controlsAlpha },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(enabled = controlsAlpha >= 0.99f) {
                        onClose(currentPhoto)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "‹",
                    color = Color.White,
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Light
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                "${currentPage + 1} / ${photos.size}",
                color = Color.White.copy(alpha = 0.76f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp
            )
        }
    }
}
@Composable
private fun ZoomablePhoto(photo: Photo) {
    var scale by remember(photo.uri) { mutableFloatStateOf(1f) }
    var pan by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    var resetVersion by remember(photo.uri) { mutableIntStateOf(0) }
    var interacting by remember(photo.uri) { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favoritePrefs = remember(context) {
        context.getSharedPreferences("photo_favorites", Context.MODE_PRIVATE)
    }
    var isLiked by remember(photo.uri) {
        mutableStateOf(favoritePrefs.getBoolean(photo.uri.toString(), false))
    }
    var menuVisible by remember(photo.uri) { mutableStateOf(false) }
    var menuCenter by remember(photo.uri) { mutableStateOf(Offset.Zero) }
    var menuAction by remember(photo.uri) { mutableStateOf<String?>(null) }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val viewWidth = with(density) { maxWidth.toPx() }
        val viewHeight = with(density) { maxHeight.toPx() }
        val dp = with(density) { 1.dp.toPx() }

        val imageWidth = photo.width.coerceAtLeast(1).toFloat()
        val imageHeight = photo.height.coerceAtLeast(1).toFloat()
        val fit = minOf(viewWidth / imageWidth, viewHeight / imageHeight)
        val baseWidth = imageWidth * fit
        val baseHeight = imageHeight * fit
        val maxZoom = 4f

        fun boundedPan(value: Offset, zoom: Float): Offset {
            val limitX = ((baseWidth * zoom - viewWidth) / 2f).coerceAtLeast(0f)
            val limitY = ((baseHeight * zoom - viewHeight) / 2f).coerceAtLeast(0f)
            return Offset(
                value.x.coerceIn(-limitX, limitX),
                value.y.coerceIn(-limitY, limitY)
            )
        }

        LaunchedEffect(resetVersion) {
            delay(1500)
            if (!interacting && scale > 1f) {
                val startScale = scale
                val startPan = pan

                androidx.compose.animation.core.animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = 0.78f,
                        stiffness = 180f
                    )
                ) { value, _ ->
                    scale = startScale + (1f - startScale) * value
                    pan = startPan * (1f - value)
                }

                scale = 1f
                pan = Offset.Zero
            }
        }

        AsyncImage(
            model = remember(photo.uri) {
                ImageRequest.Builder(context)
                    .data(photo.uri)
                    .size(
                        photo.width.coerceAtMost(3072).coerceAtLeast(1),
                        photo.height.coerceAtMost(3072).coerceAtLeast(1)
                    )
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
                .pointerInput(photo.uri) {
                    detectTapGestures(
                        onDoubleTap = {
                            scope.launch {
                                resetVersion++
                                val startScale = scale
                                val targetScale = if (scale > 1.05f) 1f else 2.5f
                                val startPan = pan

                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = 0.82f,
                                        stiffness = 260f
                                    )
                                ) { value, _ ->
                                    scale = startScale + (targetScale - startScale) * value
                                    pan = boundedPan(startPan * (1f - value), scale)
                                }

                                scale = targetScale
                                if (targetScale == 1f) pan = Offset.Zero
                                resetVersion++
                            }
                        }
                    )
                }
                .pointerInput(photo.uri, viewWidth, viewHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        interacting = true
                        resetVersion++

                        val longPress = awaitLongPressOrCancellation(down.id)

                        if (longPress != null) {
                            longPress.consume()

                            menuCenter = longPress.position
                            menuAction = null
                            menuVisible = true

                            var finished = false
                            while (!finished) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id }

                                if (change == null) {
                                    menuVisible = false
                                    finished = true
                                } else if (change.pressed) {
                                    change.consume()

                                    val positions = adaptiveMenuPositions(
                                        menuCenter, viewWidth, viewHeight, dp
                                    )

                                    val pointerX = change.position.x - menuCenter.x
                                    val pointerY = change.position.y - menuCenter.y
                                    val pointerRadius = kotlin.math.sqrt(
                                        pointerX * pointerX + pointerY * pointerY
                                    )
                                    val pointerAngle = kotlin.math.atan2(pointerY, pointerX)

                                    fun angleDifference(a: Float, b: Float): Float {
                                        val diff = kotlin.math.abs(a - b)
                                        return minOf(diff, (2f * Math.PI.toFloat()) - diff)
                                    }

                                    val ranked = positions.entries.map { entry ->
                                        val itemAngle = kotlin.math.atan2(
                                            entry.value.y - menuCenter.y,
                                            entry.value.x - menuCenter.x
                                        )
                                        entry to angleDifference(pointerAngle, itemAngle)
                                    }.sortedBy { it.second }

                                    val best = ranked.firstOrNull()
                                    val second = ranked.getOrNull(1)
                                    val angleLimit = second?.let {
                                        ((it.second - best!!.second) / 2f)
                                            .coerceIn(
                                                12f * Math.PI.toFloat() / 180f,
                                                32f * Math.PI.toFloat() / 180f
                                            )
                                    } ?: (25f * Math.PI.toFloat() / 180f)

                                    menuAction = best?.takeIf {
                                        pointerRadius in (64f * dp)..(240f * dp) &&
                                            it.second <= angleLimit
                                    }?.first?.key
                                } else {
                                    val action = menuAction
                                    menuVisible = false
                                    change.consume()
                                    when (action) {
                                        "Share" -> {
                                            try {
                                                val shareIntent = android.content.Intent(
                                                    android.content.Intent.ACTION_SEND
                                                ).apply {
                                                    type = "image/*"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, photo.uri)
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(
                                                    android.content.Intent.createChooser(
                                                        shareIntent, "Share image"
                                                    )
                                                )
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(
                                                    context, "Unable to share image",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        "Like" -> {
                                            isLiked = !isLiked
                                            favoritePrefs.edit()
                                                .putBoolean(photo.uri.toString(), isLiked)
                                                .apply()
                                            android.widget.Toast.makeText(
                                                context,
                                                if (isLiked) "Added to favorites" else "Removed from favorites",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        null -> Unit
                                        else -> {
                                            android.widget.Toast.makeText(
                                                context, action, android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                    finished = true
                                }
                            }
                            interacting = false
                        } else {
                            var hadMultiTouch = false

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }

                                if (pressed.size >= 2) {
                                    hadMultiTouch = true
                                    val a = pressed[0]
                                    val b = pressed[1]

                                    val dx = a.position.x - b.position.x
                                    val dy = a.position.y - b.position.y
                                    val oldDx = a.previousPosition.x - b.previousPosition.x
                                    val oldDy = a.previousPosition.y - b.previousPosition.y

                                    val distance = sqrt(dx * dx + dy * dy)
                                    val oldDistance = sqrt(oldDx * oldDx + oldDy * oldDy)

                                    val oldScale = scale
                                    if (oldDistance > 0f) {
                                        scale = (scale * distance / oldDistance).coerceIn(1f, maxZoom)
                                    }

                                    val nowCenter = Offset(
                                        (a.position.x + b.position.x) / 2f,
                                        (a.position.y + b.position.y) / 2f
                                    )
                                    val oldCenter = Offset(
                                        (a.previousPosition.x + b.previousPosition.x) / 2f,
                                        (a.previousPosition.y + b.previousPosition.y) / 2f
                                    )

                                    val zoomRatio = scale / oldScale
                                    val focal = nowCenter - Offset(viewWidth / 2f, viewHeight / 2f)
                                    val zoomPan = (pan - focal) * zoomRatio + focal
                                    pan = boundedPan(zoomPan + nowCenter - oldCenter, scale)

                                    pressed.forEach { it.consume() }
                                } else if (pressed.size == 1 && scale > 1f) {
                                    val change = pressed.first()
                                    pan = boundedPan(
                                        pan + change.position - change.previousPosition,
                                        scale
                                    )
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })

                            interacting = false
                            if (hadMultiTouch && scale > 1f) {
                                resetVersion++
                            }
                        }
                    }
                }.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                }
        )
        if (menuVisible) {
            RadialPhotoMenu(
                center = menuCenter,
                selected = menuAction,
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                dp = dp
            )
        }
    }
}

private fun adaptiveMenuPositions(
    center: Offset,
    width: Float,
    height: Float,
    dp: Float
): Map<String, Offset> {
    val margin = 70f * dp
    var dx = width / 2f - center.x
    var dy = height / 2f - center.y
    val length = kotlin.math.sqrt(dx * dx + dy * dy)

    if (length < 2f) {
        dx = 0f
        dy = -1f
    } else {
        dx /= length
        dy /= length
    }

    val anchor = Offset(
        (center.x + dx * 112f * dp).coerceIn(
            margin,
            (width - margin).coerceAtLeast(margin)
        ),
        (center.y + dy * 112f * dp).coerceIn(
            margin,
            (height - margin).coerceAtLeast(margin)
        )
    )

    fun point(x: Float, y: Float) = Offset(
        (anchor.x + x * dp).coerceIn(
            38f * dp,
            (width - 38f * dp).coerceAtLeast(38f * dp)
        ),
        (anchor.y + y * dp).coerceIn(
            38f * dp,
            (height - 38f * dp).coerceAtLeast(38f * dp)
        )
    )

    return mapOf(
        "Share" to point(-38f, 0f),
        "Like" to point(38f, 0f)
    )
}
@Composable
private fun RadialPhotoMenu(
    center: Offset,
    selected: String?,
    viewWidth: Float,
    viewHeight: Float,
    dp: Float
) {
    val positions = adaptiveMenuPositions(center, viewWidth, viewHeight, dp)

    Box(Modifier.fillMaxSize()) {
        positions.forEach { (label, position) ->
            RadialMenuItem(
                label = label,
                selected = selected == label,
                position = position,
                dp = dp
            )
        }
    }
}

@Composable
private fun RadialMenuItem(
    label: String,
    selected: Boolean,
    position: Offset,
    dp: Float
) {
    val scale = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.78f,
            stiffness = 430f
        ),
        label = "menuItemScale"
    ).value

    val targetOffset = IntOffset(
        (position.x - 29f * dp).roundToInt(),
        (position.y - 29f * dp).roundToInt()
    )
    val animatedOffset = animateIntOffsetAsState(
        targetValue = targetOffset,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.8f,
            stiffness = 500f
        ),
        label = "menuItemPosition"
    ).value

    Box(
        modifier = Modifier
            .offset { animatedOffset }
            .size(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (selected) 14f else 6f
                shape = CircleShape
                clip = false
            }
            .clip(CircleShape)
            .background(Color(0xFF242521))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (selected) 0.34f else 0.15f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RadialMenuGlyph(
                    label = label,
                    selected = true,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp
                )
            }
        } else {
            RadialMenuGlyph(
                label = label,
                selected = false,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
@Composable
private fun RadialMenuGlyph(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = Color.White.copy(alpha = if (selected) 1f else 0.94f)

    androidx.compose.foundation.Canvas(modifier) {
        val u = size.minDimension / 24f
        fun x(value: Float) = value * u
        fun y(value: Float) = value * u

        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 2f * u,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )

        when (label) {
            "Share" -> {
                drawLine(color, Offset(x(12f), y(15f)), Offset(x(12f), y(4f)), 2.2f * u)
                drawLine(color, Offset(x(7.5f), y(8.5f)), Offset(x(12f), y(4f)), 2.2f * u)
                drawLine(color, Offset(x(16.5f), y(8.5f)), Offset(x(12f), y(4f)), 2.2f * u)
                drawLine(color, Offset(x(5f), y(14f)), Offset(x(5f), y(20f)), 2f * u)
                drawLine(color, Offset(x(19f), y(14f)), Offset(x(19f), y(20f)), 2f * u)
                drawLine(color, Offset(x(5f), y(20f)), Offset(x(19f), y(20f)), 2f * u)
            }

            "Like" -> {
                val heart = androidx.compose.ui.graphics.Path().apply {
                    moveTo(x(12f), y(20f))
                    cubicTo(x(10f), y(18f), x(3f), y(13f), x(3f), y(8.5f))
                    cubicTo(x(3f), y(3f), x(9f), y(2f), x(12f), y(7f))
                    cubicTo(x(15f), y(2f), x(21f), y(3f), x(21f), y(8.5f))
                    cubicTo(x(21f), y(13f), x(14f), y(18f), x(12f), y(20f))
                    close()
                }

                drawPath(
                    path = heart,
                    color = color,
                    style = if (selected) {
                        androidx.compose.ui.graphics.drawscope.Fill
                    } else {
                        stroke
                    }
                )
            }

            "Crop" -> {
                drawLine(color, Offset(x(4f), y(9f)), Offset(x(4f), y(4f)), 2f * u)
                drawLine(color, Offset(x(4f), y(4f)), Offset(x(9f), y(4f)), 2f * u)
                drawLine(color, Offset(x(15f), y(4f)), Offset(x(20f), y(4f)), 2f * u)
                drawLine(color, Offset(x(20f), y(4f)), Offset(x(20f), y(9f)), 2f * u)
                drawLine(color, Offset(x(4f), y(15f)), Offset(x(4f), y(20f)), 2f * u)
                drawLine(color, Offset(x(4f), y(20f)), Offset(x(9f), y(20f)), 2f * u)
                drawLine(color, Offset(x(15f), y(20f)), Offset(x(20f), y(20f)), 2f * u)
                drawLine(color, Offset(x(20f), y(15f)), Offset(x(20f), y(20f)), 2f * u)
            }

            "Delete" -> {
                drawLine(color, Offset(x(4f), y(7f)), Offset(x(20f), y(7f)), 2f * u)
                drawLine(color, Offset(x(9f), y(4f)), Offset(x(15f), y(4f)), 2f * u)
                drawLine(color, Offset(x(6.5f), y(7f)), Offset(x(7.5f), y(20f)), 2f * u)
                drawLine(color, Offset(x(17.5f), y(7f)), Offset(x(16.5f), y(20f)), 2f * u)
                drawLine(color, Offset(x(7.5f), y(20f)), Offset(x(16.5f), y(20f)), 2f * u)
                drawLine(color, Offset(x(10f), y(10f)), Offset(x(10.5f), y(17f)), 1.7f * u)
                drawLine(color, Offset(x(14f), y(10f)), Offset(x(13.5f), y(17f)), 1.7f * u)
            }
        }
    }
}






