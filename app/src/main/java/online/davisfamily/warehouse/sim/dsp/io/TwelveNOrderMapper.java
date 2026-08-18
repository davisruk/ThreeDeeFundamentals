package online.davisfamily.warehouse.sim.dsp.io;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public class TwelveNOrderMapper {
    private final TwelveNMessageKindMapper messageKindMapper;

    public TwelveNOrderMapper(TwelveNMessageKindMapper messageKindMapper) {
        if (messageKindMapper == null) {
            throw new IllegalArgumentException("messageKindMapper must not be null");
        }
        this.messageKindMapper = messageKindMapper;
    }

    public MappedTwelveNOrder map(TwelveNMessageJson message, long sourceSequenceNumber) {
        TwelveNLineMappingSupport.validateMessage(message);
        if (sourceSequenceNumber < 0) {
            throw new IllegalArgumentException("sourceSequenceNumber must be >= 0");
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
        String orderId = TwelveNLineMappingSupport.requireTrimmedValue(
                message.header().orderId(), "header.orderId");
        String serviceCentreId = TwelveNLineMappingSupport.requireTrimmedValue(
                message.serviceCentre().payload(), "serviceCentre.payload");
        int sheetNumber = TwelveNLineMappingSupport.parsePositiveInt(
                message.header().sheetNumber(), "header.sheetNumber");
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                orderId,
                serviceCentreId,
                sheetNumber,
                orderType,
                items,
                sourceSequenceNumber);

        if (orderType == OrderType.EMPTY) {
            return new MappedTwelveNOrder(order, Optional.empty());
        }
        if (message.transportContainer() == null) {
            throw new IllegalArgumentException("transportContainer must not be null for physical inbound orders");
        }
        PhysicalToteId physicalToteId = new PhysicalToteId(TwelveNLineMappingSupport.requireTrimmedValue(
                message.transportContainer().payload(), "transportContainer.payload"));
        InboundToteManifest manifest = new InboundToteManifest(
                physicalToteId,
                new OrderSheetKey(orderId, sheetNumber),
                orderType,
                serviceCentreId,
                items,
                sourceSequenceNumber);
        return new MappedTwelveNOrder(order, Optional.of(manifest));
    }
}
