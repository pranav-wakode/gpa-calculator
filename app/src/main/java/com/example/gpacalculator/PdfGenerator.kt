package com.example.gpacalculator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream

data class StudentDetails(
    val universityName: String,
    val studentName: String,
    val className: String,
    val branch: String,
    val prn: String,
    val semester: String
)

object PdfGenerator {

    fun generateMarksheetPdf(
        context: Context,
        outputStream: OutputStream,
        details: StudentDetails,
        subjects: List<Subject>,
        gpa: Double,
        classification: String
    ) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size (approx 72 dpi)
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint()

        // --- STYLES ---
        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 14f
            textAlign = Paint.Align.CENTER
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
        }
        val boldTextPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // --- DRAWING ---
        val pageWidth = pageInfo.pageWidth
        val margin = 40f
        var yPos = 60f

        // 1. Border
        canvas.drawRect(margin / 2, margin / 2, pageWidth - margin / 2, pageInfo.pageHeight - margin / 2, borderPaint)

        // 2. Header
        canvas.drawText("SEMESTER GRADE REPORT", pageWidth / 2f, yPos, titlePaint)
        yPos += 30f
        canvas.drawText(details.universityName.uppercase(), pageWidth / 2f, yPos, subtitlePaint)
        yPos += 40f
        
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, linePaint)
        yPos += 20f

        // 3. Student Details (2 Columns)
        val col1X = margin + 10f
        val col2X = pageWidth / 2f + 10f
        
        canvas.drawText("Name:", col1X, yPos, boldTextPaint)
        canvas.drawText(details.studentName, col1X + 50f, yPos, textPaint)
        
        canvas.drawText("PRN:", col2X, yPos, boldTextPaint)
        canvas.drawText(details.prn, col2X + 50f, yPos, textPaint)
        yPos += 20f

        canvas.drawText("Class:", col1X, yPos, boldTextPaint)
        canvas.drawText(details.className, col1X + 50f, yPos, textPaint)

        canvas.drawText("Branch:", col2X, yPos, boldTextPaint)
        canvas.drawText(details.branch, col2X + 50f, yPos, textPaint)
        yPos += 20f

        canvas.drawText("Semester:", col1X, yPos, boldTextPaint)
        canvas.drawText(details.semester, col1X + 70f, yPos, textPaint)
        yPos += 30f

        // 4. Marks Table Header
        val tableStart = yPos
        val colSub = margin + 10f
        val colCred = pageWidth - 150f
        val colGrade = pageWidth - 80f
        val colPoint = pageWidth - 40f // Optional

        // Draw Header Box
        paint.color = Color.LTGRAY
        canvas.drawRect(margin, yPos - 15f, pageWidth - margin, yPos + 10f, paint)
        
        canvas.drawText("SUBJECT NAME", colSub, yPos, boldTextPaint)
        canvas.drawText("CREDITS", colCred, yPos, boldTextPaint)
        canvas.drawText("GRADE", colGrade, yPos, boldTextPaint)
        yPos += 25f

        // 5. Marks Rows
        subjects.forEach { sub ->
            val name = if (sub.name.isNotBlank()) sub.name else "Subject"
            canvas.drawText(name, colSub, yPos, textPaint)
            canvas.drawText(sub.credits.toString(), colCred + 15f, yPos, textPaint)
            canvas.drawText(sub.selectedGrade?.symbol ?: "-", colGrade + 10f, yPos, textPaint)
            
            canvas.drawLine(margin, yPos + 5f, pageWidth - margin, yPos + 5f, linePaint)
            yPos += 25f
        }

        yPos += 20f

        // 6. Footer (Result)
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, borderPaint) // Top line
        yPos += 30f
        
        val resultX = pageWidth - margin - 200f
        canvas.drawText("GPA:", resultX, yPos, boldTextPaint)
        canvas.drawText(String.format("%.2f", gpa), resultX + 100f, yPos, titlePaint)
        yPos += 25f
        
        canvas.drawText("Class:", resultX, yPos, boldTextPaint)
        canvas.drawText(classification, resultX + 100f, yPos, textPaint)
        
        canvas.drawLine(margin, yPos + 20f, pageWidth - margin, yPos + 20f, borderPaint) // Bottom line

        // 7. Disclaimer / Footer Text
        val footerY = pageInfo.pageHeight - margin - 20f
        val footerPaint = Paint().apply { color = Color.GRAY; textSize = 10f; textAlign = Paint.Align.CENTER }
        canvas.drawText("Generated by Multi-University GPA Calculator App", pageWidth / 2f, footerY, footerPaint)

        pdfDocument.finishPage(page)
        
        try {
            pdfDocument.writeTo(outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            pdfDocument.close()
        }
    }
}