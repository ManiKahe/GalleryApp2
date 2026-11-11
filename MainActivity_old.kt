package com.gallery.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Photo(
    val uri: Uri,
    val width: Int,
    val height: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GalleryTheme {
                GalleryApp()
            }
        }
    }
}

@Composable
private fun GalleryTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                background = Color(0xFF090909),
                surface = Color(0xFF090909),
                onBackground = Color.White,
                onSurface = Color.White,
                primary = Color.White
            )
        } else {
            lightColorScheme(
                background = Color(0xFFFAFAFA),
                surface = Color(0xFFFAFAFA),
                onBackground = Color(0xFF111111),
                onSurface = Color(0xFF111111),
                primary = Color(0xFF111111)
            )
        },
        content = content
    )
}

@Composable
private fun GalleryApp() {
    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(hasImagePermission(context))
    }

    var photos by remember {
        mutableStateOf<List<Photo>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var selectedPhotoIndex by remember { mutableIntStateOf(-1) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionGranted = hasImagePermission(context)
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            loading = true
            photos = withContext(Dispatchers.IO) {
                loadPhotos(context)
            }
            loading = false
        }
    }

    if (selectedPhotoIndex >= 0 && photos.isNotEmpty()) {
        PhotoViewer(
            photos = photos,
            initialPage = selectedPhotoIndex,
            onClose = { selectedPhotoIndex = -1 }
        )
        return
    }

    if (!permissionGranted) {
        PermissionScreen {
            permissionLauncher.launch(imagePermissions())
        }
        return
    }

    PhotoGrid(
        photos = photos,
        loading = loading,
        onPhotoClick = { selectedPhotoIndex = photos.indexOf(it) }
    )
}

private fun hasImagePermission(context: Context): Boolean {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        Build.VERSION.SDK_INT >= 33 ->
            granted(Manifest.permission.READ_MEDIA_IMAGES)

        else ->
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

private fun imagePermissions(): Array<String> {
    return when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )

        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )

        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }
}

private fun loadPhotos(context: Context): List<Photo> {
    val photos = mutableListOf<Photo>()

    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT
    )

    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->

        val idColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Images.Media._ID
        )

        val widthColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Images.Media.WIDTH
        )

        val heightColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Images.Media.HEIGHT
        )

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)

            val uri = ContentUris.withAppendedId(
                collection,
                id
            )

            photos.add(
                Photo(
                    uri = uri,
                    width = cursor.getInt(widthColumn),
                    height = cursor.getInt(heightColumn)
                )
            )
        }
    }

    return photos
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Photos",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "Allow access to your photos to open your gallery.",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Spacer(Modifier.height(24.dp))

        Button(onClick = onRequest) {
            Text("Allow access")
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    loading: Boolean,
    onPhotoClick: (Photo) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Photos",
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.weight(1f))

            Text(
                photos.size.toString(),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        when {
            loading -> {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            photos.isEmpty() -> {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No photos found",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = photos,
                        key = { it.uri.toString() },
                        contentType = { "photo" }
                    ) { photo ->
                        val imageRequest = remember(photo.uri) {
                            ImageRequest.Builder(context)
                                .data(photo.uri)
                                .size(320, 320)
                                .precision(Precision.INEXACT)
                                .crossfade(false)
                                .build()
                        }

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .clickable { onPhotoClick(photo) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoViewer(
    photos: List<Photo>,
    initialPage: Int,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { photos.size }
    )

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            AsyncImage(
                model = photos[page].uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(48.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = Color.White, fontSize = 30.sp)
        }

        Text(
            "${pagerState.currentPage + 1} / ${photos.size}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp
        )
    }
}
