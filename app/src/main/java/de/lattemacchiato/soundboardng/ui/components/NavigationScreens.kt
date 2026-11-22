package de.lattemacchiato.soundboardng.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.lattemacchiato.soundboardng.R
import de.lattemacchiato.soundboardng.data.SoundBoard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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
    NavigationPage(stringResource(R.string.nav_choose_board), onBack) { padding ->
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
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Button(
                        onClick = onCreate,
                        enabled = !locked,
                    ) {
                        Text(stringResource(R.string.board_new))
                    }

                    HorizontalDivider()

                    Text(
                        text = pluralStringResource(R.plurals.boards_total, boards.size, boards.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun BoardManagementScreen(
    boards: List<SoundBoard>,
    activeBoardId: String,
    locked: Boolean,
    defaultRowHeightLevel: Int,
    onRowHeightLevelChange: (Int?) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    NavigationPage(stringResource(R.string.nav_manage_board), onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.board_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.rename_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        boards.firstOrNull { it.id == activeBoardId }?.name ?: "–",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                IconButton(
                    onClick = onRename,
                    enabled = !locked && boards.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.board_rename),
                    )
                }

                IconButton(
                    onClick = onDelete,
                    enabled = !locked && boards.size > 1,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.board_delete_title),
                    )
                }
            }

            HorizontalDivider()

            val activeBoard = boards.firstOrNull { it.id == activeBoardId }
            val customLevel = activeBoard?.rowHeightLevel
            val useGlobal = customLevel == null

            var sliderLevel by remember(activeBoardId) {
                mutableFloatStateOf((customLevel ?: defaultRowHeightLevel).toFloat())
            }

            LaunchedEffect(customLevel, defaultRowHeightLevel) {
                sliderLevel = (customLevel ?: defaultRowHeightLevel).toFloat()
            }

            Text(
                stringResource(R.string.board_sound_button_size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.board_use_global_default), modifier = Modifier.weight(1f))

                Switch(
                    checked = useGlobal,
                    enabled = !locked,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onRowHeightLevelChange(null)
                        } else {
                            onRowHeightLevelChange(defaultRowHeightLevel)
                        }
                    },
                )
            }

            Text(
                text =
                    if (useGlobal) {
                        stringResource(R.string.board_global_level, defaultRowHeightLevel)
                    } else {
                        stringResource(R.string.board_custom_level, sliderLevel.toInt().coerceIn(1, 5))
                    },
                style = MaterialTheme.typography.labelMedium,
            )

            Slider(
                value = sliderLevel,
                onValueChange = { sliderLevel = it },
                onValueChangeFinished = {
                    if (!useGlobal && !locked) {
                        onRowHeightLevelChange(sliderLevel.toInt().coerceIn(1, 5))
                    }
                },
                valueRange = 1f..5f,
                steps = 3,
                enabled = !locked && !useGlobal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
