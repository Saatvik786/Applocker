package com.applocker.mdm;

import com.applocker.enrollment.soap.XmlSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Namespace-aware SyncML 1.2 parser for OMA-DM management requests.
 */
public final class SyncMlInbound {

    private static final List<String> CLIENT_COMMANDS =
            List.of("Alert", "Replace", "Add", "Delete", "Get", "Exec", "Results");

    private SyncMlInbound() {
    }

    public static InboundMessage parse(byte[] body) {
        if (body == null || body.length == 0) {
            return InboundMessage.empty();
        }
        Document document = XmlSupport.parse(body);
        Element root = document.getDocumentElement();
        Element syncHdr = firstChild(root, "SyncHdr");
        String sessionId = text(syncHdr, "SessionID", "1");
        String msgId = text(syncHdr, "MsgID", "1");
        String deviceLocUri = locUri(syncHdr, "Source");
        if (deviceLocUri == null || deviceLocUri.isBlank()) {
            deviceLocUri = devIdFromBody(root);
        }
        String serverLocUri = locUri(syncHdr, "Target");

        Element syncBody = firstChild(root, "SyncBody");
        List<SyncMlMessages.ClientReply> replies = new ArrayList<>();
        List<ClientCommand> commands = new ArrayList<>();
        boolean clientFinal = false;
        if (syncBody != null) {
            for (Element child : directElements(syncBody)) {
                String name = localName(child);
                if ("Final".equals(name)) {
                    clientFinal = true;
                    continue;
                }
                if ("Status".equals(name) || "Results".equals(name)) {
                    replies.add(toReply(name, child));
                    if (CLIENT_COMMANDS.contains(name)) {
                        commands.add(new ClientCommand(name, text(child, "CmdID", "")));
                    }
                    continue;
                }
                if (CLIENT_COMMANDS.contains(name)) {
                    commands.add(new ClientCommand(name, text(child, "CmdID", "")));
                }
            }
        }
        return new InboundMessage(
                new Header(sessionId, msgId, deviceLocUri, serverLocUri),
                List.copyOf(commands),
                List.copyOf(replies),
                clientFinal);
    }

    public static InboundMessage parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return InboundMessage.empty();
        }
        return parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static SyncMlMessages.ClientReply toReply(String kind, Element element) {
        String itemLocUri = null;
        String itemData = null;
        for (Element item : directElements(element)) {
            if (!"Item".equals(localName(item))) {
                continue;
            }
            if (itemLocUri == null) {
                itemLocUri = itemLocUri(item);
            }
            if (itemData == null) {
                itemData = itemData(item);
            }
        }
        return new SyncMlMessages.ClientReply(
                kind,
                text(element, "CmdID", null),
                text(element, "MsgRef", null),
                text(element, "CmdRef", null),
                text(element, "Cmd", null),
                text(element, "Data", null),
                text(element, "TargetRef", null),
                itemLocUri,
                itemData);
    }

    private static String itemLocUri(Element item) {
        String fromSource = locUri(item, "Source");
        if (fromSource != null && !fromSource.isBlank()) {
            return fromSource;
        }
        return locUri(item, "Target");
    }

    private static String devIdFromBody(Element root) {
        Element syncBody = firstChild(root, "SyncBody");
        if (syncBody == null) {
            return null;
        }
        for (Element child : directElements(syncBody)) {
            if (!"Alert".equals(localName(child))) {
                continue;
            }
            for (Element item : directElements(child)) {
                if (!"Item".equals(localName(item))) {
                    continue;
                }
                String uri = locUri(item, "Source");
                if (uri == null) {
                    uri = locUri(item, "Target");
                }
                if (uri != null && uri.toLowerCase(Locale.ROOT).contains("devid")) {
                    String data = itemData(item);
                    if (data != null && !data.isBlank()) {
                        return data.trim();
                    }
                }
            }
        }
        return null;
    }

    private static String firstLocUri(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        int open = fragment.indexOf("<LocURI>");
        if (open < 0) {
            open = fragment.indexOf(":LocURI>");
            if (open < 0) {
                return null;
            }
            open = fragment.indexOf('>', open) + 1;
        } else {
            open += "<LocURI>".length();
        }
        int close = fragment.indexOf("</", open);
        if (close < 0) {
            close = fragment.indexOf("<", open);
        }
        if (close < 0) {
            return null;
        }
        return fragment.substring(open, close).trim();
    }

    private static String itemData(Element item) {
        for (Element child : directElements(item)) {
            if ("Data".equals(localName(child))) {
                String value = child.getTextContent();
                return value == null ? null : value.trim();
            }
        }
        return firstData(elementText(item));
    }

    private static String firstData(String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return null;
        }
        int open = fragment.indexOf("<Data>");
        if (open < 0) {
            return null;
        }
        open += "<Data>".length();
        int close = fragment.indexOf("</Data>", open);
        if (close < 0) {
            return null;
        }
        return fragment.substring(open, close);
    }

    private static String locUri(Element parent, String wrapperLocalName) {
        if (parent == null) {
            return null;
        }
        Element wrapper = firstChild(parent, wrapperLocalName);
        if (wrapper == null) {
            return null;
        }
        Element loc = firstChild(wrapper, "LocURI");
        return loc == null ? null : loc.getTextContent().trim();
    }

    private static String text(Element parent, String localName, String fallback) {
        if (parent == null) {
            return fallback;
        }
        Element node = firstChild(parent, localName);
        if (node == null) {
            return fallback;
        }
        String value = node.getTextContent();
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        return value.isEmpty() && fallback != null ? fallback : value;
    }

    private static Element firstChild(Element parent, String localName) {
        if (parent == null) {
            return null;
        }
        for (Element child : directElements(parent)) {
            if (localName.equals(localName(child))) {
                return child;
            }
        }
        return null;
    }

    private static List<Element> directElements(Element parent) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            Node node = nodes.item(index);
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static String localName(Element element) {
        String name = element.getLocalName();
        return name != null ? name : element.getNodeName();
    }

    private static String elementText(Element element) {
        return element.getTextContent() == null ? "" : element.getTextContent();
    }

    public record Header(String sessionId, String msgId, String deviceLocUri, String serverLocUri) {
    }

    public record ClientCommand(String name, String cmdId) {
    }

    public record InboundMessage(
            Header header,
            List<ClientCommand> clientCommands,
            List<SyncMlMessages.ClientReply> replies,
            boolean clientFinal) {

        static InboundMessage empty() {
            return new InboundMessage(
                    new Header("1", "1", null, null),
                    List.of(),
                    List.of(),
                    false);
        }
    }
}
