package neoproxy.neolink.platform;

import neoproxy.neolink.config.WorkingDirectoryProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 桌面端工作目录提供者。
 *
 * <p>封装桌面平台（Windows / macOS / Linux）的标准数据目录探测策略。</p>
 *
 * <p>目录选择规则：
 * <ul>
 *   <li>Windows: {@code %LOCALAPPDATA%\NeoLink}（回退 {@code ~/AppData/Local/NeoLink}）</li>
 *   <li>macOS: {@code ~/Library/Application Support/NeoLink}</li>
 *   <li>Linux: {@code ~/.neolink}</li>
 * </ul>
 * </p>
 *
 * <p>注意：路径算法集中在 {@link WorkingDirectoryProvider#resolveDefaultDesktopWorkingDirectory()}。
 * 本类只负责 desktop 模块的显式接线与目录物化，避免 common 默认兼容路径和 desktop 显式实现漂移。</p>
 */
public final class DesktopDirectoryProvider implements WorkingDirectoryProvider {

    @Override
    public Path resolveWorkingDirectory() {
        Path workingDirectory = WorkingDirectoryProvider.resolveDefaultDesktopWorkingDirectory().toAbsolutePath();
        try {
            Files.createDirectories(workingDirectory);
            Files.createDirectories(workingDirectory.resolve("logs"));
            return workingDirectory;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create desktop working directory: " + workingDirectory, e);
        }
    }
}
