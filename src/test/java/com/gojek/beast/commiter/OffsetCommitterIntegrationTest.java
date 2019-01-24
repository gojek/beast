package com.gojek.beast.commiter;

import com.gojek.beast.consumer.KafkaConsumer;
import com.gojek.beast.models.Records;
import com.gojek.beast.util.RecordsUtil;
import com.gojek.beast.util.WorkerUtil;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;

@RunWith(MockitoJUnitRunner.class)
public class OffsetCommitterIntegrationTest {
    private Set<Map<TopicPartition, OffsetAndMetadata>> acknowledgements;

    @Mock
    private KafkaConsumer kafkaConsumer;
    private OffsetCommitter offsetCommitter;
    private int acknowledgeTimeoutMs;
    private LinkedBlockingQueue<Records> commitQueue;
    private RecordsUtil recordsUtil;
    private OffsetCommitter committer;

    @Before
    public void setUp() {
        commitQueue = new LinkedBlockingQueue<>();
        CopyOnWriteArraySet<Map<TopicPartition, OffsetAndMetadata>> ackSet = new CopyOnWriteArraySet<>();
        acknowledgements = Collections.synchronizedSet(ackSet);
        acknowledgeTimeoutMs = 1000;
        recordsUtil = new RecordsUtil();
        OffsetState offsetState = new OffsetState(acknowledgeTimeoutMs);
        committer = new OffsetCommitter(commitQueue, acknowledgements, kafkaConsumer, offsetState);
    }

    @Test
    public void shouldCommitPartitionsOfAllRecordsInSequence() throws InterruptedException {
        Records records1 = recordsUtil.createRecords("driver-", 3);
        Records records2 = recordsUtil.createRecords("customer-", 3);
        Records records3 = recordsUtil.createRecords("merchant-", 3);
        List<Records> recordsList = Arrays.asList(records1, records2, records3);
        commitQueue.addAll(recordsList);
        committer.setDefaultSleepMs(10);

        Thread committerThread = new Thread(committer);
        committerThread.start();

        Thread ackThread = new Thread(() -> recordsList.forEach(records -> {
            try {
                Thread.sleep(new Random().nextInt(100));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Committed partitions of records with index: " + records.getPartitionsCommitOffset());
            committer.acknowledge(records.getPartitionsCommitOffset());
        }));

        ackThread.start();
        WorkerUtil.closeWorker(committer, acknowledgeTimeoutMs * 5).join();
        ackThread.join();
        committerThread.join();

        InOrder inOrder = inOrder(kafkaConsumer);
        inOrder.verify(kafkaConsumer).commitSync(records1.getPartitionsCommitOffset());
        inOrder.verify(kafkaConsumer).commitSync(records2.getPartitionsCommitOffset());
        inOrder.verify(kafkaConsumer).commitSync(records3.getPartitionsCommitOffset());
        inOrder.verify(kafkaConsumer, atLeastOnce()).wakeup(anyString());
        assertTrue(acknowledgements.isEmpty());
    }

    @Test
    public void shouldStopConsumerWhenAckTimeOutHappensForNextOffset() throws InterruptedException {
        Records records1 = recordsUtil.createRecords("driver-", 3);
        Records records2 = recordsUtil.createRecords("customer-", 3);
        Records records3 = recordsUtil.createRecords("merchant-", 3);
        List<Records> recordsList = Arrays.asList(records1, records2, records3);
        commitQueue.addAll(recordsList);
        committer.setDefaultSleepMs(10);
        List<Records> ackRecordsList = Arrays.asList(records1, records3);
        Thread committerThread = new Thread(committer);
        committerThread.start();

        Thread ackThread = new Thread(() -> ackRecordsList.forEach(records -> {
            try {
                Thread.sleep(new Random().nextInt(10));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Committed partitions of records with index: " + records.getPartitionsCommitOffset());
            committer.acknowledge(records.getPartitionsCommitOffset());
        }));

        ackThread.start();
        ackThread.join();
        committerThread.join();

        InOrder inOrder = inOrder(kafkaConsumer);
        inOrder.verify(kafkaConsumer).commitSync(records1.getPartitionsCommitOffset());
        inOrder.verify(kafkaConsumer, never()).commitSync(records2.getPartitionsCommitOffset());
        inOrder.verify(kafkaConsumer, never()).commitSync(records3.getPartitionsCommitOffset());
        assertEquals(2, commitQueue.size());
        assertEquals(records2, commitQueue.take());
        assertEquals(records3, commitQueue.take());
        inOrder.verify(kafkaConsumer, atLeastOnce()).wakeup(anyString());
        assertEquals(1, acknowledgements.size());
        assertEquals(records3.getPartitionsCommitOffset(), acknowledgements.stream().findFirst().get());
    }

    @Test
    public void shouldStopWhenNoAcknowledgements() throws InterruptedException {
        Records records1 = recordsUtil.createRecords("driver-", 3);
        Records records2 = recordsUtil.createRecords("customer-", 3);
        Records records3 = recordsUtil.createRecords("merchant-", 3);
        List<Records> recordsList = Arrays.asList(records1, records2, records3);
        commitQueue.addAll(recordsList);
        committer.setDefaultSleepMs(10);
        Thread committerThread = new Thread(committer);
        committerThread.start();

        committerThread.join();

        InOrder inOrder = inOrder(kafkaConsumer);
        inOrder.verify(kafkaConsumer, never()).commitSync(anyMap());
        assertEquals(3, commitQueue.size());
        inOrder.verify(kafkaConsumer, atLeastOnce()).wakeup(anyString());
        assertTrue(acknowledgements.isEmpty());
    }
}