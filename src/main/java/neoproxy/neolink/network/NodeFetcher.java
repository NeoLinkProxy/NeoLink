package neoproxy.neolink.network;

import fun.ceroxe.api.print.log.LogType;
import neoproxy.neolink.config.ConfigOperator;
import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.core.NeoLink;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 节点列表获取器
 *
 * 核心职责：
 * 1. 从 NKM（Neo Key Management）服务器获取最新可用节点列表
 * 2. 将获取的节点配置保存到本地 node.json 文件
 * 3. 防止并发重复请求（使用原子锁机制）
 *
 * 设计特点：
 * - 异步执行，不阻塞主线程
 * - 严格的超时控制（连接和读取各 1 秒）
 * - 失败静默处理，不影响现有配置
 * - 并发安全，防止重复请求
 *
 * @author NeoProxy Team
 * @since 5.11.0
 */
public class NodeFetcher {

    /**
     * 原子锁，用于防止并发重复请求节点列表
     *
     * 当多个线程同时尝试获取节点列表时（如 GUI 启动时），
     * 只有第一个线程会执行实际请求，其他线程直接返回
     */
    private static final AtomicBoolean isFetching = new AtomicBoolean(false);

    public static void fetchAndSaveNodes() {
        // 使用 CAS 操作确保只有一个线程能执行获取操作
        if (!isFetching.compareAndSet(false, true)) {
            return; // 如果已有线程在执行，直接返回
        }

        try {
            doFetchAndSaveNodes();
        } finally {
            isFetching.set(false); // 确保无论成功或失败都释放锁
        }
    }

    private static void doFetchAndSaveNodes() {
        if (NeoLink.languageData == null) {
            NeoLink.detectLanguage();
        }

        // 直接从 NeoLink 内存获取刚刚 ConfigOperator 读进来的 URL
        String urlStr = NeoLink.nkmNodeListUrl;

        // 如果用户在 config.cfg 里没配这行，就保持绝对静默，不打扰原来的体验
        if (urlStr == null || urlStr.isBlank()) {
            return;
        }

        NeoLink.say(NeoLink.languageData.FETCHING_NODE_LIST + urlStr, LogType.INFO);

        try {
            URL url = new URL(urlStr.trim());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            // 【核心规则】严格 1000ms 超时限制
            con.setConnectTimeout(1000);
            con.setReadTimeout(1000);

            int responseCode = con.getResponseCode();

            if (responseCode == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String json = response.toString();

                // 确保返回值是合法的 JSON 数组结构
                if (json.trim().startsWith("[") && json.trim().endsWith("]")) {
                    File nodeFile = new File(ConfigOperator.WORKING_DIR, "node.json");
                    Files.writeString(nodeFile.toPath(), json, StandardCharsets.UTF_8);
                    NeoLink.say(NeoLink.languageData.NODE_LIST_FETCH_SUCCESS, LogType.INFO);
                } else {
                    NeoLink.say(NeoLink.languageData.NODE_LIST_INVALID_JSON, LogType.WARNING);
                }
            } else {
                throw new RuntimeException("HTTP Status " + responseCode);
            }
        } catch (Exception e) {
            // 发生任何异常：直接跳过，不修改本地配置，仅打印警告
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            NeoLink.say(NeoLink.languageData.NODE_LIST_FETCH_FAIL + msg, LogType.WARNING);
        }
    }
}
