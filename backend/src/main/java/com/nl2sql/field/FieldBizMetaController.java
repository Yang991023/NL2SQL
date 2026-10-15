package com.nl2sql.field;

import com.nl2sql.common.result.R;
import com.nl2sql.field.dto.FieldBizMetaVO;
import com.nl2sql.field.service.FieldBizMetaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/field-biz")
@RequiredArgsConstructor
public class FieldBizMetaController {

    private final FieldBizMetaService service;

    @GetMapping("/list")
    public R<List<FieldBizMetaVO>> list(@RequestParam Long dataSourceId,
                                         @RequestParam String database,
                                         @RequestParam String table) {
        return R.ok(service.list(dataSourceId, database, table));
    }

    @PostMapping("/save")
    public R<FieldBizMetaVO> save(@RequestParam Long dataSourceId,
                                   @RequestParam String database,
                                   @RequestParam String table,
                                   @RequestParam String column,
                                   @RequestParam(required = false) String bizDesc,
                                   @RequestParam(required = false) String filterLogic,
                                   @RequestParam(required = false) String filterValue,
                                   @RequestParam(required = false) String filterLogic2,
                                   @RequestParam(required = false) String filterValue2) {
        return R.ok(service.save(dataSourceId, database, table, column,
                bizDesc, filterLogic, filterValue, filterLogic2, filterValue2));
    }
}
