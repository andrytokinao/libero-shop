package com.houssen.liberoshop.server;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves {@code liberoshop.mobile.downloads-dir} under {@code /downloads/}.
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
                .setCacheControl(CacheControl.noCache());
    }
}
