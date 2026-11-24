package com.lifeline.battery.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/device/");
        for (String site : new String[]{"device", "dashboard"}) {
            registry.addRedirectViewController("/" + site, "/" + site + "/");
            registry.addViewController("/" + site + "/").setViewName("forward:/" + site + "/index.html");
        }
    }

    /** Adds an HTTP port that only this machine can reach, next to the public HTTPS port. */
    @Bean
    @ConditionalOnProperty("lifeline.loopback-http-port")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> loopbackHttp(LifelineProperties properties) {
        return factory -> {
            Connector connector = new Connector();
            connector.setPort(properties.loopbackHttpPort());
            connector.setProperty("address", "127.0.0.1");
            factory.addAdditionalConnectors(connector);
        };
    }
}
