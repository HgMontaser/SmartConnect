package com.smartconnect.commons.audit;

import com.smartconnect.commons.messaging.EventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Records every state-changing API call (POST/PUT/PATCH/DELETE) that reaches a controller:
 * who (from the token), what (endpoint pattern + path variables) and the outcome (status -
 * denied and failed attempts included). Written to the {@value #LOGGER} log (-> Kibana) and,
 * when Kafka is available, published as an {@code audit.performed} event.
 *
 * <p>This is what attributes backoffice changes to a person: Keycloak's own admin events show
 * them as made by user-service's service account.
 */
public class AuditInterceptor implements HandlerInterceptor {

    static final String LOGGER = "smartconnect.audit";
    private static final Logger log = LoggerFactory.getLogger(LOGGER);
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final String service;
    private final EventPublisher events;

    public AuditInterceptor(String service, EventPublisher events) {
        this.service = service;
        this.events = events;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!MUTATING.contains(request.getMethod())) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return;
        }
        String actor = firstNonNull(jwt.getToken().getClaimAsString("preferred_username"),
                jwt.getToken().getClaimAsString("azp"), jwt.getName());
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        @SuppressWarnings("unchecked")
        Map<String, String> targets = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        AuditRecord record = new AuditRecord(actor, jwt.getName(), service, request.getMethod(),
                request.getMethod() + " " + (pattern != null ? pattern : request.getRequestURI()),
                targets == null ? Map.of() : Map.copyOf(targets), response.getStatus(), Instant.now());

        log.info("{} {} {} -> {}", record.actor(), record.action(), record.targets(), record.status());
        if (events != null) {
            events.publish("audit.performed", record.actorId(), record);
        }
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
