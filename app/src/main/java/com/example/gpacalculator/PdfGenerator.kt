package com.example.gpacalculator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
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
        percentage: Double,
        classification: String
    ) {
        val pdfDocument = PdfDocument()
        
        // A4 Size
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        
        // Paints
        val titlePaint = TextPaint().apply { color = Color.BLACK; textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER }
        val subtitlePaint = TextPaint().apply { color = Color.DKGRAY; textSize = 14f; textAlign = Paint.Align.CENTER }
        val textPaint = TextPaint().apply { color = Color.BLACK; textSize = 12f }
        val boldTextPaint = TextPaint().apply { color = Color.BLACK; textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }
        val borderPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }

        // --- PAGINATION LOGIC ---
        var pageNumber = 1
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var yPos = 60f // Start Cursor

        fun checkNewPage(requiredHeight: Float) {
            if (yPos + requiredHeight > pageHeight - margin - 50f) { // Leave space for footer
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, ++pageNumber).create())
                canvas = page.canvas
                yPos = 60f
                // Re-draw border on new page
                canvas.drawRect(margin/2, margin/2, pageWidth - margin/2, pageHeight - margin/2, borderPaint)
            }
        }

        // 1. Initial Border
        canvas.drawRect(margin/2, margin/2, pageWidth - margin/2, pageHeight - margin/2, borderPaint)

        // 2. Header (University Name with Wrapping)
        canvas.drawText("SEMESTER GRADE REPORT", pageWidth / 2f, yPos, titlePaint)
        yPos += 30f
        
        // Wrap University Name
        val uniLayout = StaticLayout.Builder.obtain(details.universityName.uppercase(), 0, details.universityName.length, subtitlePaint, (pageWidth - 2 * margin).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        
        canvas.save()
        canvas.translate(margin, yPos) // Center alignment handled by layout, but we position the block
        // Actually StaticLayout draws at 0,0 relative to translate. Since alignment is center, we translate to start margin?
        // No, Alignment.ALIGN_CENTER in StaticLayout aligns relative to the width provided. 
        // So we translate to left margin.
        // Let's re-adjust translate.
        // Actually for Center alignment in StaticLayout to look right on page, we translate to margin.
        // and set width to pageWidth - 2*margin.
        uniLayout.draw(canvas)
        canvas.restore()
        yPos += uniLayout.height + 20f
        
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, linePaint)
        yPos += 20f

        // 3. Student Details
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
        
        // Wrap Branch Name if too long
        if (textPaint.measureText(details.branch) > (pageWidth/2 - 80)) {
             // Simple manual wrap for branch
             val words = details.branch.split(" ")
             var line = ""
             var lineY = yPos
             words.forEach { word ->
                 if (textPaint.measureText(line + word) > (pageWidth/2 - 80)) {
                     canvas.drawText(line, col2X + 50f, lineY, textPaint)
                     line = "$word "
                     lineY += 15f
                 } else {
                     line += "$word "
                 }
             }
             canvas.drawText(line, col2X + 50f, lineY, textPaint)
             // Assuming branch won't take more than 2 lines for now to keep yPos sync simple
        } else {
            canvas.drawText(details.branch, col2X + 50f, yPos, textPaint)
        }
        yPos += 20f

        canvas.drawText("Semester:", col1X, yPos, boldTextPaint)
        canvas.drawText(details.semester, col1X + 70f, yPos, textPaint)
        yPos += 30f

        // 4. Marks Table Header
        val colSub = margin + 10f
        val colCred = pageWidth - 150f
        val colGrade = pageWidth - 80f

        // Helper to draw table header (used on new pages too if needed, but simpler to just list rows)
        val paint = Paint().apply { color = Color.LTGRAY }
        canvas.drawRect(margin, yPos - 15f, pageWidth - margin, yPos + 10f, paint)
        
        canvas.drawText("SUBJECT NAME", colSub, yPos, boldTextPaint)
        canvas.drawText("CREDITS", colCred, yPos, boldTextPaint)
        canvas.drawText("GRADE", colGrade, yPos, boldTextPaint)
        yPos += 25f

        // 5. Marks Rows with Pagination
        subjects.forEach { sub ->
            checkNewPage(30f) // Check if we have space for this row
            
            val name = if (sub.name.isNotBlank()) sub.name else "Subject"
            
            // Truncate subject name if TOO long to avoid overlap
            val maxSubWidth = colCred - colSub - 20f
            var displayName = name
            if (textPaint.measureText(displayName) > maxSubWidth) {
                // Basic truncation
                val chars = textPaint.breakText(displayName, true, maxSubWidth, null)
                displayName = displayName.substring(0, chars) + "..."
            }

            canvas.drawText(displayName, colSub, yPos, textPaint)
            canvas.drawText(sub.credits.toString(), colCred + 15f, yPos, textPaint)
            canvas.drawText(sub.selectedGrade?.symbol ?: "-", colGrade + 10f, yPos, textPaint)
            
            canvas.drawLine(margin, yPos + 5f, pageWidth - margin, yPos + 5f, linePaint)
            yPos += 25f
        }

        yPos += 20f
        checkNewPage(100f) // Check if we have space for footer

        // 6. Footer (Result)
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, borderPaint) // Top line
        yPos += 30f
        
        val resultX = pageWidth - margin - 200f
        canvas.drawText("GPA:", resultX, yPos, boldTextPaint)
        canvas.drawText(String.format("%.2f", gpa), resultX + 100f, yPos, titlePaint)
        yPos += 25f
        
        canvas.drawText("Percentage:", resultX, yPos, boldTextPaint)
        canvas.drawText("${String.format("%.2f", percentage)}%", resultX + 100f, yPos, textPaint)
        yPos += 20f

        canvas.drawText("Class:", resultX, yPos, boldTextPaint)
        // Align class name if long
        if (textPaint.measureText(classification) > 100) {
             canvas.drawText(classification, resultX + 80f, yPos + 15f, textPaint) // Bump down
        } else {
             canvas.drawText(classification, resultX + 100f, yPos, textPaint)
        }
        
        canvas.drawLine(margin, yPos + 20f, pageWidth - margin, yPos + 20f, borderPaint) // Bottom line

        // 7. Footer Disclaimer
        val footerY = pageHeight - margin - 20f
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