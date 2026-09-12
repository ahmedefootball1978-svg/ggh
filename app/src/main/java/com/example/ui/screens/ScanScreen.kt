package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApkFileInfo
import com.example.model.ScanProgress
import com.example.model.ScanStage
import com.example.ui.components.AppTab
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.HighConfidenceGreen
import com.example.ui.theme.HighConfidenceRed
import com.example.ui.theme.MediumConfidenceAmber
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.viewmodel.MainViewModel
import java.util.Locale

@Composable
fun ScanScreen(
    viewModel: MainViewModel,
    onNavigateTab: (AppTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scanProgress by viewModel.scanProgress.collectAsState()
    val apkInfo by viewModel.selectedApkInfo.collectAsState()
    val settings by viewModel.scanSettings.collectAsState()
    val rawResults by viewModel.rawResults.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "فحص التطبيق",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scanProgress.isRunning) {
                    Button(
                        onClick = { viewModel.stopScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A1E26)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("stop_scan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "إيقاف الفحص",
                            tint = HighConfidenceRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "إيقاف",
                            color = HighConfidenceRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إغلاق الشاشة",
                            tint = TextGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 1: APK / APKS Metadata Card
        apkInfo?.let { file ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF261D45)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = file.packageName,
                            fontSize = 12.sp,
                            color = TextGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "الإصدار: ${file.versionName}",
                                fontSize = 11.sp,
                                color = PurpleLight
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = TextGray
                            )
                            Text(
                                text = file.fileSizeFormatted,
                                fontSize = 11.sp,
                                color = PurpleLight
                            )
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = TextGray
                            )
                            Text(
                                text = "${file.dexCount} ملفات DEX",
                                fontSize = 11.sp,
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 2: Current Stage & Real Progress Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scanProgress.stageSubtitle.ifEmpty { "جاهز للبدء" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (scanProgress.stage) {
                            ScanStage.ERROR -> HighConfidenceRed
                            ScanStage.CANCELLED -> MediumConfidenceAmber
                            ScanStage.COMPLETED -> HighConfidenceGreen
                            else -> TextWhite
                        },
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(scanProgress.percent * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleAccent
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { scanProgress.percent.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when (scanProgress.stage) {
                        ScanStage.ERROR -> HighConfidenceRed
                        ScanStage.CANCELLED -> MediumConfidenceAmber
                        ScanStage.COMPLETED -> HighConfidenceGreen
                        else -> PurpleAccent
                    },
                    trackColor = Color(0xFF26283C)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Section 3: 4-Grid Live Metrics (Time, Candidates, Analyzed, Results)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val formattedTime = String.format(
                        Locale.US,
                        "%02d:%02d:%02d",
                        scanProgress.elapsedTimeSeconds / 3600,
                        (scanProgress.elapsedTimeSeconds % 3600) / 60,
                        scanProgress.elapsedTimeSeconds % 60
                    )
                    MetricCard(title = "الوقت المنقضي", value = formattedTime, color = TextWhite, modifier = Modifier.weight(1f))
                    MetricCard(title = "المرشحين", value = "${scanProgress.candidatesCount}", color = CyanAccent, modifier = Modifier.weight(1f))
                    MetricCard(title = "تم تحليلها", value = "${scanProgress.analyzedCount}", color = MediumConfidenceAmber, modifier = Modifier.weight(1f))
                    MetricCard(title = "النتائج", value = "${rawResults.size}", color = HighConfidenceGreen, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 4: 6-Stages Checklist
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "مراحل الفحص",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                StageCheckItem(
                    title = "فتح الحزمة واستخراج ملفات DEX",
                    isDone = scanProgress.isStep1Done,
                    isActive = scanProgress.stage == ScanStage.PREPARING_APK,
                    rotation = rotation
                )
                StageCheckItem(
                    title = "فهرسة الرموز والنصوص (DEX Indexing)",
                    isDone = scanProgress.isStep2Done,
                    isActive = scanProgress.stage == ScanStage.INDEXING_DEX,
                    rotation = rotation
                )
                StageCheckItem(
                    title = "البحث السريع عن المرشحين (Fast Discovery)",
                    isDone = scanProgress.isStep3Done,
                    isActive = scanProgress.stage == ScanStage.FINDING_CANDIDATES,
                    rotation = rotation
                )
                StageCheckItem(
                    title = "تحليل Bytecode للدوال المرشحة (Targeted)",
                    isDone = scanProgress.isStep4Done,
                    isActive = scanProgress.stage == ScanStage.ANALYZING_CANDIDATES,
                    rotation = rotation
                )
                StageCheckItem(
                    title = "حساب النقاط والتقييم الذكي (Heuristic Scoring)",
                    isDone = scanProgress.isStep5Done,
                    isActive = scanProgress.stage == ScanStage.RANKING_RESULTS,
                    rotation = rotation
                )
                StageCheckItem(
                    title = "ترتيب وتصفية النتائج النهائية",
                    isDone = scanProgress.isStep6Done,
                    isActive = scanProgress.stage == ScanStage.COMPLETED,
                    rotation = rotation
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Switch: Show results during scan
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "عرض النتائج أثناء الفحص",
                        fontSize = 13.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "تحديث القائمة فور اكتشاف دوال جديدة",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }

                Switch(
                    checked = settings.streamResultsDuringScan,
                    onCheckedChange = {
                        viewModel.updateSettings(settings.copy(streamResultsDuringScan = it))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = PurpleAccent
                    )
                )
            }
        }

        // Section 5: Operations / Actions
        if (scanProgress.stage == ScanStage.COMPLETED) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onNavigateTab(AppTab.RESULTS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(50.dp)
                    .testTag("view_results_button"),
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Assessment, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "عرض النتائج (${rawResults.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (scanProgress.stage == ScanStage.CANCELLED) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = PurpleAccent)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة الفحص", color = PurpleAccent)
                }

                if (rawResults.isNotEmpty()) {
                    Button(
                        onClick = { onNavigateTab(AppTab.RESULTS) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("النتائج الحالية (${rawResults.size})")
                    }
                }
            }
        } else if (scanProgress.stage == ScanStage.ERROR) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1B20)),
                border = androidx.compose.foundation.BorderStroke(1.dp, HighConfidenceRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = HighConfidenceRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = scanProgress.errorMessage ?: "حدث خطأ غير متوقع",
                            color = HighConfidenceRed,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.startScan() },
                        colors = ButtonDefaults.buttonColors(containerColor = HighConfidenceRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إعادة المحاولة")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
fun StageCheckItem(
    title: String,
    isDone: Boolean,
    isActive: Boolean,
    rotation: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDone) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "تم الإنجاز",
                tint = HighConfidenceGreen,
                modifier = Modifier.size(18.dp)
            )
        } else if (isActive) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "جاري العمل",
                tint = PurpleAccent,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .border(1.dp, TextGray.copy(alpha = 0.5f), CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = title,
            fontSize = 12.sp,
            color = if (isDone || isActive) TextWhite else TextGray,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isDone) {
            Text(
                text = "تم",
                fontSize = 11.sp,
                color = HighConfidenceGreen,
                fontWeight = FontWeight.Bold
            )
        } else if (isActive) {
            Text(
                text = "جاري...",
                fontSize = 11.sp,
                color = PurpleAccent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
