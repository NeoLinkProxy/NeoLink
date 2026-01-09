package neoproxy.neolink;

import fun.ceroxe.api.print.log.LogType;
import fun.ceroxe.api.print.log.State;

import java.io.PrintWriter;
import java.io.StringWriter;

import static neoproxy.neolink.NeoLink.isGUIMode;
import static neoproxy.neolink.NeoLink.loggist;

public class Debugger {

    public static void debugOperation(Exception e) {
        if (NeoLink.isDebugMode) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String fullStackTrace = sw.toString();

            if (loggist != null) {
                // Loggist 已初始化：委托给它处理
                // CLI: 输出到控制台 + 文件
                // GUI: 输出到 WebView + 文件
                loggist.say(new State(LogType.ERROR, "DEBUG", fullStackTrace));
            } else {
                // Loggist 未初始化（如启动参数解析阶段）：手动输出到控制台
                if (!isGUIMode) {
                    System.err.println("[DEBUG-EXCEPTION] " + fullStackTrace);
                }
            }
        }
    }

    public static void debugOperation(String infoMsg) {
        if (NeoLink.isDebugMode) {
            if (loggist != null) {
                // Loggist 已初始化：委托给它处理
                // CLI: 原生 Loggist 会自动 System.out.println，所以这里不需要手动 sout，否则会重复！
                // GUI: QueueBasedLoggist 会处理上屏和写文件
                loggist.say(new State(LogType.INFO, "DEBUG", infoMsg));
            } else {
                // Loggist 未初始化：手动输出
                if (!isGUIMode) {
                    System.out.println("[DEBUG] " + infoMsg);
                }
            }
        }
    }
}