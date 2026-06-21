package cl.sanosysalvos.orquestador.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Registra cada request que pasa por el gateway con su tiempo de respuesta.
 * Orden 0 para ejecutar justo después del JwtGatewayFilter (-1).
 */
@Slf4j
@Component
@Order(0)
public class RequestLoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        long start  = Instant.now().toEpochMilli();
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        return chain.filter(exchange).doFinally(signalType -> {
            long duration = Instant.now().toEpochMilli() - start;
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 0;

            if (status >= 400) {
                log.warn("[GW] {} {} → {} ({}ms)", method, path, status, duration);
            } else {
                log.debug("[GW] {} {} → {} ({}ms)", method, path, status, duration);
            }
        });
    }
}
