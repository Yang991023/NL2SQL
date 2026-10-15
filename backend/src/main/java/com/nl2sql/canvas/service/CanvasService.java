package com.nl2sql.canvas.service;

import com.nl2sql.canvas.dto.CanvasSaveRequest;
import com.nl2sql.canvas.dto.CanvasVO;
import com.nl2sql.canvas.entity.QueryCanvas;
import com.nl2sql.canvas.repo.QueryCanvasRepository;
import com.nl2sql.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CanvasService {

    private final QueryCanvasRepository repo;

    public List<CanvasVO> list(Long dataSourceId) {
        return repo.findAllByDataSourceIdOrderByUpdatedAtDesc(dataSourceId)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public CanvasVO detail(Long id) {
        return toVO(repo.findById(id).orElseThrow(() -> new BizException("画布不存在")));
    }

    @Transactional
    public CanvasVO save(CanvasSaveRequest req) {
        QueryCanvas c = req.getId() == null ? new QueryCanvas() : repo.findById(req.getId())
                .orElseThrow(() -> new BizException("画布不存在"));
        c.setDataSourceId(req.getDataSourceId());
        c.setDatabaseName(req.getDatabaseName());
        c.setName(req.getName());
        c.setCanvasContent(req.getCanvasContent());
        return toVO(repo.save(c));
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new BizException("画布不存在");
        repo.deleteById(id);
    }

    private CanvasVO toVO(QueryCanvas c) {
        CanvasVO v = new CanvasVO();
        v.setId(c.getId());
        v.setDataSourceId(c.getDataSourceId());
        v.setDatabaseName(c.getDatabaseName());
        v.setName(c.getName());
        v.setCanvasContent(c.getCanvasContent());
        v.setUpdatedAt(c.getUpdatedAt());
        return v;
    }
}
