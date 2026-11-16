# Windows provisioning and MDM

Status: the lab backend forms the discovery URL and completes enrollment. The steps for a disposable test device are in [enrollment-lab.md](enrollment-lab.md). No provisioning package is stored in this repository.

## What the package does

A `.ppkg` created for this project starts native Windows MDM enrollment. It does not install a custom agent, and it does not apply an AppLocker policy by itself.

Windows Configuration Designer builds the package. This backend only supplies the discovery URL and the enrollment token.

Enrollment and later MDM connectivity are tested separately. A device row created at enrollment is not evidence that an OMA-DM session succeeded.

## Values the administrator enters

After a token exists, the summary shows the label, the optional device limit, the expiry, this URL, and the secret:

`https://{public-host}/api/v1/mdm/windows/enrollment/discovery/{tokenId}`

In Windows Configuration Designer:

1. Install Windows Configuration Designer on a computer that is not a device being enrolled.
2. Create a provisioning package for desktop devices. Name it with the token label so the package and the token stay paired.
3. Open **Runtime settings → Workplace → Enrollments** and add a UPN. Microsoft uses that UPN as the enrollment identifier. For a bulk package it is a service-account name.
4. Set the three fields together:
   - `AuthPolicy` = `OnPremise`
   - `DiscoveryServiceFullUrl` = the formed URL
   - `Secret` = the same token
5. Leave `EnrollmentServiceFullUrl` and `PolicyServiceFullUrl` empty. Discovery returns those endpoints.
6. Export the package. Signing is optional. An unsigned package can be edited before it reaches a device, so sign it when a signing certificate is available.
7. Apply it on a disposable Windows VM during OOBE, after sign-in, or from USB or imaging media. Do not apply an enforcement test to a primary work or personal computer.

`OnPremise` keeps enrollment on the username-token path. The discovery response must also select on-premises authentication. The exact response field is taken from MS-MDE2 `DiscoverResponse` when that endpoint is implemented. Milestone 7 checks that the VM calls the token URL and does not stop on an interactive browser prompt.

Microsoft documents `AuthPolicy` as `OnPremise` or `Certificate`. This project uses `OnPremise`. `Secret` is the on-premises password string defined by the Provisioning CSP. Here that password is the `PPKG_BULK` token.

## What Windows does next

The native enrollment client:

1. Calls `DiscoveryServiceFullUrl` and validates the server TLS certificate.
2. Sends `GetPolicies` with the on-premises username token. The password must equal the token in the URL.
3. Sends `RequestSecurityToken` with the same credential and a PKCS#10 request.
4. Installs the client certificate, trust chain, and DM client settings from the Base64 WAP provisioning document.
5. Uses the built-in MDM client for later sessions. Those sessions are not part of applying the package.

The message shapes come from:

- [MS-MDE2 discovery](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-mde2/2681fd76-1997-4557-8963-cf656ab8d887)
- [MS-MDE2 GetPolicies](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-mde2/ff0847a2-c85f-41e6-99f4-b29556f37719)
- [MS-MDE2 RequestSecurityToken](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-mde2/09c69d4c-5315-4226-be5e-4cea54058317)
- [MS-MDE2 provisioning schema](https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-mde2/35e1aca6-1b8a-48ba-bbc0-23af5d46907a)
- [On-premises authentication device enrollment](https://learn.microsoft.com/en-us/windows/client-management/on-premise-authentication-device-enrollment)

DNS discovery uses `/EnrollmentServer/Discovery.svc`. This package does not use DNS discovery. It calls the full URL entered in Windows Configuration Designer.

## Certificate and failure behavior

No certificate issuer is configured. Until Milestone 6, the server cannot complete enrollment.

When that milestone exists, the device generates the private key. The server returns the issued certificate and chain inside the provisioning document. A failed discovery or policy request issues no certificate and does not consume a device slot. A failed enrollment request releases a reserved slot. A second attempt by the same device does not create a second device row.

The VM must already trust the HTTPS certificate of the public host. Installing that trust is a lab step, not a function of the enrollment token.

## AppLocker after enrollment

AppLocker policy is a later MDM command, not part of the `.ppkg`.

The command targets the AppLocker CSP nodes under `./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/{Grouping}/`. `{Grouping}` is a random GUID. Policy XML is a `RuleCollection` for `Exe`, `Dll`, `Msi`, `Script`, or `Appx`. The packaged-app CSP node is `StoreApps`.

This is not the `CodeIntegrity/Policy` node and not the ApplicationControl CSP. Those deliver App Control for Business binary policy.

The first delivery is audit-only where Windows supports that mode. Enforce mode is tested only on a disposable VM. The server does not default to a deny-all policy. A deployment stays unsuccessful until the device returns a success status for the command.

AppLocker is defense in depth. It is not supported on Server Core. Windows 10 version 2004 and later with KB5024351, and Windows 11, can enforce it on all editions. The Application Identity service must be running. How the MDM channel should ensure that is confirmed from Microsoft's documentation before policy delivery is implemented.

## Not implemented

- Token creation and the discovery URL
- Discovery, policy, and enrollment SOAP
- Certificate issuance and the WAP provisioning document
- Device persistence
- OMA-DM check-in and AppLocker CSP commands
- A stored or generated `.ppkg`
