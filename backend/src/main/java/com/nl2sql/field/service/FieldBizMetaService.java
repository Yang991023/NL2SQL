package com.nl2sql.field.service;

import com.nl2sql.field.dto.FieldBizMetaVO;
import com.nl2sql.field.entity.FieldBizMeta;
import com.nl2sql.field.repo.FieldBizMetaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FieldBizMetaService {

    private final FieldBizMetaRepository repo;

    public List<FieldBizMetaVO> list(Long dataSourceId, String databaseName, String tableName) {
        return repo.findByDataSourceIdAndDatabaseNameAndTableName(dataSourceId, databaseName, tableName)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<FieldBizMetaVO> listByDatabase(Long dataSourceId, String databaseName) {
        return repo.findByDataSourceIdAndDatabaseName(dataSourceId, databaseName)
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Transactional
    public FieldBizMetaVO save(Long dataSourceId, String databaseName, String tableName, String columnName,
                                String bizDesc, String filterLogic, String filterValue,
                                String filterLogic2, String filterValue2) {
        FieldBizMeta m = repo.findByDataSourceIdAndDatabaseNameAndTableNameAndColumnName(
                dataSourceId, databaseName, tableName, columnName)
                .orElseGet(() -> {
                    FieldBizMeta x = new FieldBizMeta();
                    x.setDataSourceId(dataSourceId);
                    x.setDatabaseName(databaseName);
                    x.setTableName(tableName);
                    x.setColumnName(columnName);
                    return x;
                });
        m.setBizDesc(bizDesc);
        m.setFilterLogic(filterLogic);
        m.setFilterValue(filterValue);
        m.setFilterLogic2(filterLogic2);
        m.setFilterValue2(filterValue2);
        return toVO(repo.save(m));
    }

    private FieldBizMetaVO toVO(FieldBizMeta m) {
        FieldBizMetaVO v = new FieldBizMetaVO();
        v.setId(m.getId());
        v.setDataSourceId(m.getDataSourceId());
        v.setDatabaseName(m.getDatabaseName());
        v.setTableName(m.getTableName());
        v.setColumnName(m.getColumnName());
        v.setBizDesc(m.getBizDesc());
        v.setFilterLogic(m.getFilterLogic());
        v.setFilterValue(m.getFilterValue());
        v.setFilterLogic2(m.getFilterLogic2());
        v.setFilterValue2(m.getFilterValue2());
        return v;
    }
}
