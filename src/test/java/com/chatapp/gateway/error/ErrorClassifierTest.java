package com.chatapp.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ErrorClassifierTest {

    @Test void noInstanceIs503() {
        var c = ErrorClassifier.classify(NotFoundException.create(false, "Unable to find instance for chat-service"));
        assertThat(c.code()).isEqualTo("NO_INSTANCES");
        assertThat(c.status().value()).isEqualTo(503);
    }

    @Test void connectionRefusedIs502() {
        var c = ErrorClassifier.classify(new RuntimeException(new ConnectException("Connection refused")));
        assertThat(c.code()).isEqualTo("UPSTREAM_UNREACHABLE");
    }

    @Test void responseTimeoutIs504() {
        var ex = new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Response took longer than timeout: PT5S", new TimeoutException());
        assertThat(ErrorClassifier.classify(ex).code()).isEqualTo("UPSTREAM_TIMEOUT");
    }

    @Test void noRouteIs404() {
        assertThat(ErrorClassifier.classify(new ResponseStatusException(HttpStatus.NOT_FOUND)).code())
                .isEqualTo("ROUTE_NOT_FOUND");
    }

    @Test void unknownIs500WithoutLeakingMessage() {
        var c = ErrorClassifier.classify(new IllegalStateException("db password is hunter2"));
        assertThat(c.code()).isEqualTo("GATEWAY_ERROR");
        assertThat(c.message()).doesNotContain("hunter2");
    }
}
