package com.example.gpacalculator

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

data class GpaUiState(
    val availableUniversities: List<University> = emptyList(),
    val selectedUniversity: University = UniversityPresets.DBATU,
    val subjects: List<Subject> = generateSubjects(5),
    val calculatedGpa: Double? = null,
    val classification: String? = null,
    val isAddingUniversity: Boolean = false,
    val universityToEdit: University? = null,
    val importMessage: String? = null // For Toast/Snackbar messages
)

fun generateSubjects(count: Int): List<Subject> {
    return List(count) { Subject() }
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

    // --- Import / Export ---

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

    // --- Main Actions ---

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