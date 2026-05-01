package neoproxy.neolink.state;

import neoproxy.neolink.config.LanguageData;
import top.ceroxe.api.print.log.Loggist;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public final class RuntimeState {
    private static volatile String tunnelAddress;
    private static volatile Loggist loggist;
    private static volatile LanguageData languageData;
    private static volatile boolean reconnectedOperation = false;
    private static volatile Scanner inputScanner = new Scanner(System.in, StandardCharsets.UTF_8);

    private RuntimeState() {
    }

    public static String tunnelAddress() {
        return tunnelAddress;
    }

    public static void setTunnelAddress(String value) {
        tunnelAddress = value;
    }

    public static Loggist loggist() {
        return loggist;
    }

    public static void setLoggist(Loggist value) {
        loggist = value;
    }

    public static LanguageData languageData() {
        return languageData;
    }

    public static void setLanguageData(LanguageData value) {
        languageData = value;
    }

    public static boolean isReconnectedOperation() {
        return reconnectedOperation;
    }

    public static void setReconnectedOperation(boolean value) {
        reconnectedOperation = value;
    }

    public static Scanner inputScanner() {
        return inputScanner;
    }

    public static void setInputScanner(Scanner value) {
        inputScanner = value;
    }
}
