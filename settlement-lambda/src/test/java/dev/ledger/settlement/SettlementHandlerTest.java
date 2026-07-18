package dev.ledger.settlement;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementHandlerTest {

    static class StubStores extends Stores {
        final Set<String> claimed = new HashSet<>();
        final List<String> released = new ArrayList<>();
        long dailyTotal;
        int minuteCount = 1;

        StubStores() {
            super(null, "d", "v");
        }

        @Override
        public boolean tryClaim(String messageId) {
            return claimed.add(messageId);
        }

        @Override
        public void releaseClaim(String messageId) {
            claimed.remove(messageId);
            released.add(messageId);
        }

        @Override
        public long addDailyTotal(long accountId, long amountCents) {
            dailyTotal += amountCents;
            return dailyTotal;
        }

        @Override
        public int incrementMinuteCount(long accountId) {
            return minuteCount;
        }
    }

    static class StubApi extends ApiClient {
        final List<Long> settled = new ArrayList<>();
        final List<String> failed = new ArrayList<>();
        boolean throwOnSettle;

        StubApi() {
            super("http://unused", "t");
        }

        @Override
        public void settle(long transferId) {
            if (throwOnSettle) {
                throw new IllegalStateException("API down");
            }
            settled.add(transferId);
        }

        @Override
        public void fail(long transferId, String reason) {
            failed.add(transferId + ":" + reason);
        }
    }

    private static SQSEvent event(String messageId, long transferId, long amountCents) {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setMessageId(messageId);
        message.setBody("{\"transferId\":%d,\"customerId\":1,\"fromAccountId\":10,\"toAccountId\":11,\"amountCents\":%d}"
                .formatted(transferId, amountCents));
        SQSEvent sqsEvent = new SQSEvent();
        sqsEvent.setRecords(List.of(message));
        return sqsEvent;
    }

    private final Rules.Limits limits = new Rules.Limits(100_000, 500_000, 5);

    @Test
    void approvedTransferCallsSettle() {
        StubStores stores = new StubStores();
        StubApi api = new StubApi();
        SQSBatchResponse response = new SettlementHandler(stores, api, limits)
                .handleRequest(event("m1", 42, 5_000), null);

        assertThat(api.settled).containsExactly(42L);
        assertThat(response.getBatchItemFailures()).isEmpty();
    }

    @Test
    void overLimitTransferCallsFail() {
        StubStores stores = new StubStores();
        StubApi api = new StubApi();
        new SettlementHandler(stores, api, limits).handleRequest(event("m1", 42, 200_000), null);

        assertThat(api.failed).containsExactly("42:Exceeds single-transfer limit");
        assertThat(api.settled).isEmpty();
    }

    @Test
    void duplicateMessageIsSkipped() {
        StubStores stores = new StubStores();
        StubApi api = new StubApi();
        SettlementHandler handler = new SettlementHandler(stores, api, limits);
        handler.handleRequest(event("same-id", 42, 5_000), null);
        handler.handleRequest(event("same-id", 42, 5_000), null);

        assertThat(api.settled).containsExactly(42L);
        assertThat(stores.dailyTotal).isEqualTo(5_000); // velocity counted once
    }

    @Test
    void apiFailureReleasesClaimAndReportsBatchFailure() {
        StubStores stores = new StubStores();
        StubApi api = new StubApi();
        api.throwOnSettle = true;
        SQSBatchResponse response = new SettlementHandler(stores, api, limits)
                .handleRequest(event("m1", 42, 5_000), null);

        assertThat(response.getBatchItemFailures()).extracting(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .containsExactly("m1");
        assertThat(stores.released).containsExactly("m1"); // retry will reprocess
    }
}
