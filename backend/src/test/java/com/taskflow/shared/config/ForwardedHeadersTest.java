package com.taskflow.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.server.WebServer;
import jakarta.servlet.http.*;

class ForwardedHeadersTest {
    @Test void nativeTomcatUsesSanitizedProxyHeaders() {
        verify("native", "https|true|443|taskflow.example.com|198.51.100.8");
    }
    @Test void localDoesNotPromoteClientForwardingHeaders() { verify("none", null); }

    private void verify(String strategy, String expected) {
        new ApplicationContextRunner(AnnotationConfigServletWebServerApplicationContext::new)
            // Include Boot's Tomcat customizer, as in the real application: it installs
            // the native RemoteIpValve from the production forwarding properties.
            .withConfiguration(AutoConfigurations.of(ServletWebServerFactoryAutoConfiguration.class,
                EmbeddedWebServerFactoryCustomizerAutoConfiguration.class))
            .withPropertyValues("server.port=0", "server.address=127.0.0.1",
                "server.forward-headers-strategy=" + strategy,
                "server.tomcat.remoteip.host-header=X-Forwarded-Host",
                "server.tomcat.remoteip.port-header=X-Forwarded-Port")
            .withBean(ServletRegistrationBean.class, () -> new ServletRegistrationBean<>(new HttpServlet() {
                @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
                        throws java.io.IOException {
                    response.getWriter().write(request.getScheme() + "|" + request.isSecure() + "|"
                        + request.getServerPort() + "|" + request.getServerName() + "|" + request.getRemoteAddr());
                }
            }, "/*"))
            .run(context -> {
                WebServer server = ((AnnotationConfigServletWebServerApplicationContext)
                    context.getSourceApplicationContext()).getWebServer();
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getPort() + "/"))
                    .header("X-Forwarded-Proto", "https").header("X-Forwarded-Port", "443")
                    .header("X-Forwarded-Host", "taskflow.example.com")
                    .header("X-Forwarded-For", "198.51.100.8").build();
                String body = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body();
                assertThat(body).isEqualTo(expected == null
                    ? "http|false|" + server.getPort() + "|127.0.0.1|127.0.0.1" : expected);
            });
    }
}
