package neoproxy.neolink.cli;

/**
 * 启动选项快照。
 *
 * @param autoStartInGui 是否满足 GUI 自动启动条件
 * @param noColor 是否禁用 ANSI 颜色输出
 */
public record LaunchOptions(
        boolean autoStartInGui,
        boolean noColor
) {
}
