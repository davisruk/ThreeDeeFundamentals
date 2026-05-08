package online.davisfamily.warehouse.sim.dsp.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;

import online.davisfamily.warehouse.sim.dsp.model.ProductCategory;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;

public class ProductMasterJsonLoader {

    public List<ProductMasterRecord> load(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        return toProductMasterRecords(JsonLoaderSupport.read(path, JsonNode.class));
    }

    public List<ProductMasterRecord> loadString(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json must not be null");
        }
        return toProductMasterRecords(JsonLoaderSupport.readString(json, JsonNode.class));
    }

    private List<ProductMasterRecord> toProductMasterRecords(JsonNode root) {
        JsonNode productsNode = extractProductsNode(root);
        List<ProductMasterRecord> products = new ArrayList<>();
        for (JsonNode productNode : productsNode) {
            ProductMasterJsonRecord record = JsonLoaderSupport.readString(productNode.toString(), ProductMasterJsonRecord.class);
            products.add(new ProductMasterRecord(
                    requireTrimmedValue(record.productId(), "productId"),
                    toProductCategory(record.category()),
                    record.thirdParty()));
        }
        return List.copyOf(products);
    }

    private JsonNode extractProductsNode(JsonNode root) {
        if (root == null || root.isNull()) {
            throw new IllegalArgumentException("Product master JSON must not be null");
        }
        if (root.isArray()) {
            return root;
        }
        if (root.isObject() && root.has("products") && root.get("products").isArray()) {
            return root.get("products");
        }
        throw new IllegalArgumentException("Product master JSON must be an array or object with a products array");
    }

    private ProductCategory toProductCategory(String categoryValue) {
        String normalized = requireTrimmedValue(categoryValue, "category").toUpperCase(Locale.ROOT);
        try {
            return ProductCategory.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown product category: " + normalized, e);
        }
    }

    private String requireTrimmedValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
