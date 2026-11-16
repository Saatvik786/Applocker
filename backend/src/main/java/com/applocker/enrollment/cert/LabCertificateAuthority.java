package com.applocker.enrollment.cert;

import com.applocker.config.ApplockerProperties;
import com.applocker.enrollment.EnrollmentRejectedException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Lab issuer for the enrollment test. The CA private key stays in the data directory
 * and is not copied to the test device. Only the CA certificate is installed there.
 */
@Component
public class LabCertificateAuthority {

    private static final Object FILE_LOCK = new Object();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path directory;
    private final Clock clock;
    private KeyPair caKey;
    private X509Certificate caCertificate;

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    public LabCertificateAuthority(ApplockerProperties properties, Clock clock) {
        this.directory = Path.of(properties.ca().directory());
        this.clock = clock;
    }

    public static String writeServerKeyStore(Path directory, List<String> hosts) {
        synchronized (FILE_LOCK) {
            try {
                Files.createDirectories(directory);
                AuthorityMaterial material = loadOrCreate(directory);
                KeyPair serverKey = rsa2048();
                X500Name subject = new X500Name("CN=" + sanName(hosts));
                X509Certificate serverCertificate = sign(
                        material,
                        subject,
                        serverKey.getPublic(),
                        false,
                        KeyPurposeId.id_kp_serverAuth,
                        hosts,
                        Duration.ofDays(825),
                        java.time.Instant.now());
                String password = readOrCreatePassword(directory);
                KeyStore store = KeyStore.getInstance("PKCS12");
                store.load(null, password.toCharArray());
                store.setKeyEntry(
                        "server",
                        serverKey.getPrivate(),
                        password.toCharArray(),
                        new Certificate[] {serverCertificate, material.certificate()});
                try (var output = Files.newOutputStream(directory.resolve("server.p12"))) {
                    store.store(output, password.toCharArray());
                }
                writeCertificate(directory.resolve("lab-ca.crt"), material.certificate());
                return password;
            } catch (Exception exception) {
                throw new IllegalStateException("Could not prepare the lab TLS certificate", exception);
            }
        }
    }

    public IssuedCertificate signDeviceRequest(byte[] pkcs10, String commonName) {
        try {
            PKCS10CertificationRequest request = new PKCS10CertificationRequest(pkcs10);
            JcaPKCS10CertificationRequest jcaRequest = new JcaPKCS10CertificationRequest(request);
            if (!request.isSignatureValid(new JcaContentVerifierProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(jcaRequest.getPublicKey()))) {
                throw new EnrollmentRejectedException();
            }
            if (!"RSA".equals(jcaRequest.getPublicKey().getAlgorithm())) {
                throw new EnrollmentRejectedException();
            }
            int bits = ((java.security.interfaces.RSAPublicKey) jcaRequest.getPublicKey()).getModulus().bitLength();
            if (bits < 2048) {
                throw new EnrollmentRejectedException();
            }
            AuthorityMaterial material = current();
            X509Certificate certificate = sign(
                    material,
                    new X500Name("CN=" + commonName),
                    jcaRequest.getPublicKey(),
                    false,
                    KeyPurposeId.id_kp_clientAuth,
                    List.of(),
                    Duration.ofDays(14),
                    clock.instant());
            return issued(certificate);
        } catch (EnrollmentRejectedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EnrollmentRejectedException();
        }
    }

    public IssuedCertificate root() {
        try {
            return issued(current().certificate());
        } catch (Exception exception) {
            throw new IllegalStateException("Lab CA is not available", exception);
        }
    }

    private AuthorityMaterial current() throws Exception {
        synchronized (FILE_LOCK) {
            if (caKey != null && caCertificate != null) {
                return new AuthorityMaterial(caKey, caCertificate);
            }
            AuthorityMaterial material = loadOrCreate(directory);
            caKey = material.keyPair();
            caCertificate = material.certificate();
            return material;
        }
    }

    private IssuedCertificate issued(X509Certificate certificate) throws Exception {
        byte[] encoded = certificate.getEncoded();
        String thumbprint = thumbprint(encoded);
        String base64 = Base64.getEncoder().encodeToString(encoded);
        return new IssuedCertificate(certificate, thumbprint, base64);
    }

    private static X509Certificate sign(
            AuthorityMaterial material,
            X500Name subject,
            java.security.PublicKey publicKey,
            boolean certificateAuthority,
            KeyPurposeId purpose,
            List<String> alternativeNames,
            Duration lifetime,
            java.time.Instant issuedAt) throws Exception {
        Date notBefore = Date.from(issuedAt.minus(Duration.ofMinutes(5)));
        Date notAfter = Date.from(issuedAt.plus(lifetime));
        JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                material.certificate(),
                serial(),
                notBefore,
                notAfter,
                subject,
                publicKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(certificateAuthority));
        int usage = certificateAuthority
                ? KeyUsage.keyCertSign | KeyUsage.cRLSign
                : KeyUsage.digitalSignature | KeyUsage.keyEncipherment;
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(usage));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(purpose));
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(publicKey));
        builder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                extensions.createAuthorityKeyIdentifier(material.certificate()));
        if (!alternativeNames.isEmpty()) {
            GeneralName[] names = alternativeNames.stream()
                    .map(LabCertificateAuthority::generalName)
                    .toArray(GeneralName[]::new);
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(material.keyPair().getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private static AuthorityMaterial loadOrCreate(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path keyPath = directory.resolve("ca-key.pem");
        Path certificatePath = directory.resolve("ca-cert.pem");
        if (Files.exists(keyPath) && Files.exists(certificatePath)) {
            KeyPair keyPair = readKeyPair(keyPath);
            X509Certificate certificate = readCertificate(certificatePath);
            return new AuthorityMaterial(keyPair, certificate);
        }
        KeyPair keyPair = rsa2048();
        X500Name name = new X500Name("CN=Applocker Lab CA");
        Date notBefore = Date.from(java.time.Instant.now().minus(Duration.ofMinutes(5)));
        Date notAfter = Date.from(java.time.Instant.now().plus(Duration.ofDays(1825)));
        JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name,
                serial(),
                notBefore,
                notAfter,
                name,
                keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
        writeKeyPair(keyPath, keyPair);
        writeCertificate(certificatePath, certificate);
        writeCertificate(directory.resolve("lab-ca.crt"), certificate);
        return new AuthorityMaterial(keyPair, certificate);
    }

    private static GeneralName generalName(String host) {
        if (host.chars().allMatch(character -> Character.isDigit(character) || character == '.' || character == ':')) {
            return new GeneralName(GeneralName.iPAddress, host);
        }
        return new GeneralName(GeneralName.dNSName, host);
    }

    private static String sanName(List<String> hosts) {
        for (String host : hosts) {
            if (!"localhost".equals(host) && !"127.0.0.1".equals(host)) {
                return host;
            }
        }
        return "localhost";
    }

    private static KeyPair rsa2048() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, RANDOM);
        return generator.generateKeyPair();
    }

    private static BigInteger serial() {
        BigInteger serial = new BigInteger(128, RANDOM).abs();
        return serial.signum() == 0 ? BigInteger.ONE : serial;
    }

    private static String readOrCreatePassword(Path directory) throws IOException {
        Path passwordFile = directory.resolve("keystore-password");
        if (Files.exists(passwordFile)) {
            String password = Files.readString(passwordFile).trim();
            if (!password.isBlank()) {
                return password;
            }
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Files.writeString(passwordFile, password);
        return password;
    }

    private static void writeKeyPair(Path path, KeyPair keyPair) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path); JcaPEMWriter pem = new JcaPEMWriter(writer)) {
            pem.writeObject(keyPair);
        }
    }

    private static void writeCertificate(Path path, X509Certificate certificate) throws Exception {
        try (Writer writer = Files.newBufferedWriter(path); JcaPEMWriter pem = new JcaPEMWriter(writer)) {
            pem.writeObject(certificate);
        }
    }

    private static KeyPair readKeyPair(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path); PEMParser parser = new PEMParser(reader)) {
            Object object = parser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (object instanceof org.bouncycastle.openssl.PEMKeyPair keyPair) {
                return converter.getKeyPair(keyPair);
            }
            throw new IllegalStateException("Lab CA key file is not a key pair");
        }
    }

    private static X509Certificate readCertificate(Path path) throws Exception {
        try (var input = Files.newInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private static String thumbprint(byte[] encoded) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-1").digest(encoded);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02X", value));
        }
        return hex.toString();
    }

    private record AuthorityMaterial(KeyPair keyPair, X509Certificate certificate) {
    }
}
