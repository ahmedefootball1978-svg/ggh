package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.KeywordImportStats
import com.example.model.KeywordEntity
import com.example.ui.theme.BackgroundDark
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.HighConfidenceGreen
import com.example.ui.theme.HighConfidenceRed
import com.example.ui.theme.MediumConfidenceAmber
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleContainer
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    val allKeywords by viewModel.allKeywords.collectAsState()
    val totalCount by viewModel.totalKeywordsCount.collectAsState()
    val enabledCount by viewModel.enabledKeywordsCount.collectAsState()
    val exceptionalCount by viewModel.exceptionalKeywordsCount.collectAsState()
    val disabledCount = (totalCount - enabledCount).coerceAtLeast(0)

    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableStateOf("ALL") } // "ALL", "EXCEPTIONAL", "ENABLED", "DISABLED"
    val selectedIds = remember { mutableStateListOf<Long>() }

    var showMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var isImportingFile by remember { mutableStateOf(false) }
    var importStatsDialog by remember { mutableStateOf<KeywordImportStats?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            isImportingFile = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val text = stream.bufferedReader().readText()
                        viewModel.importKeywordsFromText(text) { stats ->
                            isImportingFile = false
                            importStatsDialog = stats
                        }
                    } ?: run {
                        withContext(Dispatchers.Main) {
                            isImportingFile = false
                            Toast.makeText(context, "تعذر قراءة الملف المختار", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isImportingFile = false
                        Toast.makeText(context, "خطأ أثناء قراءة الملف: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val filteredList = remember(allKeywords, searchQuery, filterTab) {
        var list = when (filterTab) {
            "EXCEPTIONAL" -> allKeywords.filter { it.isExceptional }
            "ENABLED" -> allKeywords.filter { it.isEnabled }
            "DISABLED" -> allKeywords.filter { !it.isEnabled }
            else -> allKeywords
        }
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.word.contains(searchQuery.trim(), ignoreCase = true) }
        }
        list
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BackgroundDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "إدارة الكلمات المفتاحية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("keywords_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                actions = {
                    // Prominent Import TXT Icon in TopBar
                    IconButton(
                        onClick = { filePicker.launch("*/*") },
                        modifier = Modifier.testTag("import_txt_action_icon")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "استيراد TXT", tint = CyanAccent)
                    }
                    IconButton(onClick = { showAddDialog = true }, modifier = Modifier.testTag("add_keyword_action")) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة كلمة", tint = PurpleAccent)
                    }
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.testTag("keywords_menu_button")) {
                        Icon(Icons.Default.MoreVert, contentDescription = "خيارات إضافية", tint = TextWhite)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SurfaceCard)
                    ) {
                        DropdownMenuItem(
                            text = { Text("استيراد ملف TXT", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                filePicker.launch("*/*")
                            },
                            leadingIcon = { Icon(Icons.Default.UploadFile, contentDescription = null, tint = CyanAccent) }
                        )
                        DropdownMenuItem(
                            text = { Text("لصق نص الكلمات", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                showImportDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تفعيل الكل", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                viewModel.toggleAllKeywords(true)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تعطيل الكل", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                viewModel.toggleAllKeywords(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("تعيين كل المفعلة كاستثنائية ⭐", color = Color(0xFFFBBF24)) },
                            onClick = {
                                showMenu = false
                                val enabledIds = allKeywords.filter { it.isEnabled }.map { it.id }
                                viewModel.setKeywordsExceptional(enabledIds, true)
                            },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24)) }
                        )
                        DropdownMenuItem(
                            text = { Text("إلغاء الاستثنائية عن الكل", color = TextWhite) },
                            onClick = {
                                showMenu = false
                                val allIds = allKeywords.map { it.id }
                                viewModel.setKeywordsExceptional(allIds, false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف جميع الكلمات", color = HighConfidenceRed) },
                            onClick = {
                                showMenu = false
                                viewModel.deleteAllKeywords()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        bottomBar = {
            Surface(
                color = SurfaceCard,
                tonalElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedIds.isNotEmpty()) {
                        // Delete Selected button
                        Button(
                            onClick = {
                                viewModel.deleteKeywords(selectedIds.toList())
                                selectedIds.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C1D24)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delete_selected_button")
                        ) {
                            Text("حذف (${selectedIds.size})", color = HighConfidenceRed, fontSize = 11.sp, maxLines = 1)
                        }

                        // Exceptional Toggle button for selected
                        Button(
                            onClick = {
                                val selectedEntities = allKeywords.filter { selectedIds.contains(it.id) }
                                val allExceptional = selectedEntities.all { it.isExceptional }
                                viewModel.setKeywordsExceptional(selectedIds.toList(), !allExceptional)
                                selectedIds.clear()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF45240A)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("toggle_exceptional_selected_button")
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⭐ تعيين كاستثنائية", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // Clear selection
                        Button(
                            onClick = { selectedIds.clear() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232536)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.9f)
                        ) {
                            Text("إلغاء", color = TextWhite, fontSize = 11.sp, maxLines = 1)
                        }
                    } else {
                        // Requirement 1: Prominent "استيراد TXT" button
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B3842)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("import_txt_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استيراد TXT", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // Export TXT button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val exported = viewModel.exportKeywords()
                                    clipboardManager.setText(AnnotatedString(exported))
                                    Toast.makeText(context, "تم نسخ الكلمات إلى الحافظة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF232536)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_txt_button")
                        ) {
                            Text("تصدير TXT", color = TextWhite, fontSize = 12.sp, maxLines = 1)
                        }

                        // Select All button
                        Button(
                            onClick = {
                                if (selectedIds.size == allKeywords.size && allKeywords.isNotEmpty()) {
                                    selectedIds.clear()
                                } else {
                                    selectedIds.clear()
                                    selectedIds.addAll(allKeywords.map { it.id })
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PurpleContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("select_all_button")
                        ) {
                            Text("تحديد الكل", color = PurpleAccent, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Stats Row: 4 chips including exceptional count
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatPill(title = "الإجمالي", value = totalCount.toString(), color = TextWhite, modifier = Modifier.weight(1f))
                StatPill(title = "استثنائية ⭐", value = exceptionalCount.toString(), color = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
                StatPill(title = "مفعلة", value = enabledCount.toString(), color = HighConfidenceGreen, modifier = Modifier.weight(1f))
                StatPill(title = "محددة", value = selectedIds.size.toString(), color = CyanAccent, modifier = Modifier.weight(1f))
            }

            // Quick Category / Filter Row with the requested "الكلمات الاستثنائية" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterTabButton(
                    title = "الكل ($totalCount)",
                    isSelected = filterTab == "ALL",
                    onClick = { filterTab = "ALL" },
                    modifier = Modifier.weight(1f)
                )
                FilterTabButton(
                    title = "⭐ استثنائية ($exceptionalCount)",
                    isSelected = filterTab == "EXCEPTIONAL",
                    isSpecial = true,
                    onClick = { filterTab = "EXCEPTIONAL" },
                    modifier = Modifier.weight(1.3f)
                )
                FilterTabButton(
                    title = "المفعلة ($enabledCount)",
                    isSelected = filterTab == "ENABLED",
                    onClick = { filterTab = "ENABLED" },
                    modifier = Modifier.weight(1f)
                )
                FilterTabButton(
                    title = "المعطلة ($disabledCount)",
                    isSelected = filterTab == "DISABLED",
                    onClick = { filterTab = "DISABLED" },
                    modifier = Modifier.weight(1f)
                )
            }

            // Explanatory card for exceptional keywords
            if (filterTab == "EXCEPTIONAL") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF331F0A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "الكلمات الاستثنائية: يتم البحث عنها في الدوال والكلاسات والحقول والـ bytecode بدقة قصوى وتمنح أعلى أولوية للمرشحين والنتائج.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("keywords_search_field"),
                placeholder = { Text("بحث في الكلمات المفتاحية...", color = TextGray, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextGray)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PurpleAccent,
                    unfocusedBorderColor = SurfaceBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (isImportingFile) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanAccent, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("جاري استيراد الكلمات من ملف TXT في الخلفية...", fontSize = 13.sp, color = TextWhite)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Keywords List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "لا توجد كلمات مطابقة لبحثك" else "قاعدة الكلمات فارغة",
                            color = TextGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { filePicker.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد TXT الآن", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.id }) { keyword ->
                        KeywordItemRow(
                            keyword = keyword,
                            isSelected = selectedIds.contains(keyword.id),
                            onSelect = { isChecked ->
                                if (isChecked) selectedIds.add(keyword.id)
                                else selectedIds.remove(keyword.id)
                            },
                            onToggle = { isEnabled ->
                                viewModel.toggleKeyword(keyword.id, isEnabled)
                            },
                            onToggleExceptional = { isExceptional ->
                                viewModel.toggleKeywordExceptional(keyword.id, isExceptional)
                            },
                            onDelete = {
                                viewModel.deleteKeyword(keyword)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add Single Keyword Dialog
    if (showAddDialog) {
        var newWord by remember { mutableStateOf("") }
        var isExceptional by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("إضافة كلمة مفتاحية جديدة", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = newWord,
                        onValueChange = { newWord = it },
                        placeholder = { Text("مثال: is_premium, has_license", color = TextGray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Exception switch in Add dialog
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isExceptional) Color(0xFF331F0A) else SurfaceCardElevated)
                            .border(1.dp, if (isExceptional) Color(0xFFD97706) else SurfaceBorder, RoundedCornerShape(8.dp))
                            .clickable { isExceptional = !isExceptional }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⭐", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("كلمة استثنائية (أولوية ودقة قصوى)", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("يتم البحث عنها بدقة مضاعفة وأعلى أولوية", color = Color(0xFFFBBF24), fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = isExceptional,
                            onCheckedChange = { isExceptional = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFD97706)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newWord.isNotBlank()) {
                            viewModel.addKeyword(newWord, isExceptional = isExceptional)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("إضافة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("إلغاء", color = TextGray)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Paste Multi-Line Text Dialog
    if (showImportDialog) {
        var rawText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("لصق كلمات مفتاحية (TXT)", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "الصق الكلمات هنا، كلمة واحدة في كل سطر:",
                        color = TextGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PurpleAccent,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawText.isNotBlank()) {
                            viewModel.importKeywordsFromText(rawText) { stats ->
                                showImportDialog = false
                                importStatsDialog = stats
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("استيراد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("إلغاء", color = TextGray)
                }
            },
            containerColor = SurfaceCard
        )
    }

    // Import Stats dialog showing exact required metrics
    importStatsDialog?.let { stats ->
        AlertDialog(
            onDismissRequest = { importStatsDialog = null },
            title = {
                Text(
                    text = "تقرير استيراد الكلمات المفتاحية",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الأسطر المقروءة:", color = TextGray, fontSize = 13.sp)
                                Text("${stats.totalLines}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الكلمات المستوردة المضافة:", color = HighConfidenceGreen, fontSize = 13.sp)
                                Text("+${stats.finalKeywords}", color = HighConfidenceGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            if (stats.invalidLines > 0) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("أسطر فارغة تم تجاوزها:", color = TextGray, fontSize = 13.sp)
                                    Text("${stats.invalidLines}", color = TextGray, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { importStatsDialog = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                ) {
                    Text("تم")
                }
            },
            containerColor = SurfaceCard
        )
    }
}

@Composable
fun KeywordItemRow(
    keyword: KeywordEntity,
    isSelected: Boolean,
    onSelect: (Boolean) -> Unit,
    onToggle: (Boolean) -> Unit,
    onToggleExceptional: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> Color(0xFF261E38)
                keyword.isExceptional -> Color(0xFF241A10)
                else -> SurfaceCard
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isSelected -> PurpleAccent
                keyword.isExceptional -> Color(0xFFD97706)
                else -> SurfaceBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelect,
                colors = CheckboxDefaults.colors(
                    checkedColor = PurpleAccent,
                    uncheckedColor = TextGray
                )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = keyword.word,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (keyword.isEnabled) TextWhite else TextGray
                    )
                    if (keyword.isExceptional) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF52330A))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "⭐ استثنائية",
                                color = Color(0xFFFBBF24),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = if (keyword.isExceptional) "أولوية وبحث عميق • ${keyword.category}" else keyword.category,
                    fontSize = 11.sp,
                    color = if (keyword.isExceptional) Color(0xFFF59E0B) else PurpleLight
                )
            }

            // Button to toggle Exceptional status ⭐
            IconButton(
                onClick = { onToggleExceptional(!keyword.isExceptional) },
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (keyword.isExceptional) Color(0xFF45240A) else Color(0xFF1E202E))
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = if (keyword.isExceptional) "إلغاء الاستثنائية" else "تعيين كاستثنائية",
                    tint = if (keyword.isExceptional) Color(0xFFFBBF24) else TextGray.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Switch(
                checked = keyword.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PurpleAccent
                )
            )

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف الكلمة",
                    tint = TextGray.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun FilterTabButton(
    title: String,
    isSelected: Boolean,
    isSpecial: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected && isSpecial -> Color(0xFF45240A)
                isSelected -> PurpleContainer
                else -> SurfaceCard
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when {
                isSelected && isSpecial -> Color(0xFFD97706)
                isSelected -> PurpleAccent
                isSpecial -> Color(0xFF78350F)
                else -> SurfaceBorder
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected && isSpecial -> Color(0xFFFBBF24)
                    isSelected -> PurpleAccent
                    isSpecial -> Color(0xFFF59E0B)
                    else -> TextGray
                },
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatPill(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 10.sp, color = TextGray, maxLines = 1)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
