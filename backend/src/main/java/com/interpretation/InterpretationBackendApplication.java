package com.interpretation;

import com.interpretation.config.OllamaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OllamaProperties.class)
public class InterpretationBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterpretationBackendApplication.class, args);
    }
}
