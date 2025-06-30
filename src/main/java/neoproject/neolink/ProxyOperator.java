package neoproject.neolink;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.*;

import static neoproject.neolink.NeoLink.LOCAL_DOMAIN_NAME;
import static neoproject.neolink.NeoLink.REMOTE_DOMAIN_NAME;

public class ProxyOperator {
    public static String PROXY_IP_TO_LOCAL_SERVER = null;
    private static Proxy.Type PROXY_IP_TO_LOCAL_SERVER_TYPE = null;
    private static String PROXY_IP_TO_LOCAL_SERVER_IP = null;
    private static int PROXY_IP_TO_LOCAL_SERVER_PORT;
    private static String PROXY_IP_TO_LOCAL_SERVER_USERNAME = null;
    private static String PROXY_IP_TO_LOCAL_SERVER_PASSWORD = null;

    public static String PROXY_IP_TO_NEO_SERVER = null;
    private static Proxy.Type PROXY_IP_TO_NEO_SERVER_TYPE = null;
    private static String PROXY_IP_TO_NEO_SERVER_IP = null;
    private static int PROXY_IP_TO_NEO_SERVE_PORT;
    private static String PROXY_IP_TO_NEO_SERVER_USERNAME = null;
    private static String PROXY_IP_TO_NEO_SERVER_PASSWORD = null;

    public static void init() {
        if (PROXY_IP_TO_LOCAL_SERVER != null) {
            //socks->127.0.0.1:7890@Ceroxe;123456
            String[] typeAndProperty = PROXY_IP_TO_LOCAL_SERVER.split("->");
            if (typeAndProperty[0].equals("socks")) {
                PROXY_IP_TO_LOCAL_SERVER_TYPE = Proxy.Type.SOCKS;
            } else if (typeAndProperty[0].equals("http")) {
                PROXY_IP_TO_LOCAL_SERVER_TYPE = Proxy.Type.HTTP;
            } else {
                PROXY_IP_TO_LOCAL_SERVER_TYPE = Proxy.Type.DIRECT;
            }
            String[] ele = typeAndProperty[1].split("@");
            String[] ipAndPort = ele[0].split(":");
            PROXY_IP_TO_LOCAL_SERVER_IP = ipAndPort[0];
            PROXY_IP_TO_LOCAL_SERVER_PORT = Integer.parseInt(ipAndPort[1]);
            if (ele.length != 1) {
                String[] usernameAndPassword = ele[1].split(";");
                PROXY_IP_TO_LOCAL_SERVER_USERNAME = usernameAndPassword[0];
                PROXY_IP_TO_LOCAL_SERVER_PASSWORD = usernameAndPassword[1];
            }
        }


        if (PROXY_IP_TO_NEO_SERVER != null) {
            String[] typeAndProperty = PROXY_IP_TO_NEO_SERVER.split("->");
            if (typeAndProperty[0].equals("socks")) {
                PROXY_IP_TO_NEO_SERVER_TYPE = Proxy.Type.SOCKS;
            } else if (typeAndProperty[0].equals("http")) {
                PROXY_IP_TO_NEO_SERVER_TYPE = Proxy.Type.HTTP;
            } else {
                PROXY_IP_TO_NEO_SERVER_TYPE = Proxy.Type.DIRECT;
            }
            String[] ele2 = typeAndProperty[1].split("@");
            String[] ipAndPort2 = ele2[0].split(":");
            PROXY_IP_TO_NEO_SERVER_IP = ipAndPort2[0];
            PROXY_IP_TO_NEO_SERVE_PORT = Integer.parseInt(ipAndPort2[1]);
            if (ele2.length != 1) {
                String[] usernameAndPassword2 = ele2[1].split(";");
                PROXY_IP_TO_NEO_SERVER_USERNAME = usernameAndPassword2[0];
                PROXY_IP_TO_NEO_SERVER_PASSWORD = usernameAndPassword2[1];
            }
        }

    }

    public synchronized static Socket getHandledSocket(int socketType, int targetPort) throws IOException {
        Socket targetSocket;
        if (socketType == Type.TO_NEO) {
            if (PROXY_IP_TO_NEO_SERVER_USERNAME != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(PROXY_IP_TO_NEO_SERVER_USERNAME, PROXY_IP_TO_NEO_SERVER_PASSWORD.toCharArray());
                    }
                });
            }
            Proxy proxy = new Proxy(PROXY_IP_TO_NEO_SERVER_TYPE, new InetSocketAddress(PROXY_IP_TO_NEO_SERVER_IP, PROXY_IP_TO_NEO_SERVE_PORT));
            targetSocket = new Socket(proxy);
            targetSocket.connect(new InetSocketAddress(REMOTE_DOMAIN_NAME, targetPort));
        } else {
            if (PROXY_IP_TO_LOCAL_SERVER_USERNAME != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(PROXY_IP_TO_LOCAL_SERVER_USERNAME, PROXY_IP_TO_LOCAL_SERVER_PASSWORD.toCharArray());
                    }
                });
            }
            Proxy proxy = new Proxy(PROXY_IP_TO_LOCAL_SERVER_TYPE, new InetSocketAddress(PROXY_IP_TO_LOCAL_SERVER_IP, PROXY_IP_TO_LOCAL_SERVER_PORT));
            targetSocket = new Socket(proxy);
            targetSocket.connect(new InetSocketAddress(LOCAL_DOMAIN_NAME, targetPort));
        }
        return targetSocket;


    }

    public static class Type {
        public static final int TO_NEO = 0;
        public static final int TO_LOCAL = 1;
    }
}
