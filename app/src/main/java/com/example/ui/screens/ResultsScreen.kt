package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnalysisResult
import com.example.model.ConfidenceLevel
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.HighConfidenceGreen
import com.example.ui.theme.HighConfidenceRed
import com.example.ui.theme.LowConfidenceBlue
import com.example.ui.theme.MediumConfidenceAmber
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleContainer
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VeryLowConfidenceGray
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rawResults by viewModel.rawResults.collectAsState()
    val filteredResults by viewModel.filteredResults.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selectedResult by viewModel.selectedResult.collectAsState()

    var showFilterDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val availableDexNames = remember(rawResults) {
        rawResults.map { it.dexName }.distinct()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "نتائج الفحص",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(PurpleContainer)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${filteredResults.size}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PurpleLight
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.testTag("results_search_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextWhite)
                    }

                    IconButton(
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.testTag("results_filter_button")
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = PurpleAccent)
                    }

                    IconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier.testTag("results_export_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export", tint = TextWhite)
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                        modifier = Modifier.background(SurfaceCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("تصدير تقرير نصي (TXT)", color = TextWhite) },
                            onClick = {
                                showExportMenu = false
                                val report = viewModel.exportCurrentResults("TXT")
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "تم نسخ تقرير TXT إلى الحافظة", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تصدير JSON", color = TextWhite) },
                            onClick = {
                                showExportMenu = false
                                val report = viewModel.exportCurrentResults("JSON")
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "تم نسخ تقرير JSON إلى الحافظة", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تصدير CSV", color = TextWhite) },
                            onClick = {
                                showExportMenu = false
                                val report = viewModel.exportCurrentResults("CSV")
                                clipboardManager.setText(AnnotatedString(report))
                                Toast.makeText(context, "تم نسخ تقرير CSV إلى الحافظة", Toast.LENGTH_SHORT).show()
                            }
                        )
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
                .padding(horizontal = 16.dp)
        ) {
            // Expanded search field
            if (isSearchExpanded) {
                OutlinedTextField(
                    value = filters.searchQuery,
                    onValueChange = { viewModel.updateFilters(filters.copy(searchQuery = it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("results_search_input"),
                    placeholder = { Text("بحث في الكلاس، الميثود أو الكلمات...", color = TextGray, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard
                    )
                )
            }

            // Quick Filter Pills Row
            val exceptionalMatchesCount = remember(rawResults) {
                rawResults.count { it.hasExceptionalMatch }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.updateFilters(filters.copy(onlyExceptional = false)) }
                        .weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!filters.onlyExceptional) PurpleContainer else SurfaceCard
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (!filters.onlyExceptional) PurpleAccent else SurfaceBorder
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "جميع النتائج (${rawResults.size})",
                            fontSize = 11.sp,
                            fontWeight = if (!filters.onlyExceptional) FontWeight.Bold else FontWeight.Normal,
                            color = if (!filters.onlyExceptional) PurpleAccent else TextGray
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.updateFilters(filters.copy(onlyExceptional = !filters.onlyExceptional)) }
                        .weight(1.3f)
                        .testTag("filter_exceptional_only_button"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (filters.onlyExceptional) Color(0xFF45240A) else SurfaceCard
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (filters.onlyExceptional) Color(0xFFD97706) else Color(0xFF78350F)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⭐ الكلمات الاستثنائية ($exceptionalMatchesCount)",
                                fontSize = 11.sp,
                                fontWeight = if (filters.onlyExceptional) FontWeight.Bold else FontWeight.Medium,
                                color = if (filters.onlyExceptional) Color(0xFFFBBF24) else Color(0xFFF59E0B)
                            )
                        }
                    }
                }
            }

            // Results List or Empty State
            if (filteredResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (rawResults.isEmpty()) "لم يتم إجراء فحص بعد" else "لا توجد نتائج مطابقة للفلاتر",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (rawResults.isEmpty()) "اختر ملفاً من الشاشة الرئيسية واضغط على بدء الفحص" else "جرّب تعديل خيارات الفلترة لعرض نتائج أكثر",
                            fontSize = 13.sp,
                            color = TextGray.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        if (rawResults.isNotEmpty() && filteredResults.isEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { viewModel.resetFilters() },
                                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                            ) {
                                Text("إعادة ضبط الفلاتر")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "النتائج مرتبة بالنسبة المئوية تنازلياً (${filteredResults.size})",
                        fontSize = 12.sp,
                        color = TextGray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "الأعلى دقة أولاً",
                        fontSize = 11.sp,
                        color = PurpleLight,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredResults, key = { it.uniqueKey }) { item ->
                        ResultCard(
                            result = item,
                            onClick = { viewModel.selectResult(item) },
                            onCopySignature = {
                                val text = "${item.className}->${item.signature}"
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "تم نسخ الميثود", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Filter Dialog
    if (showFilterDialog) {
        FilterDialog(
            currentFilters = filters,
            availableDexNames = availableDexNames,
            onApply = { newFilters ->
                viewModel.updateFilters(newFilters)
                showFilterDialog = false
            },
            onReset = {
                viewModel.resetFilters()
            },
            onDismiss = { showFilterDialog = false }
        )
    }

    // Details Sheet Dialog
    selectedResult?.let { result ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.selectResult(null) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ResultDetailSheet(
                result = result,
                onClose = { viewModel.selectResult(null) }
            )
        }
    }
}

@Composable
fun ResultCard(
    result: AnalysisResult,
    onClick: () -> Unit,
    onCopySignature: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val copyWithFeedback: (String, String) -> Unit = { textToCopy, label ->
        clipboardManager.setText(AnnotatedString(textToCopy))
        Toast.makeText(context, "تم النسخ ✓ ($label)", Toast.LENGTH_SHORT).show()
    }

    val confidenceColor = when (result.confidenceLevel) {
        ConfidenceLevel.VERY_HIGH -> HighConfidenceRed
        ConfidenceLevel.HIGH -> HighConfidenceGreen
        ConfidenceLevel.MEDIUM -> MediumConfidenceAmber
        ConfidenceLevel.LOW -> LowConfidenceBlue
        ConfidenceLevel.VERY_LOW -> VeryLowConfidenceGray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("result_card_${result.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (result.hasExceptionalMatch) Color(0xFF22160C) else SurfaceCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (result.hasExceptionalMatch) Color(0xFFD97706) else SurfaceBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top row: ID & Confidence on Left | Matched Keyword on Right (Replacing App Code)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "#${result.id}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleLight
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(confidenceColor.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${result.score}% ${result.confidenceLevel.labelEn}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = confidenceColor
                        )
                    }

                    if (result.hasExceptionalMatch) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF52330A))
                                .border(1.dp, Color(0xFFD97706), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⭐ استثنائي",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Matched Keyword Badge with Copy (Exact original keyword from TXT)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF261D45))
                        .border(1.dp, PurpleAccent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "🔑 ${result.matchedKeyword}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { copyWithFeedback(result.matchedKeyword, "الكلمة المفتاحية") },
                        modifier = Modifier.size(24.dp).testTag("copy_keyword_${result.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ الكلمة المفتاحية",
                            tint = PurpleLight,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Class Row with Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.className,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { copyWithFeedback(result.className, "Class") },
                    modifier = Modifier.size(24.dp).testTag("copy_class_${result.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ اسم الكلاس",
                        tint = TextGray,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Method & Signature Row with Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = result.signature,
                    fontSize = 12.sp,
                    color = PurpleLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { copyWithFeedback(result.signature, "Signature") },
                    modifier = Modifier.size(24.dp).testTag("copy_sig_${result.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "نسخ توقيع الدالة",
                        tint = PurpleLight,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // DEX & Package Row with Copy Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // DEX Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceCardElevated)
                        .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "DEX: ${result.dexName}",
                        fontSize = 10.sp,
                        color = TextGray
                    )
                    IconButton(
                        onClick = { copyWithFeedback(result.dexName, "DEX") },
                        modifier = Modifier.size(20.dp).testTag("copy_dex_${result.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ اسم DEX",
                            tint = TextGray,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Package Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(SurfaceCardElevated)
                        .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                ) {
                    Text(
                        text = "Pkg: ${result.packageName}",
                        fontSize = 10.sp,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(
                        onClick = { copyWithFeedback(result.packageName, "Package") },
                        modifier = Modifier.size(20.dp).testTag("copy_pkg_${result.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ اسم الحزمة",
                            tint = TextGray,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                if (result.returnBoolean) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF0D2D35))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Boolean Return",
                            fontSize = 10.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Matched Keywords row with copy on tap
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                result.matchedKeywords.forEach { kw ->
                    val isExp = result.exceptionalKeywordsMatched.contains(kw)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isExp) Color(0xFF52330A) else Color(0xFF261D45))
                            .border(1.dp, if (isExp) Color(0xFFD97706) else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { copyWithFeedback(kw, kw) }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isExp) "⭐ $kw" else kw,
                            fontSize = 10.sp,
                            fontWeight = if (isExp) FontWeight.Bold else FontWeight.Normal,
                            color = if (isExp) Color(0xFFFBBF24) else PurpleAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom card buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1065)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("view_smali_${result.id}")
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = PurpleLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("عرض Smali", color = PurpleLight, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val smaliToCopy = if (result.smaliSnippet.isNotBlank()) result.smaliSnippet else result.signature
                        copyWithFeedback(smaliToCopy, "Smali")
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("copy_smali_${result.id}")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextWhite, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("نسخ Smali", color = TextWhite, fontSize = 12.sp)
                }
            }
        }
    }
}
