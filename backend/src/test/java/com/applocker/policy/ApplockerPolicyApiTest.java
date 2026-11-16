package com.applocker.policy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ApplockerPolicyApiTest {

    private static final String ADMIN = "Bearer test-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void auditOnlyPolicyIsStoredAndABroadAllowIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/policies").contentType("application/json").content("{\"name\":\"Finance apps\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Finance apps","description":"Audit the finance tools","includeDefaultAllowRules":true,"rules":[{"collection":"Exe","type":"Path","name":"Finance tool","path":"%PROGRAMFILES%\\\\Finance\\\\app.exe","userOrGroupSid":"S-1-1-0"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementMode").value("AuditOnly"))
                .andExpect(jsonPath("$.delivered").value(false))
                .andExpect(jsonPath("$.collections[0].type").value("Exe"))
                .andExpect(jsonPath("$.collections[0].cspNode").value("./Device/Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/{Grouping}/EXE/Policy"))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("EnforcementMode=\"AuditOnly\"")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("Action=\"Allow\"")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("%PROGRAMFILES%\\Finance\\app.exe")))
                .andExpect(jsonPath("$.collections[0].xml").value(not(containsString("Action=\"Deny\""))))
                .andExpect(jsonPath("$.collections[0].xml").value(not(containsString("CodeIntegrity"))));

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Too broad","includeDefaultAllowRules":false,"rules":[{"collection":"Exe","type":"Path","name":"Everything","path":"*","userOrGroupSid":"S-1-1-0"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("allow-everything")));

        mockMvc.perform(get("/api/v1/policies").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Finance apps')].name").value(contains("Finance apps")))
                .andExpect(jsonPath("$[?(@.name == 'Finance apps')].enforcementMode").value(contains("AuditOnly")));
    }

    @Test
    void packagedAppsUseTheAppxCollectionAndTheStoreAppsNode() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Packaged apps","includeDefaultAllowRules":false,"rules":[{"collection":"Appx","type":"Publisher","name":"Contoso app","publisherName":"CN=Contoso","productName":"Contoso.App","binaryName":"*"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections[0].type").value("Appx"))
                .andExpect(jsonPath("$.collections[0].cspNode").value(containsString("/StoreApps/Policy")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("Type=\"Appx\"")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("PublisherName=\"CN=Contoso\"")));
    }

    @Test
    void storeAppBlockIsDeliveredAndADenyAllIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Block everything","includeDefaultAllowRules":false,"rules":[{"collection":"Appx","type":"Publisher","action":"Deny","name":"Everything","publisherName":"*","productName":"*","binaryName":"*"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("deny-all")));

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Block every exe","includeDefaultAllowRules":false,"rules":[{"collection":"Exe","type":"Path","action":"Deny","name":"Block all","path":"*","userOrGroupSid":"S-1-1-0"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("deny-all")));

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Block exe","includeDefaultAllowRules":false,"rules":[{"collection":"Exe","type":"Path","action":"Deny","name":"Block cmd","path":"C:/Windows/System32/cmd.exe","userOrGroupSid":"S-1-1-0"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementMode").value("AuditOnly"))
                .andExpect(jsonPath("$.delivered").value(false))
                .andExpect(jsonPath("$.collections[0].type").value("Exe"))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("Action=\"Deny\"")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("cmd.exe")));

        mockMvc.perform(post("/api/v1/policies")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"name":"Block Calculator","includeDefaultAllowRules":false,"rules":[{"collection":"Appx","type":"Publisher","action":"Deny","name":"Calculator","publisherName":"*","productName":"Microsoft.WindowsCalculator","binaryName":"*"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementMode").value("Enabled"))
                .andExpect(jsonPath("$.delivered").value(true))
                .andExpect(jsonPath("$.collections[0].type").value("Appx"))
                .andExpect(jsonPath("$.collections[0].enforcementMode").value("Enabled"))
                .andExpect(jsonPath("$.collections[0].cspNode").value(containsString("/StoreApps/Policy")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("EnforcementMode=\"Enabled\"")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("Action=\"Deny\"")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("Microsoft.WindowsCalculator")))
                .andExpect(jsonPath("$.collections[0].xml").value(containsString("(Default Rule) All signed packaged apps")))
                .andExpect(jsonPath("$.collections[0].xml").value(not(containsString("CodeIntegrity"))))
                .andExpect(jsonPath("$.collections[0].xml").value(not(containsString("PublisherName=\"CN=*\""))));

        mockMvc.perform(get("/api/v1/policies").header("Authorization", ADMIN))
                .andExpect(jsonPath("$[?(@.name == 'Block Calculator')].storeAppsEnforced").value(contains(true)));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(initialSync("7", "3")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<SessionID>7</SessionID>")))
                .andExpect(content().string(containsString("EnrollmentType")))
                .andExpect(content().string(containsString("ApplicationLaunchRestrictions")))
                .andExpect(content().string(not(containsString("StoreApps/Policy"))));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(followUpSync("7", "4", "")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<SessionID>7</SessionID>")))
                .andExpect(content().string(containsString("<Data>200</Data>")))
                .andExpect(content().string(containsString("./Device/Vendor/MSFT/AppLocker")))
                .andExpect(content().string(containsString("StoreApps/Policy")))
                .andExpect(content().string(containsString("StoreApps/EnforcementMode")))
                .andExpect(content().string(containsString("<Data>Enabled</Data>")))
                .andExpect(content().string(containsString("&lt;RuleCollection Type=&quot;Appx&quot;")))
                .andExpect(content().string(containsString("Microsoft.WindowsCalculator")))
                .andExpect(content().string(containsString("Action=&quot;Deny&quot;")))
                .andExpect(content().string(containsString("(Default Rule) All signed packaged apps")))
                .andExpect(content().string(not(containsString("/EXE/Policy"))))
                .andExpect(content().string(not(containsString("CodeIntegrity"))))
                .andExpect(content().string(not(containsString("PublisherName=&quot;CN=*&quot;"))));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(followUpSync("7", "5", "")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("StoreApps/Policy"))));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(followUpSyncFullEnrollment("8", "2", "")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("StoreApps/Policy")));
    }

    @Test
    void storeAppsCanBeAllowedBlockedOrLeftAlone() throws Exception {
        mockMvc.perform(get("/api/v1/applocker/workspace"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/applocker/store-apps")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"packageName":"*","publisher":"*","action":"Deny"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("deny-all")));

        mockMvc.perform(put("/api/v1/applocker/store-apps")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"packageName":"Example.PackagedApp","publisher":"CN=Example","action":"Deny"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("Deny"))
                .andExpect(jsonPath("$.result").value("Would be blocked (deny rule)"));

        mockMvc.perform(get("/api/v1/applocker/store-apps").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.packageName == 'Example.PackagedApp')].action", contains("Deny")));

        mockMvc.perform(put("/api/v1/applocker/collections/Appx")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"enforcementMode":"Enabled","defaultAllow":false}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/applocker/collections/Appx")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"enforcementMode":"Enabled","defaultAllow":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementMode").value("Enabled"));

        mockMvc.perform(get("/api/v1/applocker/store-apps").header("Authorization", ADMIN))
                .andExpect(jsonPath("$[?(@.packageName == 'Example.PackagedApp')].result", contains("Blocked from install and launch")));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(initialSync("9", "1")));
        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(followUpSync("9", "2", "")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<Data>200</Data>")))
                .andExpect(content().string(containsString("<Data>Enabled</Data>")))
                .andExpect(content().string(containsString("Example.PackagedApp")))
                .andExpect(content().string(containsString("Action=&quot;Deny&quot;")))
                .andExpect(content().string(containsString("(Default Rule) All signed packaged apps")))
                .andExpect(content().string(not(containsString("/EXE/Policy"))))
                .andExpect(content().string(not(containsString("CodeIntegrity"))));

        mockMvc.perform(put("/api/v1/applocker/collections/Appx")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"enforcementMode":"AuditOnly","defaultAllow":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enforcementMode").value("AuditOnly"));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(initialSync("10", "1")));
        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(followUpSync("10", "2", "")))
                .andExpect(content().string(containsString("<Data>AuditOnly</Data>")))
                .andExpect(content().string(containsString("Example.PackagedApp")));

        mockMvc.perform(put("/api/v1/applocker/store-apps")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"packageName":"Example.PackagedApp","publisher":"CN=Example","action":"Allow"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("Allow"))
                .andExpect(jsonPath("$.result").value("Allowed to install and launch"));

        mockMvc.perform(put("/api/v1/applocker/store-apps")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"packageName":"Example.PackagedApp","publisher":"CN=Example","action":"None"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("None"))
                .andExpect(jsonPath("$.result").value("Runs (default rule)"));

        mockMvc.perform(post("/api/v1/applocker/rules")
                        .header("Authorization", ADMIN)
                        .contentType("application/json")
                        .content("""
                                {"collection":"Exe","type":"Path","action":"Allow","name":"Lab tool","path":"C:/Tools/tool.exe","userOrGroupSid":"S-1-1-0"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collection").value("Exe"))
                .andExpect(jsonPath("$.action").value("Allow"))
                .andExpect(jsonPath("$.result").value("Saved allow rule"));

        mockMvc.perform(get("/api/v1/applocker/rules?collection=Exe").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Lab tool')].path", contains("C:/Tools/tool.exe")));

        mockMvc.perform(get("/api/v1/applocker/workspace").header("Authorization", ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collections[0].id").value("Exe"))
                .andExpect(jsonPath("$.collections[0].sentToDevices").value(false))
                .andExpect(jsonPath("$.collections[0].conditions", contains("Path", "Publisher", "Hash")))
                .andExpect(jsonPath("$.collections[3].id").value("Dll"))
                .andExpect(jsonPath("$.collections[3].conditions", contains("Path", "Publisher", "Hash")))
                .andExpect(jsonPath("$.collections[4].id").value("Appx"))
                .andExpect(jsonPath("$.collections[4].sentToDevices").value(true))
                .andExpect(jsonPath("$.collections[4].conditions", contains("Publisher")));

        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(initialSync("11", "1")));
        mockMvc.perform(post("/api/v1/mdm/windows/management")
                        .contentType("application/vnd.syncml.dm+xml")
                        .content(followUpSync("11", "2", "")))
                .andExpect(content().string(not(containsString("/EXE/Policy"))))
                .andExpect(content().string(not(containsString("tool.exe"))));
    }

    private static String initialSync(String sessionId, String msgId) {
        return """
                <SyncML xmlns="SYNCML:SYNCML1.2">
                  <SyncHdr><SessionID>%s</SessionID><MsgID>%s</MsgID></SyncHdr>
                  <SyncBody><Final/></SyncBody>
                </SyncML>
                """.formatted(sessionId, msgId);
    }

    private static String followUpSyncFullEnrollment(String sessionId, String msgId, String applockerRootData) {
        return followUpSync(sessionId, msgId, applockerRootData).replace("<Data>Device</Data>", "<Data>Full</Data>");
    }

    private static String followUpSync(String sessionId, String msgId, String applockerRootData) {
        return """
                <SyncML xmlns="SYNCML:SYNCML1.2">
                  <SyncHdr><SessionID>%s</SessionID><MsgID>%s</MsgID></SyncHdr>
                  <SyncBody>
                    <Results>
                      <CmdID>1</CmdID>
                      <MsgRef>1</MsgRef>
                      <CmdRef>2</CmdRef>
                      <Item>
                        <Source><LocURI>./Device/Vendor/MSFT/DMClient/Provider/Applocker/EnrollmentType</LocURI></Source>
                        <Data>Device</Data>
                      </Item>
                    </Results>
                    <Results>
                      <CmdID>2</CmdID>
                      <MsgRef>1</MsgRef>
                      <CmdRef>3</CmdRef>
                      <Item>
                        <Source><LocURI>./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions</LocURI></Source>
                        <Data>%s</Data>
                      </Item>
                    </Results>
                    <Final/>
                  </SyncBody>
                </SyncML>
                """.formatted(sessionId, msgId, applockerRootData);
    }
}
