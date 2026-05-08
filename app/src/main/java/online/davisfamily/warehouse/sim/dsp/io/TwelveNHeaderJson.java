package online.davisfamily.warehouse.sim.dsp.io;

public record TwelveNHeaderJson(
        Integer orderIdLength,
        Integer sheetNumberLength,
        String orderId,
        String sheetNumber) {
}
