package com.example.gpacalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(
    subjects: List<Subject>,
    universityName: String,
    onGeneratePdf: (StudentDetails) -> Unit,
    onUpdateSubjectName: (Int, String) -> Unit,
    onCancel: () -> Unit
) {
    // Student Info State
    var studentName by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var prn by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf("") }
    var uniNameEdit by remember { mutableStateOf(universityName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generate Marksheet") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    Button(onClick = {
                        onGeneratePdf(
                            StudentDetails(
                                universityName = uniNameEdit,
                                studentName = studentName,
                                className = className,
                                branch = branch,
                                prn = prn,
                                semester = semester
                            )
                        )
                    }) {
                        // CHANGED: Icons.Default.Check instead of PictureAsPdf
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Finish")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. University & Student Details
            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Student Details", fontWeight = FontWeight.Bold)
                        
                        OutlinedTextField(
                            value = uniNameEdit,
                            onValueChange = { uniNameEdit = it },
                            label = { Text("University Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = studentName,
                                onValueChange = { studentName = it },
                                label = { Text("Student Name") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = prn,
                                onValueChange = { prn = it },
                                label = { Text("PRN/Roll No") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = className,
                                onValueChange = { className = it },
                                label = { Text("Class (e.g. SY B.Tech)") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = semester,
                                onValueChange = { semester = it },
                                label = { Text("Semester") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text("Branch") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
            item { Text("Rename Subjects", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

            // 2. Subjects List
            itemsIndexed(subjects) { index, subject ->
                Card {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = subject.name,
                            onValueChange = { onUpdateSubjectName(index, it) },
                            label = { Text("Subject ${index + 1}") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("Grade: ${subject.selectedGrade?.symbol ?: "-"}", fontWeight = FontWeight.Bold)
                            Text("Cred: ${subject.credits}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}