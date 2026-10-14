package com.smartconnect.commons.permissions;

import com.smartconnect.commons.security.SmartConnectPermissions;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.ReflectionUtils;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards against a permission being checked in code but never registered with SmartConnect
 * (it would exist in no token, so the endpoint would be unreachable for everyone). Scans
 * every controller's {@code @PreAuthorize} expressions for permission names and fails startup
 * if one is neither in the service's catalog nor a SmartConnect system permission.
 */
public class DeclaredPermissionsVerifier {

    private static final Pattern AUTHORITY_CALL = Pattern.compile("has(?:Any)?Authority\\(([^)]*)\\)");
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    private final ApplicationContext context;

    public DeclaredPermissionsVerifier(ApplicationContext context) {
        this.context = context;
    }

    public void verify(Set<String> declared) {
        Set<String> undeclared = new TreeSet<>();
        for (Object bean : context.getBeansWithAnnotation(Controller.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            collect(AnnotatedElementUtils.findMergedAnnotation(type, PreAuthorize.class), declared, undeclared);
            ReflectionUtils.doWithMethods(type, method ->
                    collect(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class), declared, undeclared));
        }
        if (!undeclared.isEmpty()) {
            throw new IllegalStateException("@PreAuthorize uses permissions missing from this service's catalog: "
                    + undeclared + " - declare them (smartconnect.permissions.catalog or your PermissionCatalogProvider)");
        }
    }

    private static void collect(PreAuthorize annotation, Set<String> declared, Set<String> undeclared) {
        if (annotation == null) {
            return;
        }
        extractPermissions(annotation.value()).stream()
                .filter(p -> !declared.contains(p) && !SmartConnectPermissions.SYSTEM.containsKey(p))
                .forEach(undeclared::add);
    }

    /** Permission-shaped names passed to hasAuthority/hasAnyAuthority in a SpEL expression. */
    static Set<String> extractPermissions(String expression) {
        Set<String> found = new TreeSet<>();
        Matcher call = AUTHORITY_CALL.matcher(expression);
        while (call.find()) {
            Matcher quoted = QUOTED.matcher(call.group(1));
            while (quoted.find()) {
                if (PermissionNames.isValid(quoted.group(1))) {
                    found.add(quoted.group(1));
                }
            }
        }
        return found;
    }
}
