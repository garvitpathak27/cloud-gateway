package com.chatapp.gateway.logging;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLogWebFilter implements WebFilter {

    private static final Logger ACCESS =
            LoggerFactory.getLogger("gateway.access");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator/health")) {
            return chain.filter(exchange); // Container probes: not traffic
        }

        long start = System.nanoTime();
        AtomicBoolean logged = new AtomicBoolean(false);

        // Fires once, with the final status, for proxied responses,
        // Phase 7 errors, and CORS preflights.
        exchange.getResponse().beforeCommit(() -> {
            if (logged.compareAndSet(false, true)) {
                HttpStatusCode status = exchange.getResponse().getStatusCode();
                write(exchange, path, status != null ? status.value() : 200, start);
            }
            return Mono.empty();
        });

        return chain.filter(exchange).doFinally(signal -> {
            if (signal == SignalType.ON_ERROR) {
                return; // Phase 7's handler commits the error response later.
            }

            if (logged.compareAndSet(false, true)) {
                // Reached only if nothing committed through ServerHttpResponse:
                // client cancelled before headers (499), or a WebSocket
                // upgrade that wrote natively.
                write(
                        exchange,
                        path,
                        signal == SignalType.CANCEL ? 499 : statusOrDefault(exchange),
                        start
                );
            }
        });
    }

    private static int statusOrDefault(ServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();

        if (status != null) {
            return status.value();
        }

        return "websocket".equalsIgnoreCase(
                exchange.getRequest().getHeaders().getUpgrade()
        ) ? 101 : 200;
    }

    private static void write(
            ServerWebExchange exchange,
            String path,
            int status,
            long start
    ) {
        Route route = exchange.getAttribute(GATEWAY_ROUTE_ATTR);
        URI target = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        // Target is set after the load balancer picks an instance.

        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();

        ACCESS.info(
                "method={} path={} status={} durationMs={} route={} upstream={} remote={} requestId={}",
                exchange.getRequest().getMethod(),
                path, // Never log the query string: it may contain tokens.
                status,
                (System.nanoTime() - start) / 1_000_000,
                route != null ? route.getId() : "-",
                target != null && target.getHost() != null
                        ? target.getHost() + ":" + target.getPort()
                        : "-",
                remote != null ? remote.getAddress().getHostAddress() : "-",
                exchange.getRequest().getId()
        );
    }
}