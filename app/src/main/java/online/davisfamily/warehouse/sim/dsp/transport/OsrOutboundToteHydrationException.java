package online.davisfamily.warehouse.sim.dsp.transport;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OsrOutboundToteHydrationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OsrOutboundToteHydrationException(
            PhysicalToteId physicalToteId,
            String detail) {
        this(physicalToteId, detail, null);
    }

    public OsrOutboundToteHydrationException(
            PhysicalToteId physicalToteId,
            String detail,
            Throwable cause) {
        super(message(physicalToteId, detail), cause);
    }

    private static String message(PhysicalToteId physicalToteId, String detail) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("detail must not be blank");
        }
        return "Unable to hydrate outbound physical tote " + physicalToteId.value()
                + ": " + detail.trim();
    }
}
