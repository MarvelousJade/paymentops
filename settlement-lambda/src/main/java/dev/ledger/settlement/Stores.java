package dev.ledger.settlement;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** DynamoDB-backed dedup and velocity state. Both tables use on-demand billing and TTL. */
public class Stores {

    private final DynamoDbClient dynamo;
    private final String dedupTable;
    private final String velocityTable;

    public Stores(DynamoDbClient dynamo, String dedupTable, String velocityTable) {
        this.dynamo = dynamo;
        this.dedupTable = dedupTable;
        this.velocityTable = velocityTable;
    }

    /** Conditional put: first caller wins. SQS is at-least-once; this is the message-level dedup layer. */
    public boolean tryClaim(String messageId) {
        try {
            dynamo.putItem(b -> b.tableName(dedupTable)
                    .item(Map.of(
                            "pk", AttributeValue.fromS(messageId),
                            "ttl", AttributeValue.fromN(String.valueOf(Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond()))))
                    .conditionExpression("attribute_not_exists(pk)"));
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /** Compensation for a failed downstream call: let the SQS retry reprocess the message. */
    public void releaseClaim(String messageId) {
        dynamo.deleteItem(b -> b.tableName(dedupTable).key(Map.of("pk", AttributeValue.fromS(messageId))));
    }

    /** Atomic ADD; returns the account's daily total including this amount. */
    public long addDailyTotal(long accountId, long amountCents) {
        return addAndGet("daily#" + accountId + "#" + LocalDate.now(ZoneOffset.UTC), amountCents,
                Instant.now().plus(2, ChronoUnit.DAYS));
    }

    /** Atomic ADD; returns the account's transfer count for the current minute including this one. */
    public int incrementMinuteCount(long accountId) {
        long minute = Instant.now().getEpochSecond() / 60;
        return (int) addAndGet("minute#" + accountId + "#" + minute, 1,
                Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private long addAndGet(String key, long delta, Instant expiry) {
        // On a retry after a downstream failure the ADD runs again, over-counting.
        // Over-counting only rejects sooner — the safe direction for limit checks.
        var response = dynamo.updateItem(b -> b.tableName(velocityTable)
                .key(Map.of("pk", AttributeValue.fromS(key)))
                .updateExpression("ADD #v :d SET #t = if_not_exists(#t, :ttl)")
                .expressionAttributeNames(Map.of("#v", "val", "#t", "ttl"))
                .expressionAttributeValues(Map.of(
                        ":d", AttributeValue.fromN(String.valueOf(delta)),
                        ":ttl", AttributeValue.fromN(String.valueOf(expiry.getEpochSecond()))))
                .returnValues(ReturnValue.UPDATED_NEW));
        return Long.parseLong(response.attributes().get("val").n());
    }
}
