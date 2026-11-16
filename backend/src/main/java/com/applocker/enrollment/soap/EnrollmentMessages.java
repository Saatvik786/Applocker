package com.applocker.enrollment.soap;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * SOAP bodies taken from Microsoft's on-premises enrollment example.
 * The hash OID is SHA-256. The enrollment client installs the certificate in
 * the store for its own session. The DMClient account always refers to
 * My\System so later sync runs as the machine.
 */
public final class EnrollmentMessages {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private EnrollmentMessages() {
    }

    public static byte[] discovery(String messageId, String requestedVersion, String policyUrl, String enrollUrl) {
        String version = version(requestedVersion);
        String xml = """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing">
                  <s:Header>
                    <a:Action s:mustUnderstand="1">http://schemas.microsoft.com/windows/management/2012/01/enrollment/IDiscoveryService/DiscoverResponse</a:Action>
                    <ActivityId>%s</ActivityId>
                    <a:RelatesTo>%s</a:RelatesTo>
                  </s:Header>
                  <s:Body xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                    <DiscoverResponse xmlns="http://schemas.microsoft.com/windows/management/2012/01/enrollment">
                      <DiscoverResult>
                        <AuthPolicy>OnPremise</AuthPolicy>
                        <EnrollmentVersion>%s</EnrollmentVersion>
                        <EnrollmentPolicyServiceUrl>%s</EnrollmentPolicyServiceUrl>
                        <EnrollmentServiceUrl>%s</EnrollmentServiceUrl>
                      </DiscoverResult>
                    </DiscoverResponse>
                  </s:Body>
                </s:Envelope>
                """.formatted(
                XmlSupport.xml(UUID.randomUUID().toString()),
                XmlSupport.xml(limit(messageId, 200)),
                XmlSupport.xml(version),
                XmlSupport.xml(policyUrl),
                XmlSupport.xml(enrollUrl));
        return XmlSupport.utf8(xml);
    }

    public static byte[] policies(String messageId) {
        // MS-XCEP requires hashAlgorithmOIDReference to point to an OID in group 1
        // (hash algorithms). Advertise SHA-256 itself, not sha256WithRSAEncryption,
        // which is a group 4 signature algorithm.
        String xml = """
                <s:Envelope xmlns:u="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd" xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing">
                  <s:Header>
                    <a:Action s:mustUnderstand="1">http://schemas.microsoft.com/windows/pki/2009/01/enrollmentpolicy/IPolicy/GetPoliciesResponse</a:Action>
                    <a:RelatesTo>%s</a:RelatesTo>
                  </s:Header>
                  <s:Body xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
                    <GetPoliciesResponse xmlns="http://schemas.microsoft.com/windows/pki/2009/01/enrollmentpolicy">
                      <response>
                        <policyID/>
                        <policyFriendlyName xsi:nil="true"/>
                        <nextUpdateHours xsi:nil="true"/>
                        <policiesNotChanged xsi:nil="true"/>
                        <policies>
                          <policy>
                            <policyOIDReference>0</policyOIDReference>
                            <cAs xsi:nil="true"/>
                            <attributes>
                              <commonName>ApplockerDevice</commonName>
                              <policySchema>3</policySchema>
                              <certificateValidity>
                                <validityPeriodSeconds>1209600</validityPeriodSeconds>
                                <renewalPeriodSeconds>172800</renewalPeriodSeconds>
                              </certificateValidity>
                              <permission>
                                <enroll>true</enroll>
                                <autoEnroll>false</autoEnroll>
                              </permission>
                              <privateKeyAttributes>
                                <minimalKeyLength>2048</minimalKeyLength>
                                <keySpec xsi:nil="true"/>
                                <keyUsageProperty xsi:nil="true"/>
                                <permissions xsi:nil="true"/>
                                <algorithmOIDReference xsi:nil="true"/>
                                <cryptoProviders xsi:nil="true"/>
                              </privateKeyAttributes>
                              <revision>
                                <majorRevision>101</majorRevision>
                                <minorRevision>0</minorRevision>
                              </revision>
                              <supersededPolicies xsi:nil="true"/>
                              <privateKeyFlags xsi:nil="true"/>
                              <subjectNameFlags xsi:nil="true"/>
                              <enrollmentFlags xsi:nil="true"/>
                              <generalFlags xsi:nil="true"/>
                              <hashAlgorithmOIDReference>0</hashAlgorithmOIDReference>
                              <rARequirements xsi:nil="true"/>
                              <keyArchivalAttributes xsi:nil="true"/>
                              <extensions xsi:nil="true"/>
                            </attributes>
                          </policy>
                        </policies>
                      </response>
                      <cAs xsi:nil="true"/>
                      <oIDs>
                        <oID>
                          <value>2.16.840.1.101.3.4.2.1</value>
                          <group>1</group>
                          <oIDReferenceID>0</oIDReferenceID>
                          <defaultName>szOID_NIST_sha256</defaultName>
                        </oID>
                      </oIDs>
                    </GetPoliciesResponse>
                  </s:Body>
                </s:Envelope>
                """.formatted(XmlSupport.xml(limit(messageId, 200)));
        return XmlSupport.utf8(xml);
    }

    public static byte[] enrollment(String messageId, String provisioningBase64, Instant issuedAt) {
        String created = TIMESTAMP.format(issuedAt.truncatedTo(ChronoUnit.MILLIS));
        String expires = TIMESTAMP.format(issuedAt.plusSeconds(300).truncatedTo(ChronoUnit.MILLIS));
        String xml = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" xmlns:a="http://www.w3.org/2005/08/addressing" xmlns:u="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">
                  <s:Header>
                    <a:Action s:mustUnderstand="1">http://schemas.microsoft.com/windows/pki/2009/01/enrollment/RSTRC/wstep</a:Action>
                    <a:RelatesTo>%s</a:RelatesTo>
                    <o:Security s:mustUnderstand="1" xmlns:o="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                      <u:Timestamp u:Id="_0">
                        <u:Created>%s</u:Created>
                        <u:Expires>%s</u:Expires>
                      </u:Timestamp>
                    </o:Security>
                  </s:Header>
                  <s:Body>
                    <RequestSecurityTokenResponseCollection xmlns="http://docs.oasis-open.org/ws-sx/ws-trust/200512">
                      <RequestSecurityTokenResponse>
                        <TokenType>http://schemas.microsoft.com/5.0.0.0/ConfigurationManager/Enrollment/DeviceEnrollmentToken</TokenType>
                        <DispositionMessage xmlns="http://schemas.microsoft.com/windows/pki/2009/01/enrollment"/>
                        <RequestedSecurityToken>
                          <BinarySecurityToken ValueType="http://schemas.microsoft.com/5.0.0.0/ConfigurationManager/Enrollment/DeviceEnrollmentProvisionDoc" EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd#base64binary" xmlns="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">%s</BinarySecurityToken>
                        </RequestedSecurityToken>
                        <RequestID xmlns="http://schemas.microsoft.com/windows/pki/2009/01/enrollment">0</RequestID>
                      </RequestSecurityTokenResponse>
                    </RequestSecurityTokenResponseCollection>
                  </s:Body>
                </s:Envelope>
                """.formatted(XmlSupport.xml(limit(messageId, 200)), created, expires, provisioningBase64);
        return XmlSupport.utf8(xml);
    }

    public static String provisioningDocument(
            String rootThumbprint,
            String rootBase64,
            String clientThumbprint,
            String clientBase64,
            String managementUrl,
            String providerId,
            String deviceName,
            String commonName,
            String clientSecret,
            String clientNonce,
            String serverSecret) {
        // The sample encoding is application/vnd.syncml.dm+wbxml. This lab requests XML.
        // The certificate is installed in My\User and My\System. Enrollment finishes
        // because the user-store copy exists. Sync runs as the machine, and the
        // user-store copy does not expose its private key, which surfaces as
        // 0x80072f9a. The account therefore searches My\System. DMClient rejects
        // ROLE and the SslClientCert* parms, so those stay off this document.
        String criteria = "Subject=CN%3d" + commonName + "&amp;Stores=My%5CSystem";
        return """
                <wap-provisioningdoc version="1.1">
                  <characteristic type="CertificateStore">
                    <characteristic type="Root">
                      <characteristic type="System">
                        <characteristic type="%s">
                          <parm name="EncodedCertificate" value="%s"/>
                        </characteristic>
                      </characteristic>
                    </characteristic>
                  </characteristic>
                  <characteristic type="CertificateStore">
                    <characteristic type="My">
                      <characteristic type="User">
                        <characteristic type="%s">
                          <parm name="EncodedCertificate" value="%s"/>
                        </characteristic>
                        <characteristic type="PrivateKeyContainer"/>
                      </characteristic>
                      <characteristic type="System">
                        <characteristic type="%s">
                          <parm name="EncodedCertificate" value="%s"/>
                        </characteristic>
                        <characteristic type="PrivateKeyContainer"/>
                      </characteristic>
                      <characteristic type="WSTEP">
                        <characteristic type="Renew">
                          <parm name="ROBOSupport" value="true" datatype="boolean"/>
                          <parm name="RenewPeriod" value="60" datatype="integer"/>
                          <parm name="RetryInterval" value="4" datatype="integer"/>
                        </characteristic>
                      </characteristic>
                    </characteristic>
                  </characteristic>
                  <characteristic type="APPLICATION">
                    <parm name="APPID" value="w7"/>
                    <parm name="ROLE" value="4294967295"/>
                    <parm name="PROVIDER-ID" value="%s"/>
                    <parm name="NAME" value="%s"/>
                    <parm name="ADDR" value="%s"/>
                    <parm name="CONNRETRYFREQ" value="6"/>
                    <parm name="INITIALBACKOFFTIME" value="30000"/>
                    <parm name="MAXBACKOFFTIME" value="120000"/>
                    <parm name="BACKCOMPATRETRYDISABLED"/>
                    <parm name="DEFAULTENCODING" value="application/vnd.syncml.dm+xml"/>
                    <parm name="SSLCLIENTCERTSEARCHCRITERIA" value="%s"/>
                    <characteristic type="APPAUTH">
                      <parm name="AAUTHLEVEL" value="CLIENT"/>
                      <parm name="AAUTHTYPE" value="DIGEST"/>
                      <parm name="AAUTHSECRET" value="%s"/>
                      <parm name="AAUTHDATA" value="%s"/>
                    </characteristic>
                    <characteristic type="APPAUTH">
                      <parm name="AAUTHLEVEL" value="APPSRV"/>
                      <parm name="AAUTHTYPE" value="BASIC"/>
                      <parm name="AAUTHNAME" value="%s"/>
                      <parm name="AAUTHSECRET" value="%s"/>
                    </characteristic>
                  </characteristic>
                  <characteristic type="DMClient">
                    <characteristic type="Provider">
                      <characteristic type="%s">
                        <characteristic type="Poll">
                          <parm name="NumberOfFirstRetries" value="8" datatype="integer"/>
                          <parm name="IntervalForFirstSetOfRetries" value="15" datatype="integer"/>
                          <parm name="NumberOfSecondRetries" value="5" datatype="integer"/>
                          <parm name="IntervalForSecondSetOfRetries" value="3" datatype="integer"/>
                          <parm name="NumberOfRemainingScheduledRetries" value="0" datatype="integer"/>
                          <parm name="IntervalForRemainingScheduledRetries" value="1560" datatype="integer"/>
                          <parm name="PollOnLogin" value="true" datatype="boolean"/>
                        </characteristic>
                        <parm name="EntDeviceName" value="%s" datatype="string"/>
                      </characteristic>
                    </characteristic>
                  </characteristic>
                </wap-provisioningdoc>
                """.formatted(
                XmlSupport.xml(rootThumbprint),
                rootBase64,
                XmlSupport.xml(clientThumbprint),
                clientBase64,
                XmlSupport.xml(clientThumbprint),
                clientBase64,
                XmlSupport.xml(providerId),
                XmlSupport.xml(providerId),
                XmlSupport.xml(managementUrl),
                criteria,
                XmlSupport.xml(clientSecret),
                XmlSupport.xml(clientNonce),
                XmlSupport.xml(providerId),
                XmlSupport.xml(serverSecret),
                XmlSupport.xml(providerId),
                XmlSupport.xml(deviceName));
    }

    public static byte[] fault() {
        String xml = """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                  <s:Body>
                    <s:Fault>
                      <s:Code><s:Value>s:Sender</s:Value></s:Code>
                      <s:Reason><s:Text xml:lang="en">Enrollment rejected</s:Text></s:Reason>
                    </s:Fault>
                  </s:Body>
                </s:Envelope>
                """;
        return XmlSupport.utf8(xml);
    }

    private static String version(String requested) {
        if (requested != null && requested.trim().matches("[0-9]+(\\.[0-9]+){0,3}")) {
            return requested.trim();
        }
        return "3.0";
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }
}
