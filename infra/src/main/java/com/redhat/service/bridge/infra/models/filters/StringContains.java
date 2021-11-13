package com.redhat.service.bridge.infra.models.filters;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

public class StringContains extends BaseFilter<List<String>> {

    public static final String FILTER_TYPE_NAME = "StringContains";

    @JsonProperty("values")
    private List<String> values;

    public StringContains() {
        super(FILTER_TYPE_NAME);
    }

    public StringContains(String key, String value) {
        super(FILTER_TYPE_NAME, key);
        try {
            this.values = ObjectMapperFactory.get().readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("The value is not a list of strings.");
        }

    }

    @Override
    public List<String> getValue() {
        return values;
    }
}
