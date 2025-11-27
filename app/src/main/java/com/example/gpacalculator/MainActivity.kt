package com.example.gpacalculator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpacalculator.ui.theme.GPACalculatorTheme
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPACalculatorTheme {
                val viewModel: GpaViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                val context = LocalContext.current

                LaunchedEffect(uiState.importMessage) {
                    uiState.importMessage?.let { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        viewModel.clearImportMessage()
                    }
                }
                
                var pendingDetails by remember { mutableStateOf<StudentDetails?>(null) }
                val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                    if (uri != null && pendingDetails != null) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            viewModel.generatePdf(outputStream, pendingDetails!!)
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        uiState.isPrinting -> {
                            PrintScreen(
                                subjects = uiState.subjects,
                                universityName = uiState.selectedUniversity.name,
                                onGeneratePdf = { details -> 
                                    pendingDetails = details
                                    pdfLauncher.launch("Marksheet_${details.studentName}.pdf")
                                },
                                onUpdateSubjectName = { idx, name -> viewModel.updateSubjectName(idx, name) },
                                onCancel = { viewModel.cancelPrinting() }
                            )
                        }
                        uiState.isScanning && uiState.scanImageUri != null && uiState.scannedRows == null -> {
                            if (uiState.isProcessingOcr) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                    Text("Reading marks...", Modifier.padding(top = 64.dp))
                                }
                            } else {
                                CropScreen(
                                    imageUri = uiState.scanImageUri!!,
                                    onCropConfirmed = { bitmap -> viewModel.processCroppedImage(bitmap) },
                                    onCancel = { viewModel.cancelScan() }
                                )
                            }
                        }
                        uiState.isScanning && uiState.scannedRows != null -> {
                            VerificationScreen(
                                scannedRows = uiState.scannedRows!!,
                                university = uiState.selectedUniversity,
                                onConfirm = { subjects -> viewModel.applyScannedSubjects(subjects) },
                                onCancel = { viewModel.cancelScan() }
                            )
                        }
                        uiState.isAddingUniversity -> {
                            AddUniversityScreen(
                                existingUniversity = uiState.universityToEdit,
                                onSave = { name, grades, rules, percRules -> viewModel.saveUniversity(name, grades, rules, percRules) },
                                onCancel = { viewModel.cancelAddingUniversity() }
                            )
                        }
                        else -> {
                            GpaCalculatorScreen(viewModel = viewModel, uiState = uiState)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaCalculatorScreen(viewModel: GpaViewModel, uiState: GpaUiState) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var tempImageUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { if (it && tempImageUri != null) viewModel.onImagePicked(tempImageUri!!) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) viewModel.onImagePicked(it) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { 
        if (it) {
            val file = File(context.cacheDir, "scan_temp.jpg")
            tempImageUri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            cameraLauncher.launch(tempImageUri)
        } 
    }

    fun initiateScan(camera: Boolean) {
        viewModel.startScan()
        if (camera) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val file = File(context.cacheDir, "scan_temp.jpg")
                tempImageUri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                cameraLauncher.launch(tempImageUri)
            } else permissionLauncher.launch(Manifest.permission.CAMERA)
        } else galleryLauncher.launch("image/*")
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) context.contentResolver.openInputStream(uri)?.use { viewModel.importData(BufferedReader(InputStreamReader(it)).readText()) }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.getExportData().toByteArray()) }
    }

    val showStickySubtitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var showScanDialog by remember { mutableStateOf(false) }

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Scan Marksheet") },
            text = { Text("Capture image of grades table") },
            confirmButton = { TextButton(onClick = { showScanDialog = false; initiateScan(true) }) { Text("Camera") } },
            dismissButton = { TextButton(onClick = { showScanDialog = false; initiateScan(false) }) { Text("Gallery") } }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("GPA Calculator", fontWeight = FontWeight.Bold)
                        AnimatedVisibility(visible = showStickySubtitle, enter = fadeIn(), exit = fadeOut()) {
                            Text(text = uiState.selectedUniversity.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.Menu, "Menu") }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(text = { Text("Export") }, leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { showMenu = false; exportLauncher.launch("gpa.json") })
                        DropdownMenuItem(text = { Text("Import") }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { showMenu = false; importLauncher.launch("application/json") })
                    }
                },
                actions = { TextButton(onClick = { showScanDialog = true }) { Text("SCAN") } }
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { viewModel.clearAll() }, Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = { viewModel.calculateGpa() }, Modifier.weight(1f)) { Text("Calculate") }
                }
            }
        }
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { UniversitySelector(uiState.availableUniversities, uiState.selectedUniversity, { viewModel.setUniversity(it) }, { viewModel.startAddingUniversity() }, { viewModel.startEditingUniversity(it) }, { viewModel.deleteUniversity(it) }) }
            item { SubjectCountHeader(uiState.subjects.size, { viewModel.setSubjectCount(it) }) }
            itemsIndexed(uiState.subjects) { i, s -> SubjectCard(i + 1, s, uiState.selectedUniversity, { c, g -> viewModel.updateSubject(i, c, g) }) }
        }
        if (uiState.calculatedGpa != null) {
            ResultDialog(
                gpa = uiState.calculatedGpa!!,
                percentage = uiState.calculatedPercentage ?: 0.0,
                classification = uiState.classification ?: "",
                onPrintClick = { viewModel.startPrinting() },
                onDismiss = { viewModel.dismissResult() }
            )
        }
    }
}

@Composable
fun ResultDialog(
    gpa: Double,
    percentage: Double, // New Param
    classification: String,
    onPrintClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Calculation Result") },
        text = {
            Column {
                Text("$gpa", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                // Show Percentage
                if (percentage > 0) {
                    Text("Percentage: ${String.format("%.2f", percentage)}%", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text(classification, style = MaterialTheme.typography.headlineSmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPrintClick) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Print") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUniversityScreen(
    existingUniversity: University? = null,
    onSave: (String, List<Grade>, List<ClassificationRule>, List<PercentageRule>) -> Unit,
    onCancel: () -> Unit
) {
    var uniName by remember { mutableStateOf(existingUniversity?.name ?: "") }
    
    val grades = remember { mutableStateListOf<TempGrade>().apply {
        if (existingUniversity != null) addAll(existingUniversity.grades.map { TempGrade(symbol = it.symbol, pointStr = it.point.toString()) })
        else add(TempGrade(symbol = "AA", pointStr = "10.0"))
    } }
    
    val classifications = remember { mutableStateListOf<TempRule>().apply {
        if (existingUniversity != null) addAll(existingUniversity.classifications.map { TempRule(minStr = it.minGpa.toString(), maxStr = it.maxGpa.toString(), label = it.label) })
        else { add(TempRule(minStr = "0.0", maxStr = "5.0", label = "Fail")); add(TempRule(minStr = "5.0", maxStr = "10.1", label = "Pass")) }
    } }

    // New: Percentage Rules State
    val percentRules = remember { mutableStateListOf<TempPercentageRule>().apply {
        if (existingUniversity != null && existingUniversity.percentageRules.isNotEmpty()) {
            addAll(existingUniversity.percentageRules.map { TempPercentageRule(minStr = it.minGpa.toString(), maxStr = it.maxGpa.toString(), formula = it.formula) })
        } else {
            add(TempPercentageRule(minStr = "0.0", maxStr = "10.1", formula = "(gpa - 0.5) * 10"))
        }
    } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingUniversity == null) "Add University" else "Edit University") },
                navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { 
                        val g = grades.map { Grade(it.symbol, it.pointStr.toDoubleOrNull()?:0.0) }
                        val c = classifications.map { ClassificationRule(it.minStr.toDoubleOrNull()?:0.0, it.maxStr.toDoubleOrNull()?:0.0, it.label) }
                        val p = percentRules.map { PercentageRule(it.minStr.toDoubleOrNull()?:0.0, it.maxStr.toDoubleOrNull()?:0.0, it.formula) }
                        onSave(uniName, g, c, p) 
                    }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(value = uniName, onValueChange = { uniName = it }, label = { Text("University Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
            
            Text("Grading Scale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            grades.forEachIndexed { i, g ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = g.symbol, onValueChange = { grades[i] = g.copy(symbol = it) }, label = { Text("Grade") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = g.pointStr, onValueChange = { grades[i] = g.copy(pointStr = it) }, label = { Text("Point") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    IconButton(onClick = { if(grades.size > 1) grades.removeAt(i) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                }
            }
            OutlinedButton(onClick = { grades.add(TempGrade(symbol = "", pointStr = "")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add Grade") }
            Spacer(Modifier.height(24.dp))

            Text("Percentage Formulas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Enter GPA range and Formula. Use 'gpa' variable.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            
            percentRules.forEachIndexed { i, rule ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = rule.minStr, onValueChange = { percentRules[i] = rule.copy(minStr = it) }, label = { Text("Min GPA") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = rule.maxStr, onValueChange = { percentRules[i] = rule.copy(maxStr = it) }, label = { Text("Max GPA") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            IconButton(onClick = { if(percentRules.size > 1) percentRules.removeAt(i) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Formula Input using TextRange for cursor positioning (Simplified here using TextFieldValue logic if needed, but String is easier for MVVM)
                            // To implement "Insert GPA" button, we need mutable state for the string
                            OutlinedTextField(
                                value = rule.formula, 
                                onValueChange = { percentRules[i] = rule.copy(formula = it) }, 
                                label = { Text("Formula (e.g. 12 * gpa - 25)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilledTonalButton(onClick = { 
                                percentRules[i] = rule.copy(formula = rule.formula + "gpa") 
                            }) {
                                Text("gpa")
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { percentRules.add(TempPercentageRule(minStr="", maxStr="", formula="")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add Formula") }
            
            Spacer(Modifier.height(24.dp))
            Text("Classifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            classifications.forEachIndexed { i, r ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Row {
                            OutlinedTextField(value = r.label, onValueChange = { classifications[i] = r.copy(label = it) }, label = { Text("Label") }, modifier = Modifier.weight(1f))
                            IconButton(onClick = { if(classifications.size > 2) classifications.removeAt(i) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = r.minStr, onValueChange = { classifications[i] = r.copy(minStr = it) }, label = { Text("Min") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = r.maxStr, onValueChange = { classifications[i] = r.copy(maxStr = it) }, label = { Text("Max") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { classifications.add(TempRule(minStr="", maxStr="", label="")) }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("Add Rule") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// Sub-components (Selector, Header, Card) remain unchanged from previous fix, assuming they are present.
// To be safe, I must include them because I am overwriting the file.

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun UniversitySelector(
    available: List<University>, 
    selected: University, 
    onSelect: (University) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (University) -> Unit,
    onDeleteClick: (University) -> Unit
) {
    var showMenuFor by remember { mutableStateOf<University?>(null) }
    Column {
        Text("Select University", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                available.forEach { uni ->
                    val isSelected = uni.id == selected.id
                    Box {
                        FilterChip(
                            selected = isSelected, onClick = { onSelect(uni) },
                            label = { Text(uni.name, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) },
                            trailingIcon = if (uni.isCustom) { { IconButton(onClick = { showMenuFor = uni }, Modifier.size(16.dp)) { Icon(Icons.Default.MoreVert, null, Modifier.size(16.dp)) } } } else null,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = Color.White)
                        )
                        DropdownMenu(expanded = showMenuFor == uni, onDismissRequest = { showMenuFor = null }) {
                            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { showMenuFor = null; onEditClick(uni) })
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenuFor = null; onDeleteClick(uni) })
                        }
                    }
                }
            }
            FilledTonalButton(onClick = onAddClick) { Text("+ Add") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCountHeader(count: Int, onCountChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Subjects: $count", style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = "Select", onValueChange = {}, readOnly = true, enabled = false, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().width(120.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = true }, textStyle = MaterialTheme.typography.bodySmall, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant))
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.height(300.dp)) {
                (3..50).toList().forEach { number -> DropdownMenuItem(text = { Text("$number") }, onClick = { onCountChange(number); expanded = false }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectCard(index: Int, subject: Subject, university: University, onUpdate: (Int?, Grade?) -> Unit) {
    Card(elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Text("Subject $index", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Credits", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0..4).forEach { credit -> FilterChip(selected = subject.credits == credit, onClick = { onUpdate(credit, null) }, label = { Text("$credit") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer)) }
            }
            Spacer(Modifier.height(8.dp))
            Text("Grade", style = MaterialTheme.typography.labelSmall)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                university.grades.forEach { grade -> FilterChip(selected = subject.selectedGrade?.symbol == grade.symbol, onClick = { onUpdate(null, grade) }, label = { Text(grade.symbol) }) }
            }
        }
    }
}