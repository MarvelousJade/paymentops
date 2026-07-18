import http from "k6/http";
import { check, sleep } from "k6";

// 100 VUs mixing transfers and transaction reads against a seeded pair of accounts.
// Usage: k6 run k6/smoke.js -e BASE=http://localhost:8080
const BASE = __ENV.BASE || "http://localhost:8080";

export const options = {
  stages: [
    { duration: "20s", target: 100 },
    { duration: "60s", target: 100 },
    { duration: "10s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:transfer}": ["p(95)<1000"],
    "http_req_duration{name:transactions}": ["p(95)<500"],
  },
};

const params = (token) => ({
  headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
});

const ACCOUNTS = 10;

export function setup() {
  const email = `k6_${Date.now()}@x.dev`;
  const register = http.post(`${BASE}/auth/register`, JSON.stringify({
    email, password: "password123", fullName: "K6 Smoke",
  }), { headers: { "Content-Type": "application/json" } });
  const token = register.json("accessToken");

  const ids = [];
  for (let i = 0; i < ACCOUNTS; i++) {
    const id = http.post(`${BASE}/accounts`, JSON.stringify({ type: "CHEQUING" }), params(token)).json("id");
    http.post(`${BASE}/accounts/${id}/deposits`, JSON.stringify({ amount: "1000000.00" }), params(token));
    ids.push(id);
  }
  return { token, ids };
}

export default function (data) {
  const { token, ids } = data;
  const mine = ids[__VU % ACCOUNTS];
  if (__ITER % 3 === 0) {
    // 1 in 3: transfer to a neighbouring account; overlapping pairs still exercise lock ordering
    const to = ids[(__VU + 1 + (__ITER % (ACCOUNTS - 1))) % ACCOUNTS];
    const res = http.post(`${BASE}/transfers`, JSON.stringify({
      fromAccountId: mine, toAccountId: to, amount: "1.00",
    }), {
      headers: {
        ...params(token).headers,
        "Idempotency-Key": `k6-${__VU}-${__ITER}-${Date.now()}`,
      },
      tags: { name: "transfer" },
    });
    check(res, { "transfer 201": (r) => r.status === 201 });
  } else {
    // 2 in 3: paginated transaction reads
    const res = http.get(`${BASE}/accounts/${mine}/transactions?size=20`,
      { ...params(token), tags: { name: "transactions" } });
    check(res, { "transactions 200": (r) => r.status === 200 });
  }
  sleep(0.1);
}
