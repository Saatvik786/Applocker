# Enrollment lab

This is the path for one disposable Windows device. The backend does not build the `.ppkg`. Windows Configuration Designer does. Do not apply the package to the computer that runs this service.

Token and device rows live in memory. Leave the server running from token creation through enrollment. A restart invalidates the token and the lab TLS certificate name stays valid only if the same CA files remain in `backend/data/lab-ca`.

## 1. Start the service on the computer that will receive enrollment calls

From `backend`, set the address the test device will use and start the app:

```powershell
$env:PUBLIC_BASE_URL = "https://192.168.1.20:8443"
.\mvnw.cmd spring-boot:run
```

Use one of the addresses printed at startup, not `localhost`. The startup log also prints:

- the CA certificate to copy, `data\lab-ca\lab-ca.crt`
- the admin token file, `data\admin-token`

`data\lab-ca` contains the CA private key. Copy only `lab-ca.crt` to the test device. Allow inbound TCP 8443 on this computer if the test device cannot connect.

## 2. Create the token

In another terminal, from `backend`:

```powershell
$admin = (Get-Content .\data\admin-token).Trim()
curl.exe -k -H "Authorization: Bearer $admin" -H "Content-Type: application/json" -d '{"label":"lab-vm","deviceLimit":2,"ttlHours":48}' https://localhost:8443/api/v1/enrollment-tokens
```

The response shows `discoveryUrl` and `secret`. They are the same token id. Save both. The list endpoint does not show them again.

## 3. Trust the lab CA on the test device only

Copy `lab-ca.crt` to the test device and install it in the local machine Trusted Root Certification Authorities store:

```powershell
certutil -addstore -f Root lab-ca.crt
```

Run that on the test device, in an elevated PowerShell. Windows refuses the enrollment call when it does not trust the discovery host.

## 4. Build the package

On a computer with Windows Configuration Designer, not on the device you are enrolling:

1. Create a desktop provisioning package. Name it `lab-vm`.
2. Open **Runtime settings → Workplace → Enrollments** and add a UPN, for example `device@lab.test`.
3. Set `AuthPolicy` to `OnPremise`.
4. Paste `discoveryUrl` into `DiscoveryServiceFullUrl`.
5. Paste `secret` into `Secret`.
6. Leave `EnrollmentServiceFullUrl` and `PolicyServiceFullUrl` empty.
7. Export the package. Do not put the `.ppkg` in this repository.

## 5. Apply it on the test device

Apply the package on the disposable device. The server log should show discovery accepted and a certificate issued. It does not print the token.

Check the device row from the server computer:

```powershell
curl.exe -k -H "Authorization: Bearer $admin" https://localhost:8443/api/v1/devices
```

A device object means enrollment stored the device. `managementRequests` greater than zero means a later management session reached the server. Enrollment can succeed while that count stays at zero. This lab acknowledges a management session and does not deliver AppLocker policy.

Revoking the token stops a new enrollment. It does not unenroll a device that already received a certificate:

```powershell
curl.exe -k -H "Authorization: Bearer $admin" -H "Content-Type: application/json" -d '{"label":"lab-vm"}' https://localhost:8443/api/v1/enrollment-tokens/revocations
```
