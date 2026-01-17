package io.github.lzdev42.catalyticui.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 通用简单提示弹窗
 * 
 * @param visible 是否显示
 * @param title 标题
 * @param message 消息内容
 * @param confirmText 确认按钮文字，默认"确定"
 * @param onDismiss 关闭回调
 */
@Composable
fun SimpleAlertDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String = "确定",
    onDismiss: () -> Unit
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(confirmText)
                }
            }
        )
    }
}

/**
 * 提示弹窗状态
 */
data class AlertState(
    val visible: Boolean = false,
    val title: String = "",
    val message: String = ""
) {
    companion object {
        val Hidden = AlertState()
        
        fun show(title: String, message: String) = AlertState(
            visible = true,
            title = title,
            message = message
        )
    }
}
