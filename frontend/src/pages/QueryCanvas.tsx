import { useEffect, useState } from 'react';
import { Button, Input, Select, Space, Table, Tag, message, Empty, Alert, Card, Popconfirm } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ThunderboltOutlined, CheckCircleOutlined, PlayCircleOutlined, DeleteOutlined, CopyOutlined, ExportOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import { bus, EVT_PROJECT_TABLE_CHANGED } from '../utils/bus';
import { http } from '../api/http';
import * as XLSX from 'xlsx';

type RangeOp = '>' | '<' | '>=' | '<=' | '!=';
type FieldMode = 'empty' | 'fixed' | 'range' | 'like' | 'notLike';

interface RangeCondition { op: RangeOp; value: string; }

interface FieldConfig {
  name: string; type: string; length?: number; nullable?: boolean;
  isPk?: boolean; comment?: string; biz?: string; mode?: FieldMode;
  fixedValue?: string; rangeConditions?: RangeCondition[]; rangeLogic?: 'and' | 'or';
  likeValue?: string; caseSensitive?: boolean;
  joinTable?: string; joinField?: string;
}

interface TableInfo {
  database: string; schema?: string; table: string; columns: FieldConfig[];
}

const dropdownStyle: React.CSSProperties = { width: 'auto', maxWidth: '100%' };
const RANGE_OPS: { value: RangeOp; label: string }[] = [
  { value: '>=', label: '≥' }, { value: '>',  label: '>' }, { value: '<=', label: '≤' },
  { value: '<',  label: '<' }, { value: '!=', label: '≠' },
];

export default function QueryCanvas({ mode = 'normal' }: { mode?: 'normal' | 'project' }) {
  const isProject = mode === 'project';
  const [searchParams] = useSearchParams();

  // --- 数据源 & 级联 ---
  const [dataSources, setDataSources] = useState<any[]>([]);
  const [selectedDs, setSelectedDs] = useState<number | undefined>(undefined);
  const [dbType, setDbType] = useState<string>('');

  // 各级选项
  const [databases, setDatabases] = useState<any[]>([]);
  const [selectedDb, setSelectedDb] = useState<string | undefined>(undefined);
  const [schemas, setSchemas] = useState<any[]>([]);
  const [selectedSchema, setSelectedSchema] = useState<string | undefined>(undefined);
  const [tables, setTables] = useState<string[]>([]);

  // 已选表
  const [selectedTables, setSelectedTables] = useState<TableInfo[]>([]);

  // 其余状态
  const [nlQuery, setNlQuery] = useState('');
  const [editingSql, setEditingSql] = useState('');
  const [checkResult, setCheckResult] = useState<any>(null);
  const [queryResult, setQueryResult] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [skillList, setSkillList] = useState<any[]>([]);
  const [canvasSkillId, setCanvasSkillId] = useState<number | undefined>(undefined);
  const [nlSkillId, setNlSkillId] = useState<number | undefined>(undefined);

  // --- 项目表模式专用 ---
  const [projectTables, setProjectTables] = useState<any[]>([]);
  const [selectedProject, setSelectedProject] = useState<string | undefined>(undefined); // projectName
  const [projectTableNames, setProjectTableNames] = useState<string[]>([]);

  useEffect(() => { loadSkills(); }, []);
  useEffect(() => {
    if (isProject) loadProjectTables();
    else loadDataSources();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isProject]);

  // 监听项目表数据变更事件: 跨页操作(导入/删除/编辑)后自动刷新
  useEffect(() => {
    if (!isProject) return;
    const off = bus.on(EVT_PROJECT_TABLE_CHANGED, () => {
      loadProjectTables();
    });
    return off;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isProject]);

  // ==================== 加载 ====================
  const loadDataSources = async () => {
    const r: any = await http.get('/datasource/list');
    setDataSources(r ?? []);
    const urlDsId = searchParams.get('dsId');
    if (urlDsId && !isNaN(Number(urlDsId))) {
      const id = Number(urlDsId);
      const ds = (r ?? []).find((d: any) => d.id === id);
      if (ds) {
        setSelectedDs(id); setDbType(ds.dbType ?? '');
        loadDatabases(id);
      }
      const urlDb = searchParams.get('db');
      const urlSchema = searchParams.get('schema');
      const urlTable = searchParams.get('table');
      if (urlDb) {
        if (urlSchema) setSelectedSchema(urlSchema ?? undefined);
        if (urlTable) setTimeout(() => doAddTable(urlDb, urlSchema ?? undefined, urlTable), 300);
      }
    }
  };

  const loadSkills = async () => {
    const r: any = await http.get('/skill/list');
    setSkillList(r ?? []);
    const canvasDef = (r ?? []).find((s: any) => s.name === '查询面板生成SQL');
    if (canvasDef) setCanvasSkillId(canvasDef.id);
    const nlDef = (r ?? []).find((s: any) => s.name === '自然语言描述生成SQL');
    if (nlDef) setNlSkillId(nlDef.id);
  };

  // ==================== 项目表模式加载 ====================
  const loadProjectTables = async () => {
    try {
      const r: any = await http.get('/project-table/list');
      setProjectTables(r ?? []);
    } catch { setProjectTables([]); }
  };
  const onProjectChange = (projectName: string) => {
    setSelectedProject(projectName);
    setSelectedTables([]);
    const names = projectTables
      .filter(t => t.projectName === projectName)
      .map(t => t.tableName);
    setProjectTableNames(names);
  };

  // ==================== 级联变化 ====================
  const onDsChange = (id: number) => {
    setSelectedDs(id); setSelectedDb(undefined); setSelectedSchema(undefined); setTables([]);
    setSelectedTables([]);
    const ds = dataSources.find(d => d.id === id);
    setDbType(ds?.dbType ?? '');
    loadDatabases(id);
  };

  const loadDatabases = async (dsId: number) => {
    const r: any = await http.get(`/meta/${dsId}/databases`);
    setDatabases(r ?? []);
  };

  const onDbChange = async (db: string) => {
    setSelectedDb(db); setSelectedSchema(undefined); setTables([]);
    setSelectedTables([]);
    if (!selectedDs) return;
    if (dbType === 'MYSQL') {
      loadTables(selectedDs, db, undefined);
    } else {
      loadSchemas(selectedDs, db);
    }
  };

  const loadSchemas = async (dsId: number, db: string) => {
    try {
      const r: any = await http.get(`/meta/${dsId}/schemas`, { params: { database: db } });
      const list: any[] = r ?? [];
      setSchemas(list);
      if (list.length === 0) {
        // 无 schema，直接加载表
        loadTables(dsId, db, undefined);
      }
    } catch { setSchemas([]); }
  };

  const onSchemaChange = async (schema: string) => {
    setSelectedSchema(schema); setTables([]);
    setSelectedTables([]);
    if (!selectedDs || !selectedDb) return;
    loadTables(selectedDs, selectedDb, schema);
  };

  const loadTables = async (dsId: number, db: string, schema: string | undefined) => {
    try {
      const params = schema ? { database: db, schema } : { database: db };
      const r: any = await http.get(`/meta/${dsId}/tables`, { params });
      setTables(r ?? []);
    } catch { setTables([]); }
  };

  // ==================== 添加 / 移除表 ====================
  const doAddTable = async (database: string, schema: string | undefined, tableName: string) => {
    const dsId = selectedDs ?? (dataSources.find(d => d.name === (searchParams.get('dsId') ? '' : ''))?.id);
    if (!dsId) return;
    if (selectedTables.find(t => t.table === tableName && t.database === database)) {
      message.warning('该表已添加'); return;
    }
    const params = schema ? { database, schema, table: tableName } : { database, table: tableName };
    const r: any = await http.get(`/meta/${dsId}/table`, { params });
    const cols: any[] = r?.columns ?? [];
    const tableInfo: TableInfo = {
      database, schema, table: tableName,
      columns: cols.map((c: any) => ({ ...c, biz: c.comment ?? '', mode: 'empty' as FieldMode })),
    };
    setSelectedTables(prev => [...prev, tableInfo]);
    message.success(`已添加: ${tableName}`);
  };

  const onTableSelect = async (tableName: string) => {
    if (isProject) {
      // 项目表模式：直接从 projectTables 中取出已配置的字段
      if (selectedTables.find(t => t.table === tableName)) {
        message.warning('该表已添加'); return;
      }
      const meta = projectTables.find(t => t.projectName === selectedProject && t.tableName === tableName);
      if (!meta) { message.error('未找到表元数据'); return; }
      const fields: FieldConfig[] = (meta.fields ?? []).map((c: any) => ({
        name: c.columnName,
        type: c.columnType,
        comment: c.columnComment,
        biz: c.bizDesc ?? c.columnComment ?? '',
        mode: 'empty' as FieldMode,
      }));
      const tableInfo: TableInfo = {
        database: String(meta.id), // 项目表模式下用 projectTableId 作为标识（后端生成 SQL 需要）
        schema: undefined,
        table: tableName,
        columns: fields,
      };
      setSelectedTables(prev => [...prev, tableInfo]);
      message.success(`已添加: ${tableName}`);
      return;
    }
    if (!selectedDb) return;
    await doAddTable(selectedDb, selectedSchema, tableName);
  };

  const removeTable = (tableName: string) => {
    setSelectedTables(prev => prev.filter(t => t.table !== tableName).map(t => ({
      ...t,
      columns: t.columns.map(c => c.joinTable === tableName ? { ...c, joinTable: undefined, joinField: undefined } : c),
    })));
    setEditingSql(''); setCheckResult(null); setQueryResult(null);
  };

  const updateField = (tableName: string, fieldName: string, patch: Partial<FieldConfig>) => {
    setSelectedTables(prev => prev.map(t =>
      t.table === tableName
        ? { ...t, columns: t.columns.map(c => c.name === fieldName ? { ...c, ...patch } : c) }
        : t
    ));
  };

  const updateTableBiz = (tableName: string, fieldName: string, biz: string) => {
    updateField(tableName, fieldName, { biz });
    if (!selectedDs) return;
    const t = selectedTables.find(t => t.table === tableName);
    if (!t) return;
    http.post(`/meta/${selectedDs}/column-comment`, { database: t.database, table: tableName, column: fieldName, comment: biz });
  };

  const onClear = () => {
    setSelectedTables([]); setSelectedDb(undefined); setSelectedSchema(undefined); setTables([]);
    setNlQuery(''); setEditingSql(''); setCheckResult(null); setQueryResult(null);
  };

  // ==================== SQL 生成 ====================
  const buildFieldsAndJoins = (): { fields: any[]; joins: any[] } => {
    const fields: any[] = [], joins: any[] = [];
    for (const t of selectedTables) {
      for (const c of t.columns) {
        const mode = c.mode ?? 'empty';
        const hasJoin = !!(c.joinTable && c.joinField);
        if (mode === 'empty' && !hasJoin) continue;
        const field: any = { table: t.table, column: c.name, bizDesc: c.biz ?? '' };
        if (hasJoin) { field.joinTable = c.joinTable; field.joinField = c.joinField; }
        if (mode === 'fixed') {
          field.filterLogic = 'IN'; field.filterValue = c.fixedValue ?? '';
        } else if (mode === 'range') {
          const conds = c.rangeConditions ?? [];
          if (conds.length === 0) continue;
          if (conds.length === 1) {
            field.filterLogic = conds[0].op; field.filterValue = conds[0].value;
          } else {
            field.filterLogic = c.rangeLogic === 'or' ? 'RANGE_OR' : 'RANGE_AND';
            field.filterLogic2 = conds[1].op; field.filterValue = conds[0].value; field.filterValue2 = conds[1].value;
          }
        } else if (mode === 'like' || mode === 'notLike') {
          const val = c.likeValue ?? '';
          if (!val) continue;
          field.filterLogic = mode === 'like' ? 'LIKE' : 'NOT LIKE';
          field.filterLogic2 = (c.caseSensitive ?? false) ? 'CASE_SENSITIVE' : 'CASE_INSENSITIVE';
          field.filterValue = val;
        }
        fields.push(field);
        if (hasJoin) joins.push({ leftTable: t.table, leftColumn: c.name, rightTable: c.joinTable, rightColumn: c.joinField });
      }
    }
    return { fields, joins };
  };

  const onGenerateFromCanvas = async () => {
    if (!selectedTables.length) { message.warning('请先添加数据表'); return; }
    setLoading(true);
    try {
      const { fields, joins } = buildFieldsAndJoins();
      const payload: any = {
        dataSourceId: selectedDs,
        databaseName: selectedTables[0].database,
        tables: selectedTables.map(t => t.table), fields, joins,
        userNlQuery: '', skillId: canvasSkillId,
      };
      // 项目表模式：传 projectTableId 走专用路径（不依赖真实数据源）
      if (isProject) {
        payload.projectTableId = Number(selectedTables[0].database);
        delete payload.dataSourceId;
        delete payload.databaseName;
      }
      const r: any = await http.post('/workspace/generate-from-canvas', payload);
      setEditingSql(r.sql ?? '');
      r.warnings?.forEach((s: string) => message.info(s));
    } catch (e: any) { message.error(e?.message ?? '生成失败'); }
    finally { setLoading(false); }
  };

  const onGenerateFromNl = async () => {
    if (!selectedTables.length) { message.warning('请先添加数据表'); return; }
    if (!nlQuery.trim()) { message.warning('请输入自然语言描述'); return; }
    setLoading(true);
    try {
      const fields = selectedTables.flatMap(t => t.columns.map(c => ({ table: t.table, column: c.name, bizDesc: c.biz ?? '' })));
      const payload: any = {
        dataSourceId: selectedDs,
        databaseName: selectedTables[0].database,
        tables: selectedTables.map(t => t.table), fields, joins: [],
        userNlQuery: nlQuery, skillId: nlSkillId,
      };
      // 项目表模式：传 projectTableId 走专用路径（不依赖真实数据源）
      if (isProject) {
        payload.projectTableId = Number(selectedTables[0].database);
        delete payload.dataSourceId;
        delete payload.databaseName;
      }
      const r: any = await http.post('/workspace/generate-from-nl', payload);
      setEditingSql(r.sql ?? '');
      r.warnings?.forEach((s: string) => message.info(s));
    } catch (e: any) { message.error(e?.message ?? '生成失败'); }
    finally { setLoading(false); }
  };

  const onCheck = async () => {
    if (!editingSql.trim()) return;
    setLoading(true);
    try {
      const r: any = await http.post(`/sql/check?dataSourceId=${selectedDs}`, editingSql, { headers: { 'Content-Type': 'text/plain' } });
      setCheckResult(r);
      if (r.status === 'PASS') message.success('检查通过 ✓');
      if (r.status === 'WARNING') message.warning('检查警告: ' + (r.reason ?? ''));
      if (r.status === 'REJECT') message.error('检查拒绝: ' + (r.reason ?? ''));
    } catch (e: any) { message.error(e?.message ?? '检查失败'); }
    finally { setLoading(false); }
  };

  const onExec = async () => {
    if (!editingSql.trim()) return;
    setLoading(true); setCheckResult(null);
    try {
      const r: any = await http.post(`/sql/exec?dataSourceId=${selectedDs}`, editingSql, { headers: { 'Content-Type': 'text/plain' } });
      setQueryResult(r);
      message.success(`查询成功: ${r.rowCount} 行, 耗时 ${r.costMs}ms`);
    } catch (e: any) { message.error(e?.message ?? '执行失败'); setQueryResult(null); }
    finally { setLoading(false); }
  };

  const onCopy = () => { if (editingSql) { navigator.clipboard.writeText(editingSql); message.success('已复制到剪贴板'); } };

  const onExportSchema = async () => {
    if (!selectedTables.length) return;
    const rows = selectedTables.flatMap(t => t.columns.map(c => ({
      数据表名称: t.table, 字段名称: c.name, 字段类型: c.type, 字段长度: c.length ?? '-',
      是否主键: c.isPk ? '是' : '否', 是否可空: c.nullable ? '是' : '否',
      原始注释: c.comment ?? '-', 业务描述: c.biz ?? '-',
    })));
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(rows), '表结构文档');
    const blob = new Blob([XLSX.write(wb, { bookType: 'xlsx', type: 'array' })], { type: 'application/octet-stream' });
    const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
    a.download = `表结构文档_${new Date().toLocaleDateString().replace(/\//g, '-')}.xlsx`;
    a.click(); URL.revokeObjectURL(a.href);
    message.success('表结构已导出');
  };

  const onExportData = async (format: string) => {
    if (!queryResult?.rows?.length) return;
    const cols = queryResult.columns, rows = queryResult.rows as any[];
    if (format === 'csv') {
      const csv = [cols.join(','), ...rows.map((r: any) => cols.map((c: string) => `"${r[c] ?? ''}"`).join(','))].join('\n');
      const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
      a.download = 'result.csv'; a.click(); URL.revokeObjectURL(a.href);
    } else {
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(rows), '查询结果');
      const blob = new Blob([XLSX.write(wb, { bookType: 'xlsx', type: 'array' })], { type: 'application/octet-stream' });
      const a = document.createElement('a'); a.href = URL.createObjectURL(blob);
      a.download = 'result.xlsx'; a.click(); URL.revokeObjectURL(a.href);
    }
    message.success('导出成功');
  };

  // ==================== 列定义 ====================
  const isMultiTable = selectedTables.length > 1;
  const cellStyle = { padding: '4px 6px' };

  const buildColDefs = (currentTable: TableInfo): ColumnsType<FieldConfig> => [
    {
      title: '字段', dataIndex: 'name', key: 'name', width: 100, fixed: 'left' as const,
      onCell: () => ({ style: cellStyle }),
      render: (v: string, r: FieldConfig) => (
        <Space size={2}>
          <b>{v}</b>
          {r.isPk && <Tag color="gold" style={{ fontSize: 10, padding: '0 2px' }}>PK</Tag>}
        </Space>
      ),
    },
    {
      title: '类型', dataIndex: 'type', key: 'type', width: 80, fixed: 'left' as const, onCell: () => ({ style: cellStyle }),
    },
    {
      title: '注释', dataIndex: 'comment', key: 'comment', width: 80, fixed: 'left' as const, onCell: () => ({ style: cellStyle }),
      render: (v: string) => <span style={{ color: '#888', fontSize: 12 }}>{v || '-'}</span>,
    },
    {
      title: '业务描述', dataIndex: 'biz', key: 'biz', width: 140, fixed: 'left' as const, onCell: () => ({ style: cellStyle }),
      render: (_: any, r: FieldConfig) => (
        <Input size="small" placeholder="业务含义" value={r.biz ?? ''}
          onChange={e => updateTableBiz(currentTable.table, r.name, e.target.value)} />
      ),
    },
    {
      title: '查询逻辑', key: 'queryLogic', width: 100, onCell: () => ({ style: cellStyle }),
      render: (_: any, r: FieldConfig) => (
        <Select size="small" dropdownStyle={dropdownStyle} value={r.mode ?? 'empty'}
          onChange={v => updateField(currentTable.table, r.name, {
            mode: v as FieldMode, fixedValue: '', rangeConditions: undefined,
            rangeLogic: undefined, likeValue: undefined,
          })}
          style={{ width: '100%' }} popupMatchSelectWidth={false}
          options={[
            { value: 'empty', label: '空' }, { value: 'fixed', label: '固定' },
            { value: 'range', label: '范围' }, { value: 'like', label: '包含' }, { value: 'notLike', label: '不包含' },
          ]} />
      ),
    },
    {
      title: '值', key: 'value', width: 260, onCell: () => ({ style: cellStyle }),
      render: (_: any, r: FieldConfig) => {
        const mode = r.mode ?? 'empty';
        if (mode === 'empty') return <span style={{ color: '#ccc', fontSize: 12 }}>—</span>;
        if (mode === 'fixed') return (
          <Input size="small" placeholder="多值逗号分隔" value={r.fixedValue ?? ''}
            onChange={e => updateField(currentTable.table, r.name, { fixedValue: e.target.value })} />
        );
        if (mode === 'like' || mode === 'notLike') {
          return (
            <Space.Compact size="small" style={{ width: '100%' }}>
              <Button
                size="small"
                onClick={() => {
                  const newCs = !(r.caseSensitive ?? false);
                  updateField(currentTable.table, r.name, { caseSensitive: newCs });
                }}
                style={{ fontSize: 11, padding: '0 6px', height: 24, whiteSpace: 'nowrap' }}
              >
                {r.caseSensitive ? 'Aa' : 'aa'} 区分
              </Button>
              <Input size="small" placeholder="输入文本值" value={r.likeValue ?? ''}
                onChange={e => updateField(currentTable.table, r.name, { likeValue: e.target.value })}
                style={{ flex: 1, minWidth: 0 }} />
            </Space.Compact>
          );
        }
        const conds = r.rangeConditions ?? [{ op: '>=' as RangeOp, value: '' }];
        const logic = r.rangeLogic ?? 'and';
        return (
          <Space direction="vertical" size={2} style={{ width: '100%' }}>
            {conds.map((cond, idx) => (
              <Space key={idx} size={2} style={{ flexWrap: 'nowrap' }}>
                {idx > 0 && (
                  <Select size="small" value={logic}
                    onChange={v => updateField(currentTable.table, r.name, { rangeLogic: v })}
                    style={{ width: 44, fontSize: 11 }}
                    options={[{ value: 'and', label: '且' }, { value: 'or', label: '或' }]} />
                )}
                <Select size="small" value={cond.op}
                  onChange={v => { const u = [...conds]; u[idx] = { ...u[idx], op: v }; updateField(currentTable.table, r.name, { rangeConditions: u }); }}
                  style={{ width: 54 }} listHeight={200} virtual={false} options={RANGE_OPS} />
                <Input size="small" placeholder="值" value={cond.value}
                  onChange={e => { const u = [...conds]; u[idx] = { ...u[idx], value: e.target.value }; updateField(currentTable.table, r.name, { rangeConditions: u }); }}
                  style={{ flex: 1, minWidth: 0 }} />
                {conds.length > 1 && (
                  <Button size="small" danger icon={<DeleteOutlined />}
                    onClick={() => updateField(currentTable.table, r.name, { rangeConditions: conds.filter((_, i) => i !== idx) })}
                    style={{ padding: '0 4px', minWidth: 0 }} />
                )}
              </Space>
            ))}
            <Button size="small" type="dashed"
              onClick={() => updateField(currentTable.table, r.name, { rangeConditions: [...conds, { op: '>=' as RangeOp, value: '' }] })}
              style={{ fontSize: 11 }}>+ 添加条件</Button>
          </Space>
        );
      },
    },
    {
      title: '关联表字段', key: 'joinField', width: 180, onCell: () => ({ style: cellStyle }),
      render: (_: any, r: FieldConfig) => {
        const joinOptions = selectedTables
          .filter(t => t.table !== currentTable.table)
          .flatMap(t => t.columns.map(c => ({ value: `${t.table}.${c.name}`, label: `${t.table}.${c.name} (${c.type})` })));
        return (
          <Select size="small" disabled={!isMultiTable}
            placeholder={isMultiTable ? '选择关联字段' : '单表不可用'}
            value={r.joinTable ? `${r.joinTable}.${r.joinField}` : undefined}
            onChange={val => {
              if (!val) { updateField(currentTable.table, r.name, { joinTable: undefined, joinField: undefined }); return; }
              const [jt, jf] = (val as string).split('.');
              updateField(currentTable.table, r.name, { joinTable: jt, joinField: jf });
            }}
            showSearch filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
            style={{ width: '100%' }} listHeight={300} virtual={false} popupMatchSelectWidth={false}
            notFoundContent={isMultiTable ? '无关联表' : '请添加多张表'}
            options={joinOptions} />
        );
      },
    },
  ];

  // ==================== 渲染 ====================
  const tableTitle = (t: TableInfo) => (
    <Space>
      <Tag color="blue">{t.database}</Tag>
      {t.schema && <Tag color="purple">{t.schema}</Tag>}
      <b>{t.table}</b>
    </Space>
  );

  const showSchemaSelect = dbType !== 'MYSQL' && schemas.length > 0;

  return (
    <div style={{ padding: 16, overflowY: 'auto', height: 'calc(100vh - 96px)' }}>

      {/* === 顶部级联选择区 === */}
      <Space style={{ marginBottom: 12 }} wrap align="start">
        {isProject ? (
          <>
            {/* ① 项目名称 */}
            <Select placeholder="① 选择项目" showSearch allowClear
              filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
              value={selectedProject} onChange={onProjectChange}
              style={{ width: 240 }} virtual={false} listHeight={300}
              options={Array.from(new Set(projectTables.map((t: any) => t.projectName)))
                .filter(Boolean)
                .map(p => ({ value: p, label: p }))} />

            {/* ② 表 */}
            <Select placeholder="② 选择表" showSearch allowClear
              filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
              value={undefined} onChange={onTableSelect}
              disabled={!selectedProject} style={{ width: 220 }} virtual={false} listHeight={300}
              options={projectTableNames
                .filter(t => !selectedTables.find(s => s.table === t))
                .map(t => ({ value: t, label: t }))} />
          </>
        ) : (
          <>
            {/* ① 数据源 */}
            <Select placeholder="① 选择数据源" showSearch allowClear
              filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
              value={selectedDs} onChange={onDsChange}
              style={{ width: 200 }} virtual={false} listHeight={300}
              options={dataSources.map(d => ({ value: d.id, label: d.name }))} />

            {/* ② 数据库 */}
            <Select placeholder="② 选择数据库" showSearch allowClear
              filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
              value={selectedDb} onChange={onDbChange}
              disabled={!selectedDs} style={{ width: 180 }} virtual={false} listHeight={300}
              options={databases.map(d => ({ value: d.name, label: d.name }))} />

            {/* ③ Schema (仅 PG/Oracle) */}
            {showSchemaSelect && (
              <Select placeholder="③ 选择 Schema" showSearch allowClear
                filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
                value={selectedSchema} onChange={onSchemaChange}
                disabled={!selectedDb} style={{ width: 180 }} virtual={false} listHeight={300}
                options={schemas.map((s: any) => ({ value: s.name, label: s.name }))} />
            )}

            {/* ④ 添加表 */}
            <Select placeholder={showSchemaSelect ? '④ 选择表' : '③ 选择表'} showSearch allowClear
              filterOption={(input, option) => (option?.label ?? '').toLowerCase().includes(input.toLowerCase())}
              value={undefined} onChange={onTableSelect}
              disabled={!selectedDb} style={{ width: 200 }} virtual={false} listHeight={300}
              options={tables.filter(t => !selectedTables.find(s => s.table === t)).map(t => ({ value: t, label: t }))} />
          </>
        )}

        {/* 操作按钮 */}
        <Button type="primary" icon={<ThunderboltOutlined />} onClick={onGenerateFromCanvas}
          loading={loading} disabled={!selectedTables.length}>查询面板生成SQL</Button>
        <Popconfirm title="确认清空画布?" onConfirm={onClear}>
          <Button icon={<DeleteOutlined />}>清空画布</Button>
        </Popconfirm>
        {!isProject && (
          <Button icon={<ExportOutlined />} onClick={onExportSchema} disabled={!selectedTables.length}>导出表结构</Button>
        )}
      </Space>

      {/* === 已选表标签区 === */}
      {selectedTables.length > 0 && (
        <div style={{ marginBottom: 12, padding: '8px 12px', background: '#f8f8f8', borderRadius: 6 }}>
          <Space size={4} wrap>
            <span style={{ color: '#666', fontSize: 12, marginRight: 4 }}>已选表 ({selectedTables.length}):</span>
            {selectedTables.map(t => (
              <Tag key={t.table} color="blue" closable onClose={() => removeTable(t.table)}
                style={{ cursor: 'pointer', marginBottom: 4 }}>
                {t.schema ? `${t.schema}.${t.table}` : t.table}
              </Tag>
            ))}
          </Space>
        </div>
      )}

      {/* === 表结构配置区 === */}
      {selectedTables.map(table => (
        <Card key={table.table} size="small" className="query-canvas-table"
          title={tableTitle(table)} style={{ marginBottom: 12 }}
          extra={<Space><Tag>{table.columns.length} 个字段</Tag>
            <Button size="small" danger icon={<DeleteOutlined />} onClick={() => removeTable(table.table)}>移除</Button>
          </Space>}
        >
          <Table rowKey="name" size="small" pagination={false} dataSource={table.columns}
            columns={buildColDefs(table)} scroll={{ x: 1120 }} />
        </Card>
      ))}

      {/* === 自然语言描述区 === */}
      <Card size="small"
        title={<Space><span>自然语言描述</span>{selectedTables.length > 0 && <Tag color="green">{selectedTables.length} 张表</Tag>}</Space>}
        extra={
          <Button type="primary" icon={<ThunderboltOutlined />} onClick={onGenerateFromNl}
            loading={loading} disabled={!selectedTables.length || !nlQuery.trim()}>生成 SQL</Button>
        }
        style={{ marginBottom: 12 }}
      >
        <Input.TextArea rows={4} value={nlQuery} onChange={e => setNlQuery(e.target.value)}
          placeholder={
            !selectedTables.length ? '请先添加数据表后再输入查询需求'
              : '例如: 查询每个用户最近一笔订单的金额（表范围来自上方已添加的表）'
          }
          disabled={!selectedTables.length} style={{ resize: 'none', fontSize: 13 }} />
        {!selectedTables.length && <div style={{ color: '#ff4d4f', fontSize: 12, marginTop: 4 }}>⚠️ 请先从上方添加数据表</div>}
        {selectedTables.length > 0 && (
          <div style={{ color: '#52c41a', fontSize: 12, marginTop: 4 }}>
            ✅ 已选中 {selectedTables.map(t => t.table).join('、')} 共 {selectedTables.length} 张表
          </div>
        )}
      </Card>

      {/* === SQL 编辑区 === */}
      <Card size="small" title="SQL 编辑" style={{ marginBottom: 12 }}
        extra={<Space>
          <Button size="small" icon={<CopyOutlined />} onClick={onCopy} disabled={!editingSql}>复制</Button>
          <Button size="small" icon={<CheckCircleOutlined />} onClick={onCheck} disabled={!editingSql} loading={loading}>检查</Button>
          {!isProject && (
            <Button size="small" type="primary" icon={<PlayCircleOutlined />} onClick={onExec} disabled={!editingSql} loading={loading}>执行 SQL</Button>
          )}
        </Space>}
      >
        <Input.TextArea rows={8} value={editingSql} onChange={e => setEditingSql(e.target.value)}
          style={{ fontFamily: 'JetBrains Mono, Consolas, monospace', fontSize: 13 }}
          placeholder="生成的 SQL 将在此显示，支持手动修改" />
      </Card>

      {/* === 校验结果 === */}
      {checkResult && (
        <Alert style={{ marginBottom: 12 }}
          type={checkResult.status === 'REJECT' ? 'error' : checkResult.status === 'WARNING' ? 'warning' : 'success'}
          showIcon message={`校验结果: ${checkResult.status}`}
          description={<div>
            {checkResult.reason && <p>原因: {checkResult.reason}</p>}
            {checkResult.issues?.length > 0 && <ul style={{ margin: '4px 0' }}>{checkResult.issues.map((i: string, k: number) => <li key={k}>{i}</li>)}</ul>}
            {checkResult.fixedSql && <p>修复后: <code>{checkResult.fixedSql}</code></p>}
            {checkResult.checkCostMs != null && <p>耗时: {checkResult.checkCostMs}ms</p>}
          </div>}
        />
      )}

      {/* === 查询结果（仅 normal 模式显示） === */}
      {!isProject && (
        <Card size="small"
          title={queryResult ? <span>查询结果 <Tag color="green">{queryResult.rowCount} 行</Tag><Tag>{queryResult.costMs}ms</Tag></span> : '查询结果'}
          extra={
            <Button icon={<ExportOutlined />} size="small" disabled={!queryResult?.rows?.length}
              onClick={() => { const fmt = window.prompt('导出格式: 输入 xlsx 或 csv', 'xlsx'); if (fmt === 'xlsx' || fmt === 'csv') onExportData(fmt); }}>
              导出数据
            </Button>
          }
        >
          {queryResult?.rows?.length > 0 ? (
            <Table rowKey={(_, i) => String(i)} size="small" dataSource={queryResult.rows}
              columns={queryResult.columns.map((c: string) => ({ title: c, dataIndex: c, key: c, ellipsis: true, width: 150 }))}
              scroll={{ x: true }} pagination={{ pageSize: 50, showSizeChanger: true, showTotal: t => `共 ${t} 条` }} />
          ) : (
            <Empty description={queryResult ? '当前查询无数据' : '请先执行 SQL 查询'} image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      )}

    </div>
  );
}
