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
    http_req_duration: ["p(95)<500"],
  },
};

const params = (token) => ({
  headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
});

export function setup() {
  const email = `k6_${Date.now()}@x.dev`;
  const register = http.post(`${BASE}/auth/register`, JSON.stringify({
    email, password: "password123", fullName: "K6 Smoke",
  }), { headers: { "Content-Type": "application/json" } });
  const token = register.json("accessToken");

  const mkAccount = () => http.post(`${BASE}/accounts`, JSON.stringify({ type: "CHEQUING" }), params(token)).json("id");
  const a = mkAccount();
  const b = mkAccount();
  http.post(`${BASE}/accounts/${a}/deposits`, JSON.stringify({ amount: "1000000.00" }), params(token));
  http.post(`${BASE}/accounts/${b}/deposits`, JSON.stringify({ amount: "1000000.00" }), params(token));
  return { token, a, b };
}

export default function (data) {
  const { token, a, b } = data;
  if (__ITER % 3 === 0) {
    // 1 in 3: a transfer with a fresh idempotency key, direction alternating per VU
    const [from, to] = __VU % 2 === 0 ? [a, b] : [b, a];
    const res = http.post(`${BASE}/transfers`, JSON.stringify({
      fromAccountId: from, toAccountId: to, amount: "1.00",
    }), {
      headers: {
        ...params(token).headers,
        "Idempotency-Key": `k6-${__VU}-${__ITER}-${Date.now()}`,
      },
    });
    check(res, { "transfer 201": (r) => r.status === 201 });
  } else {
    // 2 in 3: paginated transaction reads
    const res = http.get(`${BASE}/accounts/${__VU % 2 === 0 ? a : b}/transactions?size=20`, params(token));
    check(res, { "transactions 200": (r) => r.status === 200 });
  }
  sleep(0.1);
}
