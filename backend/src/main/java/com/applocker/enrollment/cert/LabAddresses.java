package com.applocker.enrollment.cert;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class LabAddresses {

    private LabAddresses() {
    }

    public static List<String> hosts(String publicBaseUrl) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add("localhost");
        hosts.add("127.0.0.1");
        add(hosts, hostName());
        add(hosts, hostOf(publicBaseUrl));
        addInterfaceAddresses(hosts);
        return List.copyOf(hosts);
    }

    private static void add(LinkedHashSet<String> hosts, String value) {
        if (value != null && !value.isBlank()) {
            hosts.add(value.trim());
        }
    }

    private static String hostName() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host == null || host.isBlank() || "127.0.0.1".equals(host)) {
                return null;
            }
            return host;
        } catch (Exception exception) {
            return null;
        }
    }

    private static String hostOf(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(publicBaseUrl.trim()).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static void addInterfaceAddresses(LinkedHashSet<String> hosts) {
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                var addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        hosts.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException exception) {
            hosts.add("127.0.0.1");
        }
    }
}
