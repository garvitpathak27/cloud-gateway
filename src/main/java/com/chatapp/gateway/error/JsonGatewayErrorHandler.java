package com.chatapp.gateway.error;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class JsonGatewayErrorHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonGatewayErrorHandler.class);
    private final ObjectMapper objectMapper;

    public JsonGatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        ErrorClassifier.Classification c = ErrorClassifier.classify(ex);
        String path = exchange.getRequest().getPath().value();
        String requestId = exchange.getRequest().getId();
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        String routeId = route != null ? route.getId() : "-";

        if ("GATEWAY_ERROR".equals(c.code())) {
            log.error("gateway_error route={} path={} requestId={}", routeId, path, requestId, ex);
        } else if (c.status().is5xxServerError()) {
            log.warn("upstream_error code={} route={} path={} requestId={} cause={}",
                    c.code(), routeId, path, requestId, rootMessage(ex));
        }

        HttpStatus resolved = HttpStatus.resolve(c.status().value());
        GatewayError body = new GatewayError(
                Instant.now().toString(), c.status().value(),
                resolved != null ? resolved.getReasonPhrase() : "Error",
                c.code(), c.message(), path, requestId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + c.status().value() + ",\"code\":\"" + c.code() + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
        }

        response.setStatusCode(c.status());
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (c.status().value() == 503) {
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, "5");
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
