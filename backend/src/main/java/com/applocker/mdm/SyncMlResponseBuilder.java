package com.applocker.mdm;

import com.applocker.enrollment.soap.XmlSupport;

import java.util.List;

/**
 * Builds a SyncML 1.2 server response with monotonic CmdIDs and correct header routing.
 */
public final class SyncMlResponseBuilder {

    private final String sessionId;
    private final String serverMsgId;
    private final String clientMsgRef;
    private final String targetDeviceUri;
    private final String sourceServerUri;
    private final StringBuilder body = new StringBuilder();
    private int nextCmdId = 2;

    public SyncMlResponseBuilder(
            String sessionId,
            String serverMsgId,
            String clientMsgRef,
            String targetDeviceUri,
            String sourceServerUri) {
        this.sessionId = sessionId;
        this.serverMsgId = serverMsgId;
        this.clientMsgRef = clientMsgRef;
        this.targetDeviceUri = targetDeviceUri == null || targetDeviceUri.isBlank() ? "device" : targetDeviceUri;
        this.sourceServerUri = sourceServerUri;
    }

    public SyncMlResponseBuilder acknowledgeClientCommands(List<SyncMlInbound.ClientCommand> clientCommands) {
        for (SyncMlInbound.ClientCommand command : clientCommands) {
            if (command.cmdId() == null || command.cmdId().isBlank()) {
                continue;
            }
            appendStatus(command.cmdId(), command.name(), "200");
        }
        return this;
    }

    public SyncMlResponseBuilder appendRawCommands(String xml) {
        if (xml == null || xml.isBlank()) {
            return this;
        }
        body.append(xml);
        nextCmdId = Math.max(nextCmdId, SyncMlMessages.maxOutgoingCmdId(xml) + 1);
        return this;
    }

    public int nextCommandId() {
        return nextCmdId;
    }

    public String build(boolean includeFinal) {
        StringBuilder xml = new StringBuilder();
        xml.append("""
                <SyncML xmlns="SYNCML:SYNCML1.2">
                  <SyncHdr>
                    <VerDTD>1.2</VerDTD>
                    <VerProto>DM/1.2</VerProto>
                    <SessionID>""").append(XmlSupport.xml(sessionId)).append("""
                </SessionID>
                    <MsgID>""").append(XmlSupport.xml(serverMsgId)).append("""
                </MsgID>
                    <Target><LocURI>""").append(XmlSupport.xml(targetDeviceUri)).append("""
                </LocURI></Target>
                    <Source><LocURI>""").append(XmlSupport.xml(sourceServerUri)).append("""
                </LocURI></Source>
                  </SyncHdr>
                  <SyncBody>
                    <Status>
                      <CmdID>1</CmdID>
                      <MsgRef>""").append(XmlSupport.xml(clientMsgRef)).append("""
                </MsgRef>
                      <CmdRef>0</CmdRef>
                      <Cmd>SyncHdr</Cmd>
                      <Data>200</Data>
                    </Status>
                """);
        xml.append(body);
        if (includeFinal) {
            xml.append("<Final/>\n");
        }
        xml.append("""
                  </SyncBody>
                </SyncML>
                """);
        return xml.toString();
    }

    private void appendStatus(String cmdRef, String cmd, String data) {
        body.append("""
                  <Status>
                    <CmdID>""").append(nextCmdId++).append("""
                </CmdID>
                    <MsgRef>""").append(XmlSupport.xml(clientMsgRef)).append("""
                </MsgRef>
                    <CmdRef>""").append(XmlSupport.xml(cmdRef)).append("""
                </CmdRef>
                    <Cmd>""").append(XmlSupport.xml(cmd)).append("""
                </Cmd>
                    <Data>""").append(XmlSupport.xml(data)).append("""
                </Data>
                  </Status>
                """);
    }
}
