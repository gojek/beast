package com.gojek.beast.config;

import org.aeonbits.owner.Config;

public interface AppConfig extends Config {
    @Key("BQ_TABLE_NAME")
    String getTable();

    @Key("BQ_DATASET_NAME")
    String getDataset();

    @Key("BQ_PROTO_COLUMN_MAPPING")
    @ConverterClass(ProtoIndexToFieldMapConverter.class)
    ColumnMapping getProtoColumnMapping();
}