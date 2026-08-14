package online.davisfamily.warehouse.sim.dsp.model;

import java.util.Optional;

import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

public record ProductMasterRecord(
        String productId,
        String displayName,
        Optional<String> thirdPartyLocation,
        Optional<PackDimensions> dimensions) {

    public ProductMasterRecord {
        productId = requireTrimmedValue(productId, "productId");
        displayName = requireTrimmedValue(displayName, "displayName");
        if (thirdPartyLocation == null) {
            throw new IllegalArgumentException("thirdPartyLocation must not be null");
        }
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
        thirdPartyLocation = thirdPartyLocation.map(location -> requireTrimmedValue(location, "thirdPartyLocation"));
    }

    public boolean thirdParty() {
        return thirdPartyLocation.isPresent();
    }

    private static String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
