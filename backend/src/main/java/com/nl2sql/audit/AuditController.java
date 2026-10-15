package com.nl2sql.audit;

import com.nl2sql.audit.entity.OpLog;
import com.nl2sql.audit.repo.OpLogRepository;
import com.nl2sql.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final OpLogRepository repo;

    @GetMapping("/logs")
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId) {
        PageRequest pr = PageRequest.of(Math.max(0, page - 1), Math.min(100, size));
        Page<OpLog> p = repo.findAllByOrderByCreatedAtDesc(pr);
        Map<String, Object> data = new HashMap<>();
        data.put("list", p.getContent());
        data.put("total", p.getTotalElements());
        data.put("page", page);
        data.put("size", size);
        return R.ok(data);
    }
}
