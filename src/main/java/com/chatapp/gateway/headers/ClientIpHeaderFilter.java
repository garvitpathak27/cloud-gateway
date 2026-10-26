package com.chatapp.gateway.headers;

import java.net.InetSocketAddress;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Writes X-Forwarded-For with the address the gateway actually observed,
 * discarding anything the client sent.
 *
 * Spring Cloud Gateway's own XForwardedHeadersFilter APPENDS to the incoming
 * value, so the header becomes "whatever-the-client-claimed, real-address" and
 * every backend must know to read the last element. Getting that wrong - which
 * is easy - means trusting attacker input. We use set() instead: the header is
 * replaced outright, so a backend can read it with no ordering rules at all.
 *
 * This is only meaningful because the gateway is the sole ingress (Phase 10
 * lockdown). Anything on the chatapp network can still call a backend directly
 * and forge the header; see KNOWN-ISSUES P-4.
 */
@Component
public class ClientIpHeaderFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Forwarded-For";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String clientIp = (remote != null && remote.getAddress() != null)
                ? remote.getAddress().getHostAddress()
                : "unknown";

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, clientIp))
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
