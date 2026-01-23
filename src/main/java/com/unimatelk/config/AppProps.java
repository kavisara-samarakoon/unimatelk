package com.unimatelk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProps {
    @Value("${app.uploads-dir:./uploads}")
    private String uploadsDir;

    public String getUploadsDir() { return uploadsDir; }
}
