package com.interpretation.config;

import com.interpretation.websocket.InterpretationWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InterpretationWebSocketHandler interpretationWebSocketHandler;

    public WebSocketConfig(InterpretationWebSocketHandler interpretationWebSocketHandler) {
        this.interpretationWebSocketHandler = interpretationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(interpretationWebSocketHandler, "/ws/interpretation")
                .setAllowedOrigins("*");
    }
}
