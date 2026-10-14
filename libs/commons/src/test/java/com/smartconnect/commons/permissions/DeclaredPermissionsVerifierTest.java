package com.smartconnect.commons.permissions;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeclaredPermissionsVerifierTest {

    @Test
    void extractsPermissionsFromAuthorityCalls() {
        assertThat(DeclaredPermissionsVerifier.extractPermissions(
                "hasAuthority('orders:read') and hasAnyAuthority('orders:cancel', 'orders:refund')"))
                .containsExactly("orders:cancel", "orders:read", "orders:refund");
    }

    @Test
    void ignoresRolesAndNonPermissionStrings() {
        assertThat(DeclaredPermissionsVerifier.extractPermissions(
                "hasRole('ADMIN') or hasAuthority('ROLE_ADMIN') or isAuthenticated()"))
                .isEmpty();
    }

    @Test
    void validatesNames() {
        assertThat(PermissionNames.isValid("orders:read")).isTrue();
        assertThat(PermissionNames.isValid("Orders:Read")).isFalse();
        assertThat(PermissionNames.isValid("orders")).isFalse();
        assertThat(PermissionNames.resource("orders:read")).isEqualTo("orders");
    }

    @Test
    void failsStartupOnUndeclaredPermissionButAllowsSystemOnes() {
        new ApplicationContextRunner().withBean(SampleController.class).run(context -> {
            DeclaredPermissionsVerifier verifier = new DeclaredPermissionsVerifier(context);

            assertThatThrownBy(() -> verifier.verify(Set.of("orders:read")))
                    .hasMessageContaining("orders:refund")
                    .hasMessageNotContaining("users:read");
            assertThatCode(() -> verifier.verify(Set.of("orders:read", "orders:refund"))).doesNotThrowAnyException();
        });
    }

    @Controller
    static class SampleController {

        @PreAuthorize("hasAuthority('orders:read')")
        public void read() {
        }

        @PreAuthorize("hasAuthority('orders:refund') or hasAuthority('users:read')")
        public void refund() {
        }
    }
}
