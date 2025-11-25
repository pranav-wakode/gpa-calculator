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
import kotlin.math.max
import kotlin.math.min

object OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap, university: University): List<ScannedRow> = withContext(Dispatchers.Default) {
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
            .filter { it.text.isNotBlank() }

        if (allElements.isEmpty()) return emptyList()

        // Clustering: Vertical Overlap
        val sortedElements = allElements.sortedBy { it.boundingBox?.top ?: 0 }
        val rows = mutableListOf<MutableList<Text.Element>>()

        for (element in sortedElements) {
            val eBox = element.boundingBox ?: continue
            
            val bestRow = rows.find { row ->
                val anchor = row.first().boundingBox ?: return@find false
                val intersectTop = max(eBox.top, anchor.top)
                val intersectBottom = min(eBox.bottom, anchor.bottom)
                
                if (intersectBottom > intersectTop) {
                    val overlapHeight = intersectBottom - intersectTop
                    val minHeight = min(eBox.height(), anchor.height())
                    overlapHeight > (minHeight * 0.3)
                } else {
                    false
                }
            }

            if (bestRow != null) {
                bestRow.add(element)
            } else {
                rows.add(mutableListOf(element))
            }
        }

        val results = mutableListOf<ScannedRow>()

        for (row in rows) {
            row.sortBy { it.boundingBox?.left ?: 0 }

            // --- FILTERING HEADER ROWS ---
            // If row contains header keywords, skip it completely
            val rowText = row.joinToString(" ") { it.text.uppercase() }
            if (rowText.contains("CREDIT") || rowText.contains("GRADE") || rowText.contains("SUBJECT") || rowText.contains("CODE")) {
                continue
            }

            var bestCredit: Int? = null
            var bestGrade: String? = null

            for (element in row) {
                val rawText = element.text.trim().uppercase()
                    .replace("O", "0") 
                    .replace("I", "1")
                    .replace("L", "1")

                // 1. Try as Credit (Strict numbers)
                if (bestCredit == null) {
                    if (rawText.matches(Regex("^\\d{1,2}$"))) {
                        val digit = rawText.toIntOrNull()
                        if (digit != null && digit <= 25) { 
                            bestCredit = digit
                            continue 
                        }
                    }
                }

                // 2. Try as Grade
                val gradeText = element.text.trim().uppercase()
                if (bestGrade == null) {
                    val match = findBestGradeMatch(gradeText, university.grades)
                    if (match != null) {
                        bestGrade = match
                    }
                }
            }

            // --- SPECIAL LOGIC FOR 'AU' (AUDIT) ---
            if (bestGrade == "AU") {
                bestCredit = 0
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