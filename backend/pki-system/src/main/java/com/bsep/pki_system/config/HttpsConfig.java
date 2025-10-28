package com.bsep.pki_system.config;

import com.bsep.pki_system.service.HttpsCertificateService;
import com.bsep.pki_system.service.RootCertificateExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.io.File;

@Configuration
public class HttpsConfig {

    private static final Logger logger = LoggerFactory.getLogger(HttpsConfig.class);
    private final HttpsCertificateService httpsCertificateService;
    private final RootCertificateExportService rootCertificateExportService;
    
    public HttpsConfig(HttpsCertificateService httpsCertificateService,
                      RootCertificateExportService rootCertificateExportService) {
        this.httpsCertificateService = httpsCertificateService;
        this.rootCertificateExportService = rootCertificateExportService;
    }
    
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> servletContainerCustomizer() {
        return factory -> {
            File certFile = new File("server.p12");
            if (certFile.exists()) {
                Ssl ssl = new Ssl();
                ssl.setEnabled(true);
                ssl.setKeyStore("server.p12");
                ssl.setKeyStoreType("PKCS12");
                ssl.setKeyStorePassword("changeit");
                ssl.setKeyAlias("server");
                factory.setSsl(ssl);
                logger.info("HTTPS enabled with certificate: server.p12");
            } else {
                logger.warn("HTTPS disabled - server.p12 not found. Application starting in HTTP mode.");
                logger.warn("To enable HTTPS: create a Root certificate, restart application.");
            }
        };
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        File certFile = new File("server.p12");
        if (!certFile.exists()) {
            logger.info("Attempting to generate HTTPS certificate...");
            try {
                httpsCertificateService.generateHttpsCertificate();
                logger.info("============================================================");
                logger.info("HTTPS certificate generated successfully!");
                logger.info("Please RESTART the application to enable HTTPS.");
                logger.info("============================================================");
            } catch (Exception e) {
                logger.error("Could not generate HTTPS certificate: {}", e.getMessage());
                logger.error("Certificate generation requires a valid Root certificate in the PKI system.");
            }
        }
        
        // Export Root CA certificate for browser trust
        File rootCertFile = new File("root-ca.crt");
        if (!rootCertFile.exists()) {
            try {
                rootCertificateExportService.exportRootCertificate();
                logger.info("Root CA certificate exported to root-ca.crt");
            } catch (Exception e) {
                logger.warn("Could not export Root certificate: {}", e.getMessage());
            }
        }
    }
}

