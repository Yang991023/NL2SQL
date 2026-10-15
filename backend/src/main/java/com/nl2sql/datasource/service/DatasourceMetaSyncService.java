package com.nl2sql.datasource.service;

import com.nl2sql.datasource.dto.SyncResultVO;
import com.nl2sql.datasource.entity.DataSource;
import com.nl2sql.datasource.repo.DataSourceRepository;
import com.nl2sql.meta.service.MetaSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 数据源侧的元数据同步入口(委托给 meta.MetaSyncService)
 *
 * - syncAsync: fire-and-forget, 用于保存数据源时自动触发
 * - syncSync : 同步等待 + 返回统计, 用于"手动刷新元数据"按钮
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceMetaSyncService {

    private final MetaSyncService metaSyncService;
    private final DataSourceRepository repo;

    /** 异步触发, 独立事务, 异常仅记录, 不影响主流程 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncAsync(DataSource ds) {
        try {
            metaSyncService.syncInternal(ds);
        } catch (Exception e) {
            log.error("数据源侧异步同步失败: {}", e.getMessage());
            ds.setLastMetaSyncAt(LocalDateTime.now());
            repo.save(ds);
        }
    }

    /**
     * 真异步入口: 提交数据源后调用此方法, 它会立即返回, 后台线程跑同步任务
     * 注意: 调用方应确保在事务提交后调用, 否则可能读到未持久化的 id
     */
    public void syncAsyncAfterCommit(Long dataSourceId) {
        metaSyncService.syncInternalAsync(dataSourceId);
    }

    /**
     * 同步执行并返回统计结果。
     * 出错时通过 SyncResultVO.errorMessage 表达(不抛异常), 方便前端展示。
     */
    @Transactional
    public SyncResultVO syncSync(DataSource ds) {
        long start = System.currentTimeMillis();
        try {
            MetaSyncService.SyncResult r = metaSyncService.syncInternal(ds);
            return new SyncResultVO(
                    r.tableCount(),
                    r.columnCount(),
                    System.currentTimeMillis() - start,
                    r.schemaName(),
                    null
            );
        } catch (Exception e) {
            log.error("数据源侧同步失败", e);
            ds.setLastMetaSyncAt(LocalDateTime.now());
            repo.save(ds);
            return new SyncResultVO(0, 0, System.currentTimeMillis() - start, null,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }
}
