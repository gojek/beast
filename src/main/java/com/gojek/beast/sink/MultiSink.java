package com.gojek.beast.sink;

import com.gojek.beast.models.MultiException;
import com.gojek.beast.models.Records;
import com.gojek.beast.models.Status;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

import static com.gojek.beast.config.Constants.SUCCESS_STATUS;

@Slf4j
@AllArgsConstructor
public class MultiSink implements Sink {
    private final List<Sink> sinks;

    @Override
    public Status push(Records records) {
        List<Status> failures = sinks.stream()
                .map(s -> s.push(records))
                .filter(s -> !s.isSuccess())
                .collect(Collectors.toList());
        return failures.isEmpty() ? SUCCESS_STATUS : new MultiException(failures);
    }

    @Override
    public void close(String reason) {
        sinks.forEach(sink -> sink.close(reason));
        log.info("Stopped MultiSink Successfully: {}", reason);
    }
}
