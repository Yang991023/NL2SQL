package com.nl2sql.datasource.service;

import com.nl2sql.common.exception.BizException;
import com.nl2sql.datasource.dto.ConnTestResult;
import com.nl2sql.datasource.dto.DataSourceRequest;
import com.nl2sql.datasource.dto.DataSourceVO;
import com.nl2sql.datasource.dto.SyncResultVO;
import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.security.AESUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataSourceService {

    private final DataSourceRepository repo;
    private final AESUtil aes;
    private final ConnTester tester;
    private final DatasourceMetaSyncService metaSyncService;

    public List<DataSourceVO> list() {
        return repo.findAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    public DataSourceVO detail(Long id) {
        DataSource d = repo.findById(id).orElseThrow(() -> new BizException("数据源不存在"));
        return toVO(d);
    }

    /** 仅测试，不入库 */
    public ConnTestResult testConn(DataSourceRequest req) {
        return tester.test(req.getDbType(), req.getHost(),
                Integer.parseInt(req.getPort()), req.getDatabaseName(),
                req.getUsername(), req.getPassword());
    }

    @Transactional
    public DataSourceVO save(DataSourceRequest req) {
        DataSource d = req.getId() == null ? new DataSource() : repo.findById(req.getId())
                .orElseThrow(() -> new BizException("数据源不存在"));
        d.setName(req.getName());
        // 统一规范化: POSTGRES -> POSTGRESQL, 避免下游 switch 漏匹配
        String dbType = req.getDbType() == null || req.getDbType().isBlank()
                ? "POSTGRESQL"
                : req.getDbType().toUpperCase();
        if ("POSTGRES".equals(dbType)) dbType = "POSTGRESQL";
        d.setDbType(dbType);
        d.setHost(req.getHost());
        d.setPort(Integer.parseInt(req.getPort()));
        d.setDatabaseName(req.getDatabaseName());
        d.setUsername(req.getUsername());
        d.setRemark(req.getRemark());
        d.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            d.setPasswordEnc(aes.encrypt(req.getPassword()));
        }
        d = repo.save(d);

        // 关键: 元数据同步放到"事务提交后"才触发, 真正异步执行 (走 Spring @Async)
        // - 目标 DB 不可达不会阻塞 save 接口
        // - 失败仅记日志, 不影响数据源保存
        triggerMetaSyncAfterCommit(d);

        return toVO(d);
    }

    /** 事务提交后再异步触发元数据同步, 失败仅记录日志, 不影响主流程 */
    private void triggerMetaSyncAfterCommit(DataSource ds) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 在事务内: 注册 afterCommit 钩子, 提交后再异步跑
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    metaSyncService.syncAsyncAfterCommit(ds.getId());
                }
            });
        } else {
            // 不在事务中: 直接异步
            metaSyncService.syncAsyncAfterCommit(ds.getId());
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new BizException("数据源不存在");
        repo.deleteById(id);
        // ⚠️ 仅移除本系统配置,不操作目标数据库
    }

    /**
     * 详情中手动刷新元数据
     * 返回 SyncResultVO: { tableCount, columnCount, durationMs, schemaName, errorMessage }
     */
    public SyncResultVO refreshMeta(Long id) {
        DataSource d = repo.findById(id).orElseThrow(() -> new BizException("数据源不存在"));
        return metaSyncService.syncSync(d);
    }

    private DataSourceVO toVO(DataSource d) {
        DataSourceVO v = new DataSourceVO();
        v.setId(d.getId());
        v.setName(d.getName());
        v.setDbType(d.getDbType());
        v.setHost(d.getHost());
        v.setPort(d.getPort() == null ? null : d.getPort().toString());
        v.setDatabaseName(d.getDatabaseName());
        v.setUsername(d.getUsername());
        v.setRemark(d.getRemark());
        v.setEnabled(d.getEnabled());
        v.setLastMetaSyncAt(d.getLastMetaSyncAt());
        v.setCreatedAt(d.getCreatedAt());
        return v;
    }
}
