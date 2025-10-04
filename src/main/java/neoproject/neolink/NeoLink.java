package neoproject.neolink;

import neoproject.neolink.threads.CheckAliveThread;
import neoproject.neolink.threads.Transformer;
import plethora.management.bufferedFile.SizeCalculator;
import plethora.net.NetworkUtils;
import plethora.net.SecureSocket;
import plethora.os.detect.OSDetector;
import plethora.os.windowsSystem.WindowsOperation;
import plethora.print.log.LogType;
import plethora.print.log.Loggist;
import plethora.print.log.State;
import plethora.thread.ThreadManager;
import plethora.time.Time;
import plethora.utils.Sleeper;
import plethora.utils.StringUtils;

import java.io.*;
import java.net.Socket;
import java.util.Locale;
import java.util.Scanner;

import static neoproject.neolink.InternetOperator.*;

public class NeoLink {
    public static int REMOTE_PORT;
    public static String REMOTE_DOMAIN_NAME = "127.0.0.1";
    public static String LOCAL_DOMAIN_NAME = "127.0.0.1";
    public static int HOST_HOOK_PORT = 801;
    public static int HOST_CONNECT_PORT = 802;
    public static int UPDATE_PORT = 803;
    public static final String CURRENT_DIR_PATH = System.getProperty("user.dir");
    public static SecureSocket hookSocket;
    public static String key = null;
    public static int localPort = -1;
    public static Loggist loggist;
    public static String OUTPUT_FILE_PATH = null;
    public static LanguageData languageData = new LanguageData();
    public static final String CLIENT_FILE_PREFIX = "NeoLink-";

    public static boolean IS_RECONNECTED_OPERATION = false;
    public static boolean IS_DEBUG_MODE = false;
    public static boolean ENABLE_AUTO_RECONNECT = true;
    public static int RECONNECTION_INTERVAL = 60;//s


    private static void initLoggist() {
        if (OUTPUT_FILE_PATH!=null){
            File logFile = new File(OUTPUT_FILE_PATH);
            if (!logFile.exists()){
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            Loggist l = new Loggist(logFile);
            l.openWriteChannel();
            NeoLink.loggist=l;
        }else {
            String currentDir = System.getProperty("user.dir");
            File logFile = new File(currentDir + File.separator + "logs" + File.separator + Time.getCurrentTimeAsFileName(false) + ".log");
            Loggist l = new Loggist(logFile);
            l.openWriteChannel();
            NeoLink.loggist=l;
        }
    }

    private static void checkARGS(String[] args) {
        for (String arg : args) {
            switch (arg) {
                case "--en-us" -> languageData = new LanguageData();
                case "--zh-ch" -> languageData = LanguageData.getChineseLanguage();
                case "--no-color" -> loggist.disableColor();
                case "--debug" -> IS_DEBUG_MODE = true;
            }
            if (arg.contains("=")) {
                String[] ele = arg.split("=");
                switch (ele[0]) {//--key=aaabbb --localPort=25565
                    case "--key" -> NeoLink.key = ele[1];
                    case "--local-port" -> NeoLink.localPort = Integer.parseInt(ele[1]);
                    case "--output-file" -> NeoLink.OUTPUT_FILE_PATH = ele[1];
                }
            }
        }
    }

    public static void main(String[] args) {
        checkARGS(args);

        initLoggist();

        ConfigOperator.readAndSetValue();
        ProxyOperator.init();

        printlnLogo();

        try {
            printBasicInfo();//检测语言，打印基本信息

            enterAccessCode();//告知用户输入key

            connectToNeo();//使用key连接NeoServer

            uploadAndReceiveMessages();//上传key和版本信息，并且获取响应，如果版本过旧旧获取新版本

            //通过验证，开始服务
            CheckAliveThread.startThread();

            enterLocalPort();//告知用户输入需要内网穿透的本地端口

            detectAndCatchNeoOperation();//持续检测Neo发来的指令或者消息，用于服务
        } catch (Exception e) {
            debugOperation(e);

            reconnectOperation();//连接失败后，尝试重新连接服务器
        }
    }

    private static void reconnectOperation() {
        say(languageData.FAIL_TO_BUILD_A_CHANNEL_FROM + REMOTE_DOMAIN_NAME, LogType.ERROR);
        if (ENABLE_AUTO_RECONNECT) {
            for (int i = 0; i < RECONNECTION_INTERVAL; i++) {
                languageData.sayReconnectMsg(RECONNECTION_INTERVAL - i);
                Sleeper.sleep(1000);//1s
            }
            NeoLink.IS_RECONNECTED_OPERATION = true;
            NeoLink.main(new String[]{key, String.valueOf(localPort)});
            System.exit(0);
        } else {
            exitAndFreeze(-1);
        }
    }

    private static void detectAndCatchNeoOperation() throws IOException {
        String msg;
        while ((msg = receiveStr()) != null) {
            try {
                checkInterruption();
            } catch (InterruptedException e) {
                say("隧道正在停止...");
                return;
            }
//            System.out.println("msg = " + msg);
            if (msg.startsWith(":>")) {
                msg = msg.substring(2);
                String[] ele = msg.split(";");
                if (ele[0].equals("sendSocket")) {//:>sendSocket;
                    new Thread(() -> NeoLink.createNewConnection(ele[1])).start();
                } else if (ele[0].equals("exit")) {
                    exitAndFreeze(0);
                } else {
                    NeoLink.REMOTE_PORT = Integer.parseInt(ele[0]);
                }
            } else if (msg.contains("This access code have") || msg.contains("消耗") || msg.contains("使用链接")) {
                loggist.say(new State(LogType.WARNING, "SERVER", msg));
            } else {
                say(msg);
            }
        }
    }

    private static void checkInterruption() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException("隧道被用户中断");
        }
    }

    private static void enterLocalPort() {
        if (localPort == -1) {
            sayInfoNoNewLine(languageData.ENTER_PORT_MSG);
            try {
                localPort = Integer.parseInt(NeoLink.inputStr());
                if (localPort < 1 || localPort > 65535) {
                    throw new IndexOutOfBoundsException();
                }
            } catch (IndexOutOfBoundsException e) {
                say(languageData.PORT_OUT_OF_RANGE_MSG, LogType.ERROR);
                exitAndFreeze(-1);
            } catch (NumberFormatException e) {
                say(languageData.IT_MUST_BE_INT, LogType.ERROR);
                exitAndFreeze(-1);
            }
        }
    }

    private static void connectToNeo() throws IOException {
        say(languageData.CONNECT_TO + REMOTE_DOMAIN_NAME + languageData.OMITTED);
        if (ProxyOperator.PROXY_IP_TO_NEO_SERVER != null) {
            hookSocket = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, HOST_HOOK_PORT);
        } else {
            hookSocket = new SecureSocket(REMOTE_DOMAIN_NAME, HOST_HOOK_PORT);
        }
    }

    private static void printlnLogo() {
        if (!IS_RECONNECTED_OPERATION) {
            say("""
                    
                       _____                                    \s
                      / ____|                                   \s
                     | |        ___   _ __    ___   __  __   ___\s
                     | |       / _ \\ | '__|  / _ \\  \\ \\/ /  / _ \\
                     | |____  |  __/ | |    | (_) |  >  <  |  __/
                      \\_____|  \\___| |_|     \\___/  /_/\\_\\  \\___|
                                                                \s
                                                                 \
                    """);
        }
    }

    private static void printBasicInfo() {
        if (!IS_RECONNECTED_OPERATION) {
            speakAnnouncement();
            say(languageData.VERSION + VersionInfo.VERSION);
        }
    }

    private static void enterAccessCode() {
        if (key == null) {
            sayInfoNoNewLine(languageData.PLEASE_ENTER_ACCESS_CODE);
            NeoLink.key = NeoLink.inputStr();
        }
    }

    private static void uploadAndReceiveMessages() throws IOException {
        // zh version key
        String clientInfo = NeoLink.formateClientInfoString(languageData, key);
        sendStr(clientInfo);
        String str = receiveStr();
//        System.out.println("str = " + str);
        if (str.contains("nsupported") || str.contains("不") || str.contains("旧")) {
            loggist.say(new State(LogType.ERROR, "SERVER", str));
            String versions = str.split(":")[1];
            String[] ver = versions.split("\\|");
            String version = ver[ver.length - 1];

            checkUpdate(CLIENT_FILE_PREFIX + version);//it will exit!

        } else if (str.contains("exit") || str.contains("退") || str.contains("错误") || str.contains("denied") || str.contains("already") || str.contains("过期") || str.contains("占")) {
            say(str);
            exitAndFreeze(0);
        } else {
            if (OSDetector.isWindows()) {
                int latency = NetworkUtils.getLatency(REMOTE_DOMAIN_NAME);
                if (latency == -1) {
                    loggist.say(new State(LogType.INFO, "SERVER", languageData.TOO_LONG_LATENCY_MSG));
                    loggist.say(new State(LogType.INFO, "SERVER", str));
                } else {
                    loggist.say(new State(LogType.INFO, "SERVER", str + " " + latency + "ms"));
                }
            } else {
                loggist.say(new State(LogType.INFO, "SERVER", str));
            }
        }
    }

    public static void checkUpdate(String fileName) {
        try {
            boolean isWindows = OSDetector.isWindows();

            File clientFile;
            if (isWindows) {
                clientFile = new File(System.getProperty("user.dir") + File.separator + fileName + ".exe");
            } else {
                clientFile = new File(System.getProperty("user.dir") + File.separator + fileName + ".jar");
            }

            if (clientFile.exists()) {
                if (isWindows) {
                    clientFile.renameTo(new File(clientFile.getParent() + File.separator + fileName + " - copy" + ".exe"));
                } else {
                    clientFile.renameTo(new File(clientFile.getParent() + File.separator + fileName + " - copy" + ".jar"));
                }
                clientFile.createNewFile();
            } else {
                clientFile.createNewFile();
            }

            Socket socket = new Socket(REMOTE_DOMAIN_NAME, UPDATE_PORT);
            BufferedInputStream clientBufferedInputStream = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream clientBufferedOutputStream = new BufferedOutputStream(socket.getOutputStream());
            BufferedOutputStream fileBufferedOutputStream = new BufferedOutputStream(new FileOutputStream(clientFile));

            if (isWindows) {
                clientBufferedOutputStream.write(0);
                clientBufferedOutputStream.flush();//告诉服务端是什么版本，0：exe，1：jar
            } else {
                clientBufferedOutputStream.write(1);
                clientBufferedOutputStream.flush();//告诉服务端是什么版本，0：exe，1：jar
            }

            sayInfoNoNewLine(languageData.START_TO_DOWNLOAD_UPDATE);

            byte[] data = new byte[(int) SizeCalculator.mibToByte(5)];
            int len;
            while ((len = clientBufferedInputStream.read(data)) != -1) {
                fileBufferedOutputStream.write(data, 0, len);
                fileBufferedOutputStream.flush();
                System.out.print(".");
            }
            fileBufferedOutputStream.close();
            clientBufferedInputStream.close();

            System.out.println();

            say(languageData.DOWNLOAD_SUCCESS);

            if (isWindows) {
                String command = "cmd.exe /c start \"" + clientFile.getName() + "\" " + "\"" + clientFile.getAbsolutePath() + "\"";
                if (key != null) {
                    command = command + " --key:" + key;
                }
                if (localPort != -1) {
                    command = command + " --local-port:" + localPort;
                }
                WindowsOperation.run(command);
                System.exit(0);
            } else {
                say(languageData.PLEASE_RUN + clientFile.getAbsolutePath());
            }

            exitAndFreeze(0);
        } catch (IOException e) {
            debugOperation(e);
            say("Fail to check updates.", LogType.ERROR);
            exitAndFreeze(0);
        }
    }

    private static String formateClientInfoString(LanguageData languageData, String key) {
        // zh;version;key;(https-mode->LOCAL_DOMAIN_NAME)
        return languageData.getCurrentLanguage() + ";" +
                VersionInfo.VERSION +
                ";" +
                key;
    }

    public static void detectLanguage() {
        Locale l = Locale.getDefault();
        if (l.getLanguage().contains("zh")) {
            NeoLink.languageData = LanguageData.getChineseLanguage();
            say("使用zh-ch作为备选语言");
        }
    }


    public static void exitAndFreeze(int exitCode) {
        say("Press enter to exit the program...");
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
        System.exit(exitCode);
    }


    private static void speakAnnouncement() {
        say(languageData.IF_YOU_SEE_EULA);
        VersionInfo.outPutEula();
    }

    public static String inputStr() {
        Scanner scanner = new Scanner(System.in);
        return scanner.nextLine();
    }

    public static void createNewConnection(String remoteAddress) {
        try {
            Socket server;

            if (ProxyOperator.PROXY_IP_TO_LOCAL_SERVER != null) {
                server = ProxyOperator.getHandledSocket(ProxyOperator.Type.TO_LOCAL, localPort);
            } else {
                server = new Socket(LOCAL_DOMAIN_NAME, localPort);
            }

            SecureSocket transferChannelServer;
            if (ProxyOperator.PROXY_IP_TO_NEO_SERVER != null) {
                transferChannelServer = ProxyOperator.getHandledSecureSocket(ProxyOperator.Type.TO_NEO, HOST_CONNECT_PORT);
            } else {
                transferChannelServer = new SecureSocket(REMOTE_DOMAIN_NAME, HOST_CONNECT_PORT);
            }

            transferChannelServer.sendInt(REMOTE_PORT);

            say(languageData.A_CONNECTION + remoteAddress + " -> " + LOCAL_DOMAIN_NAME + ":" + localPort + languageData.BUILD_UP);

            Transformer serverToTransferChannelServerThread = new Transformer(server, transferChannelServer);
            Transformer transferChannelServerToServerThread = new Transformer(transferChannelServer, server);
            ThreadManager threadManager = new ThreadManager(serverToTransferChannelServerThread, transferChannelServerToServerThread);
            threadManager.startAll();

            closeSocket(server);
            closeSocket(transferChannelServer);

            say(languageData.A_CONNECTION + remoteAddress + " -> " + LOCAL_DOMAIN_NAME + ":" + localPort + languageData.DESTROY);

        } catch (Exception e) {
            debugOperation(e);
            say(languageData.FAIL_TO_CONNECT_LOCALHOST + localPort, LogType.ERROR);
            System.gc();
        }
    }

    public static void sayInfoNoNewLine(String str) {
        loggist.sayNoNewLine(new State(LogType.INFO, "HOST-CLIENT", str));
    }


    public static void debugOperation(Exception e) {
        if (IS_DEBUG_MODE) {
            String exceptionMsg = StringUtils.getExceptionMsg(e);
            System.out.println(exceptionMsg);
            loggist.write(exceptionMsg, true);
        }
    }

    public static void say(String str) {
        loggist.say(new State(LogType.INFO, "HOST-CLIENT", str));
    }

    public static void say(String str, LogType logType) {
        loggist.say(new State(logType, "HOST-CLIENT", str));
    }
}
