package neoproxy.neolink.config;

import java.nio.file.Path;
import java.nio.file.Paths;

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

    /**
     * 桌面端默认数据目录策略。
     *
     * <p>该默认实现是历史 CLI/桌面入口未显式注入 provider 时的兼容路径。desktop 模块仍应
     * 显式注入自己的 provider，让平台接线点清晰；路径算法集中在这里，避免 common 兼容逻辑
     * 与 desktop 显式实现各写一份后长期漂移。</p>
     */
    static Path resolveDefaultDesktopWorkingDirectory() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return Paths.get(localAppData, "NeoLink");
            }
            return Paths.get(home, "AppData", "Local", "NeoLink");
        }
        if (os.contains("mac")) {
            return Paths.get(home, "Library", "Application Support", "NeoLink");
        }
        return Paths.get(home, ".neolink");
    }
}
