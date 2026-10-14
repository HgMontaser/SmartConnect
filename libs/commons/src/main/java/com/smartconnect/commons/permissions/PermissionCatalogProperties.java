package com.smartconnect.commons.permissions;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code smartconnect.permissions.*}. Setting {@code service} is what turns permission
 * publishing on for a service. {@code catalog} is a list rather than a map because Spring
 * Boot strips the ':' out of map keys.
 *
 * <pre>
 * smartconnect:
 *   permissions:
 *     service: orders-service
 *     catalog:
 *       - name: orders:read
 *         description: View orders
 * </pre>
 */
@ConfigurationProperties(prefix = "smartconnect.permissions")
public class PermissionCatalogProperties {

    private String service;
    private List<Entry> catalog = new ArrayList<>();
    /** Fail startup when an {@code @PreAuthorize} check uses an undeclared permission. */
    private boolean verify = true;

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public List<Entry> getCatalog() {
        return catalog;
    }

    public void setCatalog(List<Entry> catalog) {
        this.catalog = catalog;
    }

    public boolean isVerify() {
        return verify;
    }

    public void setVerify(boolean verify) {
        this.verify = verify;
    }

    public static class Entry {
        private String name;
        private String description;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
