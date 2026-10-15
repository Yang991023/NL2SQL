package com.nl2sql.datasource;

import com.nl2sql.common.result.R;
import com.nl2sql.datasource.dto.ConnTestResult;
import com.nl2sql.datasource.dto.DataSourceRequest;
import com.nl2sql.datasource.dto.DataSourceVO;
import com.nl2sql.datasource.dto.SyncResultVO;
import com.nl2sql.datasource.service.DataSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DataSourceController {

    private final DataSourceService service;

    @GetMapping("/list")
    public R<List<DataSourceVO>> list() {
        return R.ok(service.list());
    }

    @GetMapping("/{id}")
    public R<DataSourceVO> detail(@PathVariable Long id) {
        return R.ok(service.detail(id));
    }

    @PostMapping("/test")
    public R<ConnTestResult> test(@RequestBody DataSourceRequest req) {
        return R.ok(service.testConn(req));
    }

    @PostMapping("/save")
    public R<DataSourceVO> save(@Valid @RequestBody DataSourceRequest req) {
        return R.ok(service.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/refresh-meta")
    public R<SyncResultVO> refresh(@PathVariable Long id) {
        return R.ok(service.refreshMeta(id));
    }
}
