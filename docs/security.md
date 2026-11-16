# Security

Status: design constraints, plus a lab enrollment path. The lab keeps tokens in process memory, protects token creation with a bearer token, and issues device certificates from a local CA. Redis and Neon are not connected yet. The CA private key stays under `backend/data/lab-ca` and must not be copied to the test device.

## Trust boundary

The administrator, the backend, Redis, Neon, and the Windows device are separate principals.

- An administrator authenticates to the management API. Creating, listing, and revoking an enrollment token requires that authentication.
- The discovery, policy, and enrollment endpoints are device-facing. The token id in the URL selects the enrollment record. The SOAP password must be that same token. An administrator session is not accepted there.
- Redis holds enrollment-token state. Neon holds devices, public certificate metadata, policies, deployments, and audit records.
- After enrollment, the device authenticates to the MDM endpoint with the credential installed from the provisioning document. The enrollment token is not the ongoing MDM credential.
- TLS protects the public endpoints. Neon connections use SSL. `sslmode=require` is the minimum, following current Neon guidance.

## Enrollment token

The token is reusable until it expires, is revoked, or reaches its optional device limit. It is shown once to the administrator, then stored in Redis as a hash with a TTL. The raw value also appears in:

- `DiscoveryServiceFullUrl`, as the last path segment
- `Secret` inside the provisioning package

Anyone with the package can enroll a device until one of those limits applies. The package is not committed.

Revocation stops new enrollment. It does not remove management from devices that already received a client certificate. Unenroll is a later MDM operation.

Concurrent enrollments must not exceed the device limit. The count changes atomically. A failed certificate issuance releases a reserved slot. A repeated enrollment of the same device does not consume another slot. The lab uses the `DeviceID` value from the enrollment request. If that value is missing, it uses a hash of the certificate request so a retry of the same request does not take another slot.

## What must not leak

The token is intentionally part of the discovery URL, so the server redacts that path segment from application logs, access logs, traces, and error messages. SOAP bodies, the `PasswordText` value, administrator passwords, refresh tokens, and private keys are not logged.

Error responses say that enrollment was rejected. They do not echo the token.

Git must not contain:

- `.env` files or real connection strings
- enrollment tokens
- private keys, PKCS#12 files, or certificate material
- exported `.ppkg` files

`.env.example`, added with the backend scaffold, contains placeholders only.

## Certificates

No certificate authority or SCEP service is available. Milestone 6 introduces an issuer boundary. The client generates the private key and sends a CSR. This backend does not store the client private key. The CA private key is an environment secret.

The sample `APPAUTH` passwords in the MS-MDE2 provisioning example are sample data. They are not copied into a provisioning response.

The lab device must trust the TLS certificate presented by the discovery host. The enrollment token does not create that trust.

## Policy changes

Policy create, assign, and deploy actions require an authorized administrator and are written to the administrative audit trail. The audit record identifies the actor and the policy version. It does not need the enrollment token.

AppLocker XML is parsed with external entities disabled and with a size limit. A deny-all payload is rejected. Audit-only is the first delivery mode. Enforce mode on a primary workstation is out of scope.

## Failure behavior

A discovery or policy-request failure issues no certificate and consumes no device slot. A failed enrollment request releases a reserved slot and returns the fault required by the protocol. Revocation is checked again before a certificate is issued. An enrollment response is not treated as a successful MDM check-in. A failed MDM command stays failed until the device reports success.
