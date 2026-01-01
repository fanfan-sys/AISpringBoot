package com.example.aispringboot.controller;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.DocumentActivity;
import com.example.aispringboot.model.DocumentCollaborator;
import com.example.aispringboot.model.DocumentVersion;
import com.example.aispringboot.model.User;
import com.example.aispringboot.repository.DocumentActivityRepository;
import com.example.aispringboot.repository.DocumentCollaboratorRepository;
import com.example.aispringboot.repository.DocumentRepository;
import com.example.aispringboot.repository.DocumentVersionRepository;
import com.example.aispringboot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*", maxAge = 3600)
public class CollaborationController {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentActivityRepository documentActivityRepository;

    @Autowired
    private DocumentCollaboratorRepository documentCollaboratorRepository;

    @Autowired
    private UserRepository userRepository;

    // 获取文档版本历史
    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getDocumentVersions(@PathVariable Long id, Authentication authentication) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // 检查权限
        if (!hasDocumentAccess(document, authentication)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
        }
        
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentOrderByVersionNumberDesc(document);
        
        List<Map<String, Object>> versionData = versions.stream().map(version -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", version.getId());
            data.put("versionNumber", version.getVersionNumber());
            data.put("title", version.getTitle());
            data.put("content", version.getContent());
            data.put("changes", version.getChanges());
            data.put("user", Map.of(
                "id", version.getUser().getId(),
                "username", version.getUser().getUsername()
            ));
            data.put("createdAt", version.getCreatedAt());
            return data;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(versionData);
    }

    // 获取文档活动历史
    @GetMapping("/{id}/activities")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getDocumentActivities(@PathVariable Long id, Authentication authentication) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // 检查权限
        if (!hasDocumentAccess(document, authentication)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
        }
        
        List<DocumentActivity> activities = documentActivityRepository.findTop10ByDocumentOrderByCreatedAtDesc(document);
        
        List<Map<String, Object>> activityData = activities.stream().map(activity -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", activity.getId());
            data.put("activityType", activity.getActivityType());
            data.put("description", activity.getDescription());
            data.put("user", Map.of(
                "id", activity.getUser().getId(),
                "username", activity.getUser().getUsername()
            ));
            data.put("createdAt", activity.getCreatedAt());
            return data;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(activityData);
    }

    // 获取文档协作者
    @GetMapping("/{id}/collaborators")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getDocumentCollaborators(@PathVariable Long id, Authentication authentication) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // 检查权限
        if (!hasDocumentAccess(document, authentication)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
        }
        
        List<DocumentCollaborator> collaborators = documentCollaboratorRepository.findByDocumentAndIsActiveTrue(document);
        
        List<Map<String, Object>> collaboratorData = collaborators.stream().map(collaborator -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", collaborator.getId());
            data.put("user", Map.of(
                "id", collaborator.getUser().getId(),
                "username", collaborator.getUser().getUsername(),
                "email", collaborator.getUser().getEmail()
            ));
            data.put("permission", collaborator.getPermission());
            data.put("joinedAt", collaborator.getJoinedAt());
            data.put("lastActivityAt", collaborator.getLastActivityAt());
            return data;
        }).collect(Collectors.toList());
        
        return ResponseEntity.ok(collaboratorData);
    }

    // 邀请协作者
    @PostMapping("/{id}/invite")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> inviteCollaborator(@PathVariable Long id, 
                                               @RequestBody Map<String, String> request,
                                               Authentication authentication) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // 检查权限 - 只有文档所有者可以邀请
        if (!document.getUser().getUsername().equals(authentication.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only document owner can invite collaborators"));
        }
        
        String email = request.get("email");
        String permission = request.get("permission");
        
        User invitedUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 检查是否已经是协作者
        if (documentCollaboratorRepository.existsByDocumentAndUserAndIsActiveTrue(document, invitedUser)) {
            return ResponseEntity.badRequest().body(Map.of("error", "User is already a collaborator"));
        }
        
        DocumentCollaborator collaborator = new DocumentCollaborator(document, invitedUser, permission);
        documentCollaboratorRepository.save(collaborator);
        
        // 记录活动
        DocumentActivity activity = new DocumentActivity(
            document,
            getCurrentUser(authentication),
            "COLLABORATOR_INVITED",
            "邀请了 " + invitedUser.getUsername() + " 作为协作者"
        );
        documentActivityRepository.save(activity);
        
        return ResponseEntity.ok(Map.of("message", "Collaborator invited successfully"));
    }

    // 切换文档版本
    @PostMapping("/{id}/versions/{versionId}/restore")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> restoreVersion(@PathVariable Long id, 
                                           @PathVariable Long versionId,
                                           Authentication authentication) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        // 检查权限
        if (!hasDocumentAccess(document, authentication)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
        }
        
        DocumentVersion version = documentVersionRepository.findById(versionId)
                .orElseThrow(() -> new RuntimeException("Version not found"));
        
        if (!version.getDocument().getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Version does not belong to this document"));
        }
        
        // 创建新版本（当前状态）
        DocumentVersion currentVersion = new DocumentVersion(
            document,
            documentVersionRepository.countByDocument(document) + 1,
            document.getTitle(),
            document.getContent(),
            "恢复到版本 " + version.getVersionNumber(),
            getCurrentUser(authentication)
        );
        documentVersionRepository.save(currentVersion);
        
        // 恢复到指定版本
        document.setTitle(version.getTitle());
        document.setContent(version.getContent());
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        // 记录活动
        DocumentActivity activity = new DocumentActivity(
            document,
            getCurrentUser(authentication),
            "VERSION_RESTORED",
            "恢复到版本 " + version.getVersionNumber()
        );
        documentActivityRepository.save(activity);
        
        return ResponseEntity.ok(Map.of("message", "Version restored successfully"));
    }

    // 辅助方法：检查文档访问权限
    private boolean hasDocumentAccess(Document document, Authentication authentication) {
        String username = authentication.getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 文档所有者
        if (document.getUser().getId().equals(currentUser.getId())) {
            return true;
        }
        
        // 协作者
        return documentCollaboratorRepository.existsByDocumentAndUserAndIsActiveTrue(document, currentUser);
    }

    // 辅助方法：获取当前用户
    private User getCurrentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}