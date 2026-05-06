package neoproxy.neolink.config;

import java.nio.file.Path;

/**
 * 工作目录提供者接口，跨平台实现。
 * <p>
 * Desktop: AppData / Library / .neolink（由安装路径可写性决定回退策略）
 * Android: context.filesDir
 * <p>
 * 该接口将平台相关的目录探测逻辑从 ConfigOperator 中解耦，使得 common 模块
 * 不需要了解操作系统差异或 Android Context API。
 */
public interface WorkingDirectoryProvider {
    Path resolveWorkingDirectory();
}
