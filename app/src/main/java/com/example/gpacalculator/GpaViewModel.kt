package com.example.gpacalculator

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class GpaUiState(
    val availableUniversities: List<University> = emptyList(),
    val selectedUniversity: University = UniversityPresets.DBATU,
    val subjects: List<Subject> = generateSubjects(5),
    val calculatedGpa: Double? = null,
    val classification: String? = null,
    
    // Navigation States
    val isAddingUniversity: Boolean = false,
    val isPrinting: Boolean = false, // Shows Print Screen
    val universityToEdit: University? = null,
    val importMessage: String? = null,
    
    // Scan States
    val isScanning: Boolean = false,
    val scanImageUri: Uri? = null,
    val scannedRows: List<ScannedRow>? = null,
    val isProcessingOcr: Boolean = false
)

fun generateSubjects(count: Int): List<Subject> {
    return List(count) { index -> Subject(name = "Subject ${index + 1}") }
}

class GpaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GpaUiState())
    val uiState: StateFlow<GpaUiState> = _uiState.asStateFlow()
    
    private val context = application.applicationContext

    init {
        refreshUniversities()
    }

    private fun refreshUniversities() {
        val customs = Storage.loadCustomUniversities(context)
        val all = UniversityPresets.getAll() + customs
        _uiState.update { 
            val currentSel = it.selectedUniversity
            val newSel = if (all.any { u -> u.id == currentSel.id }) currentSel else all.first()
            it.copy(availableUniversities = all, selectedUniversity = newSel) 
        }
    }

    // --- PRINTING LOGIC ---

    fun startPrinting() {
        // Ensure subjects have default names if empty
        _uiState.update { state ->
            val namedSubjects = state.subjects.mapIndexed { index, sub ->
                if (sub.name.isBlank()) sub.copy(name = "Subject ${index + 1}") else sub
            }
            state.copy(isPrinting = true, subjects = namedSubjects, calculatedGpa = null) // Close dialog
        }
    }

    fun cancelPrinting() {
        _uiState.update { it.copy(isPrinting = false) }
    }

    fun updateSubjectName(index: Int, newName: String) {
        _uiState.update { state ->
            val updated = state.subjects.toMutableList()
            updated[index] = updated[index].copy(name = newName)
            state.copy(subjects = updated)
        }
    }

    fun generatePdf(outputStream: OutputStream, details: StudentDetails) {
        try {
            // Re-calculate GPA internally to be sure (though should be same)
            // Or pass the previous result. Let's use current state.
            val subjects = _uiState.value.subjects
            var totalPoints = 0.0
            var totalCredits = 0
            subjects.forEach { subject ->
                val grade = subject.selectedGrade
                if (grade != null && grade.isCreditCourse) {
                    totalPoints += (grade.point * subject.credits)
                    totalCredits += subject.credits
                }
            }
            val gpa = if(totalCredits > 0) totalPoints / totalCredits else 0.0
            val finalGpa = BigDecimal(gpa).setScale(2, RoundingMode.HALF_UP).toDouble()
            
            // Find Classification
            val uni = _uiState.value.selectedUniversity
            val cls = uni.classifications.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
            val label = cls?.label ?: "Result Unknown"

            PdfGenerator.generateMarksheetPdf(
                context,
                outputStream,
                details,
                subjects,
                finalGpa,
                label
            )
            _uiState.update { it.copy(importMessage = "PDF Saved successfully!") }
        } catch (e: Exception) {
            _uiState.update { it.copy(importMessage = "Failed to generate PDF") }
        }
    }

    // --- SCANNER LOGIC ---

    fun startScan() {
        _uiState.update { it.copy(isScanning = true, scanImageUri = null, scannedRows = null) }
    }

    fun cancelScan() {
        _uiState.update { it.copy(isScanning = false, scanImageUri = null, scannedRows = null) }
    }

    fun onImagePicked(uri: Uri) {
        _uiState.update { it.copy(scanImageUri = uri) }
    }

    fun processCroppedImage(bitmap: Bitmap) {
        _uiState.update { it.copy(isProcessingOcr = true) }
        viewModelScope.launch {
            try {
                val results = OcrProcessor.processImage(bitmap, _uiState.value.selectedUniversity)
                _uiState.update { it.copy(isProcessingOcr = false, scannedRows = results) }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isProcessingOcr = false, importMessage = "OCR Failed: ${e.localizedMessage}") }
            }
        }
    }

    fun applyScannedSubjects(subjects: List<Subject>) {
        _uiState.update { current ->
            // Assign default names
            val namedSubjects = subjects.mapIndexed { i, s -> s.copy(name = "Subject ${i + 1}") }
            
            val merged = if (current.subjects.all { it.credits == 0 && it.selectedGrade == null }) {
                namedSubjects
            } else {
                current.subjects + namedSubjects
            }
            current.copy(
                subjects = merged,
                isScanning = false,
                scanImageUri = null,
                scannedRows = null,
                calculatedGpa = null
            )
        }
    }

    // --- IMPORT / EXPORT ---

    fun getExportData(): String {
        return Storage.getExportString(context)
    }

    fun importData(jsonString: String) {
        val success = Storage.importFromString(context, jsonString)
        if (success) {
            refreshUniversities()
            _uiState.update { it.copy(importMessage = "Universities imported successfully!") }
        } else {
            _uiState.update { it.copy(importMessage = "Failed to import. Invalid data.") }
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    // --- MAIN ACTIONS ---

    fun setUniversity(university: University) {
        _uiState.update { it.copy(selectedUniversity = university, calculatedGpa = null, classification = null) }
    }

    fun setSubjectCount(count: Int) {
        _uiState.update { current ->
            val newSubjects = if (count > current.subjects.size) {
                current.subjects + generateSubjects(count - current.subjects.size)
            } else {
                current.subjects.take(count)
            }
            current.copy(subjects = newSubjects, calculatedGpa = null)
        }
    }

    fun updateSubject(index: Int, credits: Int?, grade: Grade?) {
        _uiState.update { state ->
            val updatedSubjects = state.subjects.toMutableList()
            val currentSubject = updatedSubjects[index]
            
            updatedSubjects[index] = currentSubject.copy(
                credits = credits ?: currentSubject.credits,
                selectedGrade = grade ?: currentSubject.selectedGrade
            )
            state.copy(subjects = updatedSubjects, calculatedGpa = null)
        }
    }

    fun clearAll() {
        _uiState.update { 
            it.copy(
                subjects = generateSubjects(it.subjects.size),
                calculatedGpa = null,
                classification = null
            )
        }
    }

    fun dismissResult() {
        _uiState.update { it.copy(calculatedGpa = null, classification = null) }
    }

    fun calculateGpa() {
        val state = _uiState.value
        val uni = state.selectedUniversity
        val subjects = state.subjects

        var totalPoints = 0.0
        var totalCredits = 0

        subjects.forEach { subject ->
            val grade = subject.selectedGrade
            if (grade != null && grade.isCreditCourse) {
                totalPoints += (grade.point * subject.credits)
                totalCredits += subject.credits
            }
        }

        if (totalCredits == 0) {
            _uiState.update { it.copy(calculatedGpa = 0.0, classification = "N/A") }
            return
        }

        val rawGpa = totalPoints / totalCredits
        val finalGpa = BigDecimal(rawGpa).setScale(2, RoundingMode.HALF_UP).toDouble()
        
        val cls = uni.classifications.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
        val label = cls?.label ?: "Result Unknown"

        _uiState.update { 
            it.copy(calculatedGpa = finalGpa, classification = label) 
        }
    }

    // --- CRUD ---

    fun startAddingUniversity() {
        _uiState.update { it.copy(isAddingUniversity = true, universityToEdit = null) }
    }

    fun startEditingUniversity(university: University) {
        _uiState.update { it.copy(isAddingUniversity = true, universityToEdit = university) }
    }

    fun cancelAddingUniversity() {
        _uiState.update { it.copy(isAddingUniversity = false, universityToEdit = null) }
    }

    fun saveUniversity(name: String, grades: List<Grade>, rules: List<ClassificationRule>) {
        if (name.isBlank() || grades.isEmpty()) return

        val editMode = _uiState.value.universityToEdit
        val customs = Storage.loadCustomUniversities(context).toMutableList()
        
        if (editMode != null) {
            val index = customs.indexOfFirst { it.id == editMode.id }
            if (index != -1) {
                customs[index] = editMode.copy(name = name, grades = grades, classifications = rules)
            }
        } else {
            val newUni = University(
                id = UUID.randomUUID().toString(),
                name = name,
                grades = grades,
                classifications = rules,
                isCustom = true
            )
            customs.add(newUni)
        }

        Storage.saveCustomUniversities(context, customs)
        refreshUniversities()
        _uiState.update { it.copy(isAddingUniversity = false, universityToEdit = null) }
    }

    fun deleteUniversity(university: University) {
        val customs = Storage.loadCustomUniversities(context).toMutableList()
        customs.removeAll { it.id == university.id }
        Storage.saveCustomUniversities(context, customs)
        refreshUniversities()
    }
}