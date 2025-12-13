package neoproxy.neolink;

import plethora.print.log.LogType;
import plethora.print.log.State;
import plethora.utils.StringUtils;

import static neoproxy.neolink.NeoLink.isGUIMode;
import static neoproxy.neolink.NeoLink.loggist;

public class Debugger {

    public static void debugOperation(Exception e) {
        if (NeoLink.isDebugMode) {
            String exceptionMsg = StringUtils.getExceptionMsg(e);
            if (!isGUIMode) {
                System.out.println("[DEBUG-EXCEPTION] " + exceptionMsg);
            }
            if (loggist != null) {
                loggist.write("[DEBUG-EXCEPTION] " + exceptionMsg, true);
            }
        }
    }

    public static void debugOperation(String infoMsg) {
        if (NeoLink.isDebugMode) {
            // Printing to System.out ensures visibility even if Loggist isn't ready yet
            if (!isGUIMode) {
                System.out.println("[DEBUG] " + infoMsg);
            }
            if (loggist != null) {
                // Using a specific format to distinguish debug logs in the file
                loggist.say(new State(LogType.INFO, "DEBUG", infoMsg));
            }
        }
    }
}