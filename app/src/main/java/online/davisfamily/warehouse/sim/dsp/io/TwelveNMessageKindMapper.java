package online.davisfamily.warehouse.sim.dsp.io;

public class TwelveNMessageKindMapper {

    public TwelveNMessageKind map(String toteTypeCode) {
        if (toteTypeCode == null || toteTypeCode.isBlank()) {
            throw new IllegalArgumentException("toteTypeCode must not be blank");
        }

        return switch (toteTypeCode.trim()) {
            case "01" -> TwelveNMessageKind.MANUAL_PREPARATION;
            case "02" -> TwelveNMessageKind.ADAPTED_PREPARATION;
            case "03" -> TwelveNMessageKind.EMPTY_DISPATCH;
            case "04" -> TwelveNMessageKind.ASSOCIATED_DISPATCH;
            case "05" -> TwelveNMessageKind.FULL_PACK_DISPATCH;
            default -> throw new IllegalArgumentException("Unknown 12N tote type code: " + toteTypeCode.trim());
        };
    }
}
