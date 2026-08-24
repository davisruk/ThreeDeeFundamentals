package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

public record P2pElasticAllocationIssue(
        String serviceCentreId,
        P2pElasticAllocationIssueType type,
        String detail) {

    public P2pElasticAllocationIssue {
        serviceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        detail = requireValue(detail, "detail");
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
