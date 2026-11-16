package com.applocker.policy;

import com.applocker.policy.WorkspaceModels.CollectionCommand;
import com.applocker.policy.WorkspaceModels.CollectionView;
import com.applocker.policy.WorkspaceModels.RuleActionCommand;
import com.applocker.policy.WorkspaceModels.RuleView;
import com.applocker.policy.WorkspaceModels.StoreAppCommand;
import com.applocker.policy.WorkspaceModels.StoreAppView;
import com.applocker.policy.WorkspaceModels.WorkspaceView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applocker")
public class ApplockerWorkspaceController {

    private final ApplockerWorkspaceService workspace;
    private final InstalledStoreAppSource installed;

    public ApplockerWorkspaceController(ApplockerWorkspaceService workspace, InstalledStoreAppSource installed) {
        this.workspace = workspace;
        this.installed = installed;
    }

    @GetMapping("/workspace")
    public WorkspaceView workspace() {
        return workspace.workspace(installed.identityRunning());
    }

    @GetMapping("/store-apps")
    public List<StoreAppView> storeApps() {
        return workspace.storeApps(installed.apps());
    }

    @PutMapping("/store-apps")
    public ResponseEntity<?> setStoreApp(@RequestBody StoreAppCommand command) {
        try {
            return ResponseEntity.ok(workspace.setStoreApp(command));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PutMapping("/collections/{collection}")
    public ResponseEntity<?> setCollection(@PathVariable String collection, @RequestBody CollectionCommand command) {
        try {
            CollectionView updated = workspace.setCollection(collection, command);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/rules")
    public ResponseEntity<?> rules(@RequestParam String collection) {
        try {
            List<RuleView> rules = workspace.rulesFor(collection);
            return ResponseEntity.ok(rules);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PostMapping("/rules")
    public ResponseEntity<?> addRule(@RequestBody RuleRequest request) {
        try {
            return ResponseEntity.ok(workspace.addRule(request));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<?> updateRule(@PathVariable UUID id, @RequestBody RuleActionCommand command) {
        try {
            return ResponseEntity.ok(workspace.updateRule(id, command.action()));
        } catch (ApplockerPolicyNotFoundException exception) {
            return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable UUID id) {
        try {
            workspace.deleteRule(id);
            return ResponseEntity.noContent().build();
        } catch (ApplockerPolicyNotFoundException exception) {
            return ResponseEntity.status(404).body(Map.of("error", exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
