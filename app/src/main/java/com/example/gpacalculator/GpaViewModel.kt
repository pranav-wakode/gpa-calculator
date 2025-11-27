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
    
    val isAddingUniversity: Boolean = false,
    val isPrinting: Boolean = false,
    val universityToEdit: University? = null,
    val importMessage: String? = null,
    
    val isScanning: Boolean = false,
    val scanImageUri: Uri? = null,
    val scannedRows: List<ScannedRow>? = null,
    val isProcessingOcr: Boolean = false
)

// Helper to generate subjects with correct index offset
fun generateSubjects(count: Int, startIndex: Int = 0): List<Subject> {
    return List(count) { index -> Subject(name = "Subject ${startIndex + index + 1}") }
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

    fun setSubjectCount(count: Int) {
        _uiState.update { current ->
            val currentSize = current.subjects.size
            val newSubjects = if (count > currentSize) {
                // Append new subjects, continuing the index count
                current.subjects + generateSubjects(count - currentSize, startIndex = currentSize)
            } else {
                current.subjects.take(count)
            }
            current.copy(subjects = newSubjects, calculatedGpa = null)
        }
    }

    // --- PRINTING ---

    fun startPrinting() {
        _uiState.update { state ->
            // Ensure names are consistent 1..N if they are default
            val namedSubjects = state.subjects.mapIndexed { index, sub ->
                if (sub.name.isBlank() || sub.name.startsWith("Subject ")) 
                    sub.copy(name = "Subject ${index + 1}") 
                else sub
            }
            state.copy(isPrinting = true, subjects = namedSubjects, calculatedGpa = null)
        }
    }

    // Rest of the functions remain exactly as before...
    fun cancelPrinting() { _uiState.update { it.copy(isPrinting = false) } }
    fun updateSubjectName(index: Int, newName: String) { 
        _uiState.update { s -> 
            val l = s.subjects.toMutableList()
            l[index] = l[index].copy(name = newName)
            s.copy(subjects = l)
        } 
    }
    fun generatePdf(outputStream: OutputStream, details: StudentDetails) {
        try {
            val subjects = _uiState.value.subjects
            val state = _uiState.value
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

            PdfGenerator.generateMarksheetPdf(context, outputStream, details, subjects, finalGpa, percent, cls)
            _uiState.update { it.copy(importMessage = "PDF Saved successfully!") }
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(importMessage = "Failed to generate PDF") }
        }
    }
    
    // Standard Action Passthroughs
    fun setUniversity(u: University) { _uiState.update { it.copy(selectedUniversity = u, calculatedGpa = null) } }
    fun updateSubject(i: Int, c: Int?, g: Grade?) { 
        _uiState.update { s -> 
            val list = s.subjects.toMutableList()
            list[i] = list[i].copy(credits = c ?: list[i].credits, selectedGrade = g ?: list[i].selectedGrade)
            s.copy(subjects = list, calculatedGpa = null)
        }
    }
    fun clearAll() { _uiState.update { it.copy(subjects = generateSubjects(it.subjects.size), calculatedGpa = null) } }
    fun dismissResult() { _uiState.update { it.copy(calculatedGpa = null) } }
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
        if (totalCredits == 0) { _uiState.update { it.copy(calculatedGpa = 0.0, calculatedPercentage = 0.0, classification = "N/A") }; return }
        val rawGpa = totalPoints / totalCredits
        val finalGpa = BigDecimal(rawGpa).setScale(2, RoundingMode.HALF_UP).toDouble()
        val cls = uni.classifications.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }?.label ?: "Result Unknown"
        val rule = uni.percentageRules.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
        val percent = if (rule != null) FormulaEvaluator.evaluate(rule.formula, finalGpa) else 0.0
        _uiState.update { it.copy(calculatedGpa = finalGpa, calculatedPercentage = percent, classification = cls) }
    }
    
    fun startAddingUniversity() { _uiState.update { it.copy(isAddingUniversity = true, universityToEdit = null) } }
    fun startEditingUniversity(u: University) { _uiState.update { it.copy(isAddingUniversity = true, universityToEdit = u) } }
    fun cancelAddingUniversity() { _uiState.update { it.copy(isAddingUniversity = false, universityToEdit = null) } }
    fun saveUniversity(n: String, g: List<Grade>, r: List<ClassificationRule>, p: List<PercentageRule>) {
        if (n.isBlank() || g.isEmpty()) return
        val editMode = _uiState.value.universityToEdit
        val customs = Storage.loadCustomUniversities(context).toMutableList()
        val newUni = University(id = editMode?.id ?: UUID.randomUUID().toString(), name = n, grades = g, classifications = r, percentageRules = p, isCustom = true)
        if (editMode != null) { val idx = customs.indexOfFirst { it.id == editMode.id }; if (idx != -1) customs[idx] = newUni } else customs.add(newUni)
        Storage.saveCustomUniversities(context, customs)
        refreshUniversities()
        _uiState.update { it.copy(isAddingUniversity = false, universityToEdit = null) }
    }
    fun deleteUniversity(u: University) {
        val customs = Storage.loadCustomUniversities(context).toMutableList()
        customs.removeAll { it.id == u.id }
        Storage.saveCustomUniversities(context, customs)
        refreshUniversities()
    }
    fun startScan() { _uiState.update { it.copy(isScanning = true, scanImageUri = null, scannedRows = null) } }
    fun cancelScan() { _uiState.update { it.copy(isScanning = false, scanImageUri = null, scannedRows = null) } }
    fun onImagePicked(uri: Uri) { _uiState.update { it.copy(scanImageUri = uri) } }
    fun processCroppedImage(bitmap: Bitmap) { 
        _uiState.update { it.copy(isProcessingOcr = true) }
        viewModelScope.launch { try { val r = OcrProcessor.processImage(bitmap, _uiState.value.selectedUniversity); _uiState.update { it.copy(isProcessingOcr = false, scannedRows = r) } } catch (e: Exception) { _uiState.update { it.copy(isProcessingOcr = false) } } } 
    }
    fun applyScannedSubjects(s: List<Subject>) { 
        _uiState.update { c -> 
            val named = s.mapIndexed { i, sub -> sub.copy(name = "Subject ${i + 1}") }
            val merged = if (c.subjects.all { it.credits == 0 }) named else c.subjects + named
            c.copy(subjects = merged, isScanning = false, scanImageUri = null, scannedRows = null, calculatedGpa = null) 
        } 
    }
    fun getExportData() = Storage.getExportString(context)
    fun importData(s: String) { if(Storage.importFromString(context, s)) refreshUniversities() }
    fun clearImportMessage() { _uiState.update { it.copy(importMessage = null) } }
}