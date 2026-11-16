package com.applocker.policy;

import com.applocker.service.MdmPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ApplockerPolicyService {

    static final String AUDIT_ONLY = "AuditOnly";
    static final String ENABLED = "Enabled";
    private static final String EVERYONE = "S-1-1-0";
    private static final String ADMINISTRATORS = "S-1-5-32-544";
    private static final Set<String> COLLECTIONS = Set.copyOf(ApplockerPolicyXml.COLLECTIONS);

    private final ApplockerPolicyRepository policies;
    private final ApplockerPolicyVersionRepository versions;
    private final ApplockerRuleRepository rules;
    private final ApplockerCollectionSettingRepository settings;
    private final MdmPolicyService mdmPolicies;
    private final Clock clock;

    public ApplockerPolicyService(
            ApplockerPolicyRepository policies,
            ApplockerPolicyVersionRepository versions,
            ApplockerRuleRepository rules,
            ApplockerCollectionSettingRepository settings,
            MdmPolicyService mdmPolicies,
            Clock clock) {
        this.policies = policies;
        this.versions = versions;
        this.rules = rules;
        this.settings = settings;
        this.mdmPolicies = mdmPolicies;
        this.clock = clock;
    }

    @Transactional
    public PolicyDetail create(CreatePolicyRequest request) {
        String name = requiredName(request.name());
        if (policies.findByName(name).isPresent()) {
            throw new IllegalArgumentException("An AppLocker policy with that name already exists");
        }
        String description = description(request.description());
        List<ApplockerRuleEntity> stored = buildRules(request);
        ApplockerPolicyXml.collections(stored);
        boolean storeBlock = stored.stream().anyMatch(rule -> "Appx".equals(rule.collectionType()) && "Deny".equals(rule.action()));
        UUID grouping = storeBlock ? UUID.randomUUID() : null;

        ApplockerPolicyEntity policy = policies.save(new ApplockerPolicyEntity(UUID.randomUUID(), name, description, clock.instant()));
        ApplockerPolicyVersionEntity version = versions.save(new ApplockerPolicyVersionEntity(
                UUID.randomUUID(), policy.id(), 1, storeBlock ? ENABLED : AUDIT_ONLY, clock.instant()));
        for (ApplockerRuleEntity rule : stored) {
            rule.versionId(version.id());
        }
        rules.saveAll(stored);
        policies.flush();
        int blockedApps = (int) stored.stream().filter(rule -> "Deny".equals(rule.action())).count();
        mdmPolicies.saveProfile(policy.id(), name, description, policy.createdAt(), storeBlock, grouping, blockedApps);
        return detail(policy, version, stored, grouping);
    }

    @Transactional(readOnly = true)
    public List<PolicySummary> list() {
        return policies.findAllByOrderByCreatedAtAsc().stream().map(policy -> {
            ApplockerPolicyVersionEntity version = latest(policy.id());
            return new PolicySummary(
                    policy.id(),
                    policy.name(),
                    policy.description(),
                    version.versionNumber(),
                    version.enforcementMode(),
                    rules.countByVersionId(version.id()),
                    mdmPolicies.grouping(policy.id()).isPresent(),
                    policy.createdAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public PolicyDetail get(UUID id) {
        ApplockerPolicyEntity policy = policies.findById(id).orElseThrow(ApplockerPolicyNotFoundException::new);
        ApplockerPolicyVersionEntity version = latest(policy.id());
        return detail(policy, version, rules.findByVersionIdOrderBySortOrderAsc(version.id()), mdmPolicies.grouping(policy.id()).orElse(null));
    }

    @Transactional(readOnly = true)
    public Optional<PolicyCollection> enforcedStoreApps(UUID policyId) {
        return versions.findFirstByPolicyIdOrderByVersionNumberDesc(policyId).flatMap(version -> {
            String requested = settings.findByVersionIdAndCollectionType(version.id(), "Appx")
                    .map(ApplockerCollectionSettingEntity::enforcementMode)
                    .orElse(null);
            java.util.Map<String, String> modes = requested == null ? java.util.Map.of() : java.util.Map.of("Appx", requested);
            return ApplockerPolicyXml.collections(rules.findByVersionIdOrderBySortOrderAsc(version.id()), modes).stream()
                    .filter(item -> "Appx".equals(item.type()))
                    .findFirst();
        });
    }

    ApplockerRuleEntity buildCustomRule(RuleRequest request, int order) {
        return custom(request, order);
    }

    List<ApplockerRuleEntity> defaultRules(String collection, int startOrder) {
        List<ApplockerRuleEntity> stored = new ArrayList<>();
        int order = startOrder;
        if ("Appx".equals(collection)) {
            stored.add(publisher(collection, "(Default Rule) All signed packaged apps", "*", "*", "*", EVERYONE, order));
            return stored;
        }
        stored.add(path(collection, "(Default Rule) All files located in the Program Files folder", "%PROGRAMFILES%\\*", EVERYONE, order++));
        stored.add(path(collection, "(Default Rule) All files located in the Windows folder", "%WINDIR%\\*", EVERYONE, order++));
        stored.add(path(collection, "(Default Rule) All files", "*", ADMINISTRATORS, order));
        return stored;
    }

    private ApplockerPolicyVersionEntity latest(UUID policyId) {
        return versions.findFirstByPolicyIdOrderByVersionNumberDesc(policyId)
                .orElseThrow(ApplockerPolicyNotFoundException::new);
    }

    private PolicyDetail detail(ApplockerPolicyEntity policy, ApplockerPolicyVersionEntity version, List<ApplockerRuleEntity> stored, UUID grouping) {
        List<PolicyCollection> collections = ApplockerPolicyXml.collections(stored).stream().map(collection -> {
            if (grouping == null || !"Appx".equals(collection.type())) {
                return collection;
            }
            return new PolicyCollection(
                    collection.type(),
                    collection.cspNode().replace("{Grouping}", grouping.toString()),
                    collection.enforcementMode(),
                    collection.xml());
        }).toList();
        return new PolicyDetail(
                policy.id(),
                policy.name(),
                policy.description(),
                version.versionNumber(),
                version.enforcementMode(),
                grouping != null,
                policy.createdAt(),
                collections);
    }

    private List<ApplockerRuleEntity> buildRules(CreatePolicyRequest request) {
        List<RuleRequest> custom = request.rules() == null ? List.of() : request.rules();
        if (custom.size() > 40) {
            throw new IllegalArgumentException("A policy can contain at most 40 custom rules");
        }
        boolean defaults = request.includeDefaultAllowRules() == null || request.includeDefaultAllowRules();
        List<ApplockerRuleEntity> stored = new ArrayList<>();
        int order = 0;
        if (defaults) {
            List<String> collections = custom.stream().map(rule -> collection(rule.collection())).distinct().toList();
            if (collections.isEmpty()) {
                collections = List.of("Exe");
            }
            for (String collection : ApplockerPolicyXml.COLLECTIONS) {
                if (!collections.contains(collection)) {
                    continue;
                }
                if ("Appx".equals(collection)) {
                    stored.add(publisher(collection, "(Default Rule) All signed packaged apps", "*", "*", "*", EVERYONE, order++));
                } else {
                    stored.add(path(collection, "(Default Rule) All files located in the Program Files folder", "%PROGRAMFILES%\\*", EVERYONE, order++));
                    stored.add(path(collection, "(Default Rule) All files located in the Windows folder", "%WINDIR%\\*", EVERYONE, order++));
                    stored.add(path(collection, "(Default Rule) All files", "*", ADMINISTRATORS, order++));
                }
            }
        }
        for (RuleRequest rule : custom) {
            stored.add(custom(rule, order++));
        }
        ensureStoreAllow(stored);
        if (stored.isEmpty()) {
            throw new IllegalArgumentException("Add at least one allow rule");
        }
        return stored;
    }

    private void ensureStoreAllow(List<ApplockerRuleEntity> stored) {
        boolean deny = stored.stream().anyMatch(rule -> "Appx".equals(rule.collectionType()) && "Deny".equals(rule.action()));
        if (!deny) {
            return;
        }
        boolean allowed = stored.stream().anyMatch(rule -> "Allow".equals(rule.action())
                && "Publisher".equals(rule.ruleKind())
                && "*".equals(rule.publisherName())
                && "*".equals(rule.productName())
                && "*".equals(rule.binaryName()));
        if (!allowed) {
            stored.add(publisher("Appx", "(Default Rule) All signed packaged apps", "*", "*", "*", EVERYONE, stored.size()));
        }
    }

    private ApplockerRuleEntity custom(RuleRequest rule, int order) {
        String collection = collection(rule.collection());
        String type = rule.type() == null ? "" : rule.type().trim();
        String action = action(rule.action());
        if ("Appx".equals(collection) && !"Publisher".equals(type)) {
            throw new IllegalArgumentException("Packaged apps only support publisher rules");
        }
        String sid = sid(rule.userOrGroupSid());
        String name = ruleName(rule.name());
        return switch (type) {
            case "Path" -> {
                String path = required(rule.path(), "Path is required");
                rejectBroadPath(path, sid, action);
                ApplockerRuleEntity stored = path(collection, name, path, sid, order);
                stored.action(action);
                yield stored;
            }
            case "Publisher" -> {
                ApplockerRuleEntity stored = publisher(
                        collection,
                        name,
                        publisherName(rule.publisherName()),
                        blankToStar(rule.productName()),
                        blankToStar(rule.binaryName()),
                        sid,
                        order);
                stored.action(action);
                stored.binaryVersionLow(blankToNull(rule.binaryVersionLow()));
                stored.binaryVersionHigh(blankToNull(rule.binaryVersionHigh()));
                if ("Deny".equals(action) && wildcard(stored.productName()) && wildcard(stored.publisherName()) && wildcard(stored.binaryName())) {
                    throw new IllegalArgumentException("Appx".equals(collection)
                            ? "A deny-all Store app rule is rejected"
                            : "A deny-all publisher rule is rejected");
                }
                if ("Appx".equals(collection) && "Deny".equals(action) && wildcard(stored.productName())) {
                    throw new IllegalArgumentException("Name the packaged app to block");
                }
                yield stored;
            }
            case "Hash" -> {
                ApplockerRuleEntity stored = hash(collection, name, rule, sid, order);
                stored.action(action);
                yield stored;
            }
            default -> throw new IllegalArgumentException("Rule type must be Path, Publisher, or Hash");
        };
    }

    private static void rejectBroadPath(String path, String sid, String action) {
        String normalized = path.replace("/", "\\");
        boolean broad = "*".equals(normalized) || "%OSDRIVE%\\*".equalsIgnoreCase(normalized) || "%SYSTEMDRIVE%\\*".equalsIgnoreCase(normalized);
        if (!broad || !EVERYONE.equals(sid)) {
            return;
        }
        throw new IllegalArgumentException("Deny".equals(action)
                ? "A deny-all path rule for Everyone is rejected"
                : "An allow-everything path for Everyone is rejected");
    }

    private ApplockerRuleEntity path(String collection, String name, String path, String sid, int order) {
        ApplockerRuleEntity rule = base(collection, "Path", name, sid, order);
        rule.path(limit(path, 260, "Path"));
        return rule;
    }

    private ApplockerRuleEntity publisher(String collection, String name, String publisherName, String productName, String binaryName, String sid, int order) {
        ApplockerRuleEntity rule = base(collection, "Publisher", name, sid, order);
        rule.publisherName(limit(publisherName, 300, "Publisher name"));
        rule.productName(limit(productName, 200, "Product name"));
        rule.binaryName(limit(binaryName, 200, "File name"));
        return rule;
    }

    private ApplockerRuleEntity hash(String collection, String name, RuleRequest rule, String sid, int order) {
        String data = rule.hashData() == null ? "" : rule.hashData().trim();
        if (data.regionMatches(true, 0, "0x", 0, 2)) {
            data = data.substring(2);
        }
        if (!data.matches("[0-9A-Fa-f]{64}")) {
            throw new IllegalArgumentException("File hash must be a SHA-256 value");
        }
        if (rule.sourceFileLength() == null || rule.sourceFileLength() < 1) {
            throw new IllegalArgumentException("File length is required for a hash rule");
        }
        ApplockerRuleEntity stored = base(collection, "Hash", name, sid, order);
        stored.hashData("0x" + data.toUpperCase(Locale.ROOT));
        stored.sourceFileName(limit(required(rule.sourceFileName(), "File name is required"), 260, "File name"));
        stored.sourceFileLength(rule.sourceFileLength());
        return stored;
    }

    private ApplockerRuleEntity base(String collection, String kind, String name, String sid, int order) {
        ApplockerRuleEntity rule = new ApplockerRuleEntity();
        rule.id(UUID.randomUUID());
        rule.collectionType(collection);
        rule.ruleKind(kind);
        rule.name(name);
        rule.userOrGroupSid(sid);
        rule.action("Allow");
        rule.sortOrder(order);
        return rule;
    }

    private static String action(String value) {
        if (value == null || value.isBlank() || "Allow".equalsIgnoreCase(value.trim())) {
            return "Allow";
        }
        if ("Deny".equalsIgnoreCase(value.trim())) {
            return "Deny";
        }
        throw new IllegalArgumentException("Action must be Allow or Deny");
    }

    private static String publisherName(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "*";
        }
        return limit(required(text, "Publisher name is required"), 300, "Publisher name");
    }

    private static boolean wildcard(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String text = value.trim();
        return "*".equals(text) || "CN=*".equalsIgnoreCase(text);
    }

    private static String collection(String value) {
        String collection = value == null ? "" : value.trim();
        if (!COLLECTIONS.contains(collection)) {
            throw new IllegalArgumentException("Collection must be Exe, Dll, Msi, Script, or Appx");
        }
        return collection;
    }

    private static String sid(String value) {
        String sid = value == null || value.isBlank() ? EVERYONE : value.trim();
        if (!sid.matches("S-1(?:-\\d+)+") || sid.length() > 64) {
            throw new IllegalArgumentException("User or group must be a Windows SID");
        }
        return sid;
    }

    private static String requiredName(String value) {
        String name = value == null ? "" : value.trim();
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{0,79}")) {
            throw new IllegalArgumentException("Name must start with a letter or number and use only letters, numbers, spaces, dots, underscores, or hyphens");
        }
        return name;
    }

    private static String ruleName(String value) {
        String name = required(value, "Rule name is required");
        return limit(name, 120, "Rule name");
    }

    private static String description(String value) {
        String description = value == null ? "" : value.trim();
        if (description.length() > 400) {
            throw new IllegalArgumentException("Description must be 400 characters or fewer");
        }
        return description;
    }

    private static String required(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty() || text.indexOf('<') >= 0 || text.indexOf('>') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String blankToNull(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String blankToStar(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? "*" : limit(text, 200, "Rule value");
    }

    private static String limit(String value, int max, String label) {
        if (value.length() > max) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return value;
    }
}
