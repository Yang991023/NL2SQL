package com.nl2sql.model.service;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.model.dto.LlmConfigRequest;
import com.nl2sql.model.dto.LlmConfigVO;
import com.nl2sql.model.entity.LlmConfig;
import com.nl2sql.model.repo.LlmConfigRepository;
import com.nl2sql.security.AESUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private final LlmConfigRepository repo;
    private final AESUtil aes;

    public List<LlmConfigVO> list() {
        return repo.findAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public LlmConfigVO save(LlmConfigRequest req) {
        LlmConfig c = req.getId() == null ? new LlmConfig() : repo.findById(req.getId())
                .orElseThrow(() -> new BizException("模型配置不存在"));
        c.setName(req.getName());
        c.setBaseUrl(req.getBaseUrl());
        c.setModel(req.getModel());
        c.setTimeoutS(req.getTimeoutS() == null ? 60 : req.getTimeoutS());
        c.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
            c.setApiKeyEnc(aes.encrypt(req.getApiKey()));
        }
        if (Boolean.TRUE.equals(req.getIsDefault())) {
            // 清除其他默认
            repo.findAllByEnabledTrueOrderByIdAsc().forEach(x -> {
                if (!x.getId().equals(c.getId())) {
                    x.setIsDefault(false);
                    repo.save(x);
                }
            });
            c.setIsDefault(true);
        } else {
            c.setIsDefault(false);
        }
        return toVO(repo.save(c));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new BizException("模型配置不存在");
        repo.deleteById(id);
    }

    public LlmConfig getDefault() {
        return repo.findFirstByIsDefaultTrueAndEnabledTrue()
                .orElseThrow(() -> new BizException("未配置默认模型"));
    }

    public LlmConfig getById(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new BizException("模型配置不存在"));
    }

    public String getApiKeyPlain(LlmConfig c) {
        return c.getApiKeyEnc() == null ? "" : aes.decrypt(c.getApiKeyEnc());
    }

    private LlmConfigVO toVO(LlmConfig c) {
        LlmConfigVO v = new LlmConfigVO();
        v.setId(c.getId());
        v.setName(c.getName());
        v.setBaseUrl(c.getBaseUrl());
        v.setModel(c.getModel());
        v.setTimeoutS(c.getTimeoutS());
        v.setEnabled(c.getEnabled());
        v.setIsDefault(c.getIsDefault());
        v.setCreatedAt(c.getCreatedAt());
        return v;
    }
}
