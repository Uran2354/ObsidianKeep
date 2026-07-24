package com.example.obsidiankeep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownHelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.markdown_help_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("←", color = Color.White, fontSize = 28.sp, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HelpSection(stringResource(R.string.md_help_headers)) {
                HelpRow(stringResource(R.string.md_help_h1), stringResource(R.string.md_help_h1_desc))
                HelpRow(stringResource(R.string.md_help_h2), stringResource(R.string.md_help_h2_desc))
                HelpRow(stringResource(R.string.md_help_h3), stringResource(R.string.md_help_h3_desc))
                HelpRow(stringResource(R.string.md_help_h4), stringResource(R.string.md_help_h4_desc))
            }

            HelpSection(stringResource(R.string.md_help_text_format)) {
                HelpRow(stringResource(R.string.md_help_bold), stringResource(R.string.md_help_bold_desc))
                HelpRow(stringResource(R.string.md_help_italic), stringResource(R.string.md_help_italic_desc))
                HelpRow(stringResource(R.string.md_help_code), stringResource(R.string.md_help_code_desc))
            }

            HelpSection(stringResource(R.string.md_help_lists)) {
                HelpRow(stringResource(R.string.md_help_li1), stringResource(R.string.md_help_li1_desc))
                HelpRow(stringResource(R.string.md_help_li2), stringResource(R.string.md_help_li2_desc))
                HelpRow(stringResource(R.string.md_help_ol1), stringResource(R.string.md_help_ol_desc))
                HelpRow(stringResource(R.string.md_help_ol2), stringResource(R.string.md_help_ol_desc))
            }

            HelpSection(stringResource(R.string.md_help_checklists)) {
                HelpRow(stringResource(R.string.md_help_todo), stringResource(R.string.md_help_todo_desc))
                HelpRow(stringResource(R.string.md_help_done), stringResource(R.string.md_help_done_desc))
                Text(
                    stringResource(R.string.md_help_check_hint),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            HelpSection(stringResource(R.string.md_help_links)) {
                HelpRow(stringResource(R.string.md_help_wikilink), stringResource(R.string.md_help_wikilink_desc))
                Text(
                    stringResource(R.string.md_help_link_hint),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            HelpSection(stringResource(R.string.md_help_quotes)) {
                HelpRow(stringResource(R.string.md_help_quote), stringResource(R.string.md_help_quote_desc))
            }

            HelpSection(stringResource(R.string.md_help_code_block)) {
                HelpRow(stringResource(R.string.md_help_codeblock_open), stringResource(R.string.md_help_codeblock_open_desc))
                HelpRow(stringResource(R.string.md_help_codeblock_open), stringResource(R.string.md_help_codeblock_close_desc))
                Text(
                    stringResource(R.string.md_help_code_hint),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            HelpSection(stringResource(R.string.md_help_misc)) {
                HelpRow(stringResource(R.string.md_help_hr), stringResource(R.string.md_help_hr_desc))
            }

            HelpSection(stringResource(R.string.md_help_modes)) {
                Text(stringResource(R.string.md_help_edit_mode), color = Color.White, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.md_help_preview_mode), color = Color.White, fontSize = 13.sp)
            }

            HelpSection(stringResource(R.string.autocomplete_help_title)) {
                HelpRow(stringResource(R.string.autocomplete_help_link), stringResource(R.string.autocomplete_help_link_desc))
                HelpRow(stringResource(R.string.autocomplete_help_link_create), stringResource(R.string.autocomplete_help_link_create_desc))
                HelpRow(stringResource(R.string.autocomplete_help_link_pagination), stringResource(R.string.autocomplete_help_link_pagination_desc))
                HelpRow(stringResource(R.string.autocomplete_help_tag), stringResource(R.string.autocomplete_help_tag_desc))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.autocomplete_help_hint),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            HelpSection(stringResource(R.string.images_help_title)) {
                HelpRow(stringResource(R.string.images_help_syntax), stringResource(R.string.images_help_syntax_desc))
                HelpRow(stringResource(R.string.images_help_insert), stringResource(R.string.images_help_insert_desc))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.images_help_hint),
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("ObsidianKeep v4", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun HelpSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color(0xFFBB86FC), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun HelpRow(syntax: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = syntax,
            color = Color(0xFF43A047),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(description, color = Color.Gray, fontSize = 12.sp)
    }
}
