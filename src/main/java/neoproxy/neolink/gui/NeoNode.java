package neoproxy.neolink.gui;

public class NeoNode {
    private String name;
    private String address;
    private String iconSvg;
    private int hookPort;
    private int connectPort;

    public NeoNode(String name, String address, String iconSvg, int hookPort, int connectPort) {
        this.name = name;
        this.address = address;
        this.iconSvg = iconSvg;
        this.hookPort = hookPort;
        this.connectPort = connectPort;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getIconSvg() {
        return iconSvg;
    }

    public int getHookPort() {
        return hookPort;
    }

    public int getConnectPort() {
        return connectPort;
    }
}