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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AnalysisResult
import com.example.model.ConfidenceLevel
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.HighConfidenceGreen
import com.example.ui.theme.HighConfidenceRed
import com.example.ui.theme.LowConfidenceBlue
import com.example.ui.theme.MediumConfidenceAmber
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.PurpleLight
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardElevated
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextGray
import com.example.ui.theme.TextWhite
import com.example.ui.theme.VeryLowConfidenceGray

@Composable
fun ResultDetailSheet(
    result: AnalysisResult,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val confidenceColor = when (result.confidenceLevel) {
        ConfidenceLevel.VERY_HIGH -> HighConfidenceRed
        ConfidenceLevel.HIGH -> HighConfidenceGreen
        ConfidenceLevel.MEDIUM -> MediumConfidenceAmber
        ConfidenceLevel.LOW -> LowConfidenceBlue
        ConfidenceLevel.VERY_LOW -> VeryLowConfidenceGray
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("result_detail_sheet")
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تفاصيل النتيجة",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = TextGray)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Metadata Card
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(confidenceColor.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${result.score}% ${result.confidenceLevel.labelEn}",
                                fontSize = 12.sp,
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⭐ تطابق استثنائي (+30% أولوية)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFBBF24)
                                )
                            }
                        }
                    }

                    Text(
                        text = "#${result.id}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleLight
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                DetailItem(label = "الكلمة المفتاحية", value = "🔑 ${result.matchedKeyword}", onCopy = {
                    clipboardManager.setText(AnnotatedString(result.matchedKeyword))
                    Toast.makeText(context, "تم النسخ ✓ (الكلمة المفتاحية)", Toast.LENGTH_SHORT).show()
                })

                DetailItem(label = "Class", value = result.className, onCopy = {
                    clipboardManager.setText(AnnotatedString(result.className))
                    Toast.makeText(context, "تم النسخ ✓ (Class)", Toast.LENGTH_SHORT).show()
                })

                DetailItem(label = "Method", value = result.methodName, onCopy = {
                    clipboardManager.setText(AnnotatedString(result.methodName))
                    Toast.makeText(context, "تم النسخ ✓ (Method)", Toast.LENGTH_SHORT).show()
                })

                DetailItem(label = "Signature", value = result.signature, onCopy = {
                    clipboardManager.setText(AnnotatedString(result.signature))
                    Toast.makeText(context, "تم النسخ ✓ (Signature)", Toast.LENGTH_SHORT).show()
                })

                DetailItem(label = "DEX", value = result.dexName, onCopy = {
                    clipboardManager.setText(AnnotatedString(result.dexName))
                    Toast.makeText(context, "تم النسخ ✓ (DEX)", Toast.LENGTH_SHORT).show()
                })

                DetailItem(label = "Package", value = result.packageName, onCopy = {
                    clipboardManager.setText(AnnotatedString(result.packageName))
                    Toast.makeText(context, "تم النسخ ✓ (Package)", Toast.LENGTH_SHORT).show()
                })

                if (result.returnType.isNotEmpty()) {
                    DetailItem(label = "نوع الإرجاع", value = result.returnType, onCopy = {
                        clipboardManager.setText(AnnotatedString(result.returnType))
                        Toast.makeText(context, "تم النسخ ✓ (نوع الإرجاع)", Toast.LENGTH_SHORT).show()
                    })
                }

                DetailItem(label = "التصنيف", value = result.classification.label, onCopy = {
                    clipboardManager.setText(AnnotatedString(result.classification.label))
                    Toast.makeText(context, "تم النسخ ✓ (التصنيف)", Toast.LENGTH_SHORT).show()
                })

                Spacer(modifier = Modifier.height(6.dp))

                // Matched Keywords
                Text(text = "جميع الكلمات المتطابقة (اضغط للنسخ):", fontSize = 12.sp, color = TextGray)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    result.matchedKeywords.forEach { kw ->
                        val isExp = result.exceptionalKeywordsMatched.contains(kw)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isExp) Color(0xFF52330A) else Color(0xFF261D45))
                                .border(1.dp, if (isExp) Color(0xFFD97706) else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(kw))
                                    Toast.makeText(context, "تم النسخ ✓ ($kw)", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isExp) "⭐ $kw (استثنائية)" else kw,
                                fontSize = 11.sp,
                                color = if (isExp) Color(0xFFFBBF24) else PurpleLight,
                                fontWeight = if (isExp) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Scoring Reasons Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "أسباب التقييم",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )

                result.reasons.forEach { reason ->
                    val isBonus = reason.scoreDelta >= 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (isBonus) HighConfidenceGreen else HighConfidenceRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = reason.description,
                            fontSize = 12.sp,
                            color = TextWhite,
                            modifier = Modifier.weight(1f)
                        )
                        val sign = if (isBonus) "+" else ""
                        Text(
                            text = "$sign${reason.scoreDelta}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBonus) HighConfidenceGreen else HighConfidenceRed
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Smali Code Card
        Card(
            modifier = Modifier.fillMaxWidth(),
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
                        text = "كود Smali",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite
                    )

                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(result.smaliSnippet))
                            Toast.makeText(context, "تم نسخ كود Smali", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Smali", tint = PurpleAccent, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D0E15))
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = highlightSmaliSyntax(result.smaliSnippet),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(result.smaliSnippet))
                    Toast.makeText(context, "تم نسخ كود Smali", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).testTag("copy_smali_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("نسخ Smali", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    val fullSummary = "${result.className}->${result.signature}"
                    clipboardManager.setText(AnnotatedString(fullSummary))
                    Toast.makeText(context, "تم نسخ المعرّف الكامل", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("نسخ المعرّف", color = TextWhite, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextGray,
            modifier = Modifier.padding(end = 8.dp)
        )
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextGray, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

private fun highlightSmaliSyntax(code: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = code.split("\n")
        for ((idx, line) in lines.withIndex()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith(".method") || trimmed.startsWith(".end method") || trimmed.startsWith(".locals") -> {
                    pushStyle(SpanStyle(color = Color(0xFFA78BFA), fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                trimmed.startsWith("#") -> {
                    pushStyle(SpanStyle(color = Color(0xFF6B7280)))
                    append(line)
                    pop()
                }
                trimmed.startsWith(":") -> {
                    pushStyle(SpanStyle(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold))
                    append(line)
                    pop()
                }
                trimmed.startsWith("return") || trimmed.startsWith("if-") || trimmed.startsWith("goto") -> {
                    pushStyle(SpanStyle(color = Color(0xFFEF4444), fontWeight = FontWeight.Medium))
                    append(line)
                    pop()
                }
                trimmed.startsWith("const") -> {
                    pushStyle(SpanStyle(color = Color(0xFF10B981)))
                    append(line)
                    pop()
                }
                trimmed.startsWith("invoke-") -> {
                    pushStyle(SpanStyle(color = Color(0xFF06B6D4)))
                    append(line)
                    pop()
                }
                else -> {
                    pushStyle(SpanStyle(color = Color(0xFFE5E7EB)))
                    append(line)
                    pop()
                }
            }
            if (idx < lines.size - 1) append("\n")
        }
    }
}
