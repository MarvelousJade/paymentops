# Decisions

One paragraph per non-obvious choice, newest last.

## 1. Modular monolith, not microservices

Single Spring Boot app, package-by-feature (`auth / customer / account / transfer /
statement / common`). A ledger's core invariant — money is conserved across a
debit and a credit — lives inside one ACID transaction, which one service and one
database give for free. Splitting accounts and transfers into services would turn
that invariant into a distributed-saga problem with no compensating benefit at
this scale. The package boundaries keep the seams visible; if one module ever
needs independent scaling, it can be extracted along its package line.

## 2. Spring Boot 4.0.x, not 3.5.x

The plan preferred Boot 3.5 with Boot 4 allowed "if springdoc and Spring Cloud AWS
support it". Spring Initializr no longer generates 3.5 projects, springdoc 3.x
targets Boot 4, and Spring Cloud AWS is only needed for the async-settlement
phase. Starting on 4.0.x avoids a guaranteed near-term major upgrade on a resume
project whose whole point is to look current.
