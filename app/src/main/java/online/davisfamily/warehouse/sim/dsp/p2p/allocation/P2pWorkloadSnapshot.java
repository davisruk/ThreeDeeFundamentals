package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record P2pWorkloadSnapshot(List<P2pServiceCentreWorkloadSnapshot> serviceCentres) {

    public P2pWorkloadSnapshot {
        if (serviceCentres == null) {
            throw new IllegalArgumentException("serviceCentres must not be null");
        }
        Set<String> serviceCentreIds = new LinkedHashSet<>();
        for (P2pServiceCentreWorkloadSnapshot serviceCentre : serviceCentres) {
            if (serviceCentre == null) {
                throw new IllegalArgumentException("serviceCentres must not contain null");
            }
            if (!serviceCentreIds.add(serviceCentre.serviceCentreId())) {
                throw new IllegalArgumentException(
                        "Duplicate serviceCentreId: " + serviceCentre.serviceCentreId());
            }
        }
        serviceCentres = List.copyOf(serviceCentres);
    }

    public static P2pWorkloadSnapshot empty() {
        return new P2pWorkloadSnapshot(List.of());
    }

    public Optional<P2pServiceCentreWorkloadSnapshot> find(String serviceCentreId) {
        String normalizedId = requireValue(serviceCentreId);
        return serviceCentres.stream()
                .filter(serviceCentre -> serviceCentre.serviceCentreId().equals(normalizedId))
                .findFirst();
    }

    public P2pServiceCentreWorkloadSnapshot require(String serviceCentreId) {
        String normalizedId = requireValue(serviceCentreId);
        return find(normalizedId).orElseThrow(() -> new IllegalArgumentException(
                "Unknown serviceCentreId: " + normalizedId));
    }

    private static String requireValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("serviceCentreId must not be blank");
        }
        return value.trim();
    }
}
