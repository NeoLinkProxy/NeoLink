package neoproject.neolink;

import neoproject.neolink.threads.CheckAliveThread;
import neoproject.neolink.threads.Transformer;
import plethora.management.bufferedFile.BufferedFile;
import plethora.utils.config.LineConfigReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static neoproject.neolink.NeoLink.detectLanguage;
import static neoproject.neolink.NeoLink.say;


public class ConfigOperator {
    public static final BufferedFile CONFIG_FILE = new BufferedFile(NeoLink.CURRENT_DIR_PATH + File.separator + "config.cfg");

    private ConfigOperator() {
    }

    public static void readAndSetValue() {
        LineConfigReader lineConfigReader = new LineConfigReader(CONFIG_FILE);

        if (!CONFIG_FILE.exists()) {
            createAndSetDefaultConfig();
        } else {
            try {
                lineConfigReader.load();

                NeoLink.REMOTE_DOMAIN_NAME = lineConfigReader.get("REMOTE_DOMAIN_NAME");
                NeoLink.LOCAL_DOMAIN_NAME = lineConfigReader.get("LOCAL_DOMAIN_NAME");
                NeoLink.HOST_HOOK_PORT = Integer.parseInt(lineConfigReader.get("HOST_HOOK_PORT"));
                NeoLink.HOST_CONNECT_PORT = Integer.parseInt(lineConfigReader.get("HOST_CONNECT_PORT"));
                NeoLink.UPDATE_PORT = Integer.parseInt(lineConfigReader.get("UPDATE_PORT"));
                NeoLink.ENABLE_AUTO_RECONNECT = Boolean.parseBoolean(lineConfigReader.get("ENABLE_AUTO_RECONNECT"));
                NeoLink.RECONNECTION_INTERVAL = Integer.parseInt(lineConfigReader.get("RECONNECTION_INTERVAL"));
                ProxyOperator.PROXY_IP_TO_NEO_SERVER = lineConfigReader.get("PROXY_IP_TO_NEO_SERVER");
                ProxyOperator.PROXY_IP_TO_LOCAL_SERVER = lineConfigReader.get("PROXY_IP_TO_LOCAL_SERVER");
                AdminMgr.ADMIN_PORT = Integer.parseInt(lineConfigReader.get("ADMIN_PORT"));
                CheckAliveThread.HEARTBEAT_PACKET_DELAY = Integer.parseInt(lineConfigReader.get("HEARTBEAT_PACKET_DELAY"));
                Transformer.BUFFER_LEN = Integer.parseInt(lineConfigReader.get("BUFFER_LEN"));

            } catch (Exception e) {
                createAndSetDefaultConfig();
            }
        }
        detectLanguage();
    }

    private static void createAndSetDefaultConfig() {
        CONFIG_FILE.delete();
        CONFIG_FILE.createNewFile();

        try {
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(CONFIG_FILE, StandardCharsets.UTF_8));

            bufferedWriter.write("""
                    #把你要连接的NeoServer的域名或者公网ip放到这里来
                    #Put the domain name or public network ip of the NeoServer you want to connect to here
                    REMOTE_DOMAIN_NAME=127.0.0.1
                    
                    #如果你不知道以下的设置意味着什么，请你不要改变它
                    #If you don't know what the following setting means, please don't change it
                    LOCAL_DOMAIN_NAME=localhost
                    HOST_HOOK_PORT=801
                    HOST_CONNECT_PORT=802
                    UPDATE_PORT=803
                    ADMIN_PORT=945
                    
                    #设置用来连接本地服务器的代理服务器ip和端口，示例：socks->127.0.0.1:7890 如果需要登录则提供密码， 格式： ip:端口@用户名:密码   示例：socks->127.0.0.1:7890@Ceroxe;123456   如果不需要去请留空
                    #Set the proxy server IP address and port to connect to the on-premises server,Example: socks->127.0.0.1:7890 Provide password if login is required, Format: type->ip:port@username:password Example: socks->127.0.0.1:7890@Ceroxe;123456   If you don't need to go, leave it blank
                    PROXY_IP_TO_LOCAL_SERVER=
                    
                    #设置用来连接 NeoProxyServer 的代理服务器ip和端口，示例：socks->127.0.0.1:7890 如果需要登录则提供密码， 格式： ip:端口@用户名:密码   示例：socks->127.0.0.1:7890@Ceroxe;123456
                    #Set the proxy server IP address and port to connect to the NeoProxyServer,Example: socks->127.0.0.1:7890 Provide password if login is required, Format: type->ip:port@username:password Example: socks->127.0.0.1:7890@Ceroxe;123456   If you don't need to go, leave it blank
                    PROXY_IP_TO_NEO_SERVER=
                    
                    #设置发送心跳包的间隔，单位为毫秒
                    #Set the interval for sending heartbeat packets, in milliseconds
                    HEARTBEAT_PACKET_DELAY=1000
                    
                    #是否启用自动重连当服务端暂时离线的时候
                    #Whether to enable automatic reconnection when the server is temporarily offline
                    ENABLE_AUTO_RECONNECT=true
                    
                    #如果ENABLE_AUTO_RECONNECT设置为true，则将间隔多少秒后重连，单位为秒，且必须为大于0的整数
                    #If ENABLE_AUTO_RECONNECT is set to true, the number of seconds after which reconnection will be made in seconds and must be an integer greater than 0
                    RECONNECTION_INTERVAL=30
                    
                    #数据包数组的长度
                    #The length of the packet array
                    BUFFER_LEN=4096""");

            bufferedWriter.flush();
            bufferedWriter.close();

        } catch (IOException e) {
            say("Fail to write default config.");
            System.exit(-1);
        }

        readAndSetValue();
    }
}
