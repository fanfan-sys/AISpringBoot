package com.example.aispringboot.controller;

import com.example.aispringboot.model.Document;
import com.example.aispringboot.model.File;
import com.example.aispringboot.model.User;
import com.example.aispringboot.repository.DocumentRepository;
import com.example.aispringboot.repository.FileRepository;
import com.example.aispringboot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*", maxAge = 3600)
public class FileController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    // 上传文件
    @PostMapping("/upload")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "documentId", required = false) Long documentId,
                                       Authentication authentication) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload"));
            }

            // 创建上传目录
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + fileExtension;
            
            // 保存文件
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // 获取当前用户
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 创建文件记录
            File fileEntity = new File(
                fileName,
                originalFilename,
                file.getContentType(),
                file.getSize(),
                filePath.toString(),
                "/api/files/download/" + fileName,
                user,
                null
            );

            // 如果指定了文档ID，关联到文档
            if (documentId != null) {
                Document document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new RuntimeException("Document not found"));
                
                // 检查权限
                if (!hasDocumentAccess(document, user)) {
                    Files.delete(filePath); // 删除已上传的文件
                    return ResponseEntity.badRequest().body(Map.of("error", "Access denied to document"));
                }
                
                fileEntity.setDocument(document);
            }

            File savedFile = fileRepository.save(fileEntity);

            Map<String, Object> response = new HashMap<>();
            response.put("id", savedFile.getId());
            response.put("fileName", savedFile.getFileName());
            response.put("originalName", savedFile.getOriginalName());
            response.put("fileType", savedFile.getFileType());
            response.put("fileSize", savedFile.getFileSize());
            response.put("fileUrl", savedFile.getFileUrl());
            response.put("createdAt", savedFile.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    // 下载文件
    @GetMapping("/download/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(uploadDir).resolve(fileName);
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                // 获取文件记录
                File fileEntity = fileRepository.findAll().stream()
                        .filter(f -> f.getFileName().equals(fileName))
                        .findFirst()
                        .orElse(null);

                String contentType = "application/octet-stream";
                if (fileEntity != null && fileEntity.getFileType() != null) {
                    contentType = fileEntity.getFileType();
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + 
                                (fileEntity != null ? fileEntity.getOriginalName() : fileName) + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // 获取用户文件列表
    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getMyFiles(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<File> files = fileRepository.findByUserOrderByCreatedAtDesc(user);
        
        List<Map<String, Object>> fileData = files.stream().map(file -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", file.getId());
            data.put("fileName", file.getFileName());
            data.put("originalName", file.getOriginalName());
            data.put("fileType", file.getFileType());
            data.put("fileSize", file.getFileSize());
            data.put("fileUrl", file.getFileUrl());
            data.put("documentId", file.getDocument() != null ? file.getDocument().getId() : null);
            data.put("createdAt", file.getCreatedAt());
            return data;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(fileData);
    }

    // 获取文档关联的文件
    @GetMapping("/document/{documentId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getDocumentFiles(@PathVariable Long documentId, Authentication authentication) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 检查权限
        if (!hasDocumentAccess(document, user)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
        }

        List<File> files = fileRepository.findByDocumentOrderByCreatedAtDesc(document);
        
        List<Map<String, Object>> fileData = files.stream().map(file -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", file.getId());
            data.put("fileName", file.getFileName());
            data.put("originalName", file.getOriginalName());
            data.put("fileType", file.getFileType());
            data.put("fileSize", file.getFileSize());
            data.put("fileUrl", file.getFileUrl());
            data.put("createdAt", file.getCreatedAt());
            return data;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(fileData);
    }

    // 删除文件
    @DeleteMapping("/{fileId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> deleteFile(@PathVariable Long fileId, Authentication authentication) {
        try {
            File file = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("File not found"));

            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // 检查权限 - 只有文件所有者或文档所有者可以删除
            if (!file.getUser().getId().equals(user.getId())) {
                if (file.getDocument() == null || !file.getDocument().getUser().getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Access denied"));
                }
            }

            // 删除物理文件
            Path filePath = Paths.get(file.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }

            // 删除数据库记录
            fileRepository.delete(file);

            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to delete file: " + e.getMessage()));
        }
    }

    // 辅助方法：检查文档访问权限
    private boolean hasDocumentAccess(Document document, User user) {
        // 文档所有者
        if (document.getUser().getId().equals(user.getId())) {
            return true;
        }

        // 协作者
        return document.getCollaborators().stream()
                .anyMatch(collaborator -> collaborator.getUser().getId().equals(user.getId()) && 
                                         collaborator.getIsActive());
    }
}