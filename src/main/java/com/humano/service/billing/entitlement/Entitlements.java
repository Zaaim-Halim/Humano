package com.humano.service.billing.entitlement;

import java.util.Map;
import java.util.UUID;

/**
 * What a workspace's plan allows, by feature code. A feature the catalog does not have at all is
 * not sold, so it restricts nothing; a catalog feature the plan does not list is not included. A
 * {@code null} limit means unlimited.
 *
 * @param planId   the plan these come from, {@code null} for {@link #UNLIMITED}
 * @param planName its name, for messages
 * @param catalog  every feature in the catalog, by code, with how to name it
 * @param grants   what the plan includes, by code
 */
public record Entitlements(UUID planId, String planName, Map<String, Label> catalog, Map<String, Grant> grants) {
    /** One feature of the plan. */
    public record Grant(boolean included, Integer limit) {}

    /** How a feature is named to people: its name and, for counted ones, what is counted ("employees"). */
    public record Label(UUID featureId, String name, String unit) {}

    /** No plan to apply (the platform itself, or a workspace set up without one): nothing is restricted. */
    public static final Entitlements UNLIMITED = new Entitlements(null, null, Map.of(), Map.of());

    public boolean includes(String code) {
        if (!catalog.containsKey(code)) {
            return true;
        }
        Grant grant = grants.get(code);
        return grant != null && grant.included();
    }

    /** The limit of a counted feature, {@code null} when unlimited; meaningless if not {@link #includes included}. */
    public Integer limit(String code) {
        Grant grant = grants.get(code);
        return grant == null ? null : grant.limit();
    }

    /** Whether one more can be added when {@code used} are in use. */
    public boolean allowsOneMore(String code, long used) {
        if (!includes(code)) {
            return false;
        }
        Integer limit = catalog.containsKey(code) ? limit(code) : null;
        return limit == null || used < limit;
    }

    /** The feature's name for messages (its code when the catalog does not have it). */
    public String nameOf(String code) {
        Label label = catalog.get(code);
        return label != null && label.name() != null ? label.name() : code;
    }

    /** What a counted feature counts, for messages ("employees"); falls back to its name. */
    public String unitOf(String code) {
        Label label = catalog.get(code);
        return label != null && label.unit() != null ? label.unit() : nameOf(code).toLowerCase();
    }
}
