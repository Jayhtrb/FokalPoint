package com.example.ui.utils

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class InvoiceGenerator(private val context: Context) {
    
    data class InvoiceData(
        val invoiceNumber: String,
        val bookingId: Long,
        val creatorName: String,
        val creatorEmail: String,
        val creatorPhone: String,
        val customerName: String,
        val customerEmail: String,
        val customerPhone: String,
        val eventType: String,
        val date: String,
        val time: String,
        val duration: Int,
        val packageName: String,
        val amount: Double,
        val tax: Double,
        val totalAmount: Double,
        val paymentMethod: String,
        val transactionId: String,
        val bookingDate: String
    )
    
    fun generateInvoice(data: InvoiceData): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            val paint = android.graphics.Paint()
            
            // Header
            paint.textSize = 24f
            paint.color = ContextCompat.getColor(context, android.R.color.black)
            canvas.drawText("FOKALPOINT", 50f, 100f, paint)
            
            paint.textSize = 18f
            canvas.drawText("INVOICE", 50f, 140f, paint)
            
            // Invoice Details
            paint.textSize = 12f
            canvas.drawText("Invoice #: ${data.invoiceNumber}", 50f, 180f, paint)
            canvas.drawText("Date: ${data.bookingDate}", 50f, 200f, paint)
            canvas.drawText("Booking ID: #${data.bookingId}", 50f, 220f, paint)
            
            // Creator Details
            paint.textSize = 14f
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("CREATOR", 50f, 260f, paint)
            
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 12f
            canvas.drawText(data.creatorName, 50f, 280f, paint)
            canvas.drawText(data.creatorEmail, 50f, 300f, paint)
            canvas.drawText(data.creatorPhone, 50f, 320f, paint)
            
            // Customer Details
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 14f
            canvas.drawText("CUSTOMER", 350f, 260f, paint)
            
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 12f
            canvas.drawText(data.customerName, 350f, 280f, paint)
            canvas.drawText(data.customerEmail, 350f, 300f, paint)
            canvas.drawText(data.customerPhone, 350f, 320f, paint)
            
            // Service Details
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 14f
            canvas.drawText("SERVICE DETAILS", 50f, 370f, paint)
            
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 12f
            canvas.drawText("Event: ${data.eventType}", 50f, 395f, paint)
            canvas.drawText("Date: ${data.date}", 50f, 415f, paint)
            canvas.drawText("Time: ${data.time}", 50f, 435f, paint)
            canvas.drawText("Duration: ${data.duration} hours", 50f, 455f, paint)
            canvas.drawText("Package: ${data.packageName}", 50f, 475f, paint)
            
            // Payment Details
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 14f
            canvas.drawText("PAYMENT SUMMARY", 50f, 520f, paint)
            
            // Draw line
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 12f
            canvas.drawText("Service Fee", 50f, 550f, paint)
            canvas.drawText("₹${String.format("%.2f", data.amount)}", 450f, 550f, paint)
            
            canvas.drawText("Tax (GST 18%)", 50f, 570f, paint)
            canvas.drawText("₹${String.format("%.2f", data.tax)}", 450f, 570f, paint)
            
            // Total
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.textSize = 16f
            canvas.drawText("TOTAL", 50f, 610f, paint)
            canvas.drawText("₹${String.format("%.2f", data.totalAmount)}", 420f, 610f, paint)
            
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = 10f
            canvas.drawText("Payment Method: ${data.paymentMethod}", 50f, 640f, paint)
            canvas.drawText("Transaction ID: ${data.transactionId}", 50f, 660f, paint)
            
            // Footer
            paint.textSize = 10f
            paint.color = ContextCompat.getColor(context, android.R.color.darker_gray)
            canvas.drawText("Thank you for choosing FokalPoint!", 50f, 800f, paint)
            canvas.drawText("Every Moment in Focus.", 50f, 820f, paint)
            
            pdfDocument.finishPage(page)
            
            // Save PDF
            val fileName = "Invoice_${data.invoiceNumber}.pdf"
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.close()
            
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}

// Invoice Preview Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePreview(
    invoiceData: InvoiceGenerator.InvoiceData,
    onDownload: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Invoice #${invoiceData.invoiceNumber}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = invoiceData.bookingDate,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Text("PAID")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Event", style = MaterialTheme.typography.bodySmall)
                    Text(invoiceData.eventType, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Amount", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "₹${String.format("%.2f", invoiceData.totalAmount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download")
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}
