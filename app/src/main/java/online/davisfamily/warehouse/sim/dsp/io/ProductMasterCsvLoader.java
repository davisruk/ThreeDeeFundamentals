package online.davisfamily.warehouse.sim.dsp.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;

public class ProductMasterCsvLoader {
    private static final CsvMapper CSV_MAPPER = CsvMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    private static final CsvSchema CSV_SCHEMA = CsvSchema.emptySchema().withHeader();

    public List<ProductMasterRecord> load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        try (MappingIterator<ProductMasterCsvRecord> records = CSV_MAPPER
                .readerFor(ProductMasterCsvRecord.class)
                .with(CSV_SCHEMA)
                .readValues(path.toFile())) {
            return readProducts(records);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to load product master CSV: " + path, e);
        }
    }

    public List<ProductMasterRecord> loadString(String csv) {
        if (csv == null) {
            throw new IllegalArgumentException("csv must not be null");
        }
        try (MappingIterator<ProductMasterCsvRecord> records = CSV_MAPPER
                .readerFor(ProductMasterCsvRecord.class)
                .with(CSV_SCHEMA)
                .readValues(csv)) {
            return readProducts(records);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to load product master CSV", e);
        }
    }

    private List<ProductMasterRecord> readProducts(MappingIterator<ProductMasterCsvRecord> records) throws IOException {
        List<ProductMasterRecord> products = new ArrayList<>();
        Set<String> productIds = new LinkedHashSet<>();
        while (records.hasNextValue()) {
            ProductMasterCsvRecord record = records.nextValue();
            ProductMasterRecord product = toProductMasterRecord(record);
            if (!productIds.add(product.productId())) {
                throw new IllegalArgumentException("Duplicate productId: " + product.productId());
            }
            products.add(product);
        }
        return List.copyOf(products);
    }

    private ProductMasterRecord toProductMasterRecord(ProductMasterCsvRecord record) {
        String productId = requireTrimmedValue(
                record.dispensingProductPackColumbusCode(),
                "dispensingProductPackColumbusCode");
        return new ProductMasterRecord(
                productId,
                requireTrimmedValue(record.name(), "name for product " + productId),
                optionalTrimmedValue(record.thirdPartyLocation()),
                parseDimensions(record, productId));
    }

    private Optional<PackDimensions> parseDimensions(ProductMasterCsvRecord record, String productId) {
        double lengthMillimetres = parseDimension(record.length(), "length", productId);
        double widthMillimetres = parseDimension(record.width(), "width", productId);
        double heightMillimetres = parseDimension(record.height(), "height", productId);

        if (lengthMillimetres == 0d && widthMillimetres == 0d && heightMillimetres == 0d) {
            return Optional.empty();
        }
        if (lengthMillimetres <= 0d || widthMillimetres <= 0d || heightMillimetres <= 0d) {
            throw new IllegalArgumentException("Product " + productId + " must have three positive dimensions or 0 x 0 x 0");
        }
        return Optional.of(new PackDimensions(
                (float) (lengthMillimetres / 1000d),
                (float) (widthMillimetres / 1000d),
                (float) (heightMillimetres / 1000d)));
    }

    private double parseDimension(String value, String fieldName, String productId) {
        String normalized = requireTrimmedValue(value, fieldName + " for product " + productId);
        try {
            double parsed = Double.parseDouble(normalized);
            if (!Double.isFinite(parsed)) {
                throw new IllegalArgumentException(fieldName + " for product " + productId + " must be finite");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " for product " + productId + " must be numeric", e);
        }
    }

    private Optional<String> optionalTrimmedValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    private String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
