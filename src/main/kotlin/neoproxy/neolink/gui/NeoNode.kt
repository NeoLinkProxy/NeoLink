package neoproxy.neolink.gui

/**
 * 节点数据类
 *
 * 核心职责：
 * 1. 存储 NeoLink 服务器节点的配置信息
 * 2. 提供节点元数据（名称、地址、图标、端口等）
 * 3. 支持在 GUI 中显示和选择节点
 *
 * 属性说明：
 * - name: 节点显示名称
 * - address: 节点服务器地址
 * - iconSvg: 节点图标 SVG 字符串（可选）
 * - hookPort: 服务器 Hook 端口（用于控制连接）
 * - connectPort: 服务器数据传输端口
 *
 * @author NeoProxy Team
 * @since 5.11.0
 */
data class NeoNode(
    val name: String,
    val address: String,
    val iconSvg: String?,
    val hookPort: Int,
    val connectPort: Int
)