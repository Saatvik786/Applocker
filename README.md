# AppLocker policy management

A personal project for enrolling Windows devices through the built-in MDM client and, later, delivering AppLocker policy through the AppLocker CSP.

The backend does not build a provisioning package. Windows Configuration Designer does. This service will issue a reusable enrollment token and a discovery URL. An administrator pastes those values into the package.

## Current status

The backend can enroll a Windows test device. An administrator creates a token, Windows Configuration Designer builds the `.ppkg`, and the device calls discovery, certificate policy, and enrollment. The service issues a certificate from a lab CA and returns the provisioning document.

Token and device state is kept in memory. A restart clears it. Neon, Redis, Flyway, and the dashboard are not connected. Enrollment does not apply an AppLocker policy. The MDM endpoint only acknowledges a management session.

The test steps are in [docs/enrollment-lab.md](docs/enrollment-lab.md). Apply the package only on a disposable Windows device.

## Management path

1. An administrator will create a `PPKG_BULK` token. The service will show the secret once and form:

   `https://{public-host}/api/v1/mdm/windows/enrollment/discovery/{tokenId}`

2. In Windows Configuration Designer, under **Runtime settings → Workplace → Enrollments**, the administrator sets:
   - `AuthPolicy` = `OnPremise`
   - `DiscoveryServiceFullUrl` = the formed URL
   - `Secret` = the same token
3. Windows calls that URL and runs discovery, certificate policy, and enrollment itself.
4. After enrollment, the built-in MDM client is the ongoing management channel.
5. AppLocker policy delivery comes later, through the documented AppLocker CSP. It is separate from App Control for Business and the Code Integrity node.

A custom Windows agent is not part of this design.

## Stack

The later milestones use:

- Java 25
- Spring Boot 4.1.1, which [officially supports Java 17 through 26](https://docs.spring.io/spring-boot/system-requirements.html)
- Maven
- PostgreSQL on Neon, with Flyway
- Redis for expiring enrollment-token state
- React, TypeScript, and Vite for the dashboard, after the APIs exist

The lab creates its own CA under `backend/data/lab-ca` and installs only the public certificate on the test device. There is no enterprise CA and no SCEP service.

## Documents

- [Architecture](docs/architecture.md)
- [Security](docs/security.md)
- [Windows provisioning and MDM](docs/windows-management.md)
- [Milestones](docs/milestones.md)

## Secrets

Do not commit `.env` files, tokens, private keys, certificates, or `.ppkg` files. A package contains the enrollment token. Possession of the package allows enrollment until the token expires, is revoked, or reaches its device limit. Revoking a token does not unenroll devices that already received a certificate.

## Run the backend

Install JDK 25. Maven is not required: the Maven Wrapper in `backend` downloads Maven 3.9.11 on first use.

```powershell
cd backend
.\mvnw.cmd verify
$env:PUBLIC_BASE_URL = "https://192.168.1.20:8443"
.\mvnw.cmd spring-boot:run
```

Replace `192.168.1.20` with an address of this computer that the test device can reach. On macOS or Linux, use `./mvnw`. The health check is `GET https://<that-address>:8443/api/v1/health`. The first start writes a lab CA and an admin token under `backend/data`. Follow [docs/enrollment-lab.md](docs/enrollment-lab.md) before applying a package.

## Next step

Connect Neon and Redis so tokens and devices survive a restart, then confirm an MDM session from the test device. AppLocker policy delivery stays separate from enrollment.
