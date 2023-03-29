package com.mbixtech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "mbix.amqp")
public class AMQPConf {
    private String username;
    private String password;
    private String host;
    private String port;
}
