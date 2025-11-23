package com.example.gpacalculator

import java.util.UUID

// --- Core Data Models ---

data class Grade(
    val symbol: String,
    val point: Double,
    val isCreditCourse: Boolean = true
)

data class ClassificationRule(
    val minGpa: Double,
    val maxGpa: Double,
    val label: String
)

data class University(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val grades: List<Grade>,
    val classifications: List<ClassificationRule>,
    val isCustom: Boolean = false
)

data class Subject(
    val id: String = UUID.randomUUID().toString(),
    var credits: Int = 0,
    var selectedGrade: Grade? = null
)

// --- Scan & UI Helper Models ---

data class ScannedRow(
    val id: String = UUID.randomUUID().toString(),
    val detectedCredits: Int?,
    val detectedGrade: String?,
    val confidence: Float
)

data class TempGrade(
    val id: String = UUID.randomUUID().toString(), 
    var symbol: String, 
    var pointStr: String
)

data class TempRule(
    val id: String = UUID.randomUUID().toString(), 
    var minStr: String, 
    var maxStr: String, 
    var label: String
)

// --- Predefined Universities ---

object UniversityPresets {

    val DBATU = University(
        id = "dbatu_preset",
        name = "DBATU",
        grades = listOf(
            Grade("EX", 10.0), Grade("AA", 9.0), Grade("AB", 8.5),
            Grade("BB", 8.0), Grade("BC", 7.5), Grade("CC", 7.0),
            Grade("CD", 6.5), Grade("DD", 6.0), Grade("DE", 5.5),
            Grade("EE", 5.0), Grade("EF", 0.0)
        ),
        classifications = listOf(
            ClassificationRule(0.0, 5.5, "Pass Class"),
            ClassificationRule(5.5, 6.0, "Second Class"),
            ClassificationRule(6.0, 7.5, "First Class"),
            ClassificationRule(7.5, 10.1, "Distinction")
        ),
        isCustom = false
    )

    val SPPU = University(
        id = "sppu_preset",
        name = "SPPU",
        grades = listOf(
            Grade("O", 10.0), Grade("A", 9.0), Grade("B", 8.0),
            Grade("C", 7.0), Grade("D", 6.0), Grade("E", 5.0),
            Grade("F", 0.0), Grade("AP", 0.0), Grade("FX", 0.0),
            Grade("II", 0.0),
            Grade("PP", 0.0, false),
            Grade("NP", 0.0, false)
        ),
        classifications = listOf(
            ClassificationRule(0.0, 5.5, "Fail / Reappear"),
            ClassificationRule(5.5, 6.25, "Second Class"),
            ClassificationRule(6.25, 6.75, "Higher Second Class"),
            ClassificationRule(6.75, 7.75, "First Class"),
            ClassificationRule(7.75, 10.1, "First Class with Distinction")
        ),
        isCustom = false
    )
    
    fun getAll() = listOf(DBATU, SPPU)
}