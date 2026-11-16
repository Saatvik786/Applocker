package com.applocker.policy;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class InstalledStoreAppSource {

    private static final char SEPARATOR = '\u001f';
    private final Object lock = new Object();
    private List<InstalledStoreApp> apps = List.of();
    private boolean identityRunning;
    private long cachedAt;

    public List<InstalledStoreApp> apps() {
        refresh();
        return apps;
    }

    public boolean identityRunning() {
        refresh();
        return identityRunning;
    }

    private void refresh() {
        synchronized (lock) {
            if (cachedAt != 0 && System.currentTimeMillis() - cachedAt < 20_000) {
                return;
            }
            List<InstalledStoreApp> found = new ArrayList<>();
            boolean running = false;
            try {
                Process process = new ProcessBuilder(
                        "powershell.exe",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        """
                        $ErrorActionPreference = 'SilentlyContinue'
                        $identity = (Get-Service -Name AppIDSvc).Status
                        Write-Output ('IDENTITY' + [char]31 + $identity)
                        Get-AppxPackage | Where-Object { -not $_.IsFramework } | ForEach-Object {
                          $name = ([string]$_.Name) -replace '[\\r\\n]', ' '
                          $publisher = ([string]$_.Publisher) -replace '[\\r\\n]', ' '
                          $version = ([string]$_.Version)
                          if ($name) { Write-Output ($name + [char]31 + $publisher + [char]31 + $version) }
                        }
                        """).redirectErrorStream(true).start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(String.valueOf(SEPARATOR), -1);
                        if (parts.length >= 2 && "IDENTITY".equals(parts[0])) {
                            running = "Running".equalsIgnoreCase(parts[1].trim());
                            continue;
                        }
                        if (parts.length < 3 || parts[0].isBlank() || found.size() >= 500) {
                            continue;
                        }
                        found.add(new InstalledStoreApp(parts[0].trim(), parts[1].trim(), parts[2].trim()));
                    }
                }
                if (!process.waitFor(25, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (Exception exception) {
                found = new ArrayList<>();
                running = false;
            }
            apps = List.copyOf(found);
            identityRunning = running;
            cachedAt = System.currentTimeMillis();
        }
    }
}
