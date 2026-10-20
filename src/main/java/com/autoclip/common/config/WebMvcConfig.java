package com.autoclip.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        
        String storagePath = Paths.get("storage").toAbsolutePath().toUri().toString();
        if (!storagePath.endsWith("/")) {
            storagePath += "/";
        }
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(storagePath);
    }
}
