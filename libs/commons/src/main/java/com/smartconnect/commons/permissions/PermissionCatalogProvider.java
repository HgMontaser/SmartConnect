package com.smartconnect.commons.permissions;

import java.util.Map;

/**
 * Where a service's permission definitions come from. The default reads the
 * {@code smartconnect.permissions.catalog} list from configuration; a service that keeps its
 * permissions in its own database registers its own bean of this type instead, and that
 * replaces the default.
 */
public interface PermissionCatalogProvider {

    /** Permission name -> human description. Called at startup and on every republish. */
    Map<String, String> permissions();
}
