package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.KeywordSearchMode
import com.example.model.ScanMode
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.HighConfidenceGreen
import com.example.ui.theme.HighConfidenceRed
import com.example.ui.theme.MediumConfidenceAmber
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.scanSettings.collectAsState()
    val performanceReport by viewModel.performanceReport.collectAsState()

    var showReportDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات والخيارات المتقدمة",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Section 1: Scan Mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "وضع الفحص والسرعة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ScanMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings(settings.copy(scanMode = mode))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.scanMode == mode,
                                onClick = { viewModel.updateSettings(settings.copy(scanMode = mode)) },
                                colors = RadioButtonDefaults.colors(selectedColor = PurpleAccent, unselectedColor = TextGray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = mode.label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextWhite)
                                val desc = when (mode) {
                                    ScanMode.FAST -> "التركيز على ميثودات اتخاذ القرار المباشرة وأعلى مرشحين"
                                    ScanMode.BALANCED -> "تحليل متوازن للكلاسات والدوال المرتبطة (موصى به)"
                                    ScanMode.DEEP -> "تحليل شامل وموسع لجميع الإشارات والنداءات"
                                }
                                Text(text = desc, fontSize = 11.sp, color = TextGray)
                            }
                        }
                    }
                }
            }

            // Section 2: Candidate Budget
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "الحد الأقصى للمرشحين للتحليل العميق", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(100, 250, 500, 1000).forEach { budget ->
                            val isSelected = settings.candidateBudget == budget
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateSettings(settings.copy(candidateBudget = budget)) }
                                    .background(
                                        if (isSelected) PurpleAccent else Color(0xFF1E2132),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$budget",
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextGray
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: Keyword Match Mode
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "وضع مطابقة الكلمات المفتاحية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                    Spacer(modifier = Modifier.height(8.dp))

                    KeywordSearchMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateSettings(settings.copy(searchMode = mode)) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.searchMode == mode,
                                onClick = { viewModel.updateSettings(settings.copy(searchMode = mode)) },
                                colors = RadioButtonDefaults.colors(selectedColor = PurpleAccent, unselectedColor = TextGray)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = mode.label, fontSize = 13.sp, color = TextWhite)
                        }
                    }
                }
            }

            // Section 4: Filters Defaults
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "الفلاتر الافتراضية", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)

                    SettingToggleRow(
                        title = "استبعاد مكاتب الطرف الثالث تلقائيًا",
                        subtitle = "حجب مكتبات الإعلانات، الشبكات ومكتبات الدفع التابعة لجهات خارجية",
                        checked = settings.hideThirdParty,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(hideThirdParty = it)) }
                    )

                    SettingToggleRow(
                        title = "إخفاء الـ Constructors (<init>)",
                        subtitle = "تقليل الإشارات غير المجدية داخل دوال إنشاء الكائنات",
                        checked = settings.hideConstructors,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(hideConstructors = it)) }
                    )

                    SettingToggleRow(
                        title = "إخفاء الـ Initializers (<clinit>)",
                        subtitle = "إخفاء دوال تهيئة المتغيرات الثابتة",
                        checked = settings.hideClinit,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(hideClinit = it)) }
                    )
                }
            }

            // Section 5: Performance & Diagnostics
            if (performanceReport != null) {
                Button(
                    onClick = { showReportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2132)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("view_diagnostics_button")
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("عرض تقرير الأداء والتشخيصات", color = TextWhite, fontSize = 13.sp)
                }
            }

            // Section 6: Privacy Statement
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A24)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF163248))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "تحليل محلي 100% بالكامل على هاتفك دون إرسال أي ملفات أو كود أو بيانات إلى أي خوادم خارجية.",
                        fontSize = 12.sp,
                        color = Color(0xFFB5E3F5)
                    )
                }
            }

            // Section 7: Reset / Maintenance
            OutlinedButton(
                onClick = { showResetConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HighConfidenceRed)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = HighConfidenceRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة ضبط التطبيق ومسح الذاكرة المؤقتة", color = HighConfidenceRed, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Performance Report Dialog
    if (showReportDialog) {
        performanceReport?.let { report ->
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                title = { Text("تقرير أداء الفحص", color = TextWhite, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الوقت الكلي: ${report.totalTimeMs} ms", color = TextWhite)
                        Text("معالجة واستخراج DEX: ${report.dexProcessingTimeMs} ms", color = TextGray)
                        Text("اكتشاف المرشحين (Stage 1): ${report.candidateDiscoveryTimeMs} ms", color = PurpleLight)
                        Text("تحليل الـ Bytecode (Stage 2): ${report.analysisTimeMs} ms", color = PurpleAccent)
                        Text("عدد ملفات DEX: ${report.dexCount}", color = TextWhite)
                        Text("تطابقات الكلمات: ${report.keywordHits}", color = CyanAccent)
                        Text("المرشحين الفريدين: ${report.uniqueCandidates}", color = MediumConfidenceAmber)
                        Text("تم تحليلها بعمق: ${report.deeplyAnalyzed}", color = HighConfidenceGreen)
                        Text("المستبعدة من الطرف الثالث: ${report.thirdPartyFilteredCount}", color = TextGray)
                        Text("الذاكرة التقريبية: ${report.approximateMemoryMb} MB", color = TextWhite)
                    }
                },
                confirmButton = {
                    Button(onClick = { showReportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)) {
                        Text("حسناً")
                    }
                },
                containerColor = SurfaceCard
            )
        }
    }

    // Reset Confirm Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("تأكيد إعادة الضبط", color = TextWhite) },
            text = { Text("هل أنت متأكد من مسح الملف المختار والنتائج السابقة والذاكرة المؤقتة؟", color = TextGray) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAll()
                        showResetConfirmDialog = false
                        Toast.makeText(context, "تمت إعادة ضبط البيانات بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HighConfidenceRed)
                ) {
                    Text("نعم، إعادة ضبط")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("إلغاء", color = TextGray)
                }
            },
            containerColor = SurfaceCard
        )
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, color = TextWhite, fontWeight = FontWeight.Medium)
            Text(text = subtitle, fontSize = 11.sp, color = TextGray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PurpleAccent)
        )
    }
}
