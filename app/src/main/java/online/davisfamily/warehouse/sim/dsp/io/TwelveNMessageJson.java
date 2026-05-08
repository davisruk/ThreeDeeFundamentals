package online.davisfamily.warehouse.sim.dsp.io;

public record TwelveNMessageJson(
        TwelveNHeaderJson header,
        TwelveNFieldJson toteIdentifier,
        TwelveNFieldJson transportContainer,
        TwelveNFieldJson orderPriority,
        TwelveNFieldJson departureTime,
        TwelveNFieldJson serviceCentre,
        TwelveNOrderDetailJson orderDetail) {
}
