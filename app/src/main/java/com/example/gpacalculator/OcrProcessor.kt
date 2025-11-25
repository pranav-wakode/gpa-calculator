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

object OcrProcessor {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun processImage(bitmap: Bitmap, university: University): List<ScannedRow> = withContext(Dispatchers.Default) {
        // Ensure safe bitmap config for ML Kit
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

        // --- STRATEGY: SEMANTIC PAIRING ---
        // Instead of grouping lines, we identify "Candidates" for Credits and Grades
        // and match them based on proximity.

        val creditCandidates = mutableListOf<Text.Element>()
        val gradeCandidates = mutableListOf<Text.Element>()

        for (element in allElements) {
            val raw = element.text.trim().uppercase()
                .replace("O", "0")
                .replace("I", "1")
                .replace("L", "1")
                .replace("|", "1")

            // 1. Identify Credit Candidate (Strict Number, 0-25)
            if (raw.matches(Regex("^\\d{1,2}$"))) {
                val num = raw.toIntOrNull()
                if (num != null && num <= 25) {
                    creditCandidates.add(element)
                    continue // An element can't be both
                }
            }

            // 2. Identify Grade Candidate (Matches University Schema)
            // Use raw text without number replacement for grades
            val gradeRaw = element.text.trim().uppercase()
            if (findBestGradeMatch(gradeRaw, university.grades) != null) {
                gradeCandidates.add(element)
            }
        }

        // Sort Credits top-to-bottom to process in order
        creditCandidates.sortBy { it.boundingBox?.top ?: 0 }

        val results = mutableListOf<ScannedRow>()
        
        // Used to prevent reusing the same grade for multiple credits (though rare)
        val usedGradeIndices = mutableSetOf<Int>()

        for (creditElement in creditCandidates) {
            val creditBox = creditElement.boundingBox ?: continue
            val creditVal = creditElement.text.trim()
                .replace("O", "0").replace("I", "1").replace("L", "1").replace("|", "1")
                .toIntOrNull() ?: continue

            // Find the BEST Grade match for this Credit
            // Criteria:
            // 1. Must be to the RIGHT of the credit
            // 2. Vertical center must be within a tolerance (e.g., +/- 50px)
            // 3. Pick the CLOSEST one horizontally
            
            var bestGradeIndex = -1
            var minDistance = Int.MAX_VALUE

            for (i in gradeCandidates.indices) {
                if (usedGradeIndices.contains(i)) continue
                
                val gradeElement = gradeCandidates[i]
                val gradeBox = gradeElement.boundingBox ?: continue

                // Check 1: Is it to the right?
                if (gradeBox.left > creditBox.left) {
                    // Check 2: Vertical Alignment (Center-to-Center)
                    val creditCenterY = creditBox.centerY()
                    val gradeCenterY = gradeBox.centerY()
                    val verticalDiff = abs(creditCenterY - gradeCenterY)
                    
                    // Tolerance: allow large vertical shift (e.g. 1.5x element height) because
                    // grade fonts are often taller or misaligned in tables.
                    val tolerance = maxOf(creditBox.height(), gradeBox.height()) * 1.5
                    
                    if (verticalDiff < tolerance) {
                        // Check 3: Distance
                        val distance = gradeBox.left - creditBox.right
                        if (distance < minDistance) {
                            minDistance = distance
                            bestGradeIndex = i
                        }
                    }
                }
            }

            if (bestGradeIndex != -1) {
                usedGradeIndices.add(bestGradeIndex)
                val matchedGradeElement = gradeCandidates[bestGradeIndex]
                val gradeVal = findBestGradeMatch(matchedGradeElement.text.trim().uppercase(), university.grades)
                
                results.add(
                    ScannedRow(
                        detectedCredits = creditVal,
                        detectedGrade = gradeVal,
                        confidence = 1.0f
                    )
                )
            }
        }

        // --- SPECIAL CASE: Audit Subjects (AU) with 0 Credits ---
        // Sometimes '0' credit is not printed, or OCR misses it. 
        // If we have 'AU' grades left over, add them with 0 credits.
        for (i in gradeCandidates.indices) {
            if (usedGradeIndices.contains(i)) continue
            val gradeRaw = gradeCandidates[i].text.trim().uppercase()
            val matched = findBestGradeMatch(gradeRaw, university.grades)
            
            if (matched == "AU" || matched == "PP" || matched == "NP") {
                results.add(ScannedRow(detectedCredits = 0, detectedGrade = matched, confidence = 0.8f))
            }
        }

        return results
    }

    private fun findBestGradeMatch(text: String, grades: List<Grade>): String? {
        // Exact match
        grades.find { it.symbol.equals(text, ignoreCase = true) }?.let { return it.symbol }

        // Fix common OCR errors
        var fixedText = text.replace("0", "O").replace("1", "I").replace("|", "I")
        grades.find { it.symbol.equals(fixedText, ignoreCase = true) }?.let { return it.symbol }
        
        // Fuzzy match (Distance <= 1)
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
                newCost[i] = minOf(insertCost, deleteCost, replaceCost)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
}