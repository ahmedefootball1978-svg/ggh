package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.CodeClassification
import com.example.model.ConfidenceLevel
import com.example.ui.theme.HighConfidenceGreen
import com.example.ui.theme.HighConfidenceRed
import com.example.ui.theme.LowConfidenceBlue
import com.example.ui.theme.MediumConfidenceAmber
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VeryLowConfidenceGray
import com.example.viewmodel.ResultFilters

@Composable
fun FilterDialog(
    currentFilters: ResultFilters,
    availableDexNames: List<String>,
    onApply: (ResultFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var confidences by remember { mutableStateOf(currentFilters.allowedConfidences) }
    var classifications by remember { mutableStateOf(currentFilters.allowedClassifications) }
    var selectedDex by remember { mutableStateOf(currentFilters.selectedDex) }
    var onlyExceptional by remember { mutableStateOf(currentFilters.onlyExceptional) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("filter_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الفلاتر",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextGray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Exceptional Keywords Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (onlyExceptional) Color(0xFF331F0A) else SurfaceCard)
                        .border(1.dp, if (onlyExceptional) Color(0xFFD97706) else SurfaceBorder, RoundedCornerShape(10.dp))
                        .clickable { onlyExceptional = !onlyExceptional }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⭐", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("الكلمات الاستثنائية فقط", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("إظهار النتائج ذات الأولوية والدقة العالية فقط", color = Color(0xFFFBBF24), fontSize = 10.sp)
                        }
                    }
                    Switch(
                        checked = onlyExceptional,
                        onCheckedChange = { onlyExceptional = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFD97706)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Confidence range
                Text(
                    text = "نطاق الثقة",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))

                ConfidenceCheckboxRow(
                    label = "Very High (90 - 100%)",
                    color = HighConfidenceRed,
                    isChecked = confidences.contains(ConfidenceLevel.VERY_HIGH),
                    onToggle = {
                        confidences = if (it) confidences + ConfidenceLevel.VERY_HIGH else confidences - ConfidenceLevel.VERY_HIGH
                    }
                )
                ConfidenceCheckboxRow(
                    label = "High (75 - 89%)",
                    color = HighConfidenceGreen,
                    isChecked = confidences.contains(ConfidenceLevel.HIGH),
                    onToggle = {
                        confidences = if (it) confidences + ConfidenceLevel.HIGH else confidences - ConfidenceLevel.HIGH
                    }
                )
                ConfidenceCheckboxRow(
                    label = "Medium (50 - 74%)",
                    color = MediumConfidenceAmber,
                    isChecked = confidences.contains(ConfidenceLevel.MEDIUM),
                    onToggle = {
                        confidences = if (it) confidences + ConfidenceLevel.MEDIUM else confidences - ConfidenceLevel.MEDIUM
                    }
                )
                ConfidenceCheckboxRow(
                    label = "Low (25 - 49%)",
                    color = LowConfidenceBlue,
                    isChecked = confidences.contains(ConfidenceLevel.LOW),
                    onToggle = {
                        confidences = if (it) confidences + ConfidenceLevel.LOW else confidences - ConfidenceLevel.LOW
                    }
                )
                ConfidenceCheckboxRow(
                    label = "Very Low (0 - 24%)",
                    color = VeryLowConfidenceGray,
                    isChecked = confidences.contains(ConfidenceLevel.VERY_LOW),
                    onToggle = {
                        confidences = if (it) confidences + ConfidenceLevel.VERY_LOW else confidences - ConfidenceLevel.VERY_LOW
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Classification
                Text(
                    text = "التصنيف",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))

                CodeClassification.entries.forEach { cls ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                classifications = if (classifications.contains(cls)) {
                                    classifications - cls
                                } else {
                                    classifications + cls
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = classifications.contains(cls),
                            onCheckedChange = { checked ->
                                classifications = if (checked) classifications + cls else classifications - cls
                            },
                            colors = CheckboxDefaults.colors(checkedColor = PurpleAccent, uncheckedColor = TextGray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = cls.label, fontSize = 13.sp, color = TextWhite)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. DEX selector
                Text(
                    text = "DEX",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))

                val allDexOptions = listOf("الكل") + availableDexNames
                allDexOptions.forEach { dexName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedDex = dexName }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedDex == dexName,
                            onClick = { selectedDex = dexName },
                            colors = RadioButtonDefaults.colors(selectedColor = PurpleAccent, unselectedColor = TextGray)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = dexName, fontSize = 13.sp, color = TextWhite)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = {
                            onReset()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إعادة تعيين", color = TextGray, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            onApply(
                                currentFilters.copy(
                                    allowedConfidences = confidences,
                                    allowedClassifications = classifications,
                                    selectedDex = selectedDex,
                                    onlyExceptional = onlyExceptional
                                )
                            )
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("apply_filters_button")
                    ) {
                        Text("تطبيق الفلاتر", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfidenceCheckboxRow(
    label: String,
    color: Color,
    isChecked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isChecked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onToggle,
            colors = CheckboxDefaults.colors(checkedColor = PurpleAccent, uncheckedColor = TextGray)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, fontSize = 13.sp, color = color, fontWeight = FontWeight.Medium)
    }
}
