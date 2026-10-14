package com.smartconnect.commons.permissions;

import java.util.regex.Pattern;

/** Shape rules for permission and service names, shared by publishers and user-service. */
public final class PermissionNames {

    /** {@code resource:action}, e.g. {@code orders:refund}. */
    public static final Pattern PERMISSION = Pattern.compile("^[a-z][a-z0-9-]{0,29}:[a-z][a-z0-9-]{0,29}$");

    public static final Pattern SERVICE = Pattern.compile("^[a-z][a-z0-9-]{1,40}$");

    private PermissionNames() {
    }

    public static boolean isValid(String permission) {
        return permission != null && PERMISSION.matcher(permission).matches();
    }

    public static boolean isValidService(String service) {
        return service != null && SERVICE.matcher(service).matches();
    }

    /** The part before the colon - the unit of ownership between services. */
    public static String resource(String permission) {
        int idx = permission.indexOf(':');
        return idx < 0 ? permission : permission.substring(0, idx);
    }
}
