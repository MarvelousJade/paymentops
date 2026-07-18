package dev.ledger.settlement;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQS -> limit & velocity checks -> internal settle/fail callback.
 * Plain Java on purpose: no Spring, no DI container — Lambda cold starts pay for every class loaded.
 */
public class SettlementHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Stores stores;
    private final ApiClient api;
    private final Rules.Limits limits;

    public SettlementHandler() {
        this(new Stores(DynamoDbClient.create(), env("DEDUP_TABLE"), env("VELOCITY_TABLE")),
                new ApiClient(env("API_BASE_URL"), env("SERVICE_TOKEN")),
                new Rules.Limits(
                        Long.parseLong(env("MAX_SINGLE_CENTS")),
                        Long.parseLong(env("DAILY_LIMIT_CENTS")),
                        Integer.parseInt(env("PER_MINUTE_LIMIT"))));
    }

    SettlementHandler(Stores stores, ApiClient api, Rules.Limits limits) {
        this.stores = stores;
        this.api = api;
        this.limits = limits;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            if (!stores.tryClaim(message.getMessageId())) {
                continue; // duplicate delivery, already handled
            }
            try {
                process(message.getBody());
            } catch (Exception e) {
                // Release the dedup claim so the SQS retry actually reprocesses;
                // after maxReceiveCount the message lands in the DLQ.
                stores.releaseClaim(message.getMessageId());
                failures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }
        return new SQSBatchResponse(failures);
    }

    private void process(String body) throws Exception {
        JsonNode msg = MAPPER.readTree(body);
        long transferId = msg.get("transferId").asLong();
        long fromAccountId = msg.get("fromAccountId").asLong();
        long amountCents = msg.get("amountCents").asLong();

        long dailyTotal = stores.addDailyTotal(fromAccountId, amountCents);
        int minuteCount = stores.incrementMinuteCount(fromAccountId);

        Optional<String> rejection = Rules.check(amountCents, dailyTotal, minuteCount, limits);
        if (rejection.isPresent()) {
            api.fail(transferId, rejection.get());
        } else {
            api.settle(transferId);
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing env var: " + name);
        }
        return value;
    }
}
