package com.humano.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for mapping between different object types using Jackson ObjectMapper.
 * <p>
 * This service complements the existing mappers (like {@link UserMapper}) and provides
 * a generic way to transform objects between different types, especially for entity-DTO conversions.
 * </p>
 */
@Service
public class ObjectMapperService {

    private final Logger log = LoggerFactory.getLogger(ObjectMapperService.class);
    private final ObjectMapper objectMapper;

    @Autowired
    public ObjectMapperService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts an object to another type.
     *
     * @param source the source object
     * @param targetClass the target class
     * @param <T> the target type
     * @param <S> the source type
     * @return the converted object
     */
    public <T, S> T map(S source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(source, targetClass);
        } catch (Exception e) {
            log.error("Error mapping object of type {} to {}: {}",
                source.getClass().getSimpleName(),
                targetClass.getSimpleName(),
                e.getMessage());
            throw new RuntimeException("Error mapping object", e);
        }
    }

    /**
     * Converts a collection of objects to a list of another type.
     *
     * @param sourceCollection the source collection
     * @param targetClass the target class
     * @param <T> the target type
     * @param <S> the source type
     * @return the list of converted objects
     */
    public <T, S> List<T> mapAll(Collection<S> sourceCollection, Class<T> targetClass) {
        if (sourceCollection == null) {
            return List.of();
        }
        return sourceCollection.stream()
            .map(entity -> map(entity, targetClass))
            .collect(Collectors.toList());
    }

    /**
     * Converts an object to JSON string.
     *
     * @param object the object to convert
     * @return the JSON string
     * @throws JsonProcessingException if there's an error processing the JSON
     */
    public String toJson(Object object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }

    /**
     * Converts a JSON string to an object.
     *
     * @param json the JSON string
     * @param targetClass the target class
     * @param <T> the target type
     * @return the converted object
     * @throws JsonProcessingException if there's an error processing the JSON
     */
    public <T> T fromJson(String json, Class<T> targetClass) throws JsonProcessingException {
        return objectMapper.readValue(json, targetClass);
    }

    /**
     * Returns the configured ObjectMapper instance.
     *
     * @return the ObjectMapper
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Creates a clone of the current ObjectMapper with the same configuration.
     * This allows customizing the mapper for specific use cases without affecting the original.
     *
     * @return a new ObjectMapper instance with the same configuration as the current one
     */
    public ObjectMapper cloneObjectMapper() {
        return objectMapper.copy();
    }
}
