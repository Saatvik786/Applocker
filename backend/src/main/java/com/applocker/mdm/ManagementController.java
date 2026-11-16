package com.applocker.mdm;

import com.applocker.config.ApplockerProperties;
import com.applocker.device.DeviceRegistry;
import com.applocker.mdm.SyncMlMessages.ClientReply;
import com.applocker.mdm.SyncMlMessages.OutgoingCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Acknowledges an OMA-DM session and, when a Store app block is configured,
 * delivers that AppLocker StoreApps policy to the enrolled device.
 */
@Controller
@RequestMapping("/api/v1/mdm/windows/management")
public class ManagementController {

    private static final Logger log = LoggerFactory.getLogger(ManagementController.class);
    private static final int MAX_BODY_BYTES = 1_000_000;

    private final DeviceRegistry devices;
    private final StoreAppDelivery storeApps;
    private final ManagementSyncCoordinator syncCoordinator;
    private final ApplockerProperties properties;
    private final Clock clock;

    public ManagementController(
            DeviceRegistry devices,
            StoreAppDelivery storeApps,
            ManagementSyncCoordinator syncCoordinator,
            ApplockerProperties properties,
            Clock clock) {
        this.devices = devices;
        this.storeApps = storeApps;
        this.syncCoordinator = syncCoordinator;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping
    public void session(HttpServletRequest request, HttpServletResponse response) throws IOException {
        byte[] body = read(request, response);
        if (body == null) {
            return;
        }
        devices.recordManagement(clock.instant());
        boolean xml = body.length > 0 && body[0] == '<';
        log.info("Management session received, {} bytes, xml={}", body.length, xml);

        String responseXml;
        if (!xml) {
            responseXml = emptyAck("1", "1", "1", managementSourceUri(), "device");
        } else {
            SyncMlInbound.InboundMessage inbound = SyncMlInbound.parse(body);
            SyncMlInbound.Header header = inbound.header();
            String deviceKey = deviceKey(header);
            synchronized (syncCoordinator.lockFor(deviceKey)) {
                noteDevice(header);
                logReplies(inbound.replies());
                logEnrollmentType(inbound.replies());
                if (SyncMlMessages.syncSessionFailed(inbound.replies())) {
                    log.warn("Client reported SyncHdr 500; acknowledging without new AppLocker commands");
                }
                ManagementSyncCoordinator.CommandPlan plan =
                        syncCoordinator.plan(deviceKey, inbound.replies());

                String serverMsgId = Integer.toString(syncCoordinator.nextServerMsgId(deviceKey));
                SyncMlResponseBuilder builder = new SyncMlResponseBuilder(
                        header.sessionId(),
                        serverMsgId,
                        header.msgId(),
                        header.deviceLocUri(),
                        managementSourceUri());
                builder.acknowledgeClientCommands(inbound.clientCommands());

                int firstServerCommandId = builder.nextCommandId();
                String diagnostics = "";
                if (plan.sendDiagnostics()) {
                    diagnostics = SyncMlMessages.diagnosticGets(firstServerCommandId, properties.mdmProviderId());
                }
                String policyDelivery = "";
                if (plan.sendPolicy()) {
                    var existingGroupings = SyncMlMessages.existingApplockerGroupings(inbound.replies());
                    int policyFirstCmdId = firstServerCommandId + SyncMlMessages.commandCount(diagnostics);
                    policyDelivery = storeApps.commands(policyFirstCmdId, existingGroupings);
                }
                builder.appendRawCommands(diagnostics + policyDelivery);
                logOutgoing(diagnostics + policyDelivery);
                responseXml = builder.build(true);
            }
        }

        byte[] responseBody = responseXml.getBytes(StandardCharsets.UTF_8);
        response.setStatus(200);
        response.setContentType("application/vnd.syncml.dm+xml; charset=utf-8");
        response.setContentLength(responseBody.length);
        response.getOutputStream().write(responseBody);
    }

    private void noteDevice(SyncMlInbound.Header header) {
        String deviceId = normalizeDeviceId(header.deviceLocUri());
        if (deviceId != null) {
            devices.noteSession(deviceId, clock.instant());
        }
    }

    private static String deviceKey(SyncMlInbound.Header header) {
        String deviceId = normalizeDeviceId(header.deviceLocUri());
        return header.sessionId() + ":" + (deviceId != null ? deviceId : "unknown");
    }

    private static String normalizeDeviceId(String deviceLocUri) {
        if (deviceLocUri == null || deviceLocUri.isBlank()) {
            return null;
        }
        String value = deviceLocUri.replace("{", "").replace("}", "").trim();
        if (value.isEmpty() || value.length() > 128) {
            return null;
        }
        return value;
    }

    private void logEnrollmentType(java.util.List<ClientReply> replies) {
        String enrollmentType = SyncMlMessages.mdmEnrollmentType(replies);
        if (enrollmentType != null) {
            log.info("DMClient EnrollmentType CSP Result: {}", enrollmentType);
        }
    }

    private void logReplies(java.util.List<ClientReply> replies) {
        if (replies.isEmpty()) {
            log.info("Management HTTP response 200. Client message contains no SyncML Status or Results");
            return;
        }
        for (ClientReply reply : replies) {
            log.info(
                    "Management HTTP response 200. SyncML {} CmdID={} MsgRef={} CmdRef={} Cmd={} SyncMLData={} TargetRef={} ItemLocURI={} ItemData={}",
                    reply.kind(),
                    shown(reply.cmdId()),
                    shown(reply.msgRef()),
                    shown(reply.cmdRef()),
                    shown(reply.cmd()),
                    shown(reply.data()),
                    shown(reply.targetRef()),
                    shown(reply.itemLocUri()),
                    shown(reply.itemData()));
            if ("Status".equals(reply.kind())
                    && "Replace".equals(reply.cmd())
                    && reply.data() != null
                    && !"200".equals(reply.data())) {
                log.warn(
                        "Client Replace command failed CmdRef={} TargetRef={} SyncMLData={}",
                        shown(reply.cmdRef()),
                        shown(reply.targetRef()),
                        shown(reply.data()));
            }
        }
    }

    private void logOutgoing(String xml) {
        for (OutgoingCommand command : SyncMlMessages.outgoing(xml)) {
            log.info("SyncML command CmdID={} Cmd={} LocURI={}", command.cmdId(), command.cmd(), command.locUri());
        }
    }

    private static String shown(String value) {
        if (value == null) {
            return "(absent)";
        }
        String cleaned = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isEmpty()) {
            return "(empty)";
        }
        return cleaned.length() <= 180 ? cleaned : cleaned.substring(0, 180) + "...";
    }

    private String managementSourceUri() {
        String base = properties.publicBaseUrl().replaceAll("/+$", "");
        return base + "/api/v1/mdm/windows/management";
    }

    private static String emptyAck(
            String sessionId,
            String serverMsgId,
            String clientMsgRef,
            String sourceUri,
            String targetUri) {
        return new SyncMlResponseBuilder(sessionId, serverMsgId, clientMsgRef, targetUri, sourceUri)
                .build(true);
    }

    private static byte[] read(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = request.getInputStream().read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY_BYTES) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
