package neoproxy.neolink.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 轻量级行配置解析器。
 * <p>
 * 替代仅 JVM 的 {@code top.ceroxe.api.utils.config.LineConfigReader}，
 * 使 common 模块不依赖该核心库。
 * <p>
 * 文件格式：每行 {@code KEY=VALUE}，忽略空行和 {@code #} 开头的注释行。
 * 等号左侧去空白作为 key，右侧去空白作为 value。
 */
public final class LineConfigParser {

    private final File file;
    private final Map<String, String> entries = new LinkedHashMap<>();

    public LineConfigParser(File file) {
        this.file = file;
    }

    /**
     * 解析配置文件并缓存键值对。
     *
     * @throws IOException 文件读取失败时抛出
     */
    public void load() throws IOException {
        entries.clear();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separatorIndex = trimmed.indexOf('=');
                if (separatorIndex < 0) {
                    continue;
                }
                String key = trimmed.substring(0, separatorIndex).trim();
                String value = trimmed.substring(separatorIndex + 1).trim();
                if (!key.isEmpty()) {
                    entries.put(key, value);
                }
            }
        }
    }

    /**
     * 获取配置值（可选）。
     *
     * @param key 配置键
     * @return 非空/非空白值包装在 Optional 中；不存在或空白时返回 empty
     */
    public Optional<String> getOptional(String key) {
        String value = entries.get(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
