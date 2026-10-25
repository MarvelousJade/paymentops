import http from "k6/http";
import {check, sleep} from "k6";
import {Trend, Counter} from "k6/metrics";

const BASE = __ENV.BASE || "http://localhost:8080";
const lifecycle = new Trend("payment_lifecycle_ms", true);
const unexplained = new Counter("payment_unexplained_failures");

export const options = {
  scenarios: {
    payments: {
      executor: "constant-vus",
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || "60s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate==1"],
    payment_unexplained_failures: ["count==0"],
    http_req_duration: ["p(95)<1500", "p(99)<2500"],
    payment_lifecycle_ms: ["p(95)<5000", "p(99)<8000"],
  },
  summaryTrendStats: ["min", "med", "avg", "p(90)", "p(95)", "p(99)", "max"],
};

function json(method, path, body, token, headers = {}) {
  return http.request(method, `${BASE}${path}`, body ? JSON.stringify(body) : null, {
    headers: {
      "Content-Type": "application/json",
      ...(token ? {Authorization: `Bearer ${token}`} : {}),
      ...headers,
    },
  });
}

function register(label) {
  const unique = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  const response = json("POST", "/auth/register", {
    email: `${label}-${unique}@load.test`,
    password: "load-test-password",
    fullName: `${label} load test`,
  });
  check(response, {"registered": (r) => r.status === 201});
  return response.json("accessToken");
}

function account(token, amount) {
  const created = json("POST", "/accounts", {type: "CHEQUING"}, token);
  check(created, {"account created": (r) => r.status === 201});
  const id = created.json("id");
  if (amount) {
    const deposit = json("POST", `/accounts/${id}/deposits`, {amount}, token);
    check(deposit, {"account funded": (r) => r.status === 201});
  }
  return id;
}

export function setup() {
  const merchantToken = register("merchant");
  const payeeToken = register("payee");
  return {
    merchantToken,
    source: account(merchantToken, "1000000.00"),
    destination: account(payeeToken, null),
  };
}

function waitFor(token, paymentId, accepted, attempts = 30) {
  for (let i = 0; i < attempts; i++) {
    const response = json("GET", `/api/v1/payments/${paymentId}`, null, token);
    if (response.status === 200 && accepted.includes(response.json("status"))) return response.json();
    sleep(0.1);
  }
  return null;
}

export default function (data) {
  unexplained.add(0);
  const started = Date.now();
  const key = `${__VU}-${__ITER}-${Date.now()}-${Math.random()}`;
  const created = json("POST", "/api/v1/payments", {
    sourceAccountId: data.source,
    destinationAccountId: data.destination,
    amount: "1.00",
    currency: "CAD",
    paymentToken: `tok_load_${__VU}_${__ITER}`,
  }, data.merchantToken, {"Idempotency-Key": key});

  if (!check(created, {"payment accepted": (r) => r.status === 202})) {
    unexplained.add(1);
    return;
  }
  const paymentId = created.json("id");
  const authorized = waitFor(data.merchantToken, paymentId, ["AUTHORIZED", "REJECTED", "REVIEW_REQUIRED"]);
  if (!authorized || authorized.status !== "AUTHORIZED") {
    unexplained.add(1);
    return;
  }

  const captured = json("POST", `/api/v1/payments/${paymentId}/capture`, null,
    data.merchantToken, {"Idempotency-Key": `capture-${key}`});
  if (!check(captured, {"capture accepted": (r) => r.status === 202})) {
    unexplained.add(1);
    return;
  }
  const completed = waitFor(data.merchantToken, paymentId, ["SETTLED", "FAILED"]);
  if (check(completed, {"payment settled": (p) => p && p.status === "SETTLED"})) {
    lifecycle.add(Date.now() - started);
  } else {
    unexplained.add(1);
  }
}
