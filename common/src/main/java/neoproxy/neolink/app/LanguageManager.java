package neoproxy.neolink.app;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.state.RuntimeState;

import java.util.Locale;

/**
 * 语言决策器。
 *
 * <p>只负责根据系统 Locale 与运行态缓存选择 LanguageData，不关心日志、命令行或 tunnel
 * 生命周期，从而让国际化与本地化决策与入口编排解耦。</p>
 */
public final class LanguageManager {

    private LanguageManager() {
    }

    public static void detectLanguage() {
        if (RuntimeState.languageData() != null) {
            return;
        }
        Locale defaultLocale = Locale.getDefault();
        RuntimeState.setLanguageData(defaultLocale.getLanguage().contains("zh")
                ? LanguageData.getChineseLanguage()
                : new LanguageData());
    }
}
