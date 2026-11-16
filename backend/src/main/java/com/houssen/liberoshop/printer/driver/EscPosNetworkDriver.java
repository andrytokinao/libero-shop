package com.houssen.liberoshop.printer.driver;

import com.houssen.liberoshop.entity.PayloadKind;
import com.houssen.liberoshop.entity.PrintJob;
import com.houssen.liberoshop.entity.PrinterConfig;
import com.houssen.liberoshop.entity.PrinterType;
import com.houssen.liberoshop.printer.exception.PrintException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.EnumSet;
import java.util.Set;

/** Imprimante ticket réseau (Ethernet / Wi-Fi) : ESC/POS brut sur TCP 9100. Aucun pilote requis. */
@Component
public class EscPosNetworkDriver implements PrinterDriver {

    private static final int DEFAULT_PORT = 9100;
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int PROBE_TIMEOUT_MS = 800;
    private static final int WRITE_TIMEOUT_MS = 8000;

    @Override
    public PrinterType type() {
        return PrinterType.ESCPOS_NET;
    }

    @Override
    public Set<PayloadKind> supportedKinds() {
        return EnumSet.of(PayloadKind.ESCPOS);
    }

    @Override
    public void print(PrinterConfig cfg, PrintJob job) {
        try (Socket socket = new Socket()) {
            socket.connect(address(cfg), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(WRITE_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(job.payload());
            out.flush();
            socket.shutdownOutput();
        } catch (IOException e) {
            throw new PrintException("Imprimante injoignable : " + cfg.getAddress() + ":" + port(cfg), e);
        }
    }

    @Override
    public boolean isReachable(PrinterConfig cfg) {
        try (Socket socket = new Socket()) {
            socket.connect(address(cfg), PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static InetSocketAddress address(PrinterConfig cfg) {
        return new InetSocketAddress(cfg.getAddress(), port(cfg));
    }

    private static int port(PrinterConfig cfg) {
        return cfg.getPort() != null && cfg.getPort() > 0 ? cfg.getPort() : DEFAULT_PORT;
    }
}
