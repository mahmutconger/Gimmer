package com.mcclabs.gimmer.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mcclabs.gimmer.data.ProgressPhotoEntry
import com.mcclabs.gimmer.data.ProgressPhotoImage
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val photoDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr-TR"))
private val photoMonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("tr-TR"))

@Composable
internal fun ProgressPhotosCard(onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(
                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer),
            )).padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Görsel gelişimi yönet", style = MaterialTheme.typography.titleMedium)
                Text("Tarihli ve kilitli fotoğraf günlüğü", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun ProgressPhotosScreen(viewModel: WorkoutViewModel, onBack: () -> Unit) {
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pendingUri = uri }

    ProgressPhotoLock(onBack) {
        val photos by viewModel.progressPhotos.collectAsStateWithLifecycle(null)
        val saving by viewModel.savingPhoto.collectAsStateWithLifecycle()
        var selectedId by remember { mutableStateOf<String?>(null) }
        var editing by remember { mutableStateOf<ProgressPhotoEntry?>(null) }
        var deleting by remember { mutableStateOf<ProgressPhotoEntry?>(null) }
        val selected = photos?.firstOrNull { it.id == selectedId }

        if (selected != null) {
            BackHandler { selectedId = null }
            ProgressPhotoViewer(selected, viewModel, { selectedId = null }, { editing = selected }, { deleting = selected })
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                PhotoHeading("Görsel gelişim", "Fotoğraf günlüğün", onBack)
                Button(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (saving) "Fotoğraf kaydediliyor…" else "Fotoğraf ekle")
                }
                when {
                    photos == null -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                    photos.orEmpty().isEmpty() -> Box(Modifier.padding(top = 18.dp)) {
                        EmptyMessage("Henüz fotoğraf yok", "Galeriden bir fotoğraf seç ve çekildiği günü kaydet.")
                    }
                    else -> {
                        val groups = remember(photos) {
                            photos.orEmpty().groupBy { YearMonth.from(LocalDate.parse(it.date)) }
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            groups.forEach { (month, entries) ->
                                item(key = month.toString()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SectionTitle(month.format(photoMonthFormat))
                                        Text("${entries.size} fotoğraf", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                items(entries, key = { it.id }) { photo ->
                                    Card(onClick = { selectedId = photo.id }, shape = MaterialTheme.shapes.large) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Surface(Modifier.size(76.dp, 88.dp), shape = MaterialTheme.shapes.medium) {
                                                PhotoBitmap(photo.id, viewModel, false) { bitmap ->
                                                    Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(LocalDate.parse(photo.date).format(photoDateFormat),
                                                    style = MaterialTheme.typography.titleMedium)
                                                Text("Fotoğrafı aç", style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val uri = pendingUri
        val edited = editing
        if (uri != null || edited != null) {
            PhotoDateDialog(
                date = edited?.let { LocalDate.parse(it.date) } ?: LocalDate.now(),
                onDismiss = { pendingUri = null; editing = null },
                onConfirm = { date ->
                    if (edited != null) viewModel.changePhotoDate(edited.id, date)
                    else if (uri != null) viewModel.addProgressPhoto(uri, date)
                    pendingUri = null
                    editing = null
                },
            )
        }
        deleting?.let { photo ->
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Fotoğraf silinsin mi?") },
                text = { Text("Fotoğraf bu cihazdan kaldırılır. Önceki Drive yedeklerinde bir kopyası kalabilir.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.removeProgressPhoto(photo.id)
                        deleting = null
                        selectedId = null
                    }) { Text("Sil") }
                },
                dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } },
            )
        }
    }
}

@Composable
internal fun PhotoHeading(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Geri") }
        ScreenHeading(title, subtitle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoDateDialog(date: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val today = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = millis, selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
        }
    })
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(enabled = state.selectedDateMillis != null, onClick = {
            onConfirm(Instant.ofEpochMilli(checkNotNull(state.selectedDateMillis)).atOffset(ZoneOffset.UTC).toLocalDate())
        }) { Text("Kaydet") }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text("Vazgeç") }
    }) { DatePicker(state, title = { Text("Fotoğraf tarihi", Modifier.padding(start = 24.dp, top = 16.dp)) }) }
}

@Composable
private fun PhotoBitmap(id: String, viewModel: WorkoutViewModel, fullSize: Boolean, content: @Composable (Bitmap) -> Unit) {
    var loaded by remember(id, fullSize) { mutableStateOf(false) }
    val bitmap by produceState<Bitmap?>(null, id, fullSize) {
        value = viewModel.progressPhotoImage(id)?.let { ProgressPhotoImage.decode(it, fullSize) }
        loaded = true
    }
    val image = bitmap
    if (image != null) content(image)
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (loaded) Icon(Icons.Rounded.PhotoLibrary, "Fotoğraf açılamadı")
        else CircularProgressIndicator(Modifier.size(24.dp))
    }
}

@Composable
private fun ProgressPhotoViewer(
    photo: ProgressPhotoEntry, viewModel: WorkoutViewModel, onBack: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        PhotoHeading("Fotoğraf", LocalDate.parse(photo.date).format(photoDateFormat), onBack)
        Surface(Modifier.weight(1f).fillMaxWidth(), shape = MaterialTheme.shapes.large, color = Color.Black) {
            PhotoBitmap(photo.id, viewModel, true) { bitmap ->
                Image(
                    bitmap.asImageBitmap(), "Gelişim fotoğrafı",
                    Modifier.fillMaxSize().clipToBounds()
                        .pointerInput(photo.id) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (oldScale * zoom).coerceIn(1f, 5f)
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val ratio = newScale / oldScale
                                val proposed = (offset - (centroid - center)) * ratio + (centroid - center) + pan
                                val fit = minOf(size.width.toFloat() / bitmap.width, size.height.toFloat() / bitmap.height)
                                val maxX = ((bitmap.width * fit * newScale - size.width) / 2f).coerceAtLeast(0f)
                                val maxY = ((bitmap.height * fit * newScale - size.height) / 2f).coerceAtLeast(0f)
                                offset = Offset(proposed.x.coerceIn(-maxX, maxX), proposed.y.coerceIn(-maxY, maxY))
                                scale = newScale
                            }
                        }
                        .pointerInput(photo.id) {
                            detectTapGestures(onDoubleTap = {
                                scale = if (scale > 1f) 1f else 2f
                                offset = Offset.Zero
                            })
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (scale == 1f) "İki parmakla yakınlaştır" else "%${(scale * 100).roundToInt()}",
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(enabled = scale != 1f, onClick = { scale = 1f; offset = Offset.Zero }) {
                Icon(Icons.Rounded.ZoomOutMap, "Yakınlaştırmayı sıfırla")
            }
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.CalendarMonth, "Tarihi değiştir") }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "Fotoğrafı sil") }
        }
    }
}
