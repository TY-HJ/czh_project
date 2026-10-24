package com.repair.controller;

import com.repair.common.BusinessException;
import com.repair.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 图片上传(报修照片)
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Value("${upload.dir}")
    private String uploadDir;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        // 仅允许图片格式
        if (!ext.matches("\\.(jpg|jpeg|png|gif|webp|bmp)$")) {
            throw new BusinessException("只支持上传图片文件");
        }
        // 解析为绝对路径并自动建目录(S9: 修复相对路径工作目录漂移问题)
        File dir = com.repair.config.WebConfig.resolveUploadDir(uploadDir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            file.transferTo(new File(dir, filename));
        } catch (IOException e) {
            throw new BusinessException("文件保存失败");
        }
        // 返回可直接访问的URL(由 WebConfig 静态映射 /uploads/**)
        return Result.ok("/uploads/" + filename);
    }
}
