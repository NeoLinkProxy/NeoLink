package neoproxy.neolink.app;

import neoproxy.neolink.config.LanguageData;
import neoproxy.neolink.state.RuntimeState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("LanguageManagerTest")
class LanguageManagerTest {
    private Locale originalLocale;
    private LanguageData originalLanguage;

    @BeforeEach
    void setUp() {
        originalLocale = Locale.getDefault();
        originalLanguage = RuntimeState.languageData();
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(originalLocale);
        RuntimeState.setLanguageData(originalLanguage);
    }

    @Test
    @DisplayName("中文环境识别正确 / detects chinese locale")
    void detectsChineseLocale() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        RuntimeState.setLanguageData(null);

        LanguageManager.detectLanguage();

        assertNotNull(RuntimeState.languageData());
        assertEquals("zh", RuntimeState.languageData().getCurrentLanguage());
    }

    @Test
    @DisplayName("已设置语言不被覆盖 / existing language is preserved")
    void existingLanguageIsPreserved() {
        RuntimeState.setLanguageData(LanguageData.getChineseLanguage());
        Locale.setDefault(Locale.US);

        LanguageManager.detectLanguage();

        assertEquals("zh", RuntimeState.languageData().getCurrentLanguage());
    }
}
