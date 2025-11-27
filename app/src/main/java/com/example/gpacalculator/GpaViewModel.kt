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
    val calculatedPercentage: Double? = null,
    val classification: String? = null,
    
    // Navigation States
    val isAddingUniversity: Boolean = false,
    val isPrinting: Boolean = false,
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

    // --- RESULT & CALCULATION LOGIC ---

    // FIX: This function was missing or unresolved
    fun dismissResult() {
        _uiState.update { 
            it.copy(
                calculatedGpa = null, 
                calculatedPercentage = null, 
                classification = null
            ) 
        }
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
            _uiState.update { it.copy(calculatedGpa = 0.0, calculatedPercentage = 0.0, classification = "N/A") }
            return
        }

        val rawGpa = totalPoints / totalCredits
        val finalGpa = BigDecimal(rawGpa).setScale(2, RoundingMode.HALF_UP).toDouble()
        
        val cls = uni.classifications.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
        val label = cls?.label ?: "Result Unknown"

        // Calculate Percentage using Formula Evaluator
        val rule = uni.percentageRules.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
        val percent = if (rule != null) {
            FormulaEvaluator.evaluate(rule.formula, finalGpa)
        } else {
            0.0
        }

        _uiState.update { 
            it.copy(calculatedGpa = finalGpa, calculatedPercentage = percent, classification = label) 
        }
    }

    // --- SUBJECT & UNIVERSITY MANAGEMENT ---

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

    fun startAddingUniversity() {
        _uiState.update { it.copy(isAddingUniversity = true, universityToEdit = null) }
    }

    fun startEditingUniversity(university: University) {
        _uiState.update { it.copy(isAddingUniversity = true, universityToEdit = university) }
    }

    fun cancelAddingUniversity() {
        _uiState.update { it.copy(isAddingUniversity = false, universityToEdit = null) }
    }

    fun saveUniversity(name: String, grades: List<Grade>, rules: List<ClassificationRule>, percentageRules: List<PercentageRule>) {
        if (name.isBlank() || grades.isEmpty()) return

        val editMode = _uiState.value.universityToEdit
        val customs = Storage.loadCustomUniversities(context).toMutableList()
        
        val newUni = University(
            id = editMode?.id ?: UUID.randomUUID().toString(),
            name = name,
            grades = grades,
            classifications = rules,
            percentageRules = percentageRules,
            isCustom = true
        )

        if (editMode != null) {
            val index = customs.indexOfFirst { it.id == editMode.id }
            if (index != -1) customs[index] = newUni
        } else {
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

    // --- PRINTING ---

    fun startPrinting() {
        _uiState.update { state ->
            val namedSubjects = state.subjects.mapIndexed { index, sub ->
                if (sub.name.isBlank()) sub.copy(name = "Subject ${index + 1}") else sub
            }
            state.copy(isPrinting = true, subjects = namedSubjects, calculatedGpa = null)
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
            val subjects = _uiState.value.subjects
            val state = _uiState.value
            // Assuming calc already done or re-do logic. For safety, using stored values or re-calculating.
            // We need GPA/Class for the PDF. We can recalc here quickly.
            
            var totalPoints = 0.0
            var totalCredits = 0
            subjects.forEach { subject ->
                val grade = subject.selectedGrade
                if (grade != null && grade.isCreditCourse) {
                    totalPoints += (grade.point * subject.credits)
                    totalCredits += subject.credits
                }
            }
            val gpaVal = if(totalCredits > 0) totalPoints / totalCredits else 0.0
            val finalGpa = BigDecimal(gpaVal).setScale(2, RoundingMode.HALF_UP).toDouble()
            
            val uni = state.selectedUniversity
            val cls = uni.classifications.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }?.label ?: "Result Unknown"
            val pRule = uni.percentageRules.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
            val percent = if (pRule != null) FormulaEvaluator.evaluate(pRule.formula, finalGpa) else 0.0

            PdfGenerator.generateMarksheetPdf(
                context,
                outputStream,
                details,
                subjects,
                finalGpa,
                percent,
                cls
            )
            _uiState.update { it.copy(importMessage = "PDF Saved successfully!") }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(importMessage = "Failed to generate PDF") }
        }
    }

    // --- SCANNER ---

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

    fun getExportData(): String = Storage.getExportString(context)

    fun importData(jsonString: String) {
        if (Storage.importFromString(context, jsonString)) {
            refreshUniversities()
            _uiState.update { it.copy(importMessage = "Import Successful") }
        } else {
            _uiState.update { it.copy(importMessage = "Import Failed") }
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }
}