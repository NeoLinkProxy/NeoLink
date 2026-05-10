package neoproxy.neolink.platform;

import neoproxy.neolink.config.WorkingDirectoryProvider;

import java.io.File;
import java.nio.file.Path;

/**
 * 桌面端工作目录提供者。
 *
 * <p>封装桌面平台（Windows / macOS / Linux）的标准数据目录探测策略。
 * 该实现从原 {@code ConfigOperator.getPlatformSpecificDataPath()} 逻辑中提取，
 * 使得 common 模块无需关心操作系统差异。</p>
 *
 * <p>目录选择规则：
 * <ul>
 *   <li>Windows: {@code %LOCALAPPDATA%\NeoLink}（回退 {@code ~/AppData/Local/NeoLink}）</li>
 *   <li>macOS: {@code ~/Library/Application Support/NeoLink}</li>
 *   <li>Linux: {@code ~/.neolink}</li>
 * </ul>
 * </p>
 *
 * <p>注意：桌面端默认行为（ConfigOperator 中 provider==null 时）已内置相同逻辑。
 * 本类作为显式实现提供，便于测试注入或未来需要覆盖默认策略的场景使用。</p>
 */
public final class DesktopDirectoryProvider implements WorkingDirectoryProvider {

    @Override
    public Path resolveWorkingDirectory() {
        String dataPath = getPlatformSpecificDataPath();
        File dir = new File(dataPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.toPath().toAbsolutePath();
    }

    /**
     * 根据运行平台返回标准应用数据目录。
     * 逻辑与 ConfigOperator 内置的桌面端回退路径完全一致。
     */
    private static String getPlatformSpecificDataPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                return localAppData + File.separator + "NeoLink";
            }
            return home + File.separator + "AppData" + File.separator + "Local" + File.separator + "NeoLink";
        }
        if (os.contains("mac")) {
            return home + "/Library/Application Support/NeoLink";
        }
        // Linux / 其他类 Unix 系统
        return home + File.separator + ".neolink";
    }
}
