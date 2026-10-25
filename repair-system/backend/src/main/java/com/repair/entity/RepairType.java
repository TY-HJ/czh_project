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
    /** 派单方式: true-系统自动派单 false-必须管理员人工派单(如电梯故障需联系维保单位) */
    private Boolean autoDispatch;
}
