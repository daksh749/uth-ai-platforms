package com.paytm.mcpserver.enums;

import lombok.Getter;

/**
 * Enum representing Elasticsearch host types with their configurations
 */
@Getter
public enum EsHostEnum {
    PRIMARY("UTH_ES_Primary", 3),
    SECONDARY("UTH_ES_Secondary", 5),
    TERTIARY("UTH_ES_Tertiary", 12);

    private final String name;
    private final Integer dataSourceId;

    /**
     * Constructor for EsHostEnum
     * @param name the name of the host
     * @param dataSourceId the id of the Elasticsearch host
     */
    EsHostEnum(String name, Integer dataSourceId) {
        this.name = name;
        this.dataSourceId = dataSourceId;
    }
}