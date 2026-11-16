package com.applocker.enrollment.soap;

import com.applocker.enrollment.EnrollmentRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Controller
@RequestMapping("/api/v1/mdm/windows/enrollment")
public class WindowsEnrollmentController {

    private static final Logger log = LoggerFactory.getLogger(WindowsEnrollmentController.class);
    private static final int MAX_BODY = 1_000_000;

    private final WindowsEnrollmentService enrollment;

    public WindowsEnrollmentController(WindowsEnrollmentService enrollment) {
        this.enrollment = enrollment;
    }

    @GetMapping("/discovery/{tokenId}")
    public void discoveryGet(@PathVariable String tokenId, HttpServletResponse response) throws IOException {
        try {
            enrollment.discovery(parse(tokenId), "", null);
            write(response, 200, "text/html; charset=utf-8", new byte[0]);
        } catch (EnrollmentRejectedException exception) {
            log.info("Discovery request rejected");
            write(response, 401, "application/soap+xml; charset=utf-8", EnrollmentMessages.fault());
        }
    }

    @PostMapping("/discovery/{tokenId}")
    public void discovery(@PathVariable String tokenId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            byte[] body = readBody(request);
            Document document = body.length == 0 ? null : XmlSupport.parse(body);
            byte[] responseBody = enrollment.discovery(
                    parse(tokenId),
                    document == null ? "" : XmlSupport.firstText(document, "MessageID"),
                    document == null ? null : XmlSupport.firstText(document, "RequestVersion"));
            write(response, 200, "application/soap+xml; charset=utf-8", responseBody);
        } catch (EnrollmentRejectedException exception) {
            log.info("Discovery request rejected");
            write(response, 401, "application/soap+xml; charset=utf-8", EnrollmentMessages.fault());
        } catch (RuntimeException exception) {
            log.warn("Discovery request failed: {}", exception.getClass().getSimpleName());
            write(response, 500, "application/soap+xml; charset=utf-8", EnrollmentMessages.fault());
        }
    }

    @PostMapping("/service/{tokenId}")
    public void service(@PathVariable String tokenId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Document document = XmlSupport.parse(readBody(request));
            String action = XmlSupport.firstText(document, "Action");
            boolean policies = action != null && action.contains("GetPolicies");
            byte[] responseBody = policies
                    ? enrollment.policies(parse(tokenId), document)
                    : enrollment.enroll(parse(tokenId), document);
            String contentType = policies ? "application/soap+xml; charset=utf-8" : "text/xml; charset=utf-8";
            write(response, 200, contentType, responseBody);
        } catch (EnrollmentRejectedException exception) {
            log.info("Enrollment request rejected");
            write(response, 401, "application/soap+xml; charset=utf-8", EnrollmentMessages.fault());
        } catch (RuntimeException exception) {
            log.warn("Enrollment request failed: {}", exception.getClass().getSimpleName());
            write(response, 500, "application/soap+xml; charset=utf-8", EnrollmentMessages.fault());
        }
    }

    private static UUID parse(String tokenId) {
        try {
            return UUID.fromString(tokenId);
        } catch (IllegalArgumentException exception) {
            throw new EnrollmentRejectedException();
        }
    }

    private static byte[] readBody(HttpServletRequest request) throws IOException {
        InputStream input = request.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY) {
                throw new EnrollmentRejectedException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void write(HttpServletResponse response, int status, String contentType, byte[] body) throws IOException {
        response.setStatus(status);
        response.setContentType(contentType);
        response.setContentLength(body.length);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Pragma", "no-cache");
        response.getOutputStream().write(body);
    }
}
