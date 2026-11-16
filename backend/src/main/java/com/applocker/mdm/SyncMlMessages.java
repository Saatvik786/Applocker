package com.applocker.mdm;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads SyncML Status and Results elements. An HTTP 200 on the management
 * request only means the server accepted the message. The SyncML Data value
 * is the command result.
 */
public final class SyncMlMessages {

    static final String ENROLLMENT_TYPE_URI =
            "./Device/Vendor/MSFT/DMClient/Provider/%s/EnrollmentType";
    static final String APPLOCKER_ROOT_URI =
            "./Vendor/MSFT/AppLocker/ApplicationLaunchRestrictions";

    private static final Pattern BLOCK = Pattern.compile("<(Status|Results)>(.*?)</\\1>", Pattern.DOTALL);
    private static final Pattern ITEM = Pattern.compile("<Item>(.*?)</Item>", Pattern.DOTALL);
    private static final Pattern SIMPLE = Pattern.compile("<(CmdID|MsgRef|CmdRef|Cmd|Data|TargetRef)>([^<]*)</\\1>");
    private static final Pattern LOC_URI = Pattern.compile("<LocURI>([^<]*)</LocURI>");
    private static final Pattern OUTGOING = Pattern.compile(
            "<(Get|Add|Replace|Delete|Exec)>\\s*<CmdID>(\\d{1,10})</CmdID>.*?<LocURI>([^<]*)</LocURI>",
            Pattern.DOTALL);
    private static final Pattern PROVIDER_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Pattern GROUPING_GUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private SyncMlMessages() {
    }

    public static String mdmEnrollmentType(List<ClientReply> replies) {
        for (ClientReply reply : replies) {
            if (!"Results".equals(reply.kind()) || reply.itemLocUri() == null || reply.itemData() == null) {
                continue;
            }
            String uri = reply.itemLocUri();
            if (uri.contains("/DMClient/Provider/") && uri.endsWith("/EnrollmentType")) {
                return reply.itemData().trim();
            }
        }
        return null;
    }

    public static boolean applockerSupportedForEnrollment(List<ClientReply> replies) {
        String type = mdmEnrollmentType(replies);
        if (type == null) {
            return true;
        }
        return "Device".equalsIgnoreCase(type);
    }

    public static boolean syncSessionFailed(List<ClientReply> replies) {
        for (ClientReply reply : replies) {
            if ("Status".equals(reply.kind())
                    && "SyncHdr".equals(reply.cmd())
                    && "500".equals(reply.data())) {
                return true;
            }
        }
        return false;
    }

    public static boolean diagnosticGetResultsReady(List<ClientReply> replies) {
        for (ClientReply reply : replies) {
            if (!"Results".equals(reply.kind()) || reply.itemLocUri() == null) {
                continue;
            }
            String uri = reply.itemLocUri();
            if (APPLOCKER_ROOT_URI.equals(uri)
                    || uri.endsWith("/ApplicationLaunchRestrictions")
                    || (uri.contains("/DMClient/Provider/") && uri.endsWith("/EnrollmentType"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean policyReplaceAcknowledged(List<ClientReply> replies) {
        for (ClientReply reply : replies) {
            if (!"Status".equals(reply.kind()) || !"Replace".equals(reply.cmd())) {
                continue;
            }
            String target = reply.targetRef() != null ? reply.targetRef() : reply.itemLocUri();
            if (target == null || !target.contains("/StoreApps/Policy")) {
                continue;
            }
            if ("200".equals(reply.data())) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> existingApplockerGroupings(List<ClientReply> replies) {
        LinkedHashSet<String> groupings = new LinkedHashSet<>();
        for (ClientReply reply : replies) {
            if (!"Results".equals(reply.kind()) || reply.itemLocUri() == null) {
                continue;
            }
            String uri = reply.itemLocUri();
            if (!APPLOCKER_ROOT_URI.equals(uri) && !uri.endsWith("/ApplicationLaunchRestrictions")) {
                continue;
            }
            if (reply.itemData() == null || reply.itemData().isBlank()) {
                continue;
            }
            Matcher matcher = GROUPING_GUID.matcher(reply.itemData());
            while (matcher.find()) {
                groupings.add(matcher.group().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(groupings);
    }

    public static ParseResult parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return new ParseResult(List.of(), null);
        }
        String error = mismatch(xml, "Status");
        String resultsError = mismatch(xml, "Results");
        if (error == null) {
            error = resultsError;
        } else if (resultsError != null) {
            error = error + "; " + resultsError;
        }
        if (xml.contains("<SyncML") || xml.contains(":SyncML")) {
            return new ParseResult(SyncMlInbound.parse(xml).replies(), error);
        }
        List<ClientReply> replies = new ArrayList<>();
        Matcher blocks = BLOCK.matcher(xml);
        while (blocks.find()) {
            replies.add(reply(blocks.group(1), blocks.group(2)));
        }
        return new ParseResult(List.copyOf(replies), error);
    }

    public static int maxOutgoingCmdId(String xml) {
        int max = 0;
        for (OutgoingCommand command : outgoing(xml)) {
            try {
                max = Math.max(max, Integer.parseInt(command.cmdId()));
            } catch (NumberFormatException ignored) {
                // ignore malformed CmdID fragments in tests
            }
        }
        return max;
    }

    public static String diagnosticGets(int firstCmdId, String providerId) {
        StringBuilder xml = new StringBuilder();
        int cmdId = firstCmdId;
        if (providerId != null && PROVIDER_ID.matcher(providerId).matches()) {
            xml.append(get(cmdId++, ENROLLMENT_TYPE_URI.formatted(providerId)));
        }
        xml.append(get(cmdId, APPLOCKER_ROOT_URI));
        return xml.toString();
    }

    public static int commandCount(String xml) {
        return outgoing(xml).size();
    }

    public static List<OutgoingCommand> outgoing(String xml) {
        if (xml == null || xml.isBlank()) {
            return List.of();
        }
        List<OutgoingCommand> commands = new ArrayList<>();
        Matcher matcher = OUTGOING.matcher(xml);
        while (matcher.find()) {
            commands.add(new OutgoingCommand(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return List.copyOf(commands);
    }

    private static ClientReply reply(String kind, String body) {
        String items = "";
        Matcher item = ITEM.matcher(body);
        StringBuilder itemBodies = new StringBuilder();
        while (item.find()) {
            itemBodies.append(item.group(1));
        }
        items = itemBodies.toString();
        String withoutItems = ITEM.matcher(body).replaceAll("");
        return new ClientReply(
                kind,
                value(withoutItems, "CmdID"),
                value(withoutItems, "MsgRef"),
                value(withoutItems, "CmdRef"),
                value(withoutItems, "Cmd"),
                value(withoutItems, "Data"),
                value(withoutItems, "TargetRef"),
                value(items, "LocURI"),
                items.isEmpty() ? null : value(items, "Data"));
    }

    private static String value(String xml, String tag) {
        Matcher matcher = "LocURI".equals(tag) ? LOC_URI.matcher(xml) : SIMPLE.matcher(xml);
        while (matcher.find()) {
            if ("LocURI".equals(tag) || tag.equals(matcher.group(1))) {
                return matcher.group("LocURI".equals(tag) ? 1 : 2);
            }
        }
        return null;
    }

    private static String mismatch(String xml, String tag) {
        int open = count(xml, "<" + tag + ">");
        int close = count(xml, "</" + tag + ">");
        if (open == close) {
            return null;
        }
        return "incomplete " + tag + " elements: open=" + open + " close=" + close;
    }

    private static int count(String xml, String token) {
        int count = 0;
        int from = 0;
        while ((from = xml.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    private static String get(int cmdId, String locUri) {
        return """
                  <Get>
                    <CmdID>%d</CmdID>
                    <Item>
                      <Target><LocURI>%s</LocURI></Target>
                    </Item>
                  </Get>
                """.formatted(cmdId, locUri);
    }

    public record ParseResult(List<ClientReply> replies, String error) {
    }

    public record ClientReply(
            String kind,
            String cmdId,
            String msgRef,
            String cmdRef,
            String cmd,
            String data,
            String targetRef,
            String itemLocUri,
            String itemData) {
    }

    public record OutgoingCommand(String cmd, String cmdId, String locUri) {
    }
}
