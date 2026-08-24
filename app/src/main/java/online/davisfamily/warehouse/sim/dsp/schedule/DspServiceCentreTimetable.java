package online.davisfamily.warehouse.sim.dsp.schedule;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record DspServiceCentreTimetable(List<ServiceCentreSchedule> serviceCentres) {

    public DspServiceCentreTimetable {
        if (serviceCentres == null || serviceCentres.isEmpty()) {
            throw new IllegalArgumentException("serviceCentres must not be null or empty");
        }
        Set<String> serviceCentreIds = new LinkedHashSet<>();
        for (ServiceCentreSchedule serviceCentre : serviceCentres) {
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

    public Optional<ServiceCentreSchedule> find(String serviceCentreId) {
        String normalizedId = requireValue(serviceCentreId);
        return serviceCentres.stream()
                .filter(serviceCentre -> serviceCentre.serviceCentreId().equals(normalizedId))
                .findFirst();
    }

    public ServiceCentreSchedule require(String serviceCentreId) {
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
