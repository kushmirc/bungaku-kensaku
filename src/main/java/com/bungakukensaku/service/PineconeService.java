package com.bungakukensaku.service;

import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.proto.DescribeIndexStatsResponse;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import org.openapitools.db_control.client.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.google.protobuf.Struct;
import com.google.protobuf.ListValue;

import jakarta.annotation.PostConstruct;
import java.util.*;
import static io.pinecone.commons.IndexInterface.buildUpsertVectorWithUnsignedIndices;

@Service
public class PineconeService {
    private static final Logger logger = LoggerFactory.getLogger(PineconeService.class);
    
    private Pinecone pinecone;
    private Index index;
    
    @Value("${pinecone.api-key}")
    private String apiKey;
    
    @Value("${pinecone.environment}")
    private String environment;
    
    @Value("${pinecone.index-name}")
    private String indexName;
    
    @Value("${pinecone.dimension:1536}")
    private int dimension;
    
    @PostConstruct
    public void init() {
        try {
            this.pinecone = new Pinecone.Builder(apiKey).build();
            logger.info("Initialized Pinecone client");
            
            // Note: Index creation and connection will be done lazily when first needed
            
        } catch (Exception e) {
            logger.error("Failed to initialize Pinecone service", e);
            throw new RuntimeException("Failed to initialize Pinecone service", e);
        }
    }
    
    /**
     * Get the index connection, creating it lazily if needed
     */
    private Index getIndex() {
        if (index == null) {
            try {
                this.index = pinecone.getIndexConnection(indexName);
                logger.info("Connected to Pinecone index: {}", indexName);
            } catch (Exception e) {
                logger.error("Failed to connect to index: {}. Please ensure the index '{}' exists in your Pinecone account.", e.getMessage(), indexName);
                throw new RuntimeException("Failed to connect to Pinecone index", e);
            }
        }
        return index;
    }
    
    /**
     * Upserts a batch of vectors with metadata
     */
    public void upsertVectors(List<VectorData> vectors) {
        try {
            List<VectorWithUnsignedIndices> pineconeVectors = new ArrayList<>();
            
            for (VectorData vector : vectors) {
                // Convert metadata to protobuf Struct
                Struct.Builder metadataBuilder = Struct.newBuilder();
                for (Map.Entry<String, Object> entry : vector.getMetadata().entrySet()) {
                    // Skip null values
                    if (entry.getValue() == null) {
                        continue;
                    }
                    
                    com.google.protobuf.Value value;
                    if (entry.getValue() instanceof String) {
                        value = com.google.protobuf.Value.newBuilder().setStringValue((String) entry.getValue()).build();
                    } else if (entry.getValue() instanceof Integer || entry.getValue() instanceof Long) {
                        // For bookId and similar integer fields, preserve as integer without decimal
                        value = com.google.protobuf.Value.newBuilder().setNumberValue(((Number) entry.getValue()).longValue()).build();
                    } else if (entry.getValue() instanceof Number) {
                        value = com.google.protobuf.Value.newBuilder().setNumberValue(((Number) entry.getValue()).doubleValue()).build();
                    } else if (entry.getValue() instanceof Boolean) {
                        value = com.google.protobuf.Value.newBuilder().setBoolValue((Boolean) entry.getValue()).build();
                    } else {
                        value = com.google.protobuf.Value.newBuilder().setStringValue(entry.getValue().toString()).build();
                    }
                    metadataBuilder.putFields(entry.getKey(), value);
                }
                
                VectorWithUnsignedIndices pv = buildUpsertVectorWithUnsignedIndices(
                        vector.getId(),
                        vector.getValues(),
                        null, // sparse values
                        null, // namespace
                        metadataBuilder.build()
                );
                pineconeVectors.add(pv);
            }
            
            getIndex().upsert(pineconeVectors, "default");
            logger.info("Successfully upserted {} vectors", vectors.size());
        } catch (Exception e) {
            logger.error("Error upserting vectors: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upsert vectors", e);
        }
    }
    
    /**
     * Queries for similar vectors
     */
    public List<SearchResult> query(List<Float> queryVector, int topK, Map<String, Object> filter) {
        try {
            // Convert filter to Struct if needed
            Struct metadataFilter = null;
            if (filter != null && !filter.isEmpty()) {
                // Convert the filter map to a protobuf Struct for Pinecone
                metadataFilter = buildMetadataFilter(filter);
            }
            
            QueryResponseWithUnsignedIndices response = getIndex().queryByVector(
                    topK, // number of results
                    queryVector, // vector values
                    "default", // namespace
                    metadataFilter, // filter as Struct
                    true, // includeValues
                    true  // includeMetadata
            );
            
            List<SearchResult> results = new ArrayList<>();
            for (var match : response.getMatchesList()) {
                Map<String, Object> metadata = new HashMap<>();
                if (match.getMetadata() != null && !match.getMetadata().getFieldsMap().isEmpty()) {
                    match.getMetadata().getFieldsMap().forEach((key, value) -> {
                        if (value.hasStringValue()) {
                            metadata.put(key, value.getStringValue());
                        } else if (value.hasNumberValue()) {
                            metadata.put(key, value.getNumberValue());
                        } else if (value.hasBoolValue()) {
                            metadata.put(key, value.getBoolValue());
                        }
                    });
                }
                
                SearchResult result = new SearchResult(
                        match.getId(),
                        match.getScore(),
                        metadata
                );
                results.add(result);
            }
            
            logger.info("Query returned {} results", results.size());
            return results;
        } catch (Exception e) {
            logger.error("Error querying vectors: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query vectors", e);
        }
    }
    
    /**
     * Deletes specific vectors by IDs
     */
    public void deleteVectors(List<String> vectorIds) {
        try {
            getIndex().deleteByIds(vectorIds, "default");
            logger.debug("Deleted {} vectors from index", vectorIds.size());
        } catch (Exception e) {
            logger.error("Error deleting vectors: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete vectors", e);
        }
    }
    
    /**
     * Deletes a single vector by ID
     */
    public void deleteVector(String vectorId) {
        deleteVectors(List.of(vectorId));
    }
    
    /**
     * Deletes all vectors in the index
     */
    public void deleteAllVectors() {
        try {
            getIndex().deleteAll("default");
            logger.info("Deleted all vectors from index: {}", indexName);
        } catch (Exception e) {
            logger.error("Error deleting vectors: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete vectors", e);
        }
    }
    
    /**
     * Gets index statistics
     */
    public Map<String, Object> getIndexStats() {
        try {
            DescribeIndexStatsResponse stats = getIndex().describeIndexStats();
            Map<String, Object> result = new HashMap<>();
            result.put("dimension", stats.getDimension());
            result.put("indexFullness", stats.getIndexFullness());
            result.put("totalVectorCount", stats.getTotalVectorCount());
            result.put("namespaces", stats.getNamespacesMap());
            return result;
        } catch (Exception e) {
            logger.error("Error getting index stats: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get index stats", e);
        }
    }
    
    /**
     * Build a properly structured metadata filter for Pinecone queries.
     * This method handles nested structures like {"bookId": {"$in": [1, 2, 3]}}
     * 
     * @param filter Map containing the filter criteria
     * @return Struct representing the filter in protobuf format
     */
    private Struct buildMetadataFilter(Map<String, Object> filter) {
        logger.debug("Building metadata filter from: {}", filter);
        Struct.Builder filterBuilder = Struct.newBuilder();
        
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            logger.debug("Processing filter entry: {} = {} (type: {})", key, value, value != null ? value.getClass().getSimpleName() : "null");
            
            if (value == null) {
                continue;
            }
            
            // Handle $in operator with array values
            if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> listValue = (List<Object>) value;
                
                // Create nested structure: {"$in": [values]}
                Struct.Builder inOperatorBuilder = Struct.newBuilder();
                ListValue.Builder listBuilder = ListValue.newBuilder();
                
                // Add each value to the list
                for (Object item : listValue) {
                    logger.debug("Processing list item: {} (type: {})", item, item.getClass().getSimpleName());
                    if (item instanceof Integer || item instanceof Long) {
                        // For integer types, use the integer value to avoid decimal points
                        logger.debug("Adding as integer: {}", item);
                        listBuilder.addValues(com.google.protobuf.Value.newBuilder().setNumberValue(((Number) item).intValue()).build());
                    } else if (item instanceof Number) {
                        logger.debug("Adding as double: {}", item);
                        listBuilder.addValues(com.google.protobuf.Value.newBuilder().setNumberValue(((Number) item).doubleValue()).build());
                    } else if (item instanceof String) {
                        logger.debug("Adding as string: {}", item);
                        listBuilder.addValues(com.google.protobuf.Value.newBuilder().setStringValue((String) item).build());
                    } else if (item instanceof Boolean) {
                        logger.debug("Adding as boolean: {}", item);
                        listBuilder.addValues(com.google.protobuf.Value.newBuilder().setBoolValue((Boolean) item).build());
                    }
                }
                
                // Create the $in structure
                inOperatorBuilder.putFields("$in", com.google.protobuf.Value.newBuilder().setListValue(listBuilder.build()).build());
                
                // Add the nested structure to the filter
                filterBuilder.putFields(key, com.google.protobuf.Value.newBuilder().setStructValue(inOperatorBuilder.build()).build());
                
            } else if (value instanceof Map) {
                // Handle nested map structures (already in correct format)
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                filterBuilder.putFields(key, com.google.protobuf.Value.newBuilder().setStructValue(buildMetadataFilter(mapValue)).build());
                
            } else {
                // Handle simple scalar values
                if (value instanceof String) {
                    filterBuilder.putFields(key, com.google.protobuf.Value.newBuilder().setStringValue((String) value).build());
                } else if (value instanceof Number) {
                    filterBuilder.putFields(key, com.google.protobuf.Value.newBuilder().setNumberValue(((Number) value).doubleValue()).build());
                } else if (value instanceof Boolean) {
                    filterBuilder.putFields(key, com.google.protobuf.Value.newBuilder().setBoolValue((Boolean) value).build());
                }
            }
        }
        
        Struct builtFilter = filterBuilder.build();
        logger.debug("Built metadata filter: {}", builtFilter);
        return builtFilter;
    }
    
    // Data classes
    public static class VectorData {
        private final String id;
        private final List<Float> values;
        private final Map<String, Object> metadata;
        
        public VectorData(String id, List<Float> values, Map<String, Object> metadata) {
            this.id = id;
            this.values = values;
            this.metadata = metadata;
        }
        
        public String getId() { return id; }
        public List<Float> getValues() { return values; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
    
    public static class SearchResult {
        private final String id;
        private final Float score;
        private final Map<String, Object> metadata;
        
        public SearchResult(String id, Float score, Map<String, Object> metadata) {
            this.id = id;
            this.score = score;
            this.metadata = metadata;
        }
        
        public String getId() { return id; }
        public Float getScore() { return score; }
        public Map<String, Object> getMetadata() { return metadata; }
    }
}