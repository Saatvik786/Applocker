package com.applocker.enrollment.soap;

import com.applocker.enrollment.EnrollmentRejectedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class XmlSupport {

    private XmlSupport() {
    }

    public static Document parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new EnrollmentRejectedException();
        }
        int offset = 0;
        if (body.length >= 3 && (body[0] & 0xFF) == 0xEF && (body[1] & 0xFF) == 0xBB && (body[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(body, offset, body.length - offset));
        } catch (EnrollmentRejectedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new EnrollmentRejectedException();
        }
    }

    public static String firstText(Document document, String localName) {
        if (document == null) {
            return null;
        }
        return text(document.getDocumentElement(), localName);
    }

    public static Map<String, String> contextItems(Document document) {
        Map<String, String> values = new LinkedHashMap<>();
        collect(document.getDocumentElement(), values);
        return values;
    }

    public static String xml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static byte[] utf8(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(Node node, String localName) {
        if (node == null) {
            return null;
        }
        if (localName.equals(node.getLocalName()) && node.getTextContent() != null) {
            String value = node.getTextContent().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            String found = text(children.item(index), localName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collect(Node node, Map<String, String> values) {
        if (node instanceof Element element && "ContextItem".equals(element.getLocalName())) {
            String name = attribute(element, "Name");
            String value = text(element, "Value");
            if (name != null && value != null && !values.containsKey(name)) {
                values.put(name, value);
            }
        }
        NodeList children = node.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            collect(children.item(index), values);
        }
    }

    private static String attribute(Element element, String name) {
        NamedNodeMap attributes = element.getAttributes();
        Node item = attributes.getNamedItem(name);
        if (item == null) {
            item = attributes.getNamedItemNS(null, name);
        }
        return item == null ? null : item.getTextContent();
    }
}
