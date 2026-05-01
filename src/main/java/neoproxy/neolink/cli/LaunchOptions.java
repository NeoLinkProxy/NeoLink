package neoproxy.neolink.cli;

/**
 * 启动选项快照（launch options snapshot）。
 *
 * @param autoStartInGui 是否满足 GUI auto-start 条件
 * @param noColor 是否禁用 ANSI color 输出
 */
public record LaunchOptions(
        boolean autoStartInGui,
        boolean noColor
) {
}
