package com.smartconnect.demoservice;

import com.smartconnect.commons.client.ServiceClients;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls user-service through Consul both ways ServiceClients offers, so the result shows the
 * difference: as the caller the user's own permissions decide, as the service its service
 * account's do (granted users:read in ../keycloak/demo-service.permissions).
 */
@RestController
@RequestMapping("/api/demo")
public class ConnectivityController {

    private static final String USERS = "http://user-service/api/users";
    private static final ParameterizedTypeReference<List<Map<String, Object>>> USER_LIST = new ParameterizedTypeReference<>() {
    };

    private final ServiceClients clients;

    public ConnectivityController(ServiceClients clients) {
        this.clients = clients;
    }

    @GetMapping("/connectivity")
    @PreAuthorize("hasAuthority('demo:read')")
    public List<Check> connectivity() {
        return List.of(
                check("user-service as the caller (token relay)", clients::asCaller),
                check("user-service as demo-service (service token)", clients::asService));
    }

    private Check check(String name, Supplier<RestClient> client) {
        long start = System.nanoTime();
        try {
            List<Map<String, Object>> users = client.get().get().uri(USERS).retrieve().body(USER_LIST);
            return new Check(name, true, 200, (users == null ? 0 : users.size()) + " users visible", elapsed(start));
        } catch (RestClientResponseException e) {
            return new Check(name, false, e.getStatusCode().value(), e.getStatusText(), elapsed(start));
        } catch (RuntimeException e) {
            return new Check(name, false, 0, e.getMessage(), elapsed(start));
        }
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    public record Check(String name, boolean ok, int status, String detail, long latencyMs) {
    }
}
