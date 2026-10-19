package com.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("location")
public class Location {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String building;
    private String floor;
    private String room;
}
