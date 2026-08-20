package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.QuizQuestionEntity
import com.example.engine.quiz.ExamResultSummary
import com.example.engine.quiz.QuizConfig
import com.example.ui.theme.AccentGold
import com.example.ui.theme.DarkCard
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import org.json.JSONArray

@Composable
fun QuizScreen(
    viewModel: MainViewModel
) {
    val activeQuiz by viewModel.activeQuiz.collectAsStateWithLifecycle()
    val questions by viewModel.quizQuestions.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val isLoading by viewModel.isQuizLoading.collectAsStateWithLifecycle()
    val summaryReport by viewModel.examSummaryReport.collectAsStateWithLifecycle()
    val selectedChoice by viewModel.selectedChoiceState.collectAsStateWithLifecycle()
    val isFeedbackShowing by viewModel.isAnswerFeedbackShowing.collectAsStateWithLifecycle()
    val detectedUnits by viewModel.detectedUnitsAndLessons.collectAsStateWithLifecycle()
    val availableSubjects by viewModel.availableSubjects.collectAsStateWithLifecycle()
    val quizError by viewModel.quizErrorMessage.collectAsStateWithLifecycle()

    if (quizError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearQuizError() },
            icon = { Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = ErrorRed) },
            title = { Text("تنبيه توليد الامتحان") },
            text = { Text(quizError ?: "") },
            confirmButton = {
                Button(onClick = { viewModel.clearQuizError() }) {
                    Text("حسناً")
                }
            }
        )
    }

    when {
        isLoading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "جاري قراءة ملفات الـ PDF وتوليد أسئلة الامتحان محلياً...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        summaryReport != null -> {
            ExamResultSummaryView(
                summary = summaryReport!!,
                onRetestMistakes = {
                    viewModel.startMistakesRetest()
                },
                onNewExam = {
                    viewModel.exitQuiz()
                }
            )
        }
        activeQuiz != null && questions.isNotEmpty() -> {
            val safeQuestion = questions.getOrElse(currentIndex) { questions.first() }
            ActiveExamView(
                totalQuestions = questions.size,
                currentIndex = currentIndex.coerceIn(0, questions.size - 1),
                currentQuestion = safeQuestion,
                durationMinutes = activeQuiz?.scopeDetail?.substringAfterLast("-")?.substringBefore("دقيقة")?.trim()?.toIntOrNull() ?: 15,
                selectedChoice = selectedChoice,
                isFeedbackShowing = isFeedbackShowing,
                onSelectAnswer = { choice, elapsedSeconds ->
                    viewModel.selectAnswerAndAdvance(currentIndex, choice, elapsedSeconds)
                },
                onFinishExam = { elapsedSeconds ->
                    viewModel.finishAndGradeExam(elapsedSeconds)
                },
                onExit = {
                    viewModel.exitQuiz()
                }
            )
        }
        else -> {
            ExamConfigurationView(
                availableSubjects = availableSubjects,
                detectedUnits = detectedUnits,
                onSubjectSelected = { sub ->
                    viewModel.loadUnitsForSubject(sub)
                },
                onStartExam = { config ->
                    viewModel.startConfiguredQuiz(config)
                },
                onNavigateToSettings = {
                    viewModel.navigateTo(AppScreen.SETTINGS)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExamConfigurationView(
    availableSubjects: List<String>,
    detectedUnits: Map<String, List<String>>,
    onSubjectSelected: (String) -> Unit,
    onStartExam: (QuizConfig) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    var selectedSubject by remember { mutableStateOf(availableSubjects.firstOrNull().orEmpty()) }
    var selectedUnit by remember { mutableStateOf<String?>("المنهج كامل") }
    var selectedLesson by remember { mutableStateOf<String?>("الوحدة كاملة") }
    var questionCount by remember { mutableIntStateOf(10) }
    var customQuestionCountText by remember { mutableStateOf("") }
    var isCustomCount by remember { mutableStateOf(false) }

    var durationMinutes by remember { mutableIntStateOf(15) }
    var customDurationText by remember { mutableStateOf("") }
    var isCustomDuration by remember { mutableStateOf(false) }

    LaunchedEffect(availableSubjects) {
        if (availableSubjects.isNotEmpty() && !availableSubjects.contains(selectedSubject)) {
            selectedSubject = availableSubjects.first()
        }
    }

    LaunchedEffect(selectedSubject) {
        if (selectedSubject.isNotBlank()) {
            onSubjectSelected(selectedSubject)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "📝 اختبرني (امتحانات تفاعلية)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "نظام الاختيار من متعدد (4 خيارات) وفق مواصفات الثانوية العامة",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (availableSubjects.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️ لم يتم إضافة أي كتب أو مناهج حتى الآن",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "يرجى رفع ملف الـ PDF الخاص بالمنهج من شاشة الإعدادات حتى يتمكن التطبيق من قراءة المحتوى وتوليد الامتحانات التفاعلية الحصرية.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("الذهاب لشاشة الإعدادات ورفع PDF")
                        }
                    }
                }
            }
        } else {
            // 1. Select Subject
            item {
                Column {
                    Text(
                        text = "١. المادة المراد الاختبار بها:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableSubjects.forEach { sub ->
                            val isSelected = selectedSubject == sub
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) BorderStroke(2.dp, PrimaryBlue) else null,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedSubject = sub
                                        selectedUnit = "المنهج كامل"
                                        selectedLesson = "الوحدة كاملة"
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(vertical = 12.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "📖",
                                        fontSize = 20.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Select Scope
            item {
                Column {
                    Text(
                        text = "٢. اختر نطاق الاختبار (مستخرج ديناميكياً من ملفات المادة):",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val isAllSelected = selectedUnit == "المنهج كامل"
                        FilterChip(
                            selected = isAllSelected,
                            onClick = {
                                selectedUnit = "المنهج كامل"
                                selectedLesson = "الوحدة كاملة"
                            },
                            label = { Text("○ المنهج كامل") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        detectedUnits.keys.forEach { unitName ->
                            val isUnitSelected = selectedUnit == unitName
                            FilterChip(
                                selected = isUnitSelected,
                                onClick = {
                                    selectedUnit = unitName
                                    selectedLesson = "الوحدة كاملة"
                                },
                                label = { Text(unitName.take(28)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    if (selectedUnit != null && selectedUnit != "المنهج كامل") {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "الدروس المتاحة في ($selectedUnit):",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val lessons = detectedUnits[selectedUnit] ?: listOf("الدرس الأول", "الدرس الثاني")
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isFullUnit = selectedLesson == "الوحدة كاملة"
                            FilterChip(
                                selected = isFullUnit,
                                onClick = { selectedLesson = "الوحدة كاملة" },
                                label = { Text("○ الوحدة كاملة") }
                            )

                            lessons.forEach { lName ->
                                val isSelected = selectedLesson == lName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedLesson = lName },
                                    label = { Text(lName) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Question Count
            item {
                Column {
                    Text(
                        text = "٣. عدد الأسئلة المطلوبة:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 20, 30).forEach { cnt ->
                            val isSelected = !isCustomCount && questionCount == cnt
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    isCustomCount = false
                                    questionCount = cnt
                                },
                                label = { Text("$cnt") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        FilterChip(
                            selected = isCustomCount,
                            onClick = { isCustomCount = true },
                            label = { Text("مخصص") }
                        )
                    }

                    if (isCustomCount) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customQuestionCountText,
                            onValueChange = {
                                customQuestionCountText = it
                                val parsed = it.toIntOrNull()
                                if (parsed != null && parsed in 1..50) {
                                    questionCount = parsed
                                }
                            },
                            placeholder = { Text("أدخل عدد الأسئلة (مثلاً 25)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // 4. Exam Duration
            item {
                Column {
                    Text(
                        text = "٤. مدة الاختبار:",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 10, 15, 20, 30, 45, 60).forEach { dur ->
                            val isSelected = !isCustomDuration && durationMinutes == dur
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    isCustomDuration = false
                                    durationMinutes = dur
                                },
                                label = { Text("$dur دقائق") }
                            )
                        }

                        FilterChip(
                            selected = isCustomDuration,
                            onClick = { isCustomDuration = true },
                            label = { Text("مخصص") }
                        )
                    }

                    if (isCustomDuration) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customDurationText,
                            onValueChange = {
                                customDurationText = it
                                val parsed = it.toIntOrNull()
                                if (parsed != null && parsed in 1..120) {
                                    durationMinutes = parsed
                                }
                            },
                            placeholder = { Text("أدخل المدة بالدقائق (مثلاً 40)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            // Launch Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val finalScope = if (selectedUnit == "المنهج كامل") "FULL_CURRICULUM" else "UNIT"
                        val detail = if (selectedUnit == "المنهج كامل") "المنهج كامل" else "$selectedUnit - $selectedLesson"
                        onStartExam(
                            QuizConfig(
                                subject = selectedSubject,
                                scopeType = finalScope,
                                scopeDetail = detail,
                                unitName = selectedUnit ?: "الوحدة الأولى",
                                lessonName = selectedLesson ?: "الدرس الأول",
                                questionCount = questionCount,
                                durationMinutes = durationMinutes,
                                questionFormat = "MCQ_ONLY"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_exam_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚀 ابدأ الامتحان الآن ($questionCount أسئلة • $durationMinutes دقيقة)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveExamView(
    totalQuestions: Int,
    currentIndex: Int,
    currentQuestion: QuizQuestionEntity,
    durationMinutes: Int,
    selectedChoice: String?,
    isFeedbackShowing: Boolean,
    onSelectAnswer: (String, Int) -> Unit,
    onFinishExam: (Int) -> Unit,
    onExit: () -> Unit
) {
    var totalSecondsRemaining by remember { mutableIntStateOf(durationMinutes * 60) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (totalSecondsRemaining > 0) {
            delay(1000)
            totalSecondsRemaining -= 1
            elapsedSeconds += 1
        }
        onFinishExam(elapsedSeconds)
    }

    val minutes = totalSecondsRemaining / 60
    val seconds = totalSecondsRemaining % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    val optionsList: List<String> = remember(currentQuestion.optionsJson) {
        try {
            val arr = JSONArray(currentQuestion.optionsJson)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list
        } catch (e: Exception) {
            listOf(currentQuestion.correctAnswer, "خيار أ", "خيار ب", "خيار ج")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar: Timer, Exit, Progress
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onExit, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "خروج من الامتحان")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (totalSecondsRemaining < 120) ErrorRed.copy(alpha = 0.15f) else PrimaryBlue.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (totalSecondsRemaining < 120) ErrorRed else PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (totalSecondsRemaining < 120) ErrorRed else PrimaryBlue
                        )
                    }

                    Text(
                        text = "السؤال ${currentIndex + 1} من $totalQuestions",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / totalQuestions.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PrimaryBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }

        // Main Question & Options Area
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Source badge
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "📚 ${currentQuestion.sourceDocTitle} • ص ${currentQuestion.pageNumber}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Question Stem
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentQuestion.questionText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // 4 Options (A, B, C, D)
            val optionLabels = listOf("أ", "ب", "ج", "د")
            items(optionsList.take(4).mapIndexed { i, opt -> Pair(optionLabels.getOrElse(i) { "$i" }, opt) }) { (label, optionText) ->
                val isSelected = selectedChoice == optionText
                val isCorrectAnswer = optionText == currentQuestion.correctAnswer

                val containerColor by animateColorAsState(
                    targetValue = when {
                        isFeedbackShowing && isSelected && isCorrectAnswer -> SuccessGreen.copy(alpha = 0.2f)
                        isFeedbackShowing && isSelected && !isCorrectAnswer -> ErrorRed.copy(alpha = 0.2f)
                        isFeedbackShowing && isCorrectAnswer -> SuccessGreen.copy(alpha = 0.15f)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                    label = "OptionBg"
                )

                val borderColor by animateColorAsState(
                    targetValue = when {
                        isFeedbackShowing && isSelected && isCorrectAnswer -> SuccessGreen
                        isFeedbackShowing && isSelected && !isCorrectAnswer -> ErrorRed
                        isFeedbackShowing && isCorrectAnswer -> SuccessGreen
                        isSelected -> PrimaryBlue
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    },
                    label = "OptionBorder"
                )

                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, borderColor),
                    colors = CardDefaults.cardColors(containerColor = containerColor),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isFeedbackShowing) {
                            onSelectAnswer(optionText, elapsedSeconds)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isFeedbackShowing && isSelected && isCorrectAnswer -> SuccessGreen
                                        isFeedbackShowing && isSelected && !isCorrectAnswer -> ErrorRed
                                        isSelected -> PrimaryBlue
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected || isFeedbackShowing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = optionText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إنهاء والانسحاب")
                }

                Button(
                    onClick = { onFinishExam(elapsedSeconds) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("تسليم الامتحان الآن")
                }
            }
        }
    }
}

@Composable
fun ExamResultSummaryView(
    summary: ExamResultSummary,
    onRetestMistakes: () -> Unit,
    onNewExam: () -> Unit
) {
    val passed = summary.percentage >= 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Score Header Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (passed) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(CircleShape)
                            .background(if (passed) SuccessGreen else ErrorRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${summary.percentage}%",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (passed) "🎉 أحسنت صنعاً يا بطل!" else "💪 لا بأس، فرصة لمراجعة أخطائك والتحسن!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "حصلت على ${summary.score} من إجمالي ${summary.totalQuestions} سؤال • الوقت المستغرق: ${summary.timeSpentSeconds / 60} دقيقة",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Detailed Stats
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "الإجابات الصحيحة",
                    value = "${summary.correctCount}",
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "الإجابات الخاطئة",
                    value = "${summary.wrongCount}",
                    color = ErrorRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.wrongCount > 0) {
                    Button(
                        onClick = onRetestMistakes,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🎯 اختبرني في الأخطاء فقط (${summary.wrongCount} أسئلة)",
                            color = Color.Black,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Button(
                    onClick = onNewExam,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("بدء اختبار جديد", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
