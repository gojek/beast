package com.gojek.beast.sink.bq;


import com.gojek.beast.config.BQConfig;
import com.gojek.beast.exception.BQPartitionKeyNotSpecified;
import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.cloud.bigquery.Schema;
import com.google.cloud.bigquery.StandardTableDefinition;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BQTableDefinitionTest {
    @Mock
    private BQConfig bqConfig;

    @Test(expected = UnsupportedOperationException.class)
    public void shouldThrowUnsupportedExceptionForRangePartition() {
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(true);
        when(bqConfig.getBQTablePartitionKey()).thenReturn("int_field");

        Schema bqSchema = Schema.of(
                Field.newBuilder("int_field", LegacySQLTypeName.INTEGER).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        bqTableDefinition.getTableDefinition(bqSchema);
    }

    @Test
    public void shouldReturnTableDefinitionIfPartitionDisabled() {
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(false);
        Schema bqSchema = Schema.of(
                Field.newBuilder("int_field", LegacySQLTypeName.INTEGER).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        StandardTableDefinition tableDefinition = bqTableDefinition.getTableDefinition(bqSchema);
        Schema returnedSchema = tableDefinition.getSchema();
        assertEquals(returnedSchema.getFields().size(), bqSchema.getFields().size());
        assertEquals(returnedSchema.getFields().get(0).getName(), bqSchema.getFields().get(0).getName());
        assertEquals(returnedSchema.getFields().get(0).getMode(), bqSchema.getFields().get(0).getMode());
        assertEquals(returnedSchema.getFields().get(0).getType(), bqSchema.getFields().get(0).getType());
    }

    @Test(expected = BQPartitionKeyNotSpecified.class)
    public void shouldThrowErrorIfPartitionFieldNotSet() {
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(true);
        Schema bqSchema = Schema.of(
                Field.newBuilder("int_field", LegacySQLTypeName.INTEGER).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        StandardTableDefinition tableDefinition = bqTableDefinition.getTableDefinition(bqSchema);
        tableDefinition.getSchema();
    }

    @Test
    public void shouldCreatePartitionedTable() {
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(true);
        when(bqConfig.getBQTablePartitionKey()).thenReturn("timestamp_field");
        Schema bqSchema = Schema.of(
                Field.newBuilder("timestamp_field", LegacySQLTypeName.TIMESTAMP).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        StandardTableDefinition tableDefinition = bqTableDefinition.getTableDefinition(bqSchema);

        Schema returnedSchema = tableDefinition.getSchema();
        assertEquals(returnedSchema.getFields().size(), bqSchema.getFields().size());
        assertEquals(returnedSchema.getFields().get(0).getName(), bqSchema.getFields().get(0).getName());
        assertEquals(returnedSchema.getFields().get(0).getMode(), bqSchema.getFields().get(0).getMode());
        assertEquals(returnedSchema.getFields().get(0).getType(), bqSchema.getFields().get(0).getType());
        assertEquals("timestamp_field", tableDefinition.getTimePartitioning().getField());
    }

    @Test
    public void shouldCreateTableWithPartitionExpiry() {
        long partitionExpiry = 5184000000L;
        when(bqConfig.getBQTablePartitionExpiryMillis()).thenReturn(partitionExpiry);
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(true);
        when(bqConfig.getBQTablePartitionKey()).thenReturn("timestamp_field");
        Schema bqSchema = Schema.of(
                Field.newBuilder("timestamp_field", LegacySQLTypeName.TIMESTAMP).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        StandardTableDefinition tableDefinition = bqTableDefinition.getTableDefinition(bqSchema);

        assertEquals("timestamp_field", tableDefinition.getTimePartitioning().getField());
        assertEquals(partitionExpiry, tableDefinition.getTimePartitioning().getExpirationMs().longValue());
    }

    @Test
    public void shouldReturnTableWithNullPartitionExpiryIfLessThanZero() {
        long partitionExpiry = -1L;
        when(bqConfig.getBQTablePartitionExpiryMillis()).thenReturn(partitionExpiry);
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(true);
        when(bqConfig.getBQTablePartitionKey()).thenReturn("timestamp_field");
        Schema bqSchema = Schema.of(
                Field.newBuilder("timestamp_field", LegacySQLTypeName.TIMESTAMP).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        StandardTableDefinition tableDefinition = bqTableDefinition.getTableDefinition(bqSchema);

        assertEquals("timestamp_field", tableDefinition.getTimePartitioning().getField());
        assertEquals(null, tableDefinition.getTimePartitioning().getExpirationMs());
    }

    @Test
    public void shouldReturnTableWithNullPartitionExpiryIfEqualsZero() {
        long partitionExpiry = 0L;
        when(bqConfig.getBQTablePartitionExpiryMillis()).thenReturn(partitionExpiry);
        when(bqConfig.isBQTablePartitioningEnabled()).thenReturn(true);
        when(bqConfig.getBQTablePartitionKey()).thenReturn("timestamp_field");
        Schema bqSchema = Schema.of(
                Field.newBuilder("timestamp_field", LegacySQLTypeName.TIMESTAMP).build()
        );

        BQTableDefinition bqTableDefinition = new BQTableDefinition(bqConfig);
        StandardTableDefinition tableDefinition = bqTableDefinition.getTableDefinition(bqSchema);

        assertEquals("timestamp_field", tableDefinition.getTimePartitioning().getField());
        assertEquals(null, tableDefinition.getTimePartitioning().getExpirationMs());
    }
}
