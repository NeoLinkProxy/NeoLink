package neoproxy.neolink.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.ceroxe.api.neolink.NeoNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeSelector(
    nodes: List<NeoNode>,
    selectedNodeId: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    label: String = "选择公共节点",
    allowManual: Boolean = true,
    onSelectNode: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedNode = nodes.firstOrNull { it.realId == selectedNodeId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedNode?.safeName() ?: "手动填写",
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (allowManual) {
                DropdownMenuItem(
                    text = { Text("手动填写") },
                    onClick = {
                        onSelectNode(null)
                        expanded = false
                    }
                )
            }
            nodes.forEach { node ->
                DropdownMenuItem(
                    text = { Text(node.displayText()) },
                    onClick = {
                        onSelectNode(node.realId)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun NeoNode.safeName(): String {
    return name?.takeIf { it.isNotBlank() } ?: "未命名节点"
}

private fun NeoNode.displayText(): String {
    val addressText = address?.takeIf { it.isNotBlank() } ?: "unknown"
    val portText = connectPort.takeIf { it in 1..65535 }?.toString() ?: "?"
    return "${safeName()} ($addressText:$portText)"
}
