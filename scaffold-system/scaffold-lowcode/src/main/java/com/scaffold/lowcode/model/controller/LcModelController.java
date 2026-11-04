package com.scaffold.lowcode.model.controller;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.lowcode.model.ModelMeta;
import com.scaffold.lowcode.model.domain.FieldWriteRequest;
import com.scaffold.lowcode.model.domain.ModelStatusRequest;
import com.scaffold.lowcode.model.domain.ModelWriteRequest;
import com.scaffold.lowcode.model.service.LcFieldService;
import com.scaffold.lowcode.model.service.LcModelService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 模型与字段接口。字段读接口跟着模型列表权限走（看模型就该能看它的结构），
 * 写操作分开权限码，便于把「改结构」和「看结构」授给不同的人。
 */
@RestController
@RequestMapping("/api/lowcode")
public class LcModelController {

    private final LcModelService modelService;
    private final LcFieldService fieldService;

    public LcModelController(LcModelService modelService, LcFieldService fieldService) {
        this.modelService = modelService;
        this.fieldService = fieldService;
    }

    @GetMapping("/models")
    @RequiresPermission("lowcode:model:list")
    public R<PageResult<Map<String, Object>>> models(
            PageQuery query,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(modelService.models(query, projectId, keyword, status));
    }

    /** 类型 → Java / 列类型 / 默认控件的映射表，前端下拉与预览都读这里 */
    @GetMapping("/models/meta")
    @RequiresPermission("lowcode:model:list")
    public R<Map<String, Object>> meta() {
        return R.success(ModelMeta.describe());
    }

    /** REF 字段选目标模型用的精简列表 */
    @GetMapping("/models/options")
    @RequiresPermission("lowcode:model:list")
    public R<List<Map<String, Object>>> modelOptions(@RequestParam(required = false) Long projectId) {
        return R.success(modelService.options(projectId));
    }

    @PostMapping("/models")
    @RequiresPermission("lowcode:model:add")
    @OperLog(module = "模型管理", action = "新增模型")
    public R<Map<String, Object>> createModel(@Valid @RequestBody ModelWriteRequest request) {
        return R.success(modelService.create(request));
    }

    @PutMapping("/models/{id}")
    @RequiresPermission("lowcode:model:edit")
    @OperLog(module = "模型管理", action = "编辑模型")
    public R<Void> updateModel(@PathVariable Long id, @Valid @RequestBody ModelWriteRequest request) {
        modelService.update(id, request);
        return R.success(null);
    }

    @PutMapping("/models/{id}/status")
    @RequiresPermission("lowcode:model:edit")
    @OperLog(module = "模型管理", action = "启停模型")
    public R<Void> updateModelStatus(@PathVariable Long id, @Valid @RequestBody ModelStatusRequest request) {
        modelService.updateStatus(id, request.getStatus());
        return R.success(null);
    }

    @DeleteMapping("/models/{id}")
    @RequiresPermission("lowcode:model:remove")
    @OperLog(module = "模型管理", action = "删除模型")
    public R<Void> deleteModel(@PathVariable Long id) {
        modelService.delete(id);
        return R.success(null);
    }

    @GetMapping("/models/{id}/fields")
    @RequiresPermission("lowcode:model:list")
    public R<List<Map<String, Object>>> fields(@PathVariable Long id) {
        return R.success(fieldService.fields(id));
    }

    /** REF 字段「下拉显示哪一列」的候选 */
    @GetMapping("/models/{id}/field-options")
    @RequiresPermission("lowcode:model:list")
    public R<List<Map<String, Object>>> fieldOptions(@PathVariable Long id) {
        return R.success(fieldService.fieldOptions(id));
    }

    @PostMapping("/models/{id}/fields")
    @RequiresPermission("lowcode:field:add")
    @OperLog(module = "模型管理", action = "新增字段")
    public R<Map<String, Object>> createField(@PathVariable Long id, @Valid @RequestBody FieldWriteRequest request) {
        return R.success(fieldService.create(id, request));
    }

    @PutMapping("/fields/{id}")
    @RequiresPermission("lowcode:field:edit")
    @OperLog(module = "模型管理", action = "编辑字段")
    public R<Void> updateField(@PathVariable Long id, @Valid @RequestBody FieldWriteRequest request) {
        fieldService.update(id, request);
        return R.success(null);
    }

    @DeleteMapping("/fields/{id}")
    @RequiresPermission("lowcode:field:remove")
    @OperLog(module = "模型管理", action = "删除字段")
    public R<Void> deleteField(@PathVariable Long id) {
        fieldService.delete(id);
        return R.success(null);
    }
}
