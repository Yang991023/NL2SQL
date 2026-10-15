package com.nl2sql.project;

import com.nl2sql.common.result.R;
import com.nl2sql.project.dto.AutoFixRequest;
import com.nl2sql.project.dto.ImportScriptRequest;
import com.nl2sql.project.dto.ProjectTableMetaRequest;
import com.nl2sql.project.dto.ProjectTableMetaVO;
import com.nl2sql.project.service.ProjectTableMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/project-table")
@RequiredArgsConstructor
public class ProjectTableMetaController {

    private final ProjectTableMetaService service;

    /** 列表（支持模糊搜索） */
    @GetMapping("/list")
    public R<List<ProjectTableMetaVO>> list(
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String tableName) {
        return R.ok(service.search(projectName, tableName));
    }

    /** 详情（含字段列表） */
    @GetMapping("/{id}")
    public R<ProjectTableMetaVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    /** 新增或编辑 */
    @PostMapping("/save")
    public R<ProjectTableMetaVO> save(@RequestBody ProjectTableMetaRequest req) {
        return R.ok(service.save(req));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    /** 导入脚本 */
    @PostMapping("/import")
    public R<List<ProjectTableMetaVO>> importScript(@RequestBody ImportScriptRequest req) {
        return R.ok(service.importScript(req));
    }

    /** 自动修复脚本 */
    @PostMapping("/import/auto-fix")
    public R<String> autoFix(@RequestBody AutoFixRequest req) {
        return R.ok(service.autoFix(req));
    }
}
