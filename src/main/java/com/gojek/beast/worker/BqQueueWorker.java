package com.gojek.beast.worker;

import com.gojek.beast.commiter.Acknowledger;
import com.gojek.beast.config.QueueConfig;
import com.gojek.beast.sink.bq.handler.impl.BQErrorHandlerException;
import com.gojek.beast.models.FailureStatus;
import com.gojek.beast.models.OffsetAcknowledgementException;
import com.gojek.beast.models.Records;
import com.gojek.beast.models.Status;
import com.gojek.beast.models.SuccessStatus;
import com.gojek.beast.sink.Sink;
import com.gojek.beast.stats.Stats;
import com.google.cloud.bigquery.BigQueryException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class BqQueueWorker extends Worker {
    // Should have separate instance of sink for this worker
    private final Sink sink;
    private final QueueConfig config;
    private final BlockingQueue<Records> queue;
    private final Acknowledger acknowledger;
    private final Stats statsClient = Stats.client();

    public BqQueueWorker(String name, Sink sink, QueueConfig config, Acknowledger acknowledger, BlockingQueue<Records> queue, WorkerState workerState) {
        super(name, workerState);
        this.queue = queue;
        this.sink = sink;
        this.config = config;
        this.acknowledger = acknowledger;
    }

    @Override
    public Status job() {
        Instant start = Instant.now();
        try {
            Records poll = queue.poll(config.getTimeout(), config.getTimeoutUnit());
            if (poll == null || poll.isEmpty()) return new SuccessStatus();
            Status status = pushToSink(poll);
            if (!status.isSuccess()) {
                queue.offer(poll, config.getTimeout(), config.getTimeoutUnit());
                return status;
            }
        } catch (InterruptedException | RuntimeException e) {
            statsClient.increment("worker.queue.bq.errors");
            log.debug("Exception::Failed to poll records from read queue: " + e.getMessage());
            return new FailureStatus(e);
        }
        statsClient.timeIt("worker.queue.bq.processing", start);
        return new SuccessStatus();
    }

    private Status pushToSink(Records poll) {
        Status status;
        try {
            status = sink.push(poll);
        } catch (BigQueryException e) {
            statsClient.increment("worker.queue.bq.errors");
            log.error("Exception::Failed to write to BQ: {}", e.getMessage());
            return new FailureStatus(e);
        } catch (BQErrorHandlerException bqhe) {
            statsClient.increment("worker.queue.handler.errors");
            log.error("Exception::Could not process the errors with handler sink: {}", bqhe.getMessage());
            return new FailureStatus(bqhe);
        }
        if (status.isSuccess()) {
            boolean ackStatus = acknowledger.acknowledge(poll.getPartitionsCommitOffset());
            if (ackStatus) {
                statsClient.timeIt("batch.processing.latency.time", poll.getPolledTime());
                return new SuccessStatus();
            } else {
                return new FailureStatus(new OffsetAcknowledgementException("offset acknowledgement failed"));
            }
        } else {
            statsClient.increment("worker.queue.bq.push_failure");
            log.error("Failed to push records to sink {}", status.toString());
            return status;
        }
    }

    @Override
    public void stop(String reason) {
        log.info("Stopping BqWorker: {}", reason);
        sink.close(reason);
    }
}
