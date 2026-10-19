package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("repair_type")
public class RepairType {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String typeName;
    private Integer sort;
}
