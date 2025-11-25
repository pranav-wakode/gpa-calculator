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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
                
                // PDF Launcher for "Finish" button in Print Screen
                var pendingDetails by remember { mutableStateOf<StudentDetails?>(null) }
                val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                    if (uri != null && pendingDetails != null) {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            viewModel.generatePdf(outputStream, pendingDetails!!)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        // 1. Print Flow
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
                        // 2. Scan Flow
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
                                onSave = { name, grades, rules -> viewModel.saveUniversity(name, grades, rules) },
                                onCancel = { viewModel.cancelAddingUniversity() }
                            )
                        }
                        // 3. Main Screen
                        else -> {
                            GpaCalculatorScreen(
                                viewModel = viewModel,
                                uiState = uiState
                            )
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

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) {
            viewModel.onImagePicked(tempImageUri!!)
        }
    }
    
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.onImagePicked(uri)
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val file = File(context.cacheDir, "scan_temp_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
            tempImageUri = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "Camera permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    fun initiateScan(useCamera: Boolean) {
        viewModel.startScan() 
        if (useCamera) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val file = File(context.cacheDir, "scan_temp_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", file)
                tempImageUri = uri
                cameraLauncher.launch(uri)
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            galleryLauncher.launch("image/*")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = BufferedReader(InputStreamReader(inputStream)).readText()
                    viewModel.importData(jsonString)
                }
            } catch (e: Exception) { Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show() }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.getExportData().toByteArray()) }
                Toast.makeText(context, "Exported!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show() }
        }
    }

    val showStickySubtitle by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 100 }
    }
    
    var showScanDialog by remember { mutableStateOf(false) }

    if (showScanDialog) {
        AlertDialog(
            onDismissRequest = { showScanDialog = false },
            title = { Text("Scan Marksheet") },
            text = { Text("Choose an option to capture the grades table.") },
            confirmButton = {
                TextButton(onClick = { showScanDialog = false; initiateScan(true) }) { Text("Camera") }
            },
            dismissButton = {
                TextButton(onClick = { showScanDialog = false; initiateScan(false) }) { Text("Gallery") }
            }
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
                        DropdownMenuItem(
                            text = { Text("Export to JSON") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = { showMenu = false; exportLauncher.launch("gpa_universities.json") }
                        )
                        DropdownMenuItem(
                            text = { Text("Import from JSON") },
                            leadingIcon = { Icon(Icons.Default.Add, null) },
                            onClick = { showMenu = false; importLauncher.launch("application/json") }
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showScanDialog = true }) {
                        Text("SCAN")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            BottomAppBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { viewModel.clearAll() }, Modifier.weight(1f)) { Text("Clear") }
                    Button(onClick = { viewModel.calculateGpa() }, Modifier.weight(1f)) { Text("Calculate") }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(paddingValues).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                UniversitySelector(
                    available = uiState.availableUniversities,
                    selected = uiState.selectedUniversity,
                    onSelect = { viewModel.setUniversity(it) },
                    onAddClick = { viewModel.startAddingUniversity() },
                    onEditClick = { viewModel.startEditingUniversity(it) },
                    onDeleteClick = { viewModel.deleteUniversity(it) }
                )
            }
            item { SubjectCountHeader(count = uiState.subjects.size, onCountChange = { viewModel.setSubjectCount(it) }) }
            itemsIndexed(uiState.subjects) { index, subject ->
                SubjectCard(
                    index = index + 1,
                    subject = subject,
                    university = uiState.selectedUniversity,
                    onUpdate = { credits, grade -> viewModel.updateSubject(index, credits, grade) }
                )
            }
        }

        if (uiState.calculatedGpa != null) {
            ResultDialog(
                gpa = uiState.calculatedGpa!!,
                classification = uiState.classification ?: "",
                onPrintClick = { viewModel.startPrinting() }, // NEW: Print Trigger
                onDismiss = { viewModel.dismissResult() }
            )
        }
    }
}

// --- RESTORED COMPONENTS ---

@Composable
fun ResultDialog(
    gpa: Double, 
    classification: String, 
    onPrintClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("Calculation Result") },
        text = {
            Column {
                Text(
                    text = "$gpa",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = classification,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // CHANGED: Icons.Default.Share instead of Print (not in core)
                OutlinedButton(onClick = onPrintClick) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Print")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUniversityScreen(
    existingUniversity: University? = null,
    onSave: (String, List<Grade>, List<ClassificationRule>) -> Unit,
    onCancel: () -> Unit
) {
    var uniName by remember { mutableStateOf(existingUniversity?.name ?: "") }
    
    val grades = remember { 
        mutableStateListOf<TempGrade>().apply {
            if (existingUniversity != null) {
                addAll(existingUniversity.grades.map { TempGrade(symbol = it.symbol, pointStr = it.point.toString()) })
            } else {
                add(TempGrade(symbol = "AA", pointStr = "10.0"))
            }
        }
    }
    
    val classifications = remember { 
        mutableStateListOf<TempRule>().apply {
            if (existingUniversity != null) {
                addAll(existingUniversity.classifications.map { 
                    TempRule(minStr = it.minGpa.toString(), maxStr = it.maxGpa.toString(), label = it.label) 
                })
            } else {
                add(TempRule(minStr = "0.0", maxStr = "5.0", label = "Fail"))
                add(TempRule(minStr = "5.0", maxStr = "10.1", label = "Pass"))
            }
        } 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingUniversity == null) "Add University" else "Edit University") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { 
                        val finalGrades = grades.map { 
                            Grade(it.symbol, it.pointStr.toDoubleOrNull() ?: 0.0)
                        }
                        val finalRules = classifications.map {
                            ClassificationRule(
                                minGpa = it.minStr.toDoubleOrNull() ?: 0.0,
                                maxGpa = it.maxStr.toDoubleOrNull() ?: 0.0,
                                label = it.label
                            )
                        }
                        onSave(uniName, finalGrades, finalRules) 
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = uniName,
                onValueChange = { uniName = it },
                label = { Text("University Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Grading Scale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            grades.forEachIndexed { index, grade ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = grade.symbol,
                        onValueChange = { newSym -> grades[index] = grade.copy(symbol = newSym) },
                        label = { Text("Grade") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = grade.pointStr,
                        onValueChange = { newStr -> grades[index] = grade.copy(pointStr = newStr) },
                        label = { Text("Point") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { if(grades.size > 1) grades.removeAt(index) }) {
                        Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            OutlinedButton(onClick = { grades.add(TempGrade(symbol = "", pointStr = "")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("Add Grade Row")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Classifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("At least 2 rules required", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            
            classifications.forEachIndexed { index, rule ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = rule.label,
                                onValueChange = { classifications[index] = rule.copy(label = it) },
                                label = { Text("Label") },
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { if (classifications.size > 2) classifications.removeAt(index) },
                                enabled = classifications.size > 2
                            ) {
                                Icon(
                                    Icons.Default.Delete, 
                                    contentDescription = "Remove Rule",
                                    tint = if (classifications.size > 2) MaterialTheme.colorScheme.error else Color.Gray
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             OutlinedTextField(
                                value = rule.minStr,
                                onValueChange = { classifications[index] = rule.copy(minStr = it) },
                                label = { Text("Min") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rule.maxStr,
                                onValueChange = { classifications[index] = rule.copy(maxStr = it) },
                                label = { Text("Max") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
             OutlinedButton(onClick = { classifications.add(TempRule(minStr="", maxStr="", label="")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("Add Rule")
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FlowRow(
               modifier = Modifier.weight(1f),
               horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                available.forEach { uni ->
                    val isSelected = uni.id == selected.id
                    
                    Box {
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(uni) },
                            label = { 
                                Text(
                                    text = uni.name,
                                    fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            trailingIcon = if (uni.isCustom) {
                                {
                                    IconButton(
                                        onClick = { showMenuFor = uni },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert, 
                                            contentDescription = "Options",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )

                        DropdownMenu(
                            expanded = showMenuFor == uni,
                            onDismissRequest = { showMenuFor = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { 
                                    showMenuFor = null
                                    onEditClick(uni) 
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { 
                                    showMenuFor = null
                                    onDeleteClick(uni) 
                                }
                            )
                        }
                    }
                }
            }
            
            FilledTonalButton(onClick = onAddClick) {
                Text("+ Add")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCountHeader(count: Int, onCountChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = (3..50).toList()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Subjects: $count", style = MaterialTheme.typography.titleMedium)
        
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = "Select",
                onValueChange = {},
                readOnly = true,
                enabled = false, 
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .width(120.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = true },
                textStyle = MaterialTheme.typography.bodySmall,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.height(300.dp)
            ) {
                options.forEach { number ->
                    DropdownMenuItem(
                        text = { Text("$number") },
                        onClick = { 
                            onCountChange(number)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubjectCard(
    index: Int,
    subject: Subject,
    university: University,
    onUpdate: (Int?, Grade?) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Subject $index", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Credits", style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (0..4).forEach { credit ->
                    val isSelected = subject.credits == credit
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdate(credit, null) },
                        label = { Text(credit.toString()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Grade", style = MaterialTheme.typography.labelSmall)
            
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                university.grades.forEach { grade ->
                    val isSelected = subject.selectedGrade?.symbol == grade.symbol
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUpdate(null, grade) },
                        label = { Text(grade.symbol) }
                    )
                }
            }
        }
    }
}