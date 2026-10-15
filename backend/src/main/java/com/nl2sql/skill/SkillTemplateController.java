package com.nl2sql.skill;

import com.nl2sql.common.result.R;
import com.nl2sql.skill.dto.SkillTemplateRequest;
import com.nl2sql.skill.dto.SkillTemplateVO;
import com.nl2sql.skill.service.SkillTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillTemplateController {

    private final SkillTemplateService service;

    @GetMapping("/list")
    public R<List<SkillTemplateVO>> list() {
        return R.ok(service.list());
    }

    @PostMapping("/save")
    public R<SkillTemplateVO> save(@Valid @RequestBody SkillTemplateRequest req) {
        return R.ok(service.save(req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
