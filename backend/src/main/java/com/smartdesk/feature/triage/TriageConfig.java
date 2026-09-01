package com.smartdesk.feature.triage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TriageProperties.class)
public class TriageConfig {
}
