package com.nl2sql.model;

import com.nl2sql.common.result.R;
import com.nl2sql.model.dto.LlmConfigRequest;
import com.nl2sql.model.dto.LlmConfigVO;
import com.nl2sql.model.service.LlmConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/model")
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmConfigService service;

    @GetMapping("/list")
    public R<List<LlmConfigVO>> list() {
        return R.ok(service.list());
    }

    @PostMapping("/save")
    public R<LlmConfigVO> save(@Valid @RequestBody LlmConfigRequest req) {
        return R.ok(service.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
