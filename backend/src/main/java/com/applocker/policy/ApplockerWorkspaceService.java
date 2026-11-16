package com.applocker.policy;

import com.applocker.policy.WorkspaceModels.CollectionCommand;
import com.applocker.policy.WorkspaceModels.CollectionView;
import com.applocker.policy.WorkspaceModels.RuleView;
import com.applocker.policy.WorkspaceModels.StoreAppCommand;
import com.applocker.policy.WorkspaceModels.StoreAppView;
import com.applocker.policy.WorkspaceModels.WorkspaceView;
import com.applocker.service.MdmPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ApplockerWorkspaceService {

    static final String LOCAL_POLICY = "Local AppLocker policy";
    private static final List<CollectionCopy> COPIES = List.of(
            new CollectionCopy("Exe", "Executable", "Executable files",
                    "Path, publisher, and hash rules for executable files. Rules saved here stay on this server. Enrolled devices receive the packaged app collection.",
                    List.of("Path", "Publisher", "Hash"), false),
            new CollectionCopy("Msi", "Windows Installer", "Windows Installer",
                    "Path, publisher, and hash rules for Windows Installer files. Rules saved here stay on this server. Enrolled devices receive the packaged app collection.",
                    List.of("Path", "Publisher", "Hash"), false),
            new CollectionCopy("Script", "Script", "Scripts",
                    "Path, publisher, and hash rules for scripts. Rules saved here stay on this server. Enrolled devices receive the packaged app collection.",
                    List.of("Path", "Publisher", "Hash"), false),
            new CollectionCopy("Dll", "DLL", "DLL files",
                    "Path, publisher, and hash rules for DLL files. Windows leaves DLL enforcement off until that collection is enforced. Rules saved here stay on this server. Enrolled devices receive the packaged app collection.",
                    List.of("Path", "Publisher", "Hash"), false),
            new CollectionCopy("Appx", "Packaged apps", "Microsoft Store apps",
                    "One publisher rule controls both installing and running a packaged app. Packaged apps are signed, so a rule names the publisher, package name, and version. Allow lets that package install and launch. Block stops install and launch. This does not block the Microsoft Store download page.",
                    List.of("Publisher"), true));

    private final ApplockerPolicyRepository policies;
    private final ApplockerPolicyVersionRepository versions;
    private final ApplockerRuleRepository rules;
    private final ApplockerCollectionSettingRepository settings;
    private final ApplockerPolicyService authored;
    private final MdmPolicyService mdmPolicies;
    private final Clock clock;

    public ApplockerWorkspaceService(
            ApplockerPolicyRepository policies,
            ApplockerPolicyVersionRepository versions,
            ApplockerRuleRepository rules,
            ApplockerCollectionSettingRepository settings,
            ApplockerPolicyService authored,
            MdmPolicyService mdmPolicies,
            Clock clock) {
        this.policies = policies;
        this.versions = versions;
        this.rules = rules;
        this.settings = settings;
        this.authored = authored;
        this.mdmPolicies = mdmPolicies;
        this.clock = clock;
    }

    @Transactional
    public WorkspaceView workspace(boolean identityRunning) {
        LocalPolicy local = ensureLocal();
        return new WorkspaceView(local.policy().id(), identityRunning, collectionViews(local));
    }

    @Transactional
    public List<StoreAppView> storeApps(List<InstalledStoreApp> installed) {
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> current = rules.findByVersionIdOrderBySortOrderAsc(local.version().id());
        ApplockerCollectionSettingEntity setting = setting(local.version(), "Appx", current);
        Map<String, ApplockerRuleEntity> specific = specificStoreRules(current);
        List<StoreAppView> rows = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (InstalledStoreApp app : installed) {
            if (app.packageName() == null || app.packageName().isBlank()) {
                continue;
            }
            String key = app.packageName().toLowerCase(Locale.ROOT);
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            rows.add(storeView(app.packageName(), app.publisher(), app.version(), specific.get(key), setting));
        }
        for (ApplockerRuleEntity rule : specific.values()) {
            String key = rule.productName().toLowerCase(Locale.ROOT);
            if (!seen.contains(key)) {
                rows.add(storeView(rule.productName(), rule.publisherName(), "", rule, setting));
            }
        }
        rows.sort(Comparator.comparing(StoreAppView::packageName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Transactional
    public StoreAppView setStoreApp(StoreAppCommand command) {
        String packageName = packageName(command.packageName());
        String action = storeAction(command.action());
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> desired = new ArrayList<>(rules.findByVersionIdOrderBySortOrderAsc(local.version().id()));
        desired.removeIf(rule -> "Appx".equals(rule.collectionType()) && packageName.equalsIgnoreCase(rule.productName()));
        ApplockerRuleEntity saved = null;
        if (!"None".equals(action)) {
            saved = authored.buildCustomRule(new RuleRequest(
                    "Appx",
                    "Publisher",
                    action,
                    packageName,
                    null,
                    publisherOrAny(command.publisher()),
                    packageName,
                    "*",
                    null,
                    null,
                    null,
                    "S-1-1-0",
                    null,
                    null), desired.size());
            desired.add(saved);
        }
        ApplockerCollectionSettingEntity setting = setting(local.version(), "Appx", desired);
        validate(local.version().id(), desired);
        saveRules(local.version().id(), desired);
        syncDelivery(local, desired);
        String publisher = saved == null ? publisherOrAny(command.publisher()) : saved.publisherName();
        String version = command.version() == null ? "" : command.version().trim();
        return storeView(packageName, publisher, version, "None".equals(action) ? null : saved, setting);
    }

    @Transactional
    public CollectionView setCollection(String collection, CollectionCommand command) {
        String id = collectionId(collection);
        String mode = mode(command.enforcementMode());
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> desired = new ArrayList<>(rules.findByVersionIdOrderBySortOrderAsc(local.version().id()));
        ApplockerCollectionSettingEntity setting = setting(local.version(), id, desired);
        boolean defaultAllow = command.defaultAllow() == null ? setting.defaultAllow() : command.defaultAllow();
        if ("Appx".equals(id) && "Enabled".equals(mode) && !defaultAllow) {
            throw new IllegalArgumentException("Keep the default allow for signed packaged apps on before enforcing. Without it, packaged apps that have no allow rule cannot install or launch.");
        }
        setting.enforcementMode(mode);
        setting.defaultAllow(defaultAllow);
        applyDefault(id, desired, defaultAllow);
        if ("Appx".equals(id)) {
            local.version().enforcementMode(mode);
        }
        validate(local.version().id(), desired);
        saveRules(local.version().id(), desired);
        if ("Appx".equals(id)) {
            syncDelivery(local, desired);
        }
        return collectionViews(local).stream().filter(view -> id.equals(view.id())).findFirst().orElseThrow();
    }

    @Transactional
    public List<RuleView> rulesFor(String collection) {
        String id = collectionId(collection);
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> current = rules.findByVersionIdOrderBySortOrderAsc(local.version().id());
        ApplockerCollectionSettingEntity setting = setting(local.version(), id, current);
        boolean sent = sent(id);
        return current.stream()
                .filter(rule -> id.equals(rule.collectionType()))
                .map(rule -> ruleView(rule, setting.enforcementMode(), sent))
                .toList();
    }

    @Transactional
    public RuleView addRule(RuleRequest request) {
        String id = collectionId(request.collection());
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> desired = new ArrayList<>(rules.findByVersionIdOrderBySortOrderAsc(local.version().id()));
        if (desired.size() >= 80) {
            throw new IllegalArgumentException("A policy can contain at most 80 rules");
        }
        ApplockerRuleEntity rule = authored.buildCustomRule(request, desired.size());
        if (!id.equals(rule.collectionType())) {
            throw new IllegalArgumentException("Collection must be Exe, Dll, Msi, Script, or Appx");
        }
        desired.add(rule);
        ApplockerCollectionSettingEntity setting = setting(local.version(), id, desired);
        validate(local.version().id(), desired);
        saveRules(local.version().id(), desired);
        if ("Appx".equals(id)) {
            syncDelivery(local, desired);
        }
        return ruleView(rule, setting.enforcementMode(), sent(id));
    }

    @Transactional
    public RuleView updateRule(UUID id, String action) {
        String normalized = ruleAction(action);
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> desired = new ArrayList<>(rules.findByVersionIdOrderBySortOrderAsc(local.version().id()));
        ApplockerRuleEntity rule = desired.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(ApplockerPolicyNotFoundException::new);
        if (isDefault(rule)) {
            throw new IllegalArgumentException("Change the default rule with the collection switch");
        }
        rule.action(normalized);
        if ("Appx".equals(rule.collectionType()) && "Deny".equals(normalized) && wildcard(rule.productName())) {
            throw new IllegalArgumentException("Name the packaged app to block");
        }
        ApplockerCollectionSettingEntity setting = setting(local.version(), rule.collectionType(), desired);
        validate(local.version().id(), desired);
        if ("Appx".equals(rule.collectionType())) {
            syncDelivery(local, desired);
        }
        return ruleView(rule, setting.enforcementMode(), sent(rule.collectionType()));
    }

    @Transactional
    public void deleteRule(UUID id) {
        LocalPolicy local = ensureLocal();
        List<ApplockerRuleEntity> desired = new ArrayList<>(rules.findByVersionIdOrderBySortOrderAsc(local.version().id()));
        ApplockerRuleEntity rule = desired.stream().filter(item -> item.id().equals(id)).findFirst()
                .orElseThrow(ApplockerPolicyNotFoundException::new);
        if (isDefault(rule)) {
            throw new IllegalArgumentException("Change the default rule with the collection switch");
        }
        desired.remove(rule);
        validate(local.version().id(), desired);
        saveRules(local.version().id(), desired);
        if ("Appx".equals(rule.collectionType())) {
            syncDelivery(local, desired);
        }
    }

    private List<CollectionView> collectionViews(LocalPolicy local) {
        List<ApplockerRuleEntity> current = rules.findByVersionIdOrderBySortOrderAsc(local.version().id());
        List<CollectionView> views = new ArrayList<>();
        for (CollectionCopy copy : COPIES) {
            ApplockerCollectionSettingEntity setting = setting(local.version(), copy.id(), current);
            int count = (int) current.stream().filter(rule -> copy.id().equals(rule.collectionType())).count();
            views.add(new CollectionView(
                    copy.id(),
                    copy.label(),
                    copy.title(),
                    copy.description(),
                    copy.conditions(),
                    copy.sentToDevices(),
                    count,
                    setting.enforcementMode(),
                    setting.defaultAllow()));
        }
        return views;
    }

    private LocalPolicy ensureLocal() {
        ApplockerPolicyEntity policy = policies.findByName(LOCAL_POLICY).orElseGet(this::createLocal);
        ApplockerPolicyVersionEntity version = versions.findFirstByPolicyIdOrderByVersionNumberDesc(policy.id())
                .orElseThrow(ApplockerPolicyNotFoundException::new);
        return new LocalPolicy(policy, version);
    }

    private ApplockerPolicyEntity createLocal() {
        ApplockerPolicyEntity policy = policies.save(new ApplockerPolicyEntity(
                UUID.randomUUID(),
                LOCAL_POLICY,
                "Rules managed for every AppLocker collection.",
                clock.instant()));
        ApplockerPolicyVersionEntity version = versions.save(new ApplockerPolicyVersionEntity(
                UUID.randomUUID(),
                policy.id(),
                1,
                "AuditOnly",
                clock.instant()));
        List<ApplockerRuleEntity> desired = new ArrayList<>(authored.defaultRules("Appx", 0));
        saveRules(version.id(), desired);
        for (CollectionCopy copy : COPIES) {
            ApplockerCollectionSettingEntity setting = new ApplockerCollectionSettingEntity();
            setting.id(UUID.randomUUID());
            setting.versionId(version.id());
            setting.collectionType(copy.id());
            setting.enforcementMode("AuditOnly");
            setting.defaultAllow("Appx".equals(copy.id()));
            settings.save(setting);
        }
        policies.flush();
        mdmPolicies.syncStoreDelivery(policy.id(), policy.name(), policy.description(), policy.createdAt(), false, "AuditOnly", 0);
        return policy;
    }

    private ApplockerCollectionSettingEntity setting(ApplockerPolicyVersionEntity version, String collection, List<ApplockerRuleEntity> current) {
        return settings.findByVersionIdAndCollectionType(version.id(), collection).orElseGet(() -> {
            ApplockerCollectionSettingEntity created = new ApplockerCollectionSettingEntity();
            created.id(UUID.randomUUID());
            created.versionId(version.id());
            created.collectionType(collection);
            created.enforcementMode("Appx".equals(collection) ? version.enforcementMode() : "AuditOnly");
            created.defaultAllow(current.stream().anyMatch(rule -> collection.equals(rule.collectionType()) && isDefault(rule)));
            return settings.save(created);
        });
    }

    private void applyDefault(String collection, List<ApplockerRuleEntity> desired, boolean on) {
        desired.removeIf(rule -> collection.equals(rule.collectionType()) && isDefault(rule));
        if (on) {
            desired.addAll(authored.defaultRules(collection, desired.size()));
        }
    }

    private void validate(UUID versionId, List<ApplockerRuleEntity> desired) {
        Map<String, String> modes = new LinkedHashMap<>();
        for (ApplockerCollectionSettingEntity setting : settings.findByVersionId(versionId)) {
            modes.put(setting.collectionType(), setting.enforcementMode());
        }
        ApplockerPolicyXml.collections(desired, modes);
    }

    private void saveRules(UUID versionId, List<ApplockerRuleEntity> desired) {
        List<UUID> keep = desired.stream().map(ApplockerRuleEntity::id).toList();
        for (ApplockerRuleEntity rule : rules.findByVersionIdOrderBySortOrderAsc(versionId)) {
            if (!keep.contains(rule.id())) {
                rules.delete(rule);
            }
        }
        int order = 0;
        for (ApplockerRuleEntity rule : desired) {
            rule.versionId(versionId);
            rule.sortOrder(order++);
            rules.save(rule);
        }
    }

    private void syncDelivery(LocalPolicy local, List<ApplockerRuleEntity> desired) {
        ApplockerCollectionSettingEntity appx = setting(local.version(), "Appx", desired);
        boolean specific = desired.stream().anyMatch(rule -> "Appx".equals(rule.collectionType()) && !isDefault(rule));
        boolean deliver = specific || "Enabled".equals(appx.enforcementMode()) || mdmPolicies.grouping(local.policy().id()).isPresent();
        int blocked = (int) desired.stream().filter(rule -> "Appx".equals(rule.collectionType()) && "Deny".equals(rule.action())).count();
        mdmPolicies.syncStoreDelivery(
                local.policy().id(),
                local.policy().name(),
                local.policy().description(),
                local.policy().createdAt(),
                deliver,
                appx.enforcementMode(),
                blocked);
    }

    private StoreAppView storeView(String packageName, String publisher, String version, ApplockerRuleEntity rule, ApplockerCollectionSettingEntity setting) {
        String action = rule == null ? "None" : rule.action();
        return new StoreAppView(packageName, publisher == null ? "" : publisher, version == null ? "" : version, action,
                storeResult(action, setting.enforcementMode(), setting.defaultAllow()));
    }

    private RuleView ruleView(ApplockerRuleEntity rule, String mode, boolean sent) {
        return new RuleView(
                rule.id(),
                rule.collectionType(),
                rule.ruleKind(),
                rule.action(),
                rule.name(),
                rule.path(),
                rule.publisherName(),
                rule.productName(),
                rule.binaryName(),
                rule.userOrGroupSid(),
                isDefault(rule),
                sent ? storeResult(rule.action(), mode, false) : savedResult(rule.action()));
    }

    private static String storeResult(String action, String mode, boolean defaultAllow) {
        if ("Deny".equals(action)) {
            return "Enabled".equals(mode) ? "Blocked from install and launch" : "Would be blocked (deny rule)";
        }
        if ("Allow".equals(action)) {
            return "Allowed to install and launch";
        }
        if (defaultAllow) {
            return "Runs (default rule)";
        }
        return "Enabled".equals(mode) ? "Blocked because no allow rule matches" : "No matching rule";
    }

    private static String savedResult(String action) {
        return "Deny".equals(action) ? "Saved deny rule" : "Saved allow rule";
    }

    private static Map<String, ApplockerRuleEntity> specificStoreRules(List<ApplockerRuleEntity> current) {
        Map<String, ApplockerRuleEntity> specific = new LinkedHashMap<>();
        for (ApplockerRuleEntity rule : current) {
            if ("Appx".equals(rule.collectionType()) && "Publisher".equals(rule.ruleKind()) && !wildcard(rule.productName())) {
                specific.put(rule.productName().toLowerCase(Locale.ROOT), rule);
            }
        }
        return specific;
    }

    private static boolean isDefault(ApplockerRuleEntity rule) {
        return rule.name() != null && rule.name().startsWith("(Default Rule)");
    }

    private static boolean sent(String collection) {
        return COPIES.stream().anyMatch(copy -> copy.id().equals(collection) && copy.sentToDevices());
    }

    private static String collectionId(String value) {
        String collection = value == null ? "" : value.trim();
        if (COPIES.stream().noneMatch(copy -> copy.id().equals(collection))) {
            throw new IllegalArgumentException("Collection must be Exe, Dll, Msi, Script, or Appx");
        }
        return collection;
    }

    private static String mode(String value) {
        if ("AuditOnly".equals(value)) {
            return "AuditOnly";
        }
        if ("Enabled".equals(value)) {
            return "Enabled";
        }
        throw new IllegalArgumentException("Enforcement mode must be AuditOnly or Enabled");
    }

    private static String storeAction(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Action must be Allow, Deny, or None");
        }
        return switch (value.trim()) {
            case "Allow", "Deny", "None" -> value.trim();
            case "Block" -> "Deny";
            default -> throw new IllegalArgumentException("Action must be Allow, Deny, or None");
        };
    }

    private static String ruleAction(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Action must be Allow or Deny");
        }
        return switch (value.trim()) {
            case "Allow" -> "Allow";
            case "Deny", "Block" -> "Deny";
            default -> throw new IllegalArgumentException("Action must be Allow or Deny");
        };
    }

    private static String packageName(String value) {
        String name = value == null ? "" : value.trim();
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")) {
            throw new IllegalArgumentException("Name the packaged app. A deny-all Store app rule is rejected");
        }
        return name;
    }

    private static String publisherOrAny(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return "*";
        }
        if (text.indexOf('<') >= 0 || text.indexOf('>') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Publisher name is invalid");
        }
        if (text.length() > 300) {
            throw new IllegalArgumentException("Publisher name is too long");
        }
        return text;
    }

    private static boolean wildcard(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String text = value.trim();
        return "*".equals(text) || "CN=*".equalsIgnoreCase(text);
    }

    private record LocalPolicy(ApplockerPolicyEntity policy, ApplockerPolicyVersionEntity version) {
    }

    private record CollectionCopy(String id, String label, String title, String description, List<String> conditions, boolean sentToDevices) {
    }
}
