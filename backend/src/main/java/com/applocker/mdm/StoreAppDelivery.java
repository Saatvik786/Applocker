package com.applocker.mdm;

import com.applocker.enrollment.soap.XmlSupport;
import com.applocker.policy.ApplockerPolicyService;
import com.applocker.service.MdmPolicyService;
import com.applocker.policy.PolicyCollection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class StoreAppDelivery {

    private final MdmPolicyService policies;
    private final ApplockerPolicyService authored;

    public StoreAppDelivery(MdmPolicyService policies, ApplockerPolicyService authored) {
        this.policies = policies;
        this.authored = authored;
    }

    @Transactional(readOnly = true)
    public String commands(int firstCmdId) {
        return commands(firstCmdId, Set.of());
    }

    @Transactional(readOnly = true)
    public String commands(int firstCmdId, Set<String> existingGroupings) {
        StringBuilder xml = new StringBuilder();
        int cmdId = firstCmdId;
        for (MdmPolicyService.AssignedStorePolicy assigned : policies.assignedStorePolicies()) {
            Optional<PolicyCollection> collection = authored.enforcedStoreApps(assigned.applockerPolicyId());
            if (collection.isEmpty()) {
                continue;
            }
            String base = MdmCatalog.APPLOCKER_BASE + "/ApplicationLaunchRestrictions/" + assigned.grouping();
            String grouping = assigned.grouping().toString().toLowerCase(Locale.ROOT);
            if (!existingGroupings.contains(grouping)) {
                xml.append(add(cmdId++, base));
                xml.append(add(cmdId++, base + "/StoreApps"));
            }
            xml.append(replace(cmdId++, base + "/StoreApps/EnforcementMode", collection.get().enforcementMode()));
            xml.append(replace(cmdId++, base + "/StoreApps/Policy", collection.get().xml()));
        }
        return xml.toString();
    }

    private static String add(int cmdId, String locUri) {
        return """
                  <Add>
                    <CmdID>%d</CmdID>
                    <Item>
                      <Target><LocURI>%s</LocURI></Target>
                    </Item>
                  </Add>
                """.formatted(cmdId, locUri);
    }

    private static String replace(int cmdId, String locUri, String data) {
        return """
                  <Replace>
                    <CmdID>%d</CmdID>
                    <Item>
                      <Target><LocURI>%s</LocURI></Target>
                      <Meta><Format xmlns="syncml:metinf">chr</Format></Meta>
                      <Data>""".formatted(cmdId, locUri) + XmlSupport.xml(data) + """
                </Data>
                    </Item>
                  </Replace>
                """;
    }
}
