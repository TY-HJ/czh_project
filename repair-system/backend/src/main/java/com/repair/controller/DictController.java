package com.repair.controller;

import com.repair.common.Result;
import com.repair.entity.Location;
import com.repair.entity.RepairType;
import com.repair.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 字典管理:报修类型 + 位置
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    // ---------- 类型(所有人可查,管理员可改) ----------
    @GetMapping("/types")
    public Result<List<RepairType>> types() {
        return Result.ok(dictService.listTypes());
    }

    @PostMapping("/types")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> addType(@RequestBody RepairType type) {
        dictService.addType(type);
        return Result.ok();
    }

    @PutMapping("/types")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateType(@RequestBody RepairType type) {
        dictService.updateType(type);
        return Result.ok();
    }

    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.ok();
    }

    // ---------- 位置 ----------
    @GetMapping("/locations")
    public Result<List<Location>> locations() {
        return Result.ok(dictService.listLocations());
    }

    @PostMapping("/locations")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> addLocation(@RequestBody Location location) {
        dictService.addLocation(location);
        return Result.ok();
    }

    @PutMapping("/locations")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateLocation(@RequestBody Location location) {
        dictService.updateLocation(location);
        return Result.ok();
    }

    @DeleteMapping("/locations/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteLocation(@PathVariable Long id) {
        dictService.deleteLocation(id);
        return Result.ok();
    }
}
