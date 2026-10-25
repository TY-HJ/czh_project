package com.repair.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.repair.common.BusinessException;
import com.repair.entity.Location;
import com.repair.entity.RepairOrder;
import com.repair.entity.RepairType;
import com.repair.entity.WorkerSkill;
import com.repair.mapper.LocationMapper;
import com.repair.mapper.RepairOrderMapper;
import com.repair.mapper.RepairTypeMapper;
import com.repair.mapper.WorkerSkillMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 字典服务:报修类型 + 位置
 */
@Service
@RequiredArgsConstructor
public class DictService {

    private final RepairTypeMapper typeMapper;
    private final LocationMapper locationMapper;
    private final RepairOrderMapper orderMapper;
    private final WorkerSkillMapper workerSkillMapper;

    // ---------- 报修类型 ----------
    public List<RepairType> listTypes() {
        return typeMapper.selectList(null).stream()
                .sorted((a, b) -> Integer.compare(a.getSort(), b.getSort()))
                .toList();
    }

    public void addType(RepairType type) {
        type.setId(null);
        if (type.getAutoDispatch() == null) {
            type.setAutoDispatch(true);   // 新增类型默认走自动派单
        }
        typeMapper.insert(type);
    }

    public void updateType(RepairType type) {
        if (typeMapper.selectById(type.getId()) == null) {
            throw new BusinessException("类型不存在");
        }
        typeMapper.updateById(type);
    }

    public void deleteType(Long id) {
        Long used = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTypeId, id));
        if (used > 0) {
            throw new BusinessException("该报修类型已被 " + used + " 张工单使用,不能删除");
        }
        Long skilled = workerSkillMapper.selectCount(new LambdaQueryWrapper<WorkerSkill>()
                .eq(WorkerSkill::getTypeId, id));
        if (skilled > 0) {
            throw new BusinessException("已有 " + skilled + " 名维修工配置了该类型技能,请先解除技能绑定");
        }
        typeMapper.deleteById(id);
    }

    // ---------- 位置 ----------
    public List<Location> listLocations() {
        return locationMapper.selectList(null).stream()
                .sorted((a, b) -> a.getBuilding().compareTo(b.getBuilding()))
                .toList();
    }

    public void addLocation(Location location) {
        location.setId(null);
        locationMapper.insert(location);
    }

    public void updateLocation(Location location) {
        if (locationMapper.selectById(location.getId()) == null) {
            throw new BusinessException("位置不存在");
        }
        locationMapper.updateById(location);
    }

    public void deleteLocation(Long id) {
        Long used = orderMapper.selectCount(new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getLocationId, id));
        if (used > 0) {
            throw new BusinessException("该位置已被 " + used + " 张工单使用,不能删除");
        }
        locationMapper.deleteById(id);
    }
}
