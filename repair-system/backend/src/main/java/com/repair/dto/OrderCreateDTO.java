package com.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学生提交报修DTO
 */
@Data
public class OrderCreateDTO {
    @NotNull(message = "请选择报修类型")
    private Long typeId;

    @NotNull(message = "请选择位置")
    private Long locationId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 50, message = "标题不超过50字")
    private String title;

    private String description;

    /** 图片URL列表,逗号分隔(前端先调上传接口拿URL) */
    private String images;

    /** NORMAL / URGENT */
    private String priority;
}
