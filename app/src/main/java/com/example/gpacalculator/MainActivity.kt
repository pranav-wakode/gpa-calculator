package com.example.gpacalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gpacalculator.ui.theme.GPACalculatorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPACalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GpaCalculatorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpaCalculatorScreen(viewModel: GpaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.calculateGpa() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("Calculate GPA")
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
                selected = uiState.selectedUniversity,
                onSelect = { viewModel.setUniversity(it) }
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
                // Spacer for FAB visibility
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
            
            // 4. Results Dialog/Sheet
            if (uiState.calculatedGpa != null) {
                ResultDialog(
                    gpa = uiState.calculatedGpa!!,
                    classification = uiState.classification ?: "",
                    onDismiss = { viewModel.setSubjectCount(uiState.subjects.size) } 
                )
            }
        }
    }
}

@Composable
fun UniversitySelector(selected: University, onSelect: (University) -> Unit) {
    val universities = UniversityPresets.getAll()
    
    Column {
        Text("Select University", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            universities.forEach { uni ->
                val isSelected = uni.id == selected.id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelect(uni) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uni.name,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .clickable { }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Add", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectCountHeader(count: Int, onCountChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(3, 4, 5, 6, 7, 8, 9, 10)

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
                value = "Change Count",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().width(160.dp),
                textStyle = MaterialTheme.typography.bodySmall
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { number ->
                    DropdownMenuItem(
                        text = { Text("$number Subjects") },
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