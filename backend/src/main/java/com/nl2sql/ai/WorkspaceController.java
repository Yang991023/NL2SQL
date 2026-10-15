package com.nl2sql.ai;

import com.nl2sql.ai.service.SqlGenerateService;
import com.nl2sql.ai.service.SqlGenerateService.GenerateRequest;
import com.nl2sql.ai.service.SqlGenerateService.GenerateResponse;
import com.nl2sql.canvas.dto.CanvasSaveRequest;
import com.nl2sql.canvas.dto.CanvasVO;
import com.nl2sql.canvas.service.CanvasService;
import com.nl2sql.common.result.R;
import com.nl2sql.field.dto.FieldBizMetaVO;
import com.nl2sql.field.service.FieldBizMetaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final SqlGenerateService generateService;
    private final CanvasService canvasService;
    private final FieldBizMetaService fieldBizMetaService;

    /**
     * 【查询面板生成SQL】仅使用表结构可视化配置区的内容（字段、业务描述、过滤条件、关联关系）
     * 不依赖自然语言描述。
     * 自动使用 "查询面板生成SQL" skill 模板。
     */
    @PostMapping("/generate-from-canvas")
    public R<GenerateResponse> generateFromCanvas(@RequestBody GenerateRequest req) {
        return R.ok(generateService.generateFromCanvas(req));
    }

    /**
     * 【自然语言描述生成SQL】仅使用自然语言描述 + 已选表的范围。
     * 不依赖可视化配置区的过滤条件和关联。
     * 自动使用 "自然语言描述生成SQL" skill 模板。
     */
    @PostMapping("/generate-from-nl")
    public R<GenerateResponse> generateFromNl(@RequestBody GenerateRequest req) {
        return R.ok(generateService.generateFromNl(req));
    }

    /**
     * 原有通用端点（保留兼容）
     */
    @PostMapping("/generate-sql")
    public R<GenerateResponse> generateSql(@RequestBody GenerateRequest req) {
        return R.ok(generateService.generate(req));
    }

    @GetMapping("/canvas/list")
    public R<List<CanvasVO>> listCanvas(@RequestParam Long dataSourceId) {
        return R.ok(canvasService.list(dataSourceId));
    }

    @GetMapping("/canvas/{id}")
    public R<CanvasVO> canvasDetail(@PathVariable Long id) {
        return R.ok(canvasService.detail(id));
    }

    @PostMapping("/canvas/save")
    public R<CanvasVO> canvasSave(@Valid @RequestBody CanvasSaveRequest req) {
        return R.ok(canvasService.save(req));
    }

    @DeleteMapping("/canvas/{id}")
    public R<Void> canvasDelete(@PathVariable Long id) {
        canvasService.delete(id);
        return R.ok();
    }

    @GetMapping("/field-biz/list")
    public R<List<FieldBizMetaVO>> fieldBizList(@RequestParam Long dataSourceId,
                                                  @RequestParam String database,
                                                  @RequestParam String table) {
        return R.ok(fieldBizMetaService.list(dataSourceId, database, table));
    }

    @PostMapping("/field-biz/save")
    public R<FieldBizMetaVO> fieldBizSave(@RequestParam Long dataSourceId,
                                           @RequestParam String database,
                                           @RequestParam String table,
                                           @RequestParam String column,
                                           @RequestParam(required = false) String bizDesc,
                                           @RequestParam(required = false) String filterLogic,
                                           @RequestParam(required = false) String filterValue,
                                           @RequestParam(required = false) String filterLogic2,
                                           @RequestParam(required = false) String filterValue2) {
        return R.ok(fieldBizMetaService.save(dataSourceId, database, table, column,
                bizDesc, filterLogic, filterValue, filterLogic2, filterValue2));
    }
}
