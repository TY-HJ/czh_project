package com.repair.service;

import com.repair.common.BusinessException;
import com.repair.entity.Location;
import com.repair.entity.RepairType;
import com.repair.mapper.LocationMapper;
import com.repair.mapper.RepairTypeMapper;
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

    // ---------- 报修类型 ----------
    public List<RepairType> listTypes() {
        return typeMapper.selectList(null).stream()
                .sorted((a, b) -> Integer.compare(a.getSort(), b.getSort()))
                .toList();
    }

    public void addType(RepairType type) {
        type.setId(null);
        typeMapper.insert(type);
    }

    public void updateType(RepairType type) {
        if (typeMapper.selectById(type.getId()) == null) {
            throw new BusinessException("类型不存在");
        }
        typeMapper.updateById(type);
    }

    public void deleteType(Long id) {
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
        locationMapper.deleteById(id);
    }
}
