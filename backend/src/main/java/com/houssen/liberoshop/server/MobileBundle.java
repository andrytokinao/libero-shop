package com.houssen.liberoshop.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * The mobile pages this server offers the phones: read from a {@link MobilePagesSource}, packed by
 * the {@link MobileBundlePackager}, once, on first request, then kept -- the pages cannot change
 * under a running server.
 *
 * <p>A facade over the two, for {@link MobileUpdateController}: it neither reads nor zips, it
 * decides when and keeps the result.
 */
@Component
public class MobileBundle {

    private static final Logger log = LoggerFactory.getLogger(MobileBundle.class);

    private final MobilePagesSource source;
    private final MobileBundlePackager packager;
    private volatile Optional<MobileBundlePackage> packaged;

    public MobileBundle(MobilePagesSource source, MobileBundlePackager packager) {
        this.source = source;
        this.packager = packager;
    }

    /** Empty when this server carries no mobile pages: the phones then keep their own. */
    public Optional<MobileBundlePackage> current() {
        Optional<MobileBundlePackage> result = packaged;
        if (result == null) {
            synchronized (this) {
                result = packaged;
                if (result == null) {
                    result = pack();
                    packaged = result;
                }
            }
        }
        return result;
    }

    private Optional<MobileBundlePackage> pack() {
        MobilePages pages;
        try {
            pages = source.read();
        } catch (IOException e) {
            log.warn("Pages mobiles illisibles : mise a jour automatique desactivee. {}", e.getMessage());
            return Optional.empty();
        }
        if (!pages.isComplete()) {
            log.info("Aucune page mobile sur ce serveur : mise a jour automatique de l'application desactivee.");
            return Optional.empty();
        }
        MobileBundlePackage bundle = packager.pack(pages);
        log.info("Pages mobiles {} pretes pour la mise a jour des telephones ({} fichiers, {} Ko, APK {} minimum).",
                bundle.version(), bundle.fileCount(), bundle.zip().length / 1024, bundle.minNativeBuild());
        return Optional.of(bundle);
    }
}
