package com.houssen.liberoshop.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.HttpResource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Serves the Angular build that Maven copies into {@code classpath:/static}.
 *
 * <p>Two kinds of request reach here. A file -- {@code main-XXXX.js}, {@code styles-XXXX.css},
 * the favicon -- is served as is, or answers 404 when it does not exist. Anything else is a
 * route the Angular router owns ({@code /admin/licence}, {@code /caisse/nouvelle-vente}): the
 * server has no page there, so it answers {@code index.html} and the router takes over.
 * Without that, reloading the browser on any screen but the first would be a 404.
 *
 * <p>{@code /api} and {@code /ws} are never answered with the page: an unknown API path must
 * stay a 404 the client can read, not an HTML page it would try to parse as JSON.
 *
 * <p>The bundles' names carry a content hash, so they may be cached for long; index.html is
 * revalidated on each load, which is what makes a new version reach the cash desks.
 */
@Configuration
public class SpaWebConfiguration implements WebMvcConfigurer {

    private static final String STATIC = "classpath:/static/";
    private static final Resource INDEX = new IndexResource();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .resourceChain(true)
                .addResolver(new SpaResourceResolver());
    }

    private static final class SpaResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (resourcePath.equals("api") || resourcePath.startsWith("api/")
                    || resourcePath.equals("ws") || resourcePath.startsWith("ws/")) {
                return null;
            }
            Resource requested = location.createRelative(resourcePath);
            if (!resourcePath.isEmpty() && requested.exists() && requested.isReadable()
                    && !resourcePath.equals("index.html")) {
                return requested;
            }
            // A missing file keeps its 404; only extension-less paths are app routes.
            boolean isRoute = !resourcePath.substring(resourcePath.lastIndexOf('/') + 1).contains(".");
            return (isRoute || resourcePath.equals("index.html")) && INDEX.exists() ? INDEX : null;
        }
    }

    /**
     * index.html with its own Cache-Control: the handler's one-year header is replaced by
     * this one, because {@code ResourceHttpRequestHandler} applies an {@link HttpResource}'s
     * headers last.
     */
    private static final class IndexResource extends ClassPathResource implements HttpResource {

        IndexResource() {
            super("static/index.html");
        }

        @Override
        public HttpHeaders getResponseHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl(CacheControl.noCache());
            return headers;
        }
    }
}
