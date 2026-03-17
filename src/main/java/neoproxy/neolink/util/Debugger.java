package neoproxy.neolink.util;

import fun.ceroxe.api.print.log.LogType;
import fun.ceroxe.api.print.log.State;
import neoproxy.neolink.core.NeoLink;

import java.io.PrintWriter;
import java.io.StringWriter;

import static neoproxy.neolink.core.NeoLink.isGUIMode;
import static neoproxy.neolink.core.NeoLink.loggist;

/**
 * 调试器
 *
 * 核心职责：
 * 1. 在调试模式下输出详细的异常堆栈和调试信息
 * 2. 统一处理调试信息的输出方式（CLI/GUI 兼容）
 * 3. 提供安全的调试输出，避免空指针异常
 *
 * 设计特点：
 * - 仅在 isDebugMode 为 true 时输出信息
 * - 自动适配 CLI 和 GUI 模式的输出方式
 * - Loggist 未初始化时回退到控制台输出
 *
 * @author NeoProxy Team
 * @since 5.0.0
 */
public class Debugger {

    public static void debugOperation(Exception e) {
        if (NeoLink.isDebugMode && e != null) {
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