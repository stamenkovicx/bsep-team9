package com.bsep.pki_system.model;

import jakarta.persistence.*;
import java.util.*;
import java.util.stream.*;

@Converter
public class BooleanListConverter implements AttributeConverter<List<Boolean>, String> {

    private static final String SPLIT_CHAR = ",";

    @Override
    public String convertToDatabaseColumn(List<Boolean> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "0,0,0,0,0,0,0,0,0";
        }
        return attribute.stream()
                .map(b -> b ? "1" : "0")
                .collect(Collectors.joining(SPLIT_CHAR));
    }

    @Override
    public List<Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return Arrays.asList(false, false, false, false, false, false, false, false, false);
        }
        return Arrays.stream(dbData.split(SPLIT_CHAR))
                .map(s -> "1".equals(s.trim()))
                .collect(Collectors.toList());
    }
}