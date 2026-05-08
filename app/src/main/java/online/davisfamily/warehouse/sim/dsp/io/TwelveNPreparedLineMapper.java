package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;

public class TwelveNPreparedLineMapper {
    private final TwelveNMessageKindMapper messageKindMapper;

    public TwelveNPreparedLineMapper(TwelveNMessageKindMapper messageKindMapper) {
        if (messageKindMapper == null) {
            throw new IllegalArgumentException("messageKindMapper must not be null");
        }
        this.messageKindMapper = messageKindMapper;
    }

    public List<DspOrderItem> toPreparedLines(TwelveNMessageJson message) {
        TwelveNLineMappingSupport.validateMessage(message);

        TwelveNMessageKind messageKind = messageKindMapper.map(message.toteIdentifier().payload());
        return switch (messageKind) {
            case MANUAL_PREPARATION, ADAPTED_PREPARATION -> TwelveNLineMappingSupport.toItems(message);
            case EMPTY_DISPATCH, ASSOCIATED_DISPATCH, FULL_PACK_DISPATCH ->
                    throw new IllegalArgumentException("12N message kind " + messageKind + " is not preparation input");
        };
    }
}
