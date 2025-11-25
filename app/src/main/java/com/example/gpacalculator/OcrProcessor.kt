package com.example.gpacalculator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
        // 1. PRE-PROCESSING: Binarization (High Contrast Black & White)
        // This removes the colored background pattern which confuses OCR.
        val processedBitmap = toGrayscaleHighContrast(bitmap)

        val textResult = runTextRecognition(processedBitmap)
        parseTextToRows(textResult, university)
    }

    private fun toGrayscaleHighContrast(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val paint = Paint()

        // Saturation 0 (Grayscale) + High Contrast
        val colorMatrix = ColorMatrix().apply {
            setSaturation(0f)
            // Increase contrast matrix
            val scale = 1.5f // Contrast scale
            val translate = (-.5f * scale + .5f) * 255f
            val array = floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(array))
        }

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dest
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

        val creditCandidates = mutableListOf<Text.Element>()
        val gradeCandidates = mutableListOf<Text.Element>()

        // --- 1. LOOSE CLASSIFICATION ---
        for (element in allElements) {
            // Cleanup text
            val raw = element.text.trim().uppercase()
                .replace(".", "")  // Remove dots (OCR noise)
                .replace(",", "")
                .replace("O", "0")
                .replace("I", "1")
                .replace("L", "1")
                .replace("|", "1")
                .replace("S", "5")
                .replace("B", "8") // Sometimes B looks like 8, but for credit column we want numbers

            // Is it a valid Credit? (0-10)
            if (raw.matches(Regex("^\\d{1,2}$"))) {
                val num = raw.toIntOrNull()
                if (num != null && num <= 12) { // Typically per-subject credits are small
                    creditCandidates.add(element)
                    continue
                }
            }

            // Is it a valid Grade?
            // For grades, we use the original text but with some cleaning
            val gradeRaw = element.text.trim().uppercase()
            if (findBestGradeMatch(gradeRaw, university.grades) != null) {
                gradeCandidates.add(element)
            }
        }

        // Sort both lists Top-to-Bottom
        creditCandidates.sortBy { it.boundingBox?.top ?: 0 }
        gradeCandidates.sortBy { it.boundingBox?.top ?: 0 }

        val results = mutableListOf<ScannedRow>()

        // --- 2. STRATEGY A: COLUMN ZIPPING (High Confidence) ---
        // If we found exactly the same number of credits and grades (and > 2 rows),
        // we assume they correspond perfectly in order. This ignores horizontal alignment issues.
        if (creditCandidates.size == gradeCandidates.size && creditCandidates.size >= 2) {
            for (i in creditCandidates.indices) {
                val creditVal = cleanCredit(creditCandidates[i].text)
                val gradeVal = findBestGradeMatch(gradeCandidates[i].text.trim().uppercase(), university.grades)

                if (creditVal != null) {
                    results.add(ScannedRow(detectedCredits = creditVal, detectedGrade = gradeVal, confidence = 1.0f))
                }
            }
            return results // Return early if Zipper worked
        }

        // --- 3. STRATEGY B: NEAREST NEIGHBOR (Fallback) ---
        // If counts don't match (OCR missed a value), we use spatial logic.

        val usedGradeIndices = mutableSetOf<Int>()

        for (creditElement in creditCandidates) {
            val creditBox = creditElement.boundingBox ?: continue
            val creditVal = cleanCredit(creditElement.text) ?: continue

            var bestGradeIndex = -1
            var minDistance = Int.MAX_VALUE

            for (i in gradeCandidates.indices) {
                if (usedGradeIndices.contains(i)) continue

                val gradeElement = gradeCandidates[i]
                val gradeBox = gradeElement.boundingBox ?: continue

                // Filter 1: Grade must be to the RIGHT of Credit
                if (gradeBox.centerX() > creditBox.centerX()) {

                    // Filter 2: Vertical alignment (loose tolerance)
                    val verticalDiff = abs(creditBox.centerY() - gradeBox.centerY())
                    // Allow half-inch vertical drift (~60px or 2x height)
                    val tolerance = (creditBox.height() + gradeBox.height())

                    if (verticalDiff < tolerance) {
                        // Filter 3: Horizontal Proximity
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
                val gradeVal = findBestGradeMatch(gradeCandidates[bestGradeIndex].text.trim().uppercase(), university.grades)
                results.add(ScannedRow(detectedCredits = creditVal, detectedGrade = gradeVal, confidence = 0.8f))
            }
        }

        return results
    }

    private fun cleanCredit(text: String): Int? {
        return text.trim().uppercase()
            .replace(".", "")
            .replace(",", "")
            .replace("O", "0")
            .replace("I", "1")
            .replace("L", "1")
            .replace("|", "1")
            .replace("S", "5")
            .toIntOrNull()
    }

    private fun findBestGradeMatch(text: String, grades: List<Grade>): String? {
        // Exact match
        grades.find { it.symbol.equals(text, ignoreCase = true) }?.let { return it.symbol }

        // Fix common OCR errors for letters
        var fixedText = text.replace("0", "O").replace("1", "I").replace("|", "I").replace("8", "B")
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