package com.example.gpacalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Edit
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
                            existingUniversity = uiState.universityToEdit,
                            onSave = { name, grades, rules -> viewModel.saveUniversity(name, grades, rules) },
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
            UniversitySelector(
                available = uiState.availableUniversities,
                selected = uiState.selectedUniversity,
                onSelect = { viewModel.setUniversity(it) },
                onAddClick = { viewModel.startAddingUniversity() },
                onEditClick = { viewModel.startEditingUniversity(it) },
                onDeleteClick = { viewModel.deleteUniversity(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            SubjectCountHeader(
                count = uiState.subjects.size,
                onCountChange = { viewModel.setSubjectCount(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

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
            
            if (uiState.calculatedGpa != null) {
                ResultDialog(
                    gpa = uiState.calculatedGpa!!,
                    classification = uiState.classification ?: "",
                    onDismiss = { viewModel.dismissResult() } // FIX: Call the dismiss function
                )
            }
        }
    }
}

// --- Helper Data Class for Edit Screen to hold String input ---
data class TempGrade(val id: String = java.util.UUID.randomUUID().toString(), var symbol: String, var pointStr: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUniversityScreen(
    existingUniversity: University? = null,
    onSave: (String, List<Grade>, List<ClassificationRule>) -> Unit,
    onCancel: () -> Unit
) {
    var uniName by remember { mutableStateOf(existingUniversity?.name ?: "") }
    
    // Init grades: If editing, map existing grades to TempGrade. If new, default list.
    val grades = remember { 
        mutableStateListOf<TempGrade>().apply {
            if (existingUniversity != null) {
                addAll(existingUniversity.grades.map { TempGrade(symbol = it.symbol, pointStr = it.point.toString()) })
            } else {
                add(TempGrade(symbol = "AA", pointStr = "10.0"))
            }
        }
    }
    
    // Init classifications
    val classifications = remember { 
        mutableStateListOf<ClassificationRule>().apply {
            if (existingUniversity != null) {
                addAll(existingUniversity.classifications)
            } else {
                add(ClassificationRule(0.0, 5.0, "Fail"))
                add(ClassificationRule(5.0, 10.1, "Pass"))
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
                        // Convert TempGrade back to Grade
                        val finalGrades = grades.map { 
                            Grade(it.symbol, it.pointStr.toDoubleOrNull() ?: 0.0)
                        }
                        onSave(uniName, finalGrades, classifications) 
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
            
            // --- Grades Editor ---
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
                    
                    // FIX: Use text directly, no weird auto-formatting
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

            // --- Rules Editor ---
            Text("Classifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            classifications.forEachIndexed { index, rule ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        OutlinedTextField(
                            value = rule.label,
                            onValueChange = { classifications[index] = rule.copy(label = it) },
                            label = { Text("Label") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                             OutlinedTextField(
                                value = rule.minGpa.toString(),
                                onValueChange = { classifications[index] = rule.copy(minGpa = it.toDoubleOrNull()?:0.0) },
                                label = { Text("Min") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rule.maxGpa.toString(),
                                onValueChange = { classifications[index] = rule.copy(maxGpa = it.toDoubleOrNull()?:0.0) },
                                label = { Text("Max") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
             OutlinedButton(onClick = { classifications.add(ClassificationRule(0.0, 0.0, "")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text("Add Rule")
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// --- Sub-Components ---

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
        Text("Select University (Long press custom to edit)", style = MaterialTheme.typography.labelMedium)
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
                                    text = uni.name + if(uni.isCustom) " *" else "",
                                    fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal
                                ) 
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            // Enable Long Press logic for custom unis
                            modifier = Modifier.combinedClickable(
                                onClick = { onSelect(uni) },
                                onLongClick = { 
                                    if (uni.isCustom) {
                                        showMenuFor = uni
                                    }
                                }
                            )
                        )

                        // Context Menu for Edit/Delete
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
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().width(120.dp),
                textStyle = MaterialTheme.typography.bodySmall
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