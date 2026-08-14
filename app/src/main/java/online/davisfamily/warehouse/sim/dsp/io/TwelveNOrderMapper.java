package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;

import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;

public class TwelveNOrderMapper {
    private final TwelveNMessageKindMapper messageKindMapper;

    public TwelveNOrderMapper(TwelveNMessageKindMapper messageKindMapper) {
        if (messageKindMapper == null) {
            throw new IllegalArgumentException("messageKindMapper must not be null");
        }
        this.messageKindMapper = messageKindMapper;
    }

    public NotionalToteOrder toOrder(TwelveNMessageJson message, long sequenceNumber) {
        TwelveNLineMappingSupport.validateMessage(message);
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber must be >= 0");
        }

        TwelveNMessageKind messageKind = messageKindMapper.map(message.toteIdentifier().payload());
        OrderType orderType = switch (messageKind) {
            case ADAPTED_PREPARATION -> OrderType.ADAPTED;
            case EMPTY_DISPATCH -> OrderType.EMPTY;
            case ASSOCIATED_DISPATCH -> OrderType.ASSOCIATED;
            case FULL_PACK_DISPATCH -> OrderType.FULL_PACK;
            case MANUAL_PREPARATION ->
                    throw new IllegalArgumentException("12N message kind " + messageKind + " is not a simulated order");
        };

        List<online.davisfamily.warehouse.sim.dsp.model.DspOrderItem> items = TwelveNLineMappingSupport.toItems(message);
        return new NotionalToteOrder(
                TwelveNLineMappingSupport.requireTrimmedValue(message.header().orderId(), "header.orderId"),
                TwelveNLineMappingSupport.requireTrimmedValue(message.header().orderId(), "header.orderId"),
                TwelveNLineMappingSupport.requireTrimmedValue(message.serviceCentre().payload(), "serviceCentre.payload"),
                TwelveNLineMappingSupport.parsePositiveInt(message.header().sheetNumber(), "header.sheetNumber"),
                orderType,
                items,
                sequenceNumber);
    }
}
