package com.example.gpacalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpacalculator.ui.theme.GPACalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPACalculatorTheme {
                val viewModel: GpaViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (uiState.isAddingUniversity) {
                        AddUniversityScreen(
                            onSave = { name, grades, rules -> viewModel.saveNewUniversity(name, grades, rules) },
                            onCancel = { viewModel.cancelAddingUniversity() }
                        )
                    } else {
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

// --- Main Calculator Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaCalculatorScreen(viewModel: GpaViewModel, uiState: GpaUiState) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GPA Calculator", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            // Bottom Action Bar with Clear and Calculate
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.clearAll() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear")
                    }
                    Button(
                        onClick = { viewModel.calculateGpa() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Calculate GPA")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 1. University Selection
            UniversitySelector(
                available = uiState.availableUniversities,
                selected = uiState.selectedUniversity,
                onSelect = { viewModel.setUniversity(it) },
                onAddClick = { viewModel.startAddingUniversity() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // 2. Subject Count Config
            SubjectCountHeader(
                count = uiState.subjects.size,
                onCountChange = { viewModel.setSubjectCount(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Subject List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiState.subjects) { index, subject ->
                    SubjectCard(
                        index = index + 1,
                        subject = subject,
                        university = uiState.selectedUniversity,
                        onUpdate = { credits, grade -> 
                            viewModel.updateSubject(index, credits, grade) 
                        }
                    )
                }
            }
            
            // 4. Results Dialog
            if (uiState.calculatedGpa != null) {
                ResultDialog(
                    gpa = uiState.calculatedGpa!!,
                    classification = uiState.classification ?: "",
                    onDismiss = { /* Can just close dialog */ } 
                )
            }
        }
    }
}

// --- Add University Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUniversityScreen(
    onSave: (String, List<Grade>, List<ClassificationRule>) -> Unit,
    onCancel: () -> Unit
) {
    var uniName by remember { mutableStateOf("") }
    
    // Mutable list for grades
    val grades = remember { mutableStateListOf(Grade("AA", 10.0)) }
    
    // Mutable list for classifications (simplified defaults)
    val classifications = remember { mutableStateListOf(
        ClassificationRule(0.0, 5.0, "Fail"),
        ClassificationRule(5.0, 10.1, "Pass")
    ) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add University") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(uniName, grades, classifications) }) {
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
            
            // --- Grades Editor ---
            Text("Grading Scale", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Define symbol (e.g. 'A') and points (e.g. '9.0')", style = MaterialTheme.typography.bodySmall)
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
                        value = grade.point.toString(),
                        onValueChange = { 
                            val p = it.toDoubleOrNull() ?: 0.0
                            grades[index] = grade.copy(point = p)
                        },
                        label = { Text("Point") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { if(grades.size > 1) grades.removeAt(index) }) {
                        Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            
            OutlinedButton(onClick = { grades.add(Grade("", 0.0)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Grade Row")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Rules Editor (Simplified) ---
            Text("Classifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            classifications.forEachIndexed { index, rule ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        OutlinedTextField(
                            value = rule.label,
                            onValueChange = { classifications[index] = rule.copy(label = it) },
                            label = { Text("Class Label (e.g. First Class)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             OutlinedTextField(
                                value = rule.minGpa.toString(),
                                onValueChange = { classifications[index] = rule.copy(minGpa = it.toDoubleOrNull()?:0.0) },
                                label = { Text("Min GPA") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rule.maxGpa.toString(),
                                onValueChange = { classifications[index] = rule.copy(maxGpa = it.toDoubleOrNull()?:0.0) },
                                label = { Text("Max GPA") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
             OutlinedButton(onClick = { classifications.add(ClassificationRule(0.0, 0.0, "")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Rule")
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// --- Sub-Components ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UniversitySelector(
    available: List<University>, 
    selected: University, 
    onSelect: (University) -> Unit,
    onAddClick: () -> Unit
) {
    Column {
        Text("Select University", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp) // Spacing between items
        ) {
            // Scrollable Row for Universities if there are many
            androidx.compose.foundation.layout.FlowRow(
               modifier = Modifier.weight(1f),
               horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                available.forEach { uni ->
                    val isSelected = uni.id == selected.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelect(uni) },
                        label = { Text(uni.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            
            // Add Button (Compact)
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
    // Range 3 to 50
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().width(120.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.height(300.dp) // Limit height so it scrolls
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
            
            // Credits Segment
            Text("Credits", style = MaterialTheme.typography.labelSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                (1..4).forEach { credit ->
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

            // Grade Segment
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

@Composable
fun ResultDialog(gpa: Double, classification: String, onDismiss: () -> Unit) {
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}