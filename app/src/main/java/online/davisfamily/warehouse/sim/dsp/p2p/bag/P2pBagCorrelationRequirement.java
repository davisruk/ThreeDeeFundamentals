package online.davisfamily.warehouse.sim.dsp.p2p.bag;

/**
 * Immutable work required for one planned bag correlation.
 *
 * <p>The correlation is the unit that must remain on one P2P line.  The
 * expected pack count is the complete bag count, rather than only the packs
 * found in one input tote; this is important when one bag spans totes.</p>
 */
public record P2pBagCorrelationRequirement(
        String correlationId,
        int expectedPackCount) {

    public P2pBagCorrelationRequirement {
        correlationId = requireValue(correlationId, "correlationId");
        if (expectedPackCount <= 0) {
            throw new IllegalArgumentException("expectedPackCount must be > 0");
        }
    }

    public int packCount() {
        return expectedPackCount;
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
