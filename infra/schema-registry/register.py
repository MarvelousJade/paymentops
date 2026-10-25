import json
import os
import pathlib
import urllib.request

base = os.environ.get("SCHEMA_REGISTRY_URL", "http://schema-registry:8081")
schemas = {
    "paymentops-event-envelope-v1": "/schemas/event-envelope-v1.schema.json",
    "payment-requested-v1-value": "/schemas/payment-requested-v1.schema.json",
    "settlement-record-v1-value": "/schemas/settlement-record-v1.schema.json",
}

for subject, filename in schemas.items():
    schema = pathlib.Path(filename).read_text(encoding="utf-8")
    body = json.dumps({"schemaType": "JSON", "schema": schema}).encode()
    request = urllib.request.Request(
        f"{base}/subjects/{subject}/versions",
        data=body,
        headers={"Content-Type": "application/vnd.schemaregistry.v1+json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        print(subject, response.read().decode())
