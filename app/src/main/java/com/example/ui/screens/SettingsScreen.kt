package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DocumentEntity
import com.example.data.local.ModelFileEntity
import com.example.ui.theme.AccentGold
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: PDF Files, 1: AI Models

    val allDocs by viewModel.allDocuments.collectAsStateWithLifecycle()
    val models by viewModel.modelFiles.collectAsStateWithLifecycle()
    val testStatus by viewModel.modelTestStatus.collectAsStateWithLifecycle()

    var pendingImportSubject by remember { mutableStateOf("") }
    var pendingImportDocType by remember { mutableStateOf("EXPLANATION") }
    var pendingModelType by remember { mutableStateOf("AI_GGUF") }

    // PDF Picker
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "كتاب_المنهج.pdf"
            viewModel.importPdfForSlot(uri, fileName, pendingImportSubject, pendingImportDocType) { success, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // Model Picker
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = when (pendingModelType) {
                "AI_GGUF" -> "qwen2.5-0.5b-instruct-q4_k_m.gguf"
                "WHISPER_BIN" -> "ggml-tiny.bin"
                "TESSERACT_DATA" -> "ara.traineddata"
                else -> "model.bin"
            }
            viewModel.importModelFile(uri, fileName, pendingModelType) { _, msg ->
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Tab Header
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📚 اختيار ملفات PDF (${allDocs.size}/10)", fontWeight = FontWeight.Bold)
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("🤖 إدارة النماذج", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        if (selectedTab == 0) {
            // PDF Subject Slots Configuration
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "💡", fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "نظام الـ PDF المزدوج لكل مادة",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "خصص لكل مادة: 📖 كتاب الشرح (للإجابة عن الأسئلة) + 📝 كتاب الأسئلة (لتوليد الامتحانات).",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = pendingImportSubject,
                        onValueChange = { pendingImportSubject = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("اسم المادة") },
                        placeholder = { Text("اكتب اسم المادة كما في ملفك") },
                        singleLine = true
                    )
                }
                val subjects = allDocs.map { it.subject }.distinct().sorted()
                items(subjects) { subject ->
                    SubjectPdfSlotsCard(
                        subject = subject,
                        explanationDoc = allDocs.find { it.subject == subject && it.docType == "EXPLANATION" },
                        questionsDoc = allDocs.find { it.subject == subject && it.docType == "QUESTIONS" },
                        onUploadSlot = { docType ->
                            pendingImportSubject = subject
                            pendingImportDocType = docType
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        onReindex = { docId ->
                            viewModel.reindexDocument(docId) { _, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = { docId ->
                            viewModel.deleteDocument(docId)
                        }
                    )
                }
                if (subjects.isEmpty()) {
                    item {
                        Text(
                            "لا توجد مناهج بعد. اكتب اسم المادة أعلاه ثم ارفع ملف PDF.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // AI Models Management
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "محركات الذكاء الاصطناعي الأوفلاين",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "المحركات المحلية المتاحة على هذا الجهاز:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "• Qwen GGUF -> llama.cpp (الاستنتاج والتوليد الصارم)", style = MaterialTheme.typography.labelSmall)
                            Text(text = "• Whisper BIN -> whisper.cpp (التعرف الصوتي العربي)", style = MaterialTheme.typography.labelSmall)
                            Text(text = "• Tesseract -> ara.traineddata (استخراج نصوص الكتب العربية)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                items(models) { model ->
                    ModelItemCard(
                        model = model,
                        onTest = { viewModel.testModel(model) },
                        onDelete = { viewModel.deleteModelFile(model) }
                    )
                }

                item {
                    ImportCustomModelsCard(
                        onImportGguf = {
                            pendingModelType = "AI_GGUF"
                            modelPickerLauncher.launch("*/*")
                        },
                        onImportWhisper = {
                            pendingModelType = "WHISPER_BIN"
                            modelPickerLauncher.launch("*/*")
                        },
                        onImportTesseract = {
                            pendingModelType = "TESSERACT_DATA"
                            modelPickerLauncher.launch("*/*")
                        }
                    )
                }

                if (testStatus != null) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "تقرير الفحص التشخيصي للنموذج:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = testStatus ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubjectPdfSlotsCard(
    subject: String,
    explanationDoc: DocumentEntity?,
    questionsDoc: DocumentEntity?,
    onUploadSlot: (String) -> Unit,
    onReindex: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Subject Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (subject) {
                        "التاريخ" -> "📜"
                        "الجغرافيا" -> "🌍"
                        else -> "🇪🇬"
                    },
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مادة $subject",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Slot 1: 📖 PDF الشرح
            PdfSlotRow(
                slotLabel = "📖 ملف الشرح المعتمد",
                doc = explanationDoc,
                onAddOrReplace = { onUploadSlot("EXPLANATION") },
                onReindex = { if (explanationDoc != null) onReindex(explanationDoc.id) },
                onDelete = { if (explanationDoc != null) onDelete(explanationDoc.id) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Slot 2: 📝 PDF الأسئلة
            PdfSlotRow(
                slotLabel = "📝 ملف بنك الأسئلة",
                doc = questionsDoc,
                onAddOrReplace = { onUploadSlot("QUESTIONS") },
                onReindex = { if (questionsDoc != null) onReindex(questionsDoc.id) },
                onDelete = { if (questionsDoc != null) onDelete(questionsDoc.id) }
            )
        }
    }
}

@Composable
fun PdfSlotRow(
    slotLabel: String,
    doc: DocumentEntity?,
    onAddOrReplace: () -> Unit,
    onReindex: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, if (doc != null) PrimaryBlue.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = PrimaryBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (doc != null) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "${doc.pageCount} صفحة • ${(doc.fileSize / (1024 * 1024))} ميجابايت • مفهرس محلياً",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = SuccessGreen
                    )
                } else {
                    Text(
                        text = "لم يتم تعيين ملف لهذا القسم بعد",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doc != null) {
                    IconButton(onClick = onReindex, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "إعادة الفهرسة", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "حذف الملف", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (doc != null) MaterialTheme.colorScheme.surfaceVariant else PrimaryBlue,
                    modifier = Modifier.clickable { onAddOrReplace() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (doc != null) Icons.Default.Refresh else Icons.Default.Add,
                            contentDescription = null,
                            tint = if (doc != null) MaterialTheme.colorScheme.onSurface else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (doc != null) "استبدال" else "إضافة PDF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (doc != null) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelItemCard(
    model: ModelFileEntity,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                when (model.modelType) {
                                    "AI_GGUF" -> PrimaryBlue.copy(alpha = 0.15f)
                                    "WHISPER_BIN" -> AccentGold.copy(alpha = 0.15f)
                                    else -> SuccessGreen.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (model.modelType) {
                                "AI_GGUF" -> Icons.Default.Memory
                                "WHISPER_BIN" -> Icons.Default.Mic
                                else -> Icons.Default.PhotoCamera
                            },
                            contentDescription = null,
                            tint = when (model.modelType) {
                                "AI_GGUF" -> PrimaryBlue
                                "WHISPER_BIN" -> AccentGold
                                else -> SuccessGreen
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = model.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${model.fileName} • ${model.statusDescription}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(onClick = onTest, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Build, contentDescription = "فحص", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "حذف", tint = ErrorRed, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ImportCustomModelsCard(
    onImportGguf: () -> Unit,
    onImportWhisper: () -> Unit,
    onImportTesseract: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "📥 استيراد وتحديث ملفات النماذج يدوياً",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onImportGguf, modifier = Modifier.weight(1f)) {
                    Text("استيراد GGUF", fontSize = 10.sp)
                }
                OutlinedButton(onClick = onImportWhisper, modifier = Modifier.weight(1f)) {
                    Text("Whisper BIN", fontSize = 10.sp)
                }
                OutlinedButton(onClick = onImportTesseract, modifier = Modifier.weight(1f)) {
                    Text("Tesseract Data", fontSize = 10.sp)
                }
            }
        }
    }
}
