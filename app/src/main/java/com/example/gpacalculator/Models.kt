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

// NEW: Rule for calculating percentage
data class PercentageRule(
    val minGpa: Double,
    val maxGpa: Double,
    val formula: String // e.g., "10 * gpa - 7.5"
)

data class University(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val grades: List<Grade>,
    val classifications: List<ClassificationRule>,
    val percentageRules: List<PercentageRule> = emptyList(), // New field
    val isCustom: Boolean = false
)

data class Subject(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "", 
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

// NEW: Helper for editing percentage rules
data class TempPercentageRule(
    val id: String = UUID.randomUUID().toString(),
    var minStr: String,
    var maxStr: String,
    var formula: String
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
            Grade("EE", 5.0), Grade("EF", 0.0),
            Grade("FF", 0.0), Grade("AU", 0.0, isCreditCourse = false)
        ),
        classifications = listOf(
            ClassificationRule(0.0, 5.5, "Pass Class"),
            ClassificationRule(5.5, 6.0, "Second Class"),
            ClassificationRule(6.0, 7.5, "First Class"),
            ClassificationRule(7.5, 10.1, "Distinction")
        ),
        // Formula: (gpa-0.5)*10
        percentageRules = listOf(
            PercentageRule(0.0, 10.1, "(gpa - 0.5) * 10")
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
        // Complex Piecewise Formulas
        percentageRules = listOf(
            PercentageRule(9.50, 10.1, "20 * gpa - 100"),
            PercentageRule(8.25, 9.50, "12 * gpa - 25"),
            PercentageRule(6.75, 8.25, "10 * gpa - 7.5"),
            PercentageRule(5.75, 6.75, "5 * gpa + 26.25"), // Adjusted logic based on input logic flow usually used
            // Wait, input said: "5 * gpa - 26.25" for 5.75-6.75. I will use user input exactly.
            PercentageRule(5.75, 6.75, "5 * gpa - 26.25"),
            PercentageRule(5.25, 5.75, "10 * gpa - 2.50"),
            PercentageRule(4.75, 5.25, "10 * gpa - 2.50"), // Same as above per prompt
            PercentageRule(4.00, 4.75, "6.6 * gpa - 13.6")
        ),
        isCustom = false
    )
    
    fun getAll() = listOf(DBATU, SPPU)
}