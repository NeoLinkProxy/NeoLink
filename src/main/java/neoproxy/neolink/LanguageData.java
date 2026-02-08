package neoproxy.neolink;

import java.io.Serializable;

import static neoproxy.neolink.NeoLink.say;

public class LanguageData implements Serializable {
    public String PLEASE_UPDATE_MANUALLY = "The current version is outdated, please manually download the update.";
    public String A_UDP_CONNECTION = "A UDP connection ";
    public String SERVER_IS_OFFLINE = "The server is offline.";
    public String IT_MUST_BE_INT = "This should be an integer.";
    public String PORT_OUT_OF_RANGE_MSG = "The input port range should be between 1~65535.";
    public String START_TO_DOWNLOAD_UPDATE = "Start downloading the update.";
    public String DOWNLOAD_SUCCESS = "The update is downloaded successfully !";
    public String PLEASE_RUN = "Please run ";
    public String IF_YOU_SEE_EULA = "If you use this software, you understand and agree with eula .";
    public String VERSION = "Version : ";
    public String PLEASE_ENTER_ACCESS_CODE = "Please enter the access code:";
    public String CONNECT_TO = "Connect to ";
    public String OMITTED = " ...";
    public String A_TCP_CONNECTION = "A TCP connection ";
    public String BUILD_UP = " build up";
    public String ENTER_PORT_MSG = "Enter the port for which you want to penetrate the intranet:";
    public String USE_THE_ADDRESS = "Use the address: ";
    public String TO_START_UP_CONNECTION = " to start up connections.";
    public String CONNECTION_BUILD_UP_SUCCESSFULLY = "Connection build up successfully";
    public String FAIL_TO_BUILD_A_CHANNEL_FROM = "Fail to build a channel from ";
    public String DESTROY = " destroyed";
    public String FAIL_TO_CONNECT_LOCALHOST = "Fail to connect to " + NeoLink.localDomainName + ":";
    public String TOO_LONG_LATENCY_MSG = "Delay greater than 200 milliseconds, please note!";
    public String LOAD = "Load ";
    public String AS_A_CERTIFICATE = " as a certificate";
    public String LISTEN_AT = "Listen at ";
    public String NO_FLOW_LEFT = "No extra network traffic left.";

    // UpdateManager类中的字符串
    public String FAILED_TO_DOWNLOAD_UPDATE_FILE = "Failed to download update file.";
    public String FAILED_TO_EXTRACT_7Z_FILE = "Failed to extract 7z file.";
    public String NEOLINK_EXE_NOT_FOUND = "NeoLink.exe not found in extracted files.";
    public String FAILED_TO_BACKUP_EXISTING_JAR = "Failed to backup existing jar file.";
    public String FAILED_TO_DELETE_EXISTING_JAR = "Failed to delete existing jar file.";
    public String FAILED_TO_CHECK_UPDATES = "Failed to check updates: ";
    public String UNEXPECTED_ERROR_DURING_UPDATE = "Unexpected error during update: ";
    public String INVALID_FILE_SIZE_RECEIVED = "Invalid file size received: ";
    public String DOWNLOADING_FILE_OF_SIZE = "Downloading file of size: ";
    public String CONNECTION_CLOSED_PREMATURELY = "Connection closed prematurely";
    public String DOWNLOAD_PROGRESS = "Download progress: ";
    public String FILE_SIZE_MISMATCH = "File size mismatch. Expected: ";
    public String ERROR_WHILE_DOWNLOADING_FILE = "Error while downloading file: ";
    public String ERROR_RECEIVING_FILE = "Error receiving file: ";
    public String SEVENZ_FILE_EXTRACTED_SUCCESSFULLY = "7z file extracted successfully to: ";
    public String FAILED_TO_CREATE_DIRECTORY = "Failed to create directory: ";
    public String EXECUTABLE_NOT_FOUND = "Executable file not found or is not a file: ";
    public String STARTING_NEW_VERSION = "Starting new version with command: ";
    public String NEW_VERSION_STARTED = "New version started successfully.";
    public String FAILED_TO_START_NEW_VERSION = "Failed to start new version: ";
    public String FAILED_TO_DELETE = "Failed to delete: ";
    public String SUCCESSFULLY_DELETED = "Successfully deleted: ";
    public String ERROR_DELETING_FILE = "Error deleting file: ";
    public String FILE_DOWNLOAD_COMPLETED = "File download completed successfully.";
    public String ERROR_CLOSING_7Z_FILE = "Error closing 7z file: ";
    public String WARNING_TCP_DISABLED = "TCP service is disabled !";
    public String WARNING_UDP_DISABLED = "UDP service is disabled !";

    private String currentLanguage = "en";

    public static LanguageData getChineseLanguage() {
        LanguageData languageData = new LanguageData();
        languageData.currentLanguage = "zh";
        languageData.SERVER_IS_OFFLINE = "服务端离线。";
        languageData.IT_MUST_BE_INT = "这应该为整数。";
        languageData.PORT_OUT_OF_RANGE_MSG = "输入的端口范围应在1~65535之间。";
        languageData.IF_YOU_SEE_EULA = "如果你已经开始使用的本软件，说明你已经知晓并同意了本软件的eula协议";
        languageData.VERSION = "版本 ： ";
        languageData.PLEASE_ENTER_ACCESS_CODE = "请输入密钥：";
        languageData.CONNECT_TO = "连接 ";
        languageData.OMITTED = " ...";
        languageData.A_TCP_CONNECTION = "一个 TCP 连接 ";
        languageData.A_UDP_CONNECTION = "一个 UDP 连接 ";
        languageData.BUILD_UP = " 的通道建立";
        languageData.ENTER_PORT_MSG = "请输入你想进行内网穿透的内网端口：";
        languageData.USE_THE_ADDRESS = "使用链接地址： ";
        languageData.TO_START_UP_CONNECTION = " 来从公网连接。";
        languageData.CONNECTION_BUILD_UP_SUCCESSFULLY = "服务器连接成功";
        languageData.FAIL_TO_BUILD_A_CHANNEL_FROM = "连接以下地址失败：";
        languageData.DESTROY = " 的通道关闭";
        languageData.FAIL_TO_CONNECT_LOCALHOST = "连接本地地址失败：" + NeoLink.localDomainName + ":";
        languageData.START_TO_DOWNLOAD_UPDATE = "开始下载更新。";
        languageData.DOWNLOAD_SUCCESS = "下载更新成功。";
        languageData.PLEASE_RUN = "请运行 ";
        languageData.TOO_LONG_LATENCY_MSG = "延迟大于200毫秒，请注意。";
        languageData.LOAD = "加载 ";
        languageData.AS_A_CERTIFICATE = " 作为证书。";
        languageData.LISTEN_AT = "监听端口： ";
        languageData.NO_FLOW_LEFT = "没有多余的流量了。";
        languageData.PLEASE_UPDATE_MANUALLY = "当前版本过旧，请手动下载更新。";

        // UpdateManager类中的字符串
        languageData.FAILED_TO_DOWNLOAD_UPDATE_FILE = "下载更新文件失败。";
        languageData.FAILED_TO_EXTRACT_7Z_FILE = "解压7z文件失败。";
        languageData.NEOLINK_EXE_NOT_FOUND = "在解压的文件中未找到NeoLink.exe。";
        languageData.FAILED_TO_BACKUP_EXISTING_JAR = "备份现有jar文件失败。";
        languageData.FAILED_TO_DELETE_EXISTING_JAR = "删除现有jar文件失败。";
        languageData.FAILED_TO_CHECK_UPDATES = "检查更新失败：";
        languageData.UNEXPECTED_ERROR_DURING_UPDATE = "更新过程中发生意外错误：";
        languageData.INVALID_FILE_SIZE_RECEIVED = "接收到无效的文件大小：";
        languageData.DOWNLOADING_FILE_OF_SIZE = "正在下载文件，大小：";
        languageData.CONNECTION_CLOSED_PREMATURELY = "连接意外关闭";
        languageData.DOWNLOAD_PROGRESS = "下载进度：";
        languageData.FILE_SIZE_MISMATCH = "文件大小不匹配。预期：";
        languageData.ERROR_WHILE_DOWNLOADING_FILE = "下载文件时出错：";
        languageData.ERROR_RECEIVING_FILE = "接收文件时出错：";
        languageData.SEVENZ_FILE_EXTRACTED_SUCCESSFULLY = "7z文件成功解压到：";
        languageData.FAILED_TO_CREATE_DIRECTORY = "创建目录失败：";
        languageData.EXECUTABLE_NOT_FOUND = "找不到可执行文件或不是文件：";
        languageData.STARTING_NEW_VERSION = "正在启动新版本，命令：";
        languageData.NEW_VERSION_STARTED = "新版本启动成功。";
        languageData.FAILED_TO_START_NEW_VERSION = "启动新版本失败：";
        languageData.FAILED_TO_DELETE = "删除失败：";
        languageData.SUCCESSFULLY_DELETED = "成功删除：";
        languageData.ERROR_DELETING_FILE = "删除文件时出错：";
        languageData.FILE_DOWNLOAD_COMPLETED = "文件下载成功完成。";
        languageData.ERROR_CLOSING_7Z_FILE = "关闭7z文件时出错：";
        languageData.WARNING_TCP_DISABLED = "TCP 服务已禁用！";
        languageData.WARNING_UDP_DISABLED = "UDP 服务已禁用！";

        return languageData;
    }

    public LanguageData flush() {
        if (currentLanguage.equals("zh")) {
            return getChineseLanguage();
        } else {
            return new LanguageData();
        }
    }

    public void sayReconnectMsg(int seconds) {
        if ("en".equals(currentLanguage)) {
            say("Reconnection will begin after " + seconds + " seconds.");
        } else {
            say(seconds + " 秒将会后开始重新连接");
        }
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }
}