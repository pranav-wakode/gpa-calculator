package com.example.gpacalculator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.math.RoundingMode

data class GpaUiState(
    val selectedUniversity: University = UniversityPresets.DBATU,
    val subjects: List<Subject> = generateSubjects(5), // Default 5 subjects
    val calculatedGpa: Double? = null,
    val classification: String? = null,
    val isError: Boolean = false
)

// Helper to generate initial empty subjects
fun generateSubjects(count: Int): List<Subject> {
    return List(count) { Subject() }
}

class GpaViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GpaUiState())
    val uiState: StateFlow<GpaUiState> = _uiState.asStateFlow()

    // --- Actions ---

    fun setUniversity(university: University) {
        _uiState.update { it.copy(selectedUniversity = university, calculatedGpa = null, classification = null) }
    }

    fun setSubjectCount(count: Int) {
        _uiState.update { current ->
            // Preserve existing data if expanding, cut off if shrinking
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
            state.copy(subjects = updatedSubjects, calculatedGpa = null) // Reset result on edit
        }
    }

    fun calculateGpa() {
        val state = _uiState.value
        val uni = state.selectedUniversity
        val subjects = state.subjects

        var totalPoints = 0.0
        var totalCredits = 0

        // Validation check: Ensure all subjects have valid inputs
        val allValid = subjects.all { it.credits > 0 && it.selectedGrade != null }
        
        // Note: Actually, 0 credits might be valid for Audit courses, but usually we calculate GPA based on credit courses.
        // For the formula: GPA = Sum(GradePoint * Credit) / Sum(Credits)
        
        subjects.forEach { subject ->
            val grade = subject.selectedGrade
            if (grade != null && grade.isCreditCourse) {
                totalPoints += (grade.point * subject.credits)
                totalCredits += subject.credits
            }
        }

        if (totalCredits == 0) {
            // Avoid divide by zero or empty calculation
            _uiState.update { it.copy(calculatedGpa = 0.0, classification = "N/A") }
            return
        }

        val rawGpa = totalPoints / totalCredits
        // Round to 2 decimals
        val finalGpa = BigDecimal(rawGpa).setScale(2, RoundingMode.HALF_UP).toDouble()
        
        // Determine Classification
        val cls = uni.classifications.find { finalGpa >= it.minGpa && finalGpa < it.maxGpa }
        val label = cls?.label ?: "Result Unknown"

        _uiState.update { 
            it.copy(calculatedGpa = finalGpa, classification = label) 
        }
    }
}