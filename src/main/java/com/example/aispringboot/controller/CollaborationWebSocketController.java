package com.example.aispringboot.controller;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.DocumentActivity;
import com.example.aispringboot.model.DocumentCollaborator;
import com.example.aispringboot.model.User;
import com.example.aispringboot.repository.DocumentActivityRepository;
import com.example.aispringboot.repository.DocumentCollaboratorRepository;
import com.example.aispringboot.repository.DocumentRepository;
import com.example.aispringboot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
public class CollaborationWebSocketController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentCollaboratorRepository documentCollaboratorRepository;

    @Autowired
    private DocumentActivityRepository documentActivityRepository;

    // 文档内容同步
    @MessageMapping("/document.edit")
    public void handleDocumentEdit(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = headerAccessor.getUser().getName();
        Long documentId = Long.parseLong(payload.get("documentId").toString());
        String content = payload.get("content").toString();
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 检查权限
        if (!hasEditPermission(document, user)) {
            return; // 没有编辑权限，忽略请求
        }
        
        // 更新文档内容
        document.setContent(content);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        // 更新协作者的最后活动时间
        updateCollaboratorActivity(document, user);
        
        // 广播给所有订阅者
        Map<String, Object> broadcastMessage = Map.of(
            "type", "content_update",
            "documentId", documentId,
            "content", content,
            "user", Map.of(
                "id", user.getId(),
                "username", user.getUsername()
            ),
            "timestamp", LocalDateTime.now().toString()
        );
        
        messagingTemplate.convertAndSend("/topic/document." + documentId, broadcastMessage);
    }

    // 用户加入文档编辑
    @MessageMapping("/document.join")
    public void handleDocumentJoin(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = headerAccessor.getUser().getName();
        Long documentId = Long.parseLong(payload.get("documentId").toString());
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 检查权限
        if (!hasDocumentAccess(document, user)) {
            return; // 没有访问权限，忽略请求
        }
        
        // 添加或更新协作者
        DocumentCollaborator collaborator = documentCollaboratorRepository
                .findByDocumentAndUser(document, user)
                .orElse(new DocumentCollaborator(document, user, "read"));
        
        collaborator.setIsActive(true);
        collaborator.setLastActivityAt(LocalDateTime.now());
        documentCollaboratorRepository.save(collaborator);
        
        // 记录活动
        DocumentActivity activity = new DocumentActivity(
            document,
            user,
            "USER_JOINED",
            user.getUsername() + " 加入了文档编辑"
        );
        documentActivityRepository.save(activity);
        
        // 广播用户加入消息
        Map<String, Object> joinMessage = Map.of(
            "type", "user_joined",
            "documentId", documentId,
            "user", Map.of(
                "id", user.getId(),
                "username", user.getUsername()
            ),
            "timestamp", LocalDateTime.now().toString()
        );
        
        messagingTemplate.convertAndSend("/topic/document." + documentId, joinMessage);
    }

    // 用户离开文档编辑
    @MessageMapping("/document.leave")
    public void handleDocumentLeave(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = headerAccessor.getUser().getName();
        Long documentId = Long.parseLong(payload.get("documentId").toString());
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 更新协作者状态
        documentCollaboratorRepository.findByDocumentAndUser(document, user).ifPresent(collaborator -> {
            collaborator.setIsActive(false);
            documentCollaboratorRepository.save(collaborator);
        });
        
        // 记录活动
        DocumentActivity activity = new DocumentActivity(
            document,
            user,
            "USER_LEFT",
            user.getUsername() + " 离开了文档编辑"
        );
        documentActivityRepository.save(activity);
        
        // 广播用户离开消息
        Map<String, Object> leaveMessage = Map.of(
            "type", "user_left",
            "documentId", documentId,
            "user", Map.of(
                "id", user.getId(),
                "username", user.getUsername()
            ),
            "timestamp", LocalDateTime.now().toString()
        );
        
        messagingTemplate.convertAndSend("/topic/document." + documentId, leaveMessage);
    }

    // 文档标题更新
    @MessageMapping("/document.title")
    public void handleTitleUpdate(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        String username = headerAccessor.getUser().getName();
        Long documentId = Long.parseLong(payload.get("documentId").toString());
        String title = payload.get("title").toString();
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 检查权限
        if (!hasEditPermission(document, user)) {
            return; // 没有编辑权限，忽略请求
        }
        
        // 更新文档标题
        document.setTitle(title);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        // 更新协作者的最后活动时间
        updateCollaboratorActivity(document, user);
        
        // 广播标题更新
        Map<String, Object> titleMessage = Map.of(
            "type", "title_update",
            "documentId", documentId,
            "title", title,
            "user", Map.of(
                "id", user.getId(),
                "username", user.getUsername()
            ),
            "timestamp", LocalDateTime.now().toString()
        );
        
        messagingTemplate.convertAndSend("/topic/document." + documentId, titleMessage);
    }

    // 辅助方法：检查文档访问权限
    private boolean hasDocumentAccess(Document document, User user) {
        // 文档所有者
        if (document.getUser().getId().equals(user.getId())) {
            return true;
        }
        
        // 协作者
        return documentCollaboratorRepository.existsByDocumentAndUserAndIsActiveTrue(document, user);
    }

    // 辅助方法：检查编辑权限
    private boolean hasEditPermission(Document document, User user) {
        // 文档所有者
        if (document.getUser().getId().equals(user.getId())) {
            return true;
        }
        
        // 检查协作者权限
        return documentCollaboratorRepository.findByDocumentAndUser(document, user)
                .map(collaborator -> "edit".equals(collaborator.getPermission()) && collaborator.getIsActive())
                .orElse(false);
    }

    // 辅助方法：更新协作者活动
    private void updateCollaboratorActivity(Document document, User user) {
        documentCollaboratorRepository.findByDocumentAndUser(document, user).ifPresent(collaborator -> {
            collaborator.setLastActivityAt(LocalDateTime.now());
            documentCollaboratorRepository.save(collaborator);
        });
    }
}