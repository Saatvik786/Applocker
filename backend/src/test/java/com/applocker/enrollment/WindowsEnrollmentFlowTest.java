package com.applocker.enrollment;

import com.applocker.enrollment.cert.LabCertificateAuthority;
import com.applocker.enrollment.soap.XmlSupport;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.w3c.dom.Document;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "PUBLIC_BASE_URL=https://mdm.example.test",
        "applocker.admin-token=test-admin-token",
        "server.port=0"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WindowsEnrollmentFlowTest {

    private static final String ADMIN = "Bearer test-admin-token";
    private static final String DEVICE_ID = "7BA748C8-703E-4DF2-A74A-92984117346A";
    private static String certificateRequest;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LabCertificateAuthority certificates;

    @BeforeAll
    static void certificateRequest() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        var request = new JcaPKCS10CertificationRequestBuilder(
                new javax.security.auth.x500.X500Principal("CN=lab-device"),
                keys.getPublic())
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate()));
        certificateRequest = Base64.getEncoder().encodeToString(request.getEncoded());
    }

    @Test
    void tokenCreationRequiresTheAdminTokenAndHidesTheSecretFromTheList() throws Exception {
        mockMvc.perform(post("/api/v1/enrollment-tokens")
                        .contentType("application/json")
                        .content("{\"label\":\"open\"}"))
                .andExpect(status().isUnauthorized());

        String created = create("listed");
        mockMvc.perform(get("/api/v1/enrollment-tokens").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.label == 'listed')].discoveryUrl").value(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(field(created, "tokenId")))));
    }

    @Test
    void discoverySelectsOnPremiseAndABadSecretIsRejected() throws Exception {
        String created = create("discover");
        String tokenId = field(created, "tokenId");

        MvcResult discovery = mockMvc.perform(post("/api/v1/mdm/windows/enrollment/discovery/" + tokenId)
                        .contentType("application/soap+xml")
                        .content(discoveryRequest()))
                .andExpect(status().isOk())
                .andReturn();
        String body = discovery.getResponse().getContentAsString();
        assertThat(body).contains("<AuthPolicy>OnPremise</AuthPolicy>");
        assertThat(body).contains("https://mdm.example.test/api/v1/mdm/windows/enrollment/service/" + tokenId);
        assertThat(body).contains("<a:RelatesTo>urn:uuid:748132ec-a575-4329-b01b-6171a9cf8478</a:RelatesTo>");

        mockMvc.perform(get("/api/v1/mdm/windows/enrollment/discovery/" + tokenId))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        MvcResult rejected = mockMvc.perform(post("/api/v1/mdm/windows/enrollment/service/" + tokenId)
                        .contentType("application/soap+xml")
                        .content(policyRequest("wrong-password")))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(rejected.getResponse().getContentAsString())
                .contains("Enrollment rejected")
                .doesNotContain(tokenId)
                .doesNotContain("wrong-password");
    }

    @Test
    void revokedTokenIsRejectedAndTheResponseDoesNotEchoIt() throws Exception {
        String created = create("revoked-lab");
        String tokenId = field(created, "tokenId");
        mockMvc.perform(post("/api/v1/enrollment-tokens/revocations")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("{\"label\":\"revoked-lab\"}"))
                .andExpect(status().isOk());

        MvcResult rejected = mockMvc.perform(post("/api/v1/mdm/windows/enrollment/discovery/" + tokenId)
                        .contentType("application/soap+xml")
                        .content(discoveryRequest()))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(rejected.getResponse().getContentAsString()).doesNotContain(tokenId);
    }

    @Test
    void enrollmentIssuesAProvisioningDocumentAndRecordsTheDevice() throws Exception {
        String created = create("lab-vm");
        String tokenId = field(created, "secret");

        mockMvc.perform(post("/api/v1/mdm/windows/enrollment/service/" + tokenId)
                        .contentType("application/soap+xml")
                        .content(policyRequest(tokenId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("minimalKeyLength")))
                .andExpect(content().string(containsString("2.16.840.1.101.3.4.2.1")))
                .andExpect(content().string(not(containsString("1.2.840.113549.1.1.11"))));

        MvcResult enrolled = mockMvc.perform(post("/api/v1/mdm/windows/enrollment/service/" + tokenId)
                        .contentType("application/soap+xml")
                        .content(enrollRequest(tokenId)))
                .andExpect(status().isOk())
                .andReturn();
        String provisioning = provisioningDocument(enrolled.getResponse().getContentAsString());
        assertThat(provisioning).contains("wap-provisioningdoc");
        assertThat(provisioning).contains("https://mdm.example.test/api/v1/mdm/windows/management");
        assertThat(provisioning).contains(certificates.root().base64());
        assertThat(provisioning).contains("<characteristic type=\"User\">");
        assertThat(provisioning).contains("<characteristic type=\"System\">");
        assertThat(provisioning).contains("Stores=My%5CSystem");
        assertThat(provisioning).doesNotContain("Stores=My%5CUser");
        assertThat(provisioning).contains("name=\"ROLE\" value=\"4294967295\"");
        assertThat(provisioning).doesNotContain("SslClientCertReference");
        assertThat(provisioning).doesNotContain("SslClientCertStore");
        assertThat(provisioning).doesNotContain("SslClientCertSubjectName");
        assertThat(provisioning).doesNotContain("SslClientCertHash");
        assertThat(provisioning).doesNotContain("name=\"UPN\"");
        assertThat(provisioning).doesNotContain("password1");

        mockMvc.perform(post("/api/v1/mdm/windows/enrollment/service/" + tokenId)
                        .contentType("application/soap+xml")
                        .content(enrollRequest(tokenId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/enrollment-tokens").header("Authorization", ADMIN))
                .andExpect(jsonPath("$[?(@.label == 'lab-vm')].enrolledCount").value(org.hamcrest.Matchers.contains(1)));

        MvcResult devices = mockMvc.perform(get("/api/v1/devices").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devices[?(@.windowsDeviceId == '" + DEVICE_ID + "')].name")
                        .value(org.hamcrest.Matchers.contains("LAB-VM")))
                .andReturn();
        int before = Integer.parseInt(field(devices.getResponse().getContentAsString(), "managementRequests"));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content("""
                                <SyncML xmlns="SYNCML:SYNCML1.2">
                                  <SyncHdr><SessionID>7</SessionID><MsgID>3</MsgID></SyncHdr>
                                  <SyncBody><Final/></SyncBody>
                                </SyncML>
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<SessionID>7</SessionID>")))
                .andExpect(content().string(containsString("<Data>200</Data>")));

        mockMvc.perform(get("/api/v1/devices").header("Authorization", ADMIN))
                .andExpect(jsonPath("$.managementRequests").value(before + 1));
    }

    private String create(String label) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/enrollment-tokens")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("{\"label\":\"" + label + "\",\"deviceLimit\":2,\"ttlHours\":48}"))
                .andExpect(status().isOk())
                .andReturn();
        String created = result.getResponse().getContentAsString();
        assertThat(field(created, "secret")).isEqualTo(field(created, "tokenId"));
        assertThat(field(created, "discoveryUrl")).endsWith(field(created, "tokenId"));
        return created;
    }

    private static String field(String json, String name) {
        String textKey = "\"" + name + "\":\"";
        int text = json.indexOf(textKey);
        if (text >= 0) {
            int start = text + textKey.length();
            return json.substring(start, json.indexOf('"', start));
        }
        String numberKey = "\"" + name + "\":";
        int number = json.indexOf(numberKey);
        int start = number + numberKey.length();
        while (start < json.length() && json.charAt(start) == ' ') {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        return json.substring(start, end);
    }

    private static String provisioningDocument(String soap) {
        Document document = XmlSupport.parse(soap.getBytes(StandardCharsets.UTF_8));
        String encoded = XmlSupport.firstText(document, "BinarySecurityToken");
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private static String discoveryRequest() {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing">
                  <s:Header>
                    <a:Action>http://schemas.microsoft.com/windows/management/2012/01/enrollment/IDiscoveryService/Discover</a:Action>
                    <a:MessageID>urn:uuid:748132ec-a575-4329-b01b-6171a9cf8478</a:MessageID>
                  </s:Header>
                  <s:Body>
                    <Discover xmlns="http://schemas.microsoft.com/windows/management/2012/01/enrollment/">
                      <request><RequestVersion>3.0</RequestVersion></request>
                    </Discover>
                  </s:Body>
                </s:Envelope>
                """;
    }

    private static String policyRequest(String secret) {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                  <s:Header>
                    <a:Action>http://schemas.microsoft.com/windows/pki/2009/01/enrollmentpolicy/IPolicy/GetPolicies</a:Action>
                    <a:MessageID>urn:uuid:72048B64-0F19-448F-8C2E-B4C661860AA0</a:MessageID>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>device@lab.test</wsse:Username>
                        <wsse:Password>__SECRET__</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </s:Header>
                  <s:Body><GetPolicies xmlns="http://schemas.microsoft.com/windows/pki/2009/01/enrollmentpolicy"/></s:Body>
                </s:Envelope>
                """.replace("__SECRET__", secret);
    }

    private static String enrollRequest(String secret) {
        return """
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:a="http://www.w3.org/2005/08/addressing" xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd" xmlns:wst="http://docs.oasis-open.org/ws-sx/ws-trust/200512" xmlns:ac="http://schemas.xmlsoap.org/ws/2006/12/authorization">
                  <s:Header>
                    <a:Action>http://schemas.microsoft.com/windows/pki/2009/01/enrollment/RST/wstep</a:Action>
                    <a:MessageID>urn:uuid:0d5a1441-5891-453b-becf-a2e5f6ea3749</a:MessageID>
                    <wsse:Security>
                      <wsse:UsernameToken>
                        <wsse:Username>device@lab.test</wsse:Username>
                        <wsse:Password>__SECRET__</wsse:Password>
                      </wsse:UsernameToken>
                    </wsse:Security>
                  </s:Header>
                  <s:Body>
                    <wst:RequestSecurityToken>
                      <wsse:BinarySecurityToken>__CSR__</wsse:BinarySecurityToken>
                      <ac:AdditionalContext>
                        <ac:ContextItem Name="DeviceName"><ac:Value>LAB-VM</ac:Value></ac:ContextItem>
                        <ac:ContextItem Name="DeviceID"><ac:Value>__DEVICE__</ac:Value></ac:ContextItem>
                        <ac:ContextItem Name="EnrollmentType"><ac:Value>Full</ac:Value></ac:ContextItem>
                      </ac:AdditionalContext>
                    </wst:RequestSecurityToken>
                  </s:Body>
                </s:Envelope>
                """.replace("__SECRET__", secret).replace("__CSR__", certificateRequest).replace("__DEVICE__", DEVICE_ID);
    }
}
