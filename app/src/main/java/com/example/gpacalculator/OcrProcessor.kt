package com.example.gpacalculator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min

object OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap, university: University): List<ScannedRow> = withContext(Dispatchers.Default) {
        // ML Kit safe bitmap check
        val safeBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            bitmap
        }

        val textResult = runTextRecognition(safeBitmap)
        parseTextToRows(textResult, university)
    }

    private suspend fun runTextRecognition(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private fun parseTextToRows(text: Text, university: University): List<ScannedRow> {
        val allElements = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }

        if (allElements.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<Text.Element>>()
        val sortedElements = allElements.sortedBy { it.boundingBox?.centerY() ?: 0 }
        
        for (element in sortedElements) {
            val elementY = element.boundingBox?.centerY() ?: continue
            val elementH = element.boundingBox?.height() ?: 20

            val matchedRow = rows.find { row ->
                val rowY = row.first().boundingBox?.centerY() ?: 0
                abs(rowY - elementY) < (elementH * 0.5) 
            }

            if (matchedRow != null) {
                matchedRow.add(element)
            } else {
                rows.add(mutableListOf(element))
            }
        }

        val results = mutableListOf<ScannedRow>()

        for (row in rows) {
            // Sort L->R
            row.sortBy { it.boundingBox?.left ?: 0 }

            var bestCredit: Int? = null
            var bestGrade: String? = null

            for (element in row) {
                val rawText = element.text.trim().uppercase()

                // FIX: Allow 1 or 2 digits (e.g. "4", "04", "10")
                if (bestCredit == null && rawText.matches(Regex("^\\d{1,2}$"))) {
                    val digit = rawText.toIntOrNull()
                    if (digit != null && digit in 1..50) {
                        bestCredit = digit
                        continue
                    }
                }

                if (bestGrade == null) {
                    val match = findBestGradeMatch(rawText, university.grades)
                    if (match != null) {
                        bestGrade = match
                    }
                }
            }

            if (bestCredit != null || bestGrade != null) {
                results.add(
                    ScannedRow(
                        detectedCredits = bestCredit,
                        detectedGrade = bestGrade,
                        confidence = if (bestCredit != null && bestGrade != null) 1.0f else 0.5f
                    )
                )
            }
        }

        return results
    }

    private fun findBestGradeMatch(text: String, grades: List<Grade>): String? {
        grades.find { it.symbol.equals(text, ignoreCase = true) }?.let { return it.symbol }

        var fixedText = text.replace("0", "O").replace("1", "I").replace("|", "I")
        grades.find { it.symbol.equals(fixedText, ignoreCase = true) }?.let { return it.symbol }
        
        grades.forEach { grade ->
            if (abs(grade.symbol.length - text.length) <= 1) {
                if (levenshtein(text, grade.symbol) <= 1) return grade.symbol
            }
        }
        
        return null
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var cost = Array(lhsLen + 1) { it }
        var newCost = Array(lhsLen + 1) { 0 }

        for (j in 1..rhsLen) {
            newCost[0] = j
            for (i in 1..lhsLen) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val replaceCost = cost[i - 1] + match
                val insertCost = cost[i] + 1
                val deleteCost = newCost[i - 1] + 1
                newCost[i] = min(min(insertCost, deleteCost), replaceCost)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}