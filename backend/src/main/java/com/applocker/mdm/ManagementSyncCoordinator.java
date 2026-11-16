package com.applocker.mdm;

import com.applocker.mdm.SyncMlMessages.ClientReply;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prevents duplicate AppLocker Replace/Add bursts when the DM client posts the
 * same diagnostic Results on parallel connections or reports SyncHdr 500 after
 * an overloaded server response.
 */
@Component
public class ManagementSyncCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ManagementSyncCoordinator.class);

    private final ConcurrentHashMap<String, SessionPhase> phases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> serverMsgIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    enum SessionPhase {
        AWAITING_CLIENT,
        DIAGNOSTICS_REQUESTED,
        POLICY_SENT,
        POLICY_ACKED
    }

    record CommandPlan(boolean sendDiagnostics, boolean sendPolicy) {
        static CommandPlan none() {
            return new CommandPlan(false, false);
        }

        static CommandPlan diagnosticsOnly() {
            return new CommandPlan(true, false);
        }

        static CommandPlan policyOnly() {
            return new CommandPlan(false, true);
        }
    }

    public CommandPlan plan(String sessionKey, List<ClientReply> replies) {
        synchronized (lock(sessionKey)) {
            if (SyncMlMessages.syncSessionFailed(replies)) {
                SessionPhase phase = phases.get(sessionKey);
                if (phase == SessionPhase.POLICY_SENT) {
                    phases.put(sessionKey, SessionPhase.DIAGNOSTICS_REQUESTED);
                }
                return CommandPlan.none();
            }
            if (SyncMlMessages.policyReplaceAcknowledged(replies)) {
                phases.put(sessionKey, SessionPhase.POLICY_ACKED);
                return CommandPlan.none();
            }
            SessionPhase phase = phases.getOrDefault(sessionKey, SessionPhase.AWAITING_CLIENT);
            if (replies.isEmpty()) {
                phases.put(sessionKey, SessionPhase.DIAGNOSTICS_REQUESTED);
                return CommandPlan.diagnosticsOnly();
            }
            if (phase == SessionPhase.POLICY_SENT || phase == SessionPhase.POLICY_ACKED) {
                return CommandPlan.none();
            }
            if (SyncMlMessages.diagnosticGetResultsReady(replies)) {
                String enrollmentType = SyncMlMessages.mdmEnrollmentType(replies);
                if (enrollmentType != null && !SyncMlMessages.applockerSupportedForEnrollment(replies)) {
                    log.warn(
                            "DMClient EnrollmentType is {} (CSP Result). AppLocker StoreApps uses ./Device/ URIs; MDMFull/user enrollments often reject those nodes with 0x82aa0002 even when SyncML succeeds.",
                            enrollmentType);
                }
                phases.put(sessionKey, SessionPhase.POLICY_SENT);
                return CommandPlan.policyOnly();
            }
            return CommandPlan.none();
        }
    }

    public int nextServerMsgId(String sessionKey) {
        synchronized (lock(sessionKey)) {
            return serverMsgIds.computeIfAbsent(sessionKey, ignored -> new AtomicInteger(0)).incrementAndGet();
        }
    }

    Object lockFor(String sessionKey) {
        return lock(sessionKey);
    }

    private Object lock(String sessionKey) {
        return locks.computeIfAbsent(sessionKey, ignored -> new Object());
    }

    void resetSession(String sessionKey) {
        phases.remove(sessionKey);
        serverMsgIds.remove(sessionKey);
    }
}
