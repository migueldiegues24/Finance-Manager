package com.miguel.financemanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Relógio injetável, para os testes poderem avançar o tempo (ex.: limites de
// tentativas a expirar).
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
