package com.nl2sql.meta;

import com.nl2sql.common.result.R;
import com.nl2sql.meta.dto.DatabaseVO;
import com.nl2sql.meta.dto.SchemaVO;
import com.nl2sql.meta.dto.TableMetaVO;
import com.nl2sql.meta.service.MetaSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meta")
@RequiredArgsConstructor
public class MetaController {

    private final MetaSyncService service;

    /** 左侧树: 数据源下的所有库 */
    @GetMapping("/{dsId}/databases")
    public R<List<DatabaseVO>> listDatabases(@PathVariable Long dsId) {
        return R.ok(service.listDatabases(dsId));
    }

    /** 库下的 schema 列表(级联第三级,按 dbType 动态返回)
     *  - MySQL: 返回空列表(前端隐藏 Schema 级)
     *  - PostgreSQL/Oracle: 返回该库下的 schema 列表
     */
    @GetMapping("/{dsId}/schemas")
    public R<List<SchemaVO>> listSchemas(@PathVariable Long dsId, @RequestParam String database) {
        return R.ok(service.listSchemas(dsId, database));
    }

    /** 库(和可选 schema)下的表列表
     * @param schema 可选,仅 PG/Oracle 使用
     */
    @GetMapping("/{dsId}/tables")
    public R<List<String>> listTables(@PathVariable Long dsId,
                                       @RequestParam String database,
                                       @RequestParam(required = false) String schema) {
        return R.ok(service.listTables(dsId, database, schema));
    }

    /** 表详情(只读) */
    @GetMapping("/{dsId}/table")
    public R<TableMetaVO> tableDetail(@PathVariable Long dsId,
                                       @RequestParam String database,
                                       @RequestParam(required = false) String schema,
                                       @RequestParam String table) {
        return R.ok(service.tableDetail(dsId, database, schema, table));
    }

    /** 触发同步 */
    @PostMapping("/{dsId}/sync")
    public R<Void> sync(@PathVariable Long dsId) {
        service.syncInternalAsync(dsId);
        return R.ok();
    }
}
