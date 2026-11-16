package com.applocker.enrollment.cert;

import java.security.cert.X509Certificate;

public record IssuedCertificate(X509Certificate certificate, String thumbprint, String base64) {
}
