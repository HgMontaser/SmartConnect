package com.smartconnect.commons.permissions;

import java.util.LinkedHashMap;
import java.util.Map;

/** Default {@link PermissionCatalogProvider}: the list declared in the service's configuration. */
public class PropertiesPermissionCatalogProvider implements PermissionCatalogProvider {

    private final PermissionCatalogProperties properties;

    public PropertiesPermissionCatalogProvider(PermissionCatalogProperties properties) {
        this.properties = properties;
    }

    @Override
    public Map<String, String> permissions() {
        Map<String, String> permissions = new LinkedHashMap<>();
        properties.getCatalog().forEach(e -> permissions.put(e.getName(), e.getDescription()));
        return permissions;
    }
}
