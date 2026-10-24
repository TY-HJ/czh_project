package com.repair.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置:跨域 + 上传图片静态映射
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${upload.dir}")
    private String uploadDir;

    /** 上传目录统一解析为绝对路径(配置为相对路径时基于运行目录解析, 避免工作目录漂移) */
    public static java.io.File resolveUploadDir(String dir) {
        java.io.File f = new java.io.File(dir);
        if (!f.isAbsolute()) {
            f = new java.io.File(System.getProperty("user.dir"), dir);
        }
        if (!f.exists()) {
            f.mkdirs();
        }
        return f;
    }

    /** 全局跨域(开发环境前端5173端口) */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /** 把 /uploads/** 映射到本地磁盘目录,前端可直接访问上传的图片 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absPath = resolveUploadDir(uploadDir).getAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + absPath + "/");
    }
}
