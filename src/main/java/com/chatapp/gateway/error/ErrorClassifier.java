package com.chatapp.gateway.error;
import java.net.UnknownHostException;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.http.client.PrematureCloseException;

public final class ErrorClassifier {

    public record Classification(HttpStatusCode status, String code, String message) {}

    static final Classification NO_INSTANCES = new Classification(HttpStatus.SERVICE_UNAVAILABLE,
            "NO_INSTANCES", "The requested service is temporarily unavailable. Try again shortly.");
    static final Classification UPSTREAM_UNREACHABLE = new Classification(HttpStatus.BAD_GATEWAY,
            "UPSTREAM_UNREACHABLE", "The requested service could not be reached.");
    static final Classification UPSTREAM_TIMEOUT = new Classification(HttpStatus.GATEWAY_TIMEOUT,
            "UPSTREAM_TIMEOUT", "The requested service did not respond in time.");
    static final Classification ROUTE_NOT_FOUND = new Classification(HttpStatus.NOT_FOUND,
            "ROUTE_NOT_FOUND", "No API route matches this path.");
    static final Classification GATEWAY_ERROR = new Classification(HttpStatus.INTERNAL_SERVER_ERROR,
            "GATEWAY_ERROR", "Unexpected gateway error.");

    private ErrorClassifier() {}

    public static Classification classify(Throwable ex) {
        if (ex instanceof NotFoundException) {
            return NO_INSTANCES;
        }
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConnectTimeoutException || t instanceof TimeoutException) {
                return UPSTREAM_TIMEOUT;
            }
            if (t instanceof ConnectException || t instanceof PrematureCloseException  || t instanceof UnknownHostException ) {
                return UPSTREAM_UNREACHABLE;
            }
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatusCode s = rse.getStatusCode();
            if (s.value() == 404) return ROUTE_NOT_FOUND;
            if (s.value() == 504) return UPSTREAM_TIMEOUT;
            if (s.value() == 503) return NO_INSTANCES;
            return new Classification(s, "HTTP_" + s.value(), "The request could not be processed.");
        }
        return GATEWAY_ERROR;
    }
}
