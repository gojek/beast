package com.gojek.beast.sink.bq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.Map;

@AllArgsConstructor
@Getter
public class Record {
    Map<String, Object> columns;
}
