package com.scheduling.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita @Async no contexto Spring para envio de e-mails não-bloqueante.
 */
@Configuration
@EnableAsync
public class AsyncConfig {}
