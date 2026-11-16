# Milestones

Milestones 1 and 2 are complete. A lab enrollment path is in place for a disposable Windows device: token creation, discovery, certificate policy, certificate issue, and the provisioning document. Token and device state is in memory, so a restart clears it. Neon, Redis, and Flyway are not connected, and those milestone checks are still open.

## Milestone 1 — documentation

Done. The docs describe native MDM enrollment, the token-scoped discovery URL, Redis-backed `PPKG_BULK` tokens, the three Windows Configuration Designer fields, and later AppLocker CSP delivery. A custom agent is out of scope.

## Milestone 2 — backend scaffold

Done. The Spring Boot 4.1.1 build runs on Java 25. The effective compiler release is 25. `mvn verify` passes. `GET /api/v1/health` reports status without a database. The `local`, `deploy`, and `test` profiles load `PUBLIC_BASE_URL` from the environment. The deploy profile rejects a blank value.

## Milestone 3 — Neon, Flyway, and Redis

The app connects to Neon with SSL and to Redis. Flyway applies, and `ddl-auto=validate` passes. Tests cover both stores when Testcontainers is available. The Redis server version is checked against the Spring Data Redis version managed by Boot 4.1.1. pgAdmin instructions use placeholders.

## Milestone 4 — enrollment tokens

An authenticated administrator can create, list, and revoke a token. The summary shows the label, device limit, expiry, discovery URL, and secret once. The URL ends with the token id, and the secret is that same value. Expiry, revocation, labels, ownership, and an optional device limit work. Concurrent enrollments cannot pass the limit. Logs, traces, and errors do not contain the token, including the URL path segment. Revocation blocks a new enrollment and leaves an already enrolled device enrolled.

## Milestone 5 — discovery, policy, and enrollment SOAP

Discovery is served at `/api/v1/mdm/windows/enrollment/discovery/{tokenId}`. `GetPolicies` and `RequestSecurityToken` follow MS-MDE2, MS-XCEP, and MS-WSTEP. The discovery response selects on-premises authentication. Tests cover a valid token, a token that does not match the URL, an expired token, a revoked token, and a token at its device limit. Protocol assertions are traced to those specifications.

## Milestone 6 — certificates, provisioning document, and devices

A configured issuer signs the CSR. The response contains the Base64 WAP provisioning document required by MS-MDE2, including the client certificate and trust chain. The device row is stored once for that device identity. No CA is assumed to exist before this milestone. Private keys are not committed or logged.

## Milestone 7 — provisioning package on a disposable VM

An administrator adds a UPN under Workplace, Enrollments, sets `AuthPolicy` to `OnPremise`, pastes the formed discovery URL, pastes the same value as `Secret`, and leaves the enrollment and policy URL fields empty. The exported `.ppkg` enrolls the device, and the server sees a request to that token URL. The device row exists. A second check shows whether an MDM session reaches the server. The primary workstation is not used. The package is not committed.

## Milestone 8 — native MDM sessions

An enrolled device completes a native MDM session. The server delivers a command, records the device status, retries safely, and handles certificate renewal as specified. Enrollment success alone does not pass this milestone.

## Milestone 9 — AppLocker CSP

An assigned audit-only AppLocker policy is delivered through the documented AppLocker CSP nodes and accepted by a disposable VM. The payload is `RuleCollection` XML, not a Code Integrity policy. Success is recorded only after the device status says the command succeeded. Enforce mode stays behind the extra confirmation.

## After Milestone 9

The admin dashboard, durable audit trail, security hardening, integration tests, and release documentation come next. The dashboard uses live APIs once those APIs exist. Unresolved limits stay labeled, including AppLocker event-log collection.
