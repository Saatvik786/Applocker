package com.applocker.enrollment.soap;

import com.applocker.config.ApplockerProperties;
import com.applocker.device.DeviceRegistry;
import com.applocker.device.EnrolledDevice;
import com.applocker.enrollment.EnrollmentRejectedException;
import com.applocker.enrollment.EnrollmentTokenService;
import com.applocker.enrollment.SecretDigest;
import com.applocker.enrollment.EnrollmentTokenService.Claim;
import com.applocker.enrollment.cert.IssuedCertificate;
import com.applocker.enrollment.cert.LabCertificateAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class WindowsEnrollmentService {

    private static final Logger log = LoggerFactory.getLogger(WindowsEnrollmentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApplockerProperties properties;
    private final EnrollmentTokenService tokens;
    private final LabCertificateAuthority certificates;
    private final DeviceRegistry devices;
    private final Clock clock;

    public WindowsEnrollmentService(
            ApplockerProperties properties,
            EnrollmentTokenService tokens,
            LabCertificateAuthority certificates,
            DeviceRegistry devices,
            Clock clock) {
        this.properties = properties;
        this.tokens = tokens;
        this.certificates = certificates;
        this.devices = devices;
        this.clock = clock;
    }

    public byte[] discovery(UUID tokenId, String messageId, String requestedVersion) {
        tokens.requireActive(tokenId);
        String serviceUrl = serviceUrl(tokenId);
        log.info("Discovery request accepted");
        return EnrollmentMessages.discovery(messageId, requestedVersion, serviceUrl, serviceUrl);
    }

    public byte[] policies(UUID tokenId, Document document) {
        tokens.authorize(tokenId, XmlSupport.firstText(document, "Password"));
        log.info("Certificate policy request accepted");
        return EnrollmentMessages.policies(XmlSupport.firstText(document, "MessageID"));
    }

    public byte[] enroll(UUID tokenId, Document document) {
        tokens.authorize(tokenId, XmlSupport.firstText(document, "Password"));
        String encodedRequest = XmlSupport.firstText(document, "BinarySecurityToken");
        if (encodedRequest == null) {
            throw new EnrollmentRejectedException();
        }
        byte[] pkcs10;
        try {
            pkcs10 = Base64.getMimeDecoder().decode(encodedRequest.replaceAll("\\s", ""));
        } catch (IllegalArgumentException exception) {
            throw new EnrollmentRejectedException();
        }
        Map<String, String> context = XmlSupport.contextItems(document);
        String enrollmentType = limit(context.get("EnrollmentType"), 32);
        String deviceKey = identity(context.get("DeviceID"), pkcs10);
        String commonName = commonName(deviceKey);
        Claim claim = tokens.tryClaim(tokenId, deviceKey);
        if (claim == Claim.LIMIT) {
            throw new EnrollmentRejectedException();
        }
        try {
            IssuedCertificate client = certificates.signDeviceRequest(pkcs10, commonName);
            IssuedCertificate root = certificates.root();
            Instant enrolledAt = clock.instant();
            EnrolledDevice existing = devices.find(deviceKey);
            devices.save(new EnrolledDevice(
                    deviceKey,
                    deviceName(context.get("DeviceName"), commonName),
                    limit(context.get("OSVersion"), 32),
                    limit(context.get("EnrollmentType"), 32),
                    tokens.label(tokenId),
                    existing == null ? enrolledAt : existing.enrolledAt(),
                    client.certificate().getNotAfter().toInstant(),
                    client.thumbprint()));
            String provisioning = EnrollmentMessages.provisioningDocument(
                    root.thumbprint(),
                    root.base64(),
                    client.thumbprint(),
                    client.base64(),
                    managementUrl(),
                    properties.mdmProviderId(),
                    deviceName(context.get("DeviceName"), commonName),
                    commonName,
                    secret(),
                    Base64.getEncoder().encodeToString(randomBytes(16)),
                    secret());
            log.info(
                    "Certificate issued for device {} in My/User and My/System for enrollment type {}",
                    deviceName(context.get("DeviceName"), commonName),
                    enrollmentType);
            return EnrollmentMessages.enrollment(
                    XmlSupport.firstText(document, "MessageID"),
                    Base64.getEncoder().encodeToString(provisioning.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    enrolledAt);
        } catch (RuntimeException exception) {
            if (claim == Claim.CLAIMED) {
                tokens.release(tokenId, deviceKey);
            }
            throw exception;
        }
    }

    private String serviceUrl(UUID tokenId) {
        return base() + "/api/v1/mdm/windows/enrollment/service/" + tokenId;
    }

    private String managementUrl() {
        return base() + "/api/v1/mdm/windows/management";
    }

    private String base() {
        return properties.publicBaseUrl().replaceAll("/+$", "");
    }

    private static String identity(String deviceId, byte[] pkcs10) {
        if (deviceId != null && !deviceId.isBlank()) {
            String value = deviceId.replace("{", "").replace("}", "").trim();
            return value.length() <= 128 ? value : value.substring(0, 128);
        }
        return java.util.HexFormat.of().formatHex(SecretDigest.sha256(Base64.getEncoder().encodeToString(pkcs10)));
    }

    private static String commonName(String deviceKey) {
        String safe = deviceKey.replaceAll("[^A-Za-z0-9._-]", "");
        if (safe.isEmpty()) {
            safe = "device";
        }
        return safe.length() <= 64 ? safe : safe.substring(0, 64);
    }

    private static String deviceName(String raw, String fallback) {
        String cleaned = limit(raw, 64);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static String secret() {
        byte[] bytes = randomBytes(24);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
