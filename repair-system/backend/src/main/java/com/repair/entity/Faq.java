package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库FAQ条目
 * 分类与报修类型名称对应, 用于报修页按类型推荐
 */
@Data
@TableName("faq")
public class Faq {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String category;
    private String title;
    private String content;
    private Integer sort;
    private Integer viewCount;
    private Long adminId;
    private LocalDateTime createTime;
}
