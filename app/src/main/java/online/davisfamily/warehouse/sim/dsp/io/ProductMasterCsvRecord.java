package online.davisfamily.warehouse.sim.dsp.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record ProductMasterCsvRecord(
        String dispensingProductPackColumbusCode,
        String name,
        String thirdPartyLocation,
        String length,
        String width,
        String height) {
}
