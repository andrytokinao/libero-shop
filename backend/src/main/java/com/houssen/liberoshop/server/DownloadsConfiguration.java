package com.houssen.liberoshop.server;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the Android app, from {@code liberoshop.mobile.downloads-dir}, under {@code /downloads/}.
 *
 * <p>That one file and nothing else: the folder is open to anyone who can reach the server, and
 * whatever else ends up in it -- a backup of the database, a spreadsheet left there -- must not
 * become downloadable by being beside the APK.
 *
 * <p>More specific than the SPA's {@code /**}, so it is matched first, and a file that is not
 * there is a plain 404 -- never the Angular page, which a phone would save as "libero-shop.apk".
 *
 * <p>Revalidated on every download: the APK is replaced in place under the same name, and a
 * cached copy would hand the previous version to the next phone.
 *
 * <p>Open to anyone who can reach the server, as the web pages are: the app is useless without an
 * account, and asking for one before a phone can even install it would be a loop.
 */
@Configuration
@EnableConfigurationProperties(MobileProperties.class)
public class DownloadsConfiguration implements WebMvcConfigurer {

    public static final String PATH = "/downloads/";

    private final MobileProperties mobile;

    public DownloadsConfiguration(MobileProperties mobile) {
        this.mobile = mobile;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(PATH + "**")
                .addResourceLocations(mobile.downloadsDir().toAbsolutePath().toUri().toString())
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false)
                .addResolver(new OnlyTheApk(mobile.apkFile()));
    }

    /** Finds the published APK under its own name, and nothing under any other. */
    private static final class OnlyTheApk extends PathResourceResolver {

        private final String apkFile;

        private OnlyTheApk(String apkFile) {
            this.apkFile = apkFile;
        }

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            return apkFile.equals(resourcePath) ? super.getResource(resourcePath, location) : null;
        }
    }
}
