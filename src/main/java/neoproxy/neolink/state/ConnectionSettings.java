package neoproxy.neolink.state;

public record ConnectionSettings(
        String remoteDomainName,
        String localDomainName,
        int hostHookPort,
        int hostConnectPort,
        String key,
        int localPort,
        String specifiedNodeName
) {
}
