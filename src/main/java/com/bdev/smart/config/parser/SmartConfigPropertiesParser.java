package com.bdev.smart.config.parser;

import com.bdev.smart.config.data.inner.dimension.AllDimensions;
import com.bdev.smart.config.data.inner.property.AllProperties;
import com.bdev.smart.config.data.inner.property.DimensionProperty;
import com.bdev.smart.config.data.inner.property.Property;
import com.bdev.smart.config.data.inner.property.PropertyType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import java.io.File;
import java.util.*;

class SmartConfigPropertiesParser {
    static AllProperties parse(
            String propertiesPath,
            AllDimensions allDimensions
    ) {
        AllProperties res =
                getCollapsedProperties(getRawProperties(propertiesPath));

        res.getAllProperties().forEach((propertyName, propertyInfo) -> propertyInfo
                .getDimensionsPropertyInfo()
                .stream()
                .map(DimensionProperty::getDimensions)
                .forEach(dimensionsNames ->
                        dimensionsNames.forEach(dimensionName -> {
                            allDimensions.findDimensionByValue(dimensionName);
                        })
                ));

        return res;
    }

    private static AllProperties getCollapsedProperties(AllProperties properties) {
        for (String propertyName : properties.getAllProperties().keySet()) {
            Property property = properties.getAllProperties().get(propertyName);

            PropertyType type = null;

            for (DimensionProperty dimensionProperty : property.getDimensionsPropertyInfo()) {
                if (type == null) {
                    type = dimensionProperty.getType();
                }
            }

            property.setType(type);
        }

        return properties;
    }

    private static AllProperties getRawProperties(String propertiesPath) {
        Config config = ConfigFactory.parseFile(new File(propertiesPath));

        AllProperties allProperties = new AllProperties();

        config
                .entrySet()
                .forEach(configProperty -> {
                    String [] propertyKeyParts = splitKey(configProperty.getKey());

                    String propertyName = extractPropertyName(propertyKeyParts);
                    List<String> dimensions = extractDimensions(propertyKeyParts);

                    DimensionProperty dimensionProperty = new DimensionProperty(); {
                        Object value = config.getAnyRef(configProperty.getKey());
                        PropertyType propertyType = getType(configProperty.getValue(), value);

                        dimensionProperty.setDimensions(new HashSet<>(dimensions));
                        dimensionProperty.setValue(value);
                        dimensionProperty.setType(propertyType);
                    }

                    allProperties
                            .findOrCreateProperty(propertyName)
                            .addDimensionProperty(dimensionProperty);
                });

        return allProperties;
    }

    private static PropertyType getType(ConfigValue configValue, Object value) {
        switch (configValue.valueType()) {
            case BOOLEAN:
                return PropertyType.BOOLEAN;
            case NUMBER:
                return PropertyType.NUMBER;
            case STRING:
                return PropertyType.STRING;
            case LIST:
                return getListType((List) value);
        }

        throw new RuntimeException();
    }

    private static PropertyType getListType(List value) {
        PropertyType type = null;

        for (Object o : value) {
            PropertyType currentType;

            if (isNumber(o)) {
                currentType = PropertyType.LIST_OF_NUMBERS;
            } else if (o instanceof Boolean) {
                currentType = PropertyType.LIST_OF_BOOLEANS;
            } else if (o instanceof String) {
                currentType = PropertyType.LIST_OF_STRINGS;
            } else {
                throw new RuntimeException();
            }

            if (type == null) {
                type = currentType;
            } else {
                if (type != currentType) {
                    throw new RuntimeException();
                }
            }
        }

        return type;
    }

    private static boolean isNumber(Object o) {
        return
                (o instanceof Byte) ||
                (o instanceof Short) ||
                (o instanceof Integer) ||
                (o instanceof Long) ||
                (o instanceof Float) ||
                (o instanceof Double);
    }

    private static String [] splitKey(String key) {
        return key.split("\\.");
    }

    private static String extractPropertyName(String [] propertyKeyParts) {
        return propertyKeyParts[0];
    }

    private static List<String> extractDimensions(String [] propertyKeyParts) {
        List<String> dimensions = new ArrayList<>();

        String [] rawDimensions = propertyKeyParts[1].replace("\"", "").split("~");

        for (String rawDimension : rawDimensions) {
            String trimmedRawDimension = rawDimension.trim();

            if (!trimmedRawDimension.isEmpty()) {
                dimensions.add(trimmedRawDimension);
            }
        }

        return dimensions;
    }
}
