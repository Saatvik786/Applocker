package com.applocker.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ApplockerPolicyXml {

    static final List<String> COLLECTIONS = List.of("Exe", "Dll", "Msi", "Script", "Appx");

    private ApplockerPolicyXml() {
    }

    static String cspNode(String collection) {
        String folder = switch (collection) {
            case "Exe" -> "EXE";
            case "Dll" -> "DLL";
            case "Msi" -> "MSI";
            case "Script" -> "Script";
            case "Appx" -> "StoreApps";
            default -> throw new IllegalArgumentException("Unknown AppLocker collection");
        };
        return "./Device/Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions/{Grouping}/" + folder + "/Policy";
    }

    static List<PolicyCollection> collections(List<ApplockerRuleEntity> rules) {
        return collections(rules, Map.of());
    }

    static List<PolicyCollection> collections(List<ApplockerRuleEntity> rules, Map<String, String> requestedModes) {
        Map<String, List<ApplockerRuleEntity>> grouped = new LinkedHashMap<>();
        for (String collection : COLLECTIONS) {
            grouped.put(collection, new ArrayList<>());
        }
        for (ApplockerRuleEntity rule : rules) {
            grouped.get(rule.collectionType()).add(rule);
        }
        List<PolicyCollection> collections = new ArrayList<>();
        for (String collection : COLLECTIONS) {
            List<ApplockerRuleEntity> items = grouped.get(collection);
            if (items.isEmpty()) {
                continue;
            }
            String mode = enforcementMode(collection, items, requestedModes.get(collection));
            collections.add(new PolicyCollection(collection, cspNode(collection), mode, ruleCollection(collection, mode, items)));
        }
        return collections;
    }

    private static String enforcementMode(String collection, List<ApplockerRuleEntity> rules, String requested) {
        boolean deny = rules.stream().anyMatch(rule -> "Deny".equals(rule.action()));
        if ("Appx".equals(collection) && deny) {
            boolean targeted = rules.stream().filter(rule -> "Deny".equals(rule.action())).allMatch(ApplockerPolicyXml::targetedStoreRule);
            boolean allowSigned = rules.stream().anyMatch(ApplockerPolicyXml::allowAllSigned);
            if (!targeted) {
                throw new IllegalArgumentException("A deny-all Store app rule is rejected");
            }
            if ("Enabled".equals(requested) && !allowSigned) {
                throw new IllegalArgumentException("A Store app block must keep other signed packaged apps allowed");
            }
        }
        if (requested != null) {
            if (!"AuditOnly".equals(requested) && !"Enabled".equals(requested)) {
                throw new IllegalArgumentException("Enforcement mode must be AuditOnly or Enabled");
            }
            return requested;
        }
        return "Appx".equals(collection) && deny ? "Enabled" : "AuditOnly";
    }

    private static boolean targetedStoreRule(ApplockerRuleEntity rule) {
        return "Appx".equals(rule.collectionType()) && "Publisher".equals(rule.ruleKind()) && !wildcard(rule.productName());
    }

    private static boolean allowAllSigned(ApplockerRuleEntity rule) {
        return "Allow".equals(rule.action())
                && "Publisher".equals(rule.ruleKind())
                && "*".equals(rule.publisherName())
                && "*".equals(rule.productName())
                && "*".equals(rule.binaryName());
    }

    private static boolean wildcard(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String text = value.trim();
        return "*".equals(text) || "CN=*".equalsIgnoreCase(text);
    }

    private static String ruleCollection(String collection, String mode, List<ApplockerRuleEntity> rules) {
        StringBuilder xml = new StringBuilder();
        xml.append("<RuleCollection Type=\"").append(collection).append("\" EnforcementMode=\"").append(mode).append("\">\n");
        for (ApplockerRuleEntity rule : rules) {
            xml.append(ruleXml(rule));
        }
        xml.append("</RuleCollection>");
        String result = xml.toString();
        if (result.length() > 256_000 || result.contains("<!DOCTYPE") || result.contains("CodeIntegrity")) {
            throw new IllegalArgumentException("The AppLocker policy was rejected");
        }
        return result;
    }

    private static String ruleXml(ApplockerRuleEntity rule) {
        String id = rule.id().toString();
        String opening = switch (rule.ruleKind()) {
            case "Path" -> "FilePathRule";
            case "Publisher" -> "FilePublisherRule";
            case "Hash" -> "FileHashRule";
            default -> throw new IllegalArgumentException("Unknown AppLocker rule");
        };
        StringBuilder xml = new StringBuilder();
        xml.append("  <").append(opening)
                .append(" Id=\"").append(escape(id))
                .append("\" Name=\"").append(escape(rule.name()))
                .append("\" Description=\"\" UserOrGroupSid=\"").append(escape(rule.userOrGroupSid()))
                .append("\" Action=\"").append(action(rule)).append("\">\n");
        xml.append("    <Conditions>\n");
        switch (rule.ruleKind()) {
            case "Path" -> xml.append("      <FilePathCondition Path=\"").append(escape(rule.path())).append("\" />\n");
            case "Publisher" -> xml.append("""
                          <FilePublisherCondition PublisherName="%s" ProductName="%s" BinaryName="%s">
                            <BinaryVersionRange LowSection="%s" HighSection="%s" />
                          </FilePublisherCondition>
                    """.formatted(
                    escape(rule.publisherName()),
                    escape(rule.productName()),
                    escape(rule.binaryName()),
                    escape(versionToken(rule.binaryVersionLow())),
                    escape(versionToken(rule.binaryVersionHigh()))));
            case "Hash" -> xml.append("""
                          <FileHashCondition>
                            <FileHash Type="SHA256" Data="%s" SourceFileName="%s" SourceFileLength="%d" />
                          </FileHashCondition>
                    """.formatted(escape(rule.hashData()), escape(rule.sourceFileName()), rule.sourceFileLength()));
            default -> throw new IllegalArgumentException("Unknown AppLocker rule");
        }
        xml.append("    </Conditions>\n");
        xml.append("  </").append(opening).append(">\n");
        return xml.toString();
    }

    private static String versionToken(String value) {
        if (value == null || value.isBlank() || "*".equals(value.trim())) {
            return "*";
        }
        String text = value.trim();
        if (!text.matches("(?:\\*|[0-9]{1,5})(?:\\.(?:\\*|[0-9]{1,5})){0,3}")) {
            throw new IllegalArgumentException("Package version must use numbers, dots, or *");
        }
        return text;
    }

    private static String action(ApplockerRuleEntity rule) {
        return "Deny".equals(rule.action()) ? "Deny" : "Allow";
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
