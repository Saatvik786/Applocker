package com.applocker.mdm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncMlInboundTest {

    @Test
    void parsesHexSessionIdAndDeviceHeaderRouting() {
        SyncMlInbound.InboundMessage inbound = SyncMlInbound.parse("""
                <SyncML xmlns="SYNCML:SYNCML1.2">
                  <SyncHdr>
                    <SessionID>0xF</SessionID>
                    <MsgID>3</MsgID>
                    <Source><LocURI>AD22C5B9-4F4E-41D3-AE36-CFF48A304C48</LocURI></Source>
                    <Target><LocURI>https://localhost:8443/api/v1/mdm/windows/management</LocURI></Target>
                  </SyncHdr>
                  <SyncBody>
                    <Alert>
                      <CmdID>1</CmdID>
                      <Data>1226</Data>
                    </Alert>
                    <Final/>
                  </SyncBody>
                </SyncML>
                """);

        assertThat(inbound.header().sessionId()).isEqualTo("0xF");
        assertThat(inbound.header().msgId()).isEqualTo("3");
        assertThat(inbound.header().deviceLocUri()).isEqualTo("AD22C5B9-4F4E-41D3-AE36-CFF48A304C48");
        assertThat(inbound.clientCommands()).hasSize(1);
        assertThat(inbound.clientCommands().getFirst().name()).isEqualTo("Alert");
        assertThat(inbound.clientFinal()).isTrue();
    }

    @Test
    void responseBuilderEchoesDeviceTargetAndIncrementsMsgId() {
        String xml = new SyncMlResponseBuilder(
                        "0xF",
                        "2",
                        "3",
                        "AD22C5B9-4F4E-41D3-AE36-CFF48A304C48",
                        "https://localhost:8443/api/v1/mdm/windows/management")
                .acknowledgeClientCommands(java.util.List.of(new SyncMlInbound.ClientCommand("Alert", "1")))
                .build(true);

        assertThat(xml).contains("<SessionID>0xF</SessionID>");
        assertThat(xml).contains("<MsgID>2</MsgID>");
        assertThat(xml).contains("<MsgRef>3</MsgRef>");
        assertThat(xml).contains("<Target><LocURI>AD22C5B9-4F4E-41D3-AE36-CFF48A304C48</LocURI></Target>");
        assertThat(xml).contains("<Source><LocURI>https://localhost:8443/api/v1/mdm/windows/management</LocURI></Source>");
        assertThat(xml).contains("<CmdRef>1</CmdRef>");
        assertThat(xml).contains("<Cmd>Alert</Cmd>");
    }
}
