package com.houssen.liberoshop.printer.service;


import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.printer.driver.SystemPrinters;
import com.houssen.liberoshop.printer.dto.DiscoveredPrinter;
import com.houssen.liberoshop.printer.dto.DiscoverySource;
import com.houssen.liberoshop.repository.PrinterConfigRepository;
import org.springframework.stereotype.Service;

import javax.print.PrintService;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Détecte les imprimantes installées dans l'OS et, sur demande, celles du réseau local (TCP 9100). */
@Service
public class PrinterDiscoveryService {

    private static final int RAW_PORT = 9100;
    private static final int SCAN_TIMEOUT_MS = 400;
    private static final Pattern SUBNET = Pattern.compile("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$");
    private static final Pattern THERMAL = Pattern.compile(
            "(?i)(\\bpos\\b|receipt|ticket|thermal|\\btm-|\\bxp-|\\bstar\\b|bixolon|rongta|gprinter|"
                    + "\\b58\\s?mm|\\b80\\s?mm|esc/?pos)");

    private final PrinterConfigRepository repository;

    public PrinterDiscoveryService(PrinterConfigRepository repository) {
        this.repository = repository;
    }

    /** Toutes les imprimantes vues par l'OS, quel que soit leur nombre. */
    public List<DiscoveredPrinter> discoverSystem() {
        Set<String> known = registeredKeys();
        String osDefault = SystemPrinters.defaultService().map(PrintService::getName).orElse(null);
        return SystemPrinters.all().stream()
                .map(ps -> {
                    PrinterType suggested = THERMAL.matcher(ps.getName()).find()
                            ? PrinterType.ESCPOS_RAW_SYSTEM : PrinterType.SYSTEM;
                    boolean registered = known.contains(key(PrinterType.SYSTEM, ps.getName()))
                            || known.contains(key(PrinterType.ESCPOS_RAW_SYSTEM, ps.getName()));
                    return new DiscoveredPrinter(ps.getName(), DiscoverySource.SYSTEM, ps.getName(), null,
                            suggested, ps.getName().equals(osDefault), registered);
                })
                .sorted(Comparator.comparing(DiscoveredPrinter::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Scanne les réseaux locaux /24 à la recherche d'imprimantes ESC/POS (port 9100 ouvert).
     *
     * @param subnetPrefix ex. {@code 192.168.1} ; null pour détecter automatiquement les réseaux de la machine
     */
    public List<DiscoveredPrinter> discoverNetwork(String subnetPrefix) {
        List<String> prefixes = subnetPrefix == null || subnetPrefix.isBlank()
                ? localPrefixes() : List.of(validatePrefix(subnetPrefix.trim()));
        Set<String> known = registeredKeys();
        List<DiscoveredPrinter> found = new CopyOnWriteArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String prefix : prefixes) {
                for (int host = 1; host <= 254; host++) {
                    String ip = prefix + "." + host;
                    executor.submit(() -> {
                        if (isOpen(ip)) {
                            found.add(new DiscoveredPrinter("Imprimante réseau " + ip, DiscoverySource.NETWORK, ip,
                                    RAW_PORT, PrinterType.ESCPOS_NET, false,
                                    known.contains(key(PrinterType.ESCPOS_NET, ip))));
                        }
                    });
                }
            }
        }
        return found.stream().sorted(Comparator.comparingLong(d -> ipToLong(d.address()))).toList();
    }

    private Set<String> registeredKeys() {
        Set<String> keys = new HashSet<>();
        for (PrinterConfig c : repository.findAll()) {
            keys.add(key(c.getType(), c.getAddress()));
        }
        return keys;
    }

    private static String key(PrinterType type, String address) {
        return type + "|" + address.toLowerCase(Locale.ROOT);
    }

    private static boolean isOpen(String ip) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, RAW_PORT), SCAN_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static String validatePrefix(String prefix) {
        if (!SUBNET.matcher(prefix).matches()) {
            throw new IllegalArgumentException("Sous-réseau invalide (attendu : 192.168.1) : " + prefix);
        }
        return prefix;
    }

    private static List<String> localPrefixes() {
        List<String> prefixes = new ArrayList<>();
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return prefixes;
            }
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    if (ia.getAddress() instanceof Inet4Address a && a.isSiteLocalAddress()) {
                        String[] p = a.getHostAddress().split("\\.");
                        String prefix = p[0] + "." + p[1] + "." + p[2];
                        if (!prefixes.contains(prefix)) {
                            prefixes.add(prefix);
                        }
                    }
                }
            }
        } catch (SocketException ignored) {
            // aucune interface exploitable : liste vide
        }
        return prefixes;
    }

    private static long ipToLong(String ip) {
        long value = 0;
        for (String part : ip.split("\\.")) {
            value = (value << 8) | Integer.parseInt(part);
        }
        return value;
    }
}
