package neoproxy.neolink;

import fun.ceroxe.api.print.log.LogType;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class NodeFetcher {

    public static void fetchAndSaveNodes() {
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
