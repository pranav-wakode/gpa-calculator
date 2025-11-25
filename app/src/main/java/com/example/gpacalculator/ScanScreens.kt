package com.example.gpacalculator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

// --- HELPER: Load Scaled Bitmap ---
// INCREASED to 3000px to ensure high accuracy on full-page scans
fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 3000): Bitmap? {
    return try {
        var input = context.contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        input?.close()

        var sampleSize = 1
        while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        input = context.contentResolver.openInputStream(uri)
        val finalOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888 
        }
        val bitmap = BitmapFactory.decodeStream(input, null, finalOptions)
        input?.close()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// --- CROP SCREEN ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageUri: Uri,
    onCropConfirmed: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(imageUri) {
        loadScaledBitmap(context, imageUri)
    }

    if (bitmap == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error loading image")
            Button(onClick = onCancel) { Text("Close") }
        }
        return
    }

    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var currentOffset by remember { mutableStateOf<Offset?>(null) }
    
    val roiRect by remember {
        derivedStateOf {
            if (startOffset != null && currentOffset != null) {
                Rect(
                    left = min(startOffset!!.x, currentOffset!!.x),
                    top = min(startOffset!!.y, currentOffset!!.y),
                    right = max(startOffset!!.x, currentOffset!!.x),
                    bottom = max(startOffset!!.y, currentOffset!!.y)
                )
            } else {
                Rect.Zero
            }
        }
    }

    // State to hold the layout info of the image container
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var imageRenderedSize by remember { mutableStateOf(Size.Zero) }
    var imageOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Grades Area") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
                },
                actions = {
                    IconButton(onClick = {
                        if (!roiRect.isEmpty && imageRenderedSize.width > 0) {
                            try {
                                // Exact math to map screen coordinates to bitmap coordinates
                                val scaleX = bitmap.width / imageRenderedSize.width
                                val scaleY = bitmap.height / imageRenderedSize.height
                                
                                val relativeLeft = roiRect.left - imageOffset.x
                                val relativeTop = roiRect.top - imageOffset.y
                                val relativeRight = roiRect.right - imageOffset.x
                                val relativeBottom = roiRect.bottom - imageOffset.y

                                val cropLeft = (relativeLeft * scaleX).toInt().coerceIn(0, bitmap.width - 1)
                                val cropTop = (relativeTop * scaleY).toInt().coerceIn(0, bitmap.height - 1)
                                val cropRight = (relativeRight * scaleX).toInt().coerceIn(0, bitmap.width)
                                val cropBottom = (relativeBottom * scaleY).toInt().coerceIn(0, bitmap.height)
                                
                                val cropWidth = cropRight - cropLeft
                                val cropHeight = cropBottom - cropTop

                                if (cropWidth > 0 && cropHeight > 0) {
                                    val cropped = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight)
                                    onCropConfirmed(cropped)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Check, "Confirm")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Crop",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        containerSize = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat())
                        
                        val bitmapRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val containerRatio = containerSize.width / containerSize.height
                        
                        if (bitmapRatio > containerRatio) {
                            val w = containerSize.width
                            val h = w / bitmapRatio
                            imageRenderedSize = Size(w, h)
                            imageOffset = Offset(0f, (containerSize.height - h) / 2f)
                        } else {
                            val h = containerSize.height
                            val w = h * bitmapRatio
                            imageRenderedSize = Size(w, h)
                            imageOffset = Offset((containerSize.width - w) / 2f, 0f)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                startOffset = offset
                                currentOffset = offset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentOffset = change.position
                            }
                        )
                    }
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(Color.Black.copy(alpha = 0.5f))
                
                if (!roiRect.isEmpty) {
                    drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset.Zero, size = Size(size.width, roiRect.top))
                    drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, roiRect.bottom), size = Size(size.width, size.height - roiRect.bottom))
                    drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(0f, roiRect.top), size = Size(roiRect.left, roiRect.height))
                    drawRect(Color.Black.copy(alpha = 0.5f), topLeft = Offset(roiRect.right, roiRect.top), size = Size(size.width - roiRect.right, roiRect.height))
                    
                    drawRect(Color.Green, topLeft = roiRect.topLeft, size = roiRect.size, style = Stroke(3.dp.toPx()))
                }
            }
            
            if (roiRect.isEmpty) {
                 Text(
                    "Draw a box tightly around the table", 
                    color = Color.White, 
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

// --- VERIFICATION SCREEN ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VerificationScreen(
    scannedRows: List<ScannedRow>,
    university: University,
    onConfirm: (List<Subject>) -> Unit,
    onCancel: () -> Unit
) {
    val editableSubjects = remember(scannedRows) {
        mutableStateListOf<Subject>().apply {
            addAll(scannedRows.map { row ->
                val matchGrade = university.grades.find { g -> g.symbol == row.detectedGrade }
                Subject(
                    credits = row.detectedCredits ?: 3,
                    selectedGrade = matchGrade
                )
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Scanned Data") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Close") }
                },
                actions = {
                    Button(onClick = { onConfirm(editableSubjects) }) {
                        Text("Add to Calculator")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(editableSubjects) { subject ->
                VerifyCard(
                    subject = subject, 
                    university = university,
                    onDelete = { editableSubjects.remove(subject) },
                    onUpdate = { credits, grade -> 
                         val idx = editableSubjects.indexOf(subject)
                         if (idx != -1) {
                             editableSubjects[idx] = subject.copy(
                                 credits = credits ?: subject.credits,
                                 selectedGrade = grade ?: subject.selectedGrade
                             )
                         }
                    }
                )
            }
            
            item {
                OutlinedButton(
                    onClick = { editableSubjects.add(Subject()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Missing Row")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VerifyCard(
    subject: Subject,
    university: University,
    onDelete: () -> Unit,
    onUpdate: (Int?, Grade?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (subject.selectedGrade == null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Subject", fontWeight = FontWeight.Bold)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Text("Credits: ${subject.credits}", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..4).forEach { cr ->
                    FilterChip(
                        selected = subject.credits == cr,
                        onClick = { onUpdate(cr, null) },
                        label = { Text("$cr") },
                         modifier = Modifier.height(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Grade: ${subject.selectedGrade?.symbol ?: "?"}", style = MaterialTheme.typography.labelSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                university.grades.forEach { g ->
                    FilterChip(
                        selected = subject.selectedGrade?.symbol == g.symbol,
                        onClick = { onUpdate(null, g) },
                        label = { Text(g.symbol) },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }
    }
}