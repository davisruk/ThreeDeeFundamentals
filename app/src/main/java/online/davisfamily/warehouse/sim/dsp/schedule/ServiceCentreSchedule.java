package online.davisfamily.warehouse.sim.dsp.schedule;

import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

public record ServiceCentreSchedule(
        String serviceCentreId,
        String displayName,
        int priority,
        OperationalDayTime trunkerDepartureTime) {

    public ServiceCentreSchedule {
        serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        displayName = requireValue(displayName, "displayName");
        if (priority <= 0) {
            throw new IllegalArgumentException("priority must be positive");
        }
        if (trunkerDepartureTime == null) {
            throw new IllegalArgumentException("trunkerDepartureTime must not be null");
        }
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
