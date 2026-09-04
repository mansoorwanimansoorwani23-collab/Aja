package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class Header(val text: String, val level: Int) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class Code(val language: String, val code: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = rawText.lines()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuffer = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // Ending code block
                blocks.add(MarkdownBlock.Code(codeLang.ifBlank { "Code" }, codeBuffer.toString().trimEnd()))
                codeBuffer.clear()
                codeLang = ""
                inCodeBlock = false
            } else {
                // Starting code block
                inCodeBlock = true
                codeLang = line.trim().removePrefix("```").trim()
                codeBuffer.clear()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeBuffer.append(line).append("\n")
            i++
            continue
        }

        val trimmed = line.trim()
        when {
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Header(trimmed.removePrefix("### "), 3))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Header(trimmed.removePrefix("## "), 2))
            }
            trimmed.startsWith("# ") -> {
                blocks.add(MarkdownBlock.Header(trimmed.removePrefix("# "), 1))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.Bullet(trimmed.substring(2)))
            }
            trimmed.isNotEmpty() -> {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
        }
        i++
    }

    if (inCodeBlock && codeBuffer.isNotEmpty()) {
        blocks.add(MarkdownBlock.Code(codeLang.ifBlank { "Code" }, codeBuffer.toString().trimEnd()))
    }

    return blocks
}

@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = block.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text, textColor),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = textColor
                    )
                }

                is MarkdownBlock.Bullet -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "•",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = renderInlineMarkdown(block.text, textColor),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = textColor
                        )
                    }
                }

                is MarkdownBlock.Code -> {
                    CodeBlockCard(language = block.language, code = block.code)
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(language: String, code: String) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131722),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E2330))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase(),
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("code", code)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (copied) "Copied!" else "Copy",
                        color = if (copied) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            // Code content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    color = Color(0xFFE2E8F0),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

/**
 * Handles inline code (`...`), bold (**...**), and italic (*...*)
 */
@Composable
fun renderInlineMarkdown(text: String, defaultColor: Color) = buildAnnotatedString {
    val parts = text.split("`")
    for (i in parts.indices) {
        val segment = parts[i]
        if (i % 2 == 1) {
            // Inline code
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x3364748B),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp
                )
            ) {
                append(" $segment ")
            }
        } else {
            // Check for bold **
            val boldParts = segment.split("**")
            for (j in boldParts.indices) {
                val bPart = boldParts[j]
                if (j % 2 == 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(bPart)
                    }
                } else {
                    // Check for italic *
                    val italicParts = bPart.split("*")
                    for (k in italicParts.indices) {
                        val iPart = italicParts[k]
                        if (k % 2 == 1) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                                append(iPart)
                            }
                        } else {
                            withStyle(SpanStyle(color = defaultColor)) {
                                append(iPart)
                            }
                        }
                    }
                }
            }
        }
    }
}
