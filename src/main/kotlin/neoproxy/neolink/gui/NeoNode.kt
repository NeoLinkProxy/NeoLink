package neoproxy.neolink.gui

// 这是一个数据类，用于存储节点信息
data class NeoNode(
    val name: String,
    val address: String,
    val iconSvg: String?,
    val hookPort: Int,
    val connectPort: Int
)