package de.lattemacchiato.soundboardng.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lattemacchiato.soundboardng.data.SoundBoard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationPage(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        })
    }, content = content)
}

@Composable
fun BoardSelectorScreen(
    boards: List<SoundBoard>,
    activeBoardId: String,
    locked: Boolean,
    onSelect: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    NavigationPage("Choose board", onBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
            items(boards, key = { it.id }) { board ->
                NavigationDrawerItem(
                    label = { Text(board.name) },
                    selected = board.id == activeBoardId,
                    icon = { if (board.id == activeBoardId) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { if (!locked) onSelect(board.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                TextButton(onClick = onCreate, enabled = !locked) { Text("+ New board") }
            }
        }
    }
}

@Composable
fun BoardManagementScreen(
    boards: List<SoundBoard>,
    activeBoardId: String,
    locked: Boolean,
    onCreate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    NavigationPage("Manage boards", onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Current board", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Board:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    boards.firstOrNull { it.id == activeBoardId }?.name ?: "–",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onRename, enabled = !locked && boards.isNotEmpty()) {
                    Text("Rename…")
                }
                OutlinedButton(onClick = onDelete, enabled = !locked && boards.size > 1) {
                    Text("Delete…")
                }
            }

            HorizontalDivider()

            Text("Create board", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onCreate, enabled = !locked) { Text("New board") }

            HorizontalDivider()
            Text("${boards.size} boards total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
