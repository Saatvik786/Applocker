package com.applocker.mdm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncMlMessagesTest {

    @Test
    void statusKeepsTheCommandReferenceCodeAndTarget() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <SyncBody>
                  <Status>
                    <CmdID>8</CmdID>
                    <MsgRef>1</MsgRef>
                    <CmdRef>4</CmdRef>
                    <Cmd>Add</Cmd>
                    <Data>405</Data>
                    <TargetRef>./Device/Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/e0e8812a-3f78-4f7c-b3f4-a094a9ebcab3</TargetRef>
                  </Status>
                </SyncBody>
                """);

        assertThat(parsed.error()).isNull();
        assertThat(parsed.replies()).hasSize(1);
        SyncMlMessages.ClientReply reply = parsed.replies().getFirst();
        assertThat(reply.kind()).isEqualTo("Status");
        assertThat(reply.cmdId()).isEqualTo("8");
        assertThat(reply.msgRef()).isEqualTo("1");
        assertThat(reply.cmdRef()).isEqualTo("4");
        assertThat(reply.cmd()).isEqualTo("Add");
        assertThat(reply.data()).isEqualTo("405");
        assertThat(reply.targetRef()).doesNotContain("/StoreApps");
        assertThat(reply.targetRef()).contains("ApplicationLaunchRestrictions/e0e8812a-3f78-4f7c-b3f4-a094a9ebcab3");
        assertThat(reply.itemLocUri()).isNull();
        assertThat(reply.itemData()).isNull();
    }

    @Test
    void errorStatusCanOmitDataAndCarryAnItemUri() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <Status>
                  <CmdID>9</CmdID>
                  <CmdRef>5</CmdRef>
                  <Cmd>Replace</Cmd>
                  <Item>
                    <Target><LocURI>./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/group/StoreApps</LocURI></Target>
                    <Data>0x82aa0002</Data>
                  </Item>
                </Status>
                """);

        SyncMlMessages.ClientReply reply = parsed.replies().getFirst();
        assertThat(reply.data()).isNull();
        assertThat(reply.cmd()).isEqualTo("Replace");
        assertThat(reply.cmdRef()).isEqualTo("5");
        assertThat(reply.itemLocUri()).isEqualTo("./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/group/StoreApps");
        assertThat(reply.itemData()).isEqualTo("0x82aa0002");
    }

    @Test
    void resultsCarryTheGetValueSeparatelyFromAMissingStatusDataElement() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <SyncBody>
                  <Status>
                    <CmdID>3</CmdID>
                    <MsgRef>1</MsgRef>
                    <CmdRef>2</CmdRef>
                    <Cmd>Get</Cmd>
                    <Data></Data>
                  </Status>
                  <Results>
                    <CmdID>4</CmdID>
                    <MsgRef>1</MsgRef>
                    <CmdRef>2</CmdRef>
                    <Item>
                      <Source><LocURI>./Device/Vendor/MSFT/DMClient/Provider/Applocker/EnrollmentType</LocURI></Source>
                      <Data>Full</Data>
                    </Item>
                  </Results>
                </SyncBody>
                """);

        assertThat(parsed.replies()).hasSize(2);
        assertThat(parsed.replies().get(0).data()).isEmpty();
        assertThat(parsed.replies().get(1).kind()).isEqualTo("Results");
        assertThat(parsed.replies().get(1).cmdRef()).isEqualTo("2");
        assertThat(parsed.replies().get(1).itemLocUri())
                .isEqualTo("./Device/Vendor/MSFT/DMClient/Provider/Applocker/EnrollmentType");
        assertThat(parsed.replies().get(1).itemData()).isEqualTo("Full");
    }

    @Test
    void incompleteStatusIsReportedWithoutThrowing() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("<Status><CmdID>1</CmdID><Cmd>Add</Cmd>");

        assertThat(parsed.error()).contains("incomplete Status elements");
        assertThat(parsed.replies()).isEmpty();
    }

    @Test
    void applockerRootGetListsExistingGroupingGuids() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <Results>
                  <CmdID>5</CmdID>
                  <CmdRef>6</CmdRef>
                  <Item>
                    <Source><LocURI>./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions</LocURI></Source>
                    <Data>e0e8812a-3f78-4f7c-b3f4-a094a9ebcab3</Data>
                  </Item>
                </Results>
                """);

        assertThat(SyncMlMessages.existingApplockerGroupings(parsed.replies()))
                .containsExactly("e0e8812a-3f78-4f7c-b3f4-a094a9ebcab3");
    }

    @Test
    void fullEnrollmentIsNotSupportedForApplocker() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <Results>
                  <CmdID>1</CmdID>
                  <CmdRef>2</CmdRef>
                  <Item>
                    <Source><LocURI>./Device/Vendor/MSFT/DMClient/Provider/Applocker/EnrollmentType</LocURI></Source>
                    <Data>Full</Data>
                  </Item>
                </Results>
                """);

        assertThat(SyncMlMessages.mdmEnrollmentType(parsed.replies())).isEqualTo("Full");
        assertThat(SyncMlMessages.applockerSupportedForEnrollment(parsed.replies())).isFalse();
    }

    @Test
    void syncHdrFailureIsDetectedFromClientStatus() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <Status>
                  <CmdID>1</CmdID>
                  <MsgRef>1</MsgRef>
                  <CmdRef>0</CmdRef>
                  <Cmd>SyncHdr</Cmd>
                  <Data>500</Data>
                </Status>
                """);

        assertThat(SyncMlMessages.syncSessionFailed(parsed.replies())).isTrue();
        assertThat(SyncMlMessages.diagnosticGetResultsReady(parsed.replies())).isFalse();
    }

    @Test
    void policyReplaceAckRequiresStoreAppsPolicyWith200() {
        SyncMlMessages.ParseResult parsed = SyncMlMessages.parse("""
                <Status>
                  <CmdID>6</CmdID>
                  <MsgRef>1</MsgRef>
                  <CmdRef>5</CmdRef>
                  <Cmd>Replace</Cmd>
                  <Data>200</Data>
                  <TargetRef>./Device/Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/g/StoreApps/Policy</TargetRef>
                </Status>
                """);

        assertThat(SyncMlMessages.policyReplaceAcknowledged(parsed.replies())).isTrue();
    }

    @Test
    void diagnosticGetsUseTheDocumentedReadOnlyNodes() {
        String gets = SyncMlMessages.diagnosticGets(2, "Applocker");

        assertThat(gets).contains("<Get>");
        assertThat(gets).contains("<CmdID>2</CmdID>");
        assertThat(gets).contains("<CmdID>3</CmdID>");
        assertThat(gets).contains("./Device/Vendor/MSFT/DMClient/Provider/Applocker/EnrollmentType");
        assertThat(gets).contains("./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions");
        assertThat(gets).doesNotContain("<Add>");
        assertThat(gets).doesNotContain("<Replace>");
        assertThat(SyncMlMessages.commandCount(gets)).isEqualTo(2);
        assertThat(SyncMlMessages.diagnosticGets(2, "bad provider")).doesNotContain("DMClient/Provider/bad");
        assertThat(SyncMlMessages.diagnosticGets(2, "bad provider"))
                .contains("./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions");
    }
}
