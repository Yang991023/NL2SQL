import { useEffect, useState } from 'react';
import {
  Table, Button, Modal, Form, Input, Space, message, Popconfirm, Tabs, Radio
} from 'antd';
import {
  PlusOutlined, SearchOutlined, ReloadOutlined, EyeOutlined,
  EditOutlined, DeleteOutlined, UploadOutlined
} from '@ant-design/icons';
import { projectTableApi, ProjectTableMetaVO, FieldItemVO } from '../api/projectTable';
import dayjs from 'dayjs';
import { bus, EVT_PROJECT_TABLE_CHANGED } from '../utils/bus';

/* ─── 字段配置区：局部作用域样式（避免污染全局） ─── */
const FIELD_GRID_CSS = `
.ptm-field-grid {
  display: grid;
  /* 列宽分配原则：
     - 字段/字段类型 紧凑够用即可
     - 注释 较宽（中文短句）
     - 业务描述 占满剩余（用户长输入）
     - 文本框宽 = 列宽 - 2px（左右各 1px 留白，视觉紧致） */
  grid-template-columns: 116px 124px 148px 1fr 32px;
  gap: 0 6px;
  align-items: start;
}
.ptm-field-grid .ant-input-affix-wrapper,
.ptm-field-grid .ant-input {
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
  padding: 4px 8px !important;
  font-size: 13px;
}
.ptm-col-name   { grid-column: 1; }
.ptm-col-type   { grid-column: 2; }
.ptm-col-comment{ grid-column: 3; }
.ptm-col-biz    { grid-column: 4; }
.ptm-col-act    { grid-column: 5; text-align: center; }
.ptm-tip {
  height: 16px;
  margin-top: 2px;
  font-size: 11px;
  line-height: 16px;
  color: #ff4d4f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.ptm-tip-empty {
  height: 16px;
  margin-top: 2px;
}
.ptm-head {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f5f5f5;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 8px 0;
  font-weight: 500;
  font-size: 13px;
  color: #333;
}
.ptm-head > span {
  padding: 0 10px;
}
.ptm-row {
  padding: 4px 0 10px;
  border-bottom: 1px solid #f0f0f0;
}

/* ─── 导入弹窗样式 ─── */
.ptm-import-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
.ptm-import-grid .ant-input {
  width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
}
.ptm-hint {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  padding: 10px 12px;
  margin: 12px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #595959;
}
.ptm-hint-title {
  color: #333;
  font-weight: 500;
  margin-bottom: 4px;
}
.ptm-hint code {
  background: #f0f0f0;
  padding: 1px 5px;
  border-radius: 2px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  color: #d4380d;
}
.ptm-hint ul {
  margin: 4px 0 0;
  padding-left: 18px;
}
.ptm-hint li {
  margin: 2px 0;
}
.ptm-script-area .ant-input {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace !important;
  font-size: 12px !important;
  line-height: 1.6 !important;
  background: #fafafa;
  width: 100% !important;
}
.ptm-script-area .ant-input-data-count {
  text-align: right;
}
.ptm-import-error {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  border-radius: 4px;
  padding: 8px 12px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #ff4d4f;
  word-break: break-all;
}
.ptm-script-row {
  display: flex;
  flex-direction: column;
  gap: 0;
}
.ptm-script-row .ant-input-textarea {
  width: 100%;
}
.ptm-tabs .ant-tabs-nav {
  margin-bottom: 12px !important;
}
.ptm-mode-desc {
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
  padding: 10px 12px;
  margin-bottom: 12px;
  font-size: 12px;
  line-height: 1.7;
  color: #595959;
}
.ptm-mode-desc b {
  color: #333;
}
.ptm-mode-desc code {
  background: #f0f0f0;
  padding: 1px 5px;
  border-radius: 2px;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  color: #d4380d;
}
`;

/* ─── 列表搜索表单 ─── */
interface SearchForm {
  projectName?: string;
  tableName?: string;
}

/* ─── 新增/编辑弹窗字段行 ─── */
interface EditField {
  key: string;
  columnName: string;
  columnType: string;
  columnComment: string;
  bizDesc: string;
}

/* ─── 弹窗模式 ─── */
type ModalMode = 'add' | 'edit' | 'view' | 'import';

export default function ProjectTableMetaPage() {
  const [data, setData] = useState<ProjectTableMetaVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchForm] = Form.useForm<SearchForm>();

  /* 弹窗状态 */
  const [modalMode, setModalMode] = useState<ModalMode | null>(null);
  const [modalId, setModalId] = useState<number | null>(null);  // edit/view 时用
  const [saving, setSaving] = useState(false);

  /* 新增/编辑表单 */
  const [form] = Form.useForm();
  const [fields, setFields] = useState<EditField[]>([]);

  /* 导入弹窗 */
  const [importScript, setImportScript] = useState('');
  const [importProjectCode, setImportProjectCode] = useState('');
  const [importProjectName, setImportProjectName] = useState('');
  /** 导入识别模式：'ddl' = 建表语句；'list' = 数据库软件表结构清单 */
  const [importMode, setImportMode] = useState<'ddl' | 'list'>('ddl');
  /** 自动修复状态 */
  const [autoFixing, setAutoFixing] = useState(false);
  /** 错误提示（null = 无错误） */
  const [importError, setImportError] = useState<string | null>(null);

  // ─── 加载列表 ───
  const loadList = async (vals?: SearchForm) => {
    setLoading(true);
    try {
      const res = await projectTableApi.list(vals ?? {});
      setData(res ?? []);
    } catch (e: any) {
      message.error(e?.message ?? '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadList(); }, []);

  // ─── 搜索 ───
  const onSearch = () => {
    loadList(searchForm.getFieldsValue());
  };

  // ─── 重置 ───
  const onReset = () => {
    searchForm.resetFields();
    loadList();
  };

  // ─── 打开弹窗 ───
  const openModal = async (mode: ModalMode, id?: number) => {
    setModalMode(mode);
    setModalId(id ?? null);
    setFields([]);

    if (mode === 'add') {
      form.resetFields();
      setFields([newFieldRow()]);
    } else if (mode === 'edit' || mode === 'view') {
      try {
        const detail = await projectTableApi.detail(id!);
        form.setFieldsValue({
          projectCode: detail.projectCode,
          projectName: detail.projectName,
          tableName: detail.tableName,
        });
        setFields(detail.fields.map((f, i) => ({
          key: String(i),
          columnName: f.columnName,
          columnType: f.columnType ?? '',
          columnComment: f.columnComment ?? '',
          bizDesc: f.bizDesc ?? '',
        })));
        if (detail.fields.length === 0) setFields([newFieldRow()]);
      } catch (e: any) {
        message.error(e?.message ?? '加载详情失败');
        closeModal();
      }
    } else if (mode === 'import') {
      setImportScript('');
      setImportProjectCode('');
      setImportProjectName('');
      setImportMode('ddl');
      setImportError(null);
    }
  };

  const closeModal = () => {
    setModalMode(null);
    setModalId(null);
    form.resetFields();
  };

  // ─── 字段行操作 ───
  const newFieldRow = (): EditField => ({
    key: Date.now().toString(),
    columnName: '',
    columnType: '',
    columnComment: '',
    bizDesc: '',
  });

  const addFieldRow = () => setFields(prev => [...prev, newFieldRow()]);

  const removeFieldRow = (key: string) =>
    setFields(prev => prev.filter(f => f.key !== key));

  const updateField = (key: string, col: keyof EditField, val: string) =>
    setFields(prev => prev.map(f => f.key === key ? { ...f, [col]: val } : f));

  // ─── 保存 ───
  const onSave = async () => {
    // 基本字段校验
    let vals: any;
    try {
      vals = await form.validateFields();
    } catch {
      return;
    }

    // 字段列表校验：columnName / columnType 必填
    const fieldErrors: string[] = [];
    fields.forEach((f, i) => {
      if (!f.columnName.trim()) fieldErrors.push(`第 ${i + 1} 行字段名为必填`);
      if (!f.columnType.trim()) fieldErrors.push(`第 ${i + 1} 行字段类型为必填`);
    });
    if (fieldErrors.length) {
      message.error(fieldErrors[0]);
      return;
    }

    setSaving(true);
    try {
      await projectTableApi.save({
        id: modalId ?? undefined,
        projectCode: vals.projectCode,
        projectName: vals.projectName,
        tableName: vals.tableName,
        fields: fields.map(f => ({
          columnName: f.columnName.trim(),
          columnType: f.columnType.trim(),
          columnComment: f.columnComment.trim(),
          bizDesc: f.bizDesc.trim(),
        })),
      });
      message.success('保存成功');
      closeModal();
      loadList(searchForm.getFieldsValue());
      bus.emit(EVT_PROJECT_TABLE_CHANGED);
    } catch (e: any) {
      message.error(e?.message ?? '保存失败');
    } finally {
      setSaving(false);
    }
  };

  // ─── 导入脚本 ───
  const onImport = async () => {
    setImportError(null);
    if (!importProjectCode.trim()) {
      setImportError('请填写项目编号');
      message.error('请填写项目编号');
      return;
    }
    if (!importProjectName.trim()) {
      setImportError('请填写项目名称');
      message.error('请填写项目名称');
      return;
    }
    if (!importScript.trim()) {
      setImportError('请输入导入脚本');
      message.error('请输入导入脚本');
      return;
    }
    setSaving(true);
    try {
      await projectTableApi.importScript(
        importScript,
        importProjectCode.trim(),
        importProjectName.trim(),
        importMode,
      );
      message.success('导入成功！');
      closeModal();
      loadList(searchForm.getFieldsValue());
      bus.emit(EVT_PROJECT_TABLE_CHANGED);
    } catch (e: any) {
      const errMsg = e?.message ?? '导入失败';
      setImportError(errMsg);
      message.error(errMsg);
    } finally {
      setSaving(false);
    }
  };

  // ─── 自动修复 ───
  const onAutoFix = async () => {
    setImportError(null);
    if (!importScript.trim()) {
      message.warning('请先输入待修复的脚本内容');
      return;
    }
    setAutoFixing(true);
    try {
      const fixed = await projectTableApi.autoFix(
        importScript,
        importMode,
        importProjectCode.trim() || undefined,
        importProjectName.trim() || undefined,
      );
      setImportScript(fixed);
      message.success('自动修复完成，请检查并确认脚本内容');
    } catch (e: any) {
      const errMsg = e?.message ?? '自动修复失败';
      setImportError(errMsg);
      message.error(errMsg);
    } finally {
      setAutoFixing(false);
    }
  };

  // ─── 删除 ───
  const onDelete = async (id: number) => {
    try {
      await projectTableApi.delete(id);
      message.success('已删除');
      loadList(searchForm.getFieldsValue());
      bus.emit(EVT_PROJECT_TABLE_CHANGED);
    } catch (e: any) {
      message.error(e?.message ?? '删除失败');
    }
  };

  // ─── 弹窗标题 ───
  const modalTitles: Record<ModalMode, string> = {
    add: '新增',
    edit: '编辑',
    view: '查看详情',
    import: '导入脚本',
  };

  const readonly = modalMode === 'view';

  return (
    <div style={{ padding: 24 }}>
      {/* 字段配置区专属样式 */}
      <style>{FIELD_GRID_CSS}</style>

      {/* ── 搜索栏 ── */}
      <Form form={searchForm} layout="inline" style={{ marginBottom: 16 }}>
        <Form.Item name="projectName" style={{ minWidth: 180 }}>
          <Input placeholder="项目名称（模糊搜索）" allowClear />
        </Form.Item>
        <Form.Item name="tableName" style={{ minWidth: 180 }}>
          <Input placeholder="表名（模糊搜索）" allowClear />
        </Form.Item>
        <Form.Item name="createdAt" style={{ minWidth: 180 }}>
          <Input placeholder="创建时间（预留）" disabled />
        </Form.Item>
        <Space>
          <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>查询</Button>
          <Button icon={<ReloadOutlined />} onClick={onReset}>重置</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openModal('add')}>新增</Button>
          <Button icon={<UploadOutlined />} onClick={() => openModal('import')}>导入</Button>
        </Space>
      </Form>

      {/* ── 列表 ── */}
      <Table
        rowKey="id"
        loading={loading}
        dataSource={data}
        scroll={{ x: 1100 }}
        columns={[
          { title: '项目编号', dataIndex: 'projectCode', width: 100 },
          { title: '项目名称', dataIndex: 'projectName', width: 200 },
          { title: '表名称', dataIndex: 'tableName', width: 200 },
          {
            title: '创建时间',
            dataIndex: 'createdAt',
            width: 220,
            render: v => v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-',
          },
          {
            title: '字段数',
            dataIndex: 'fields',
            width: 100,
            render: (f: FieldItemVO[]) => f?.length ?? 0,
          },
          {
            title: '操作',
            width: 280,
            render: (_, r) => (
              <Space size="small">
                <Button size="small" icon={<EyeOutlined />} onClick={() => openModal('view', r.id)}>详情</Button>
                <Button size="small" icon={<EditOutlined />} onClick={() => openModal('edit', r.id)}>编辑</Button>
                <Popconfirm title="确认删除？" onConfirm={() => onDelete(r.id)} okText="删除" cancelText="取消" okButtonProps={{ danger: true }}>
                  <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      {/* ── 新增/编辑/查看 弹窗 ── */}
      <Modal
        open={!!modalMode && modalMode !== 'import'}
        title={modalMode ? modalTitles[modalMode] : ''}
        onCancel={closeModal}
        width={700}
        style={{ top: 20 }}
        bodyStyle={{ height: 720, padding: '16px 16px 8px', display: 'flex', flexDirection: 'column' }}
        footer={readonly ? (
          <Button onClick={closeModal}>关闭</Button>
        ) : (
          <>
            <Button onClick={closeModal}>取消</Button>
            <Button type="primary" loading={saving} onClick={onSave}>保存</Button>
          </>
        )}
      >
        {/* 顶部基本信息区（高度固定，不滚动） */}
        <div style={{ flexShrink: 0 }}>
          <Form form={form} layout="vertical">
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0 12px' }}>
              <Form.Item
                name="projectCode" label="项目编号"
                rules={[{ required: true, message: '项目编号必填' }]}
                extra={readonly ? undefined : '不允许重复'}
                style={{ marginBottom: 8 }}
              >
                <Input disabled={readonly || !!modalId} placeholder="如 P001" />
              </Form.Item>
              <Form.Item
                name="projectName" label="项目名称"
                rules={[{ required: true, message: '项目名称必填' }]}
                extra={readonly ? undefined : '不允许重复'}
                style={{ marginBottom: 8 }}
              >
                <Input disabled={readonly || !!modalId} placeholder="如 订单管理系统" />
              </Form.Item>
              <Form.Item
                name="tableName" label="表名称"
                rules={[{ required: true, message: '表名称必填' }]}
                extra={readonly ? undefined : '同一项目下不允许重复'}
                style={{ marginBottom: 8 }}
              >
                <Input disabled={readonly || !!modalId} placeholder="如 t_order" />
              </Form.Item>
            </div>
          </Form>
        </div>

        {/* 分割线 */}
        <div style={{ flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
          <span style={{ fontWeight: 500, color: '#333' }}>字段配置</span>
          {!readonly && (
            <Button size="small" onClick={addFieldRow}>+ 添加字段</Button>
          )}
        </div>

        {/* 字段配置区（可滚动） */}
        <div style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
          {/* 表头 */}
          <div className="ptm-field-grid ptm-head">
            <span>字段<span style={{ color: '#ff4d4f' }}> *</span></span>
            <span>字段类型<span style={{ color: '#ff4d4f' }}> *</span></span>
            <span>注释</span>
            <span>业务描述</span>
            <span />
          </div>

          {/* 数据行 */}
          {fields.map((f) => {
            const nameEmpty = !f.columnName.trim();
            const typeEmpty = !f.columnType.trim();
            return (
              <div key={f.key} className="ptm-field-grid ptm-row">
                <div className="ptm-col-name">
                  <Input
                    value={f.columnName}
                    onChange={e => updateField(f.key, 'columnName', e.target.value)}
                    onBlur={e => updateField(f.key, 'columnName', e.target.value.trim())}
                    disabled={readonly}
                    placeholder="字段名"
                    status={!readonly && nameEmpty ? 'error' : undefined}
                  />
                  <div className={!readonly && nameEmpty ? 'ptm-tip' : 'ptm-tip-empty'}>
                    {!readonly && nameEmpty ? '字段为必填' : ''}
                  </div>
                </div>
                <div className="ptm-col-type">
                  <Input
                    value={f.columnType}
                    onChange={e => updateField(f.key, 'columnType', e.target.value)}
                    onBlur={e => updateField(f.key, 'columnType', e.target.value.trim())}
                    disabled={readonly}
                    placeholder="如 varchar(50)"
                    status={!readonly && typeEmpty ? 'error' : undefined}
                  />
                  <div className={!readonly && typeEmpty ? 'ptm-tip' : 'ptm-tip-empty'}>
                    {!readonly && typeEmpty ? '类型为必填' : ''}
                  </div>
                </div>
                <div className="ptm-col-comment">
                  <Input
                    value={f.columnComment}
                    onChange={e => updateField(f.key, 'columnComment', e.target.value)}
                    disabled={readonly}
                    placeholder="字段注释"
                  />
                  <div className="ptm-tip-empty">&nbsp;</div>
                </div>
                <div className="ptm-col-biz">
                  <Input
                    value={f.bizDesc}
                    onChange={e => updateField(f.key, 'bizDesc', e.target.value)}
                    disabled={readonly}
                    placeholder="业务描述"
                  />
                  <div className="ptm-tip-empty">&nbsp;</div>
                </div>
                <div className="ptm-col-act" style={{ paddingTop: 2 }}>
                  {!readonly && fields.length > 1 ? (
                    <Button type="text" danger size="small" onClick={() => removeFieldRow(f.key)}>✕</Button>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      </Modal>

      {/* ── 导入弹窗 ── */}
      <Modal
        open={modalMode === 'import'}
        title="导入脚本"
        onCancel={closeModal}
        width={760}
        footer={
          <>
            <Button onClick={closeModal}>取消</Button>
            <Button type="primary" loading={saving} onClick={onImport}>导入</Button>
          </>
        }
      >
        {/* 顶部：项目编号 + 项目名称（两种模式共用） */}
        <div className="ptm-import-grid" style={{ marginBottom: 12 }}>
          <Input
            placeholder="项目编号（如 P001）"
            value={importProjectCode}
            onChange={e => setImportProjectCode(e.target.value)}
            allowClear
            status={!importProjectCode.trim() && saving ? 'error' : undefined}
          />
          <Input
            placeholder="项目名称（如 订单系统）"
            value={importProjectName}
            onChange={e => setImportProjectName(e.target.value)}
            allowClear
            status={!importProjectName.trim() && saving ? 'error' : undefined}
          />
        </div>

        {/* 错误提示（自动修复失败 或 导入失败） */}
        {importError && (
          <div className="ptm-import-error">{importError}</div>
        )}

        {/* 识别方式切换 */}
        <Tabs
          className="ptm-tabs"
          activeKey={importMode}
          onChange={k => { setImportMode(k as 'ddl' | 'list'); setImportScript(''); setImportError(null); }}
          items={[
            {
              key: 'ddl',
              label: '建表语句识别',
              children: (
                <>
                  <div className="ptm-mode-desc">
                    <div><b>适用：</b>DBA 工具导出的 <code>CREATE TABLE</code> 语句、Navicat「转储 SQL 文件」等。</div>
                    <div><b>要求：</b>每张表语句以 <code>CREATE TABLE</code> 开头；脚本顶部可用 <code>-- @tableName=xxx</code> 显式声明表名（缺省时取 SQL 中的表名）。</div>
                  </div>
                  <div className="ptm-script-area">
                    <Input.TextArea
                      value={importScript}
                      onChange={e => { setImportScript(e.target.value); setImportError(null); }}
                      rows={10}
                      showCount
                      placeholder={`-- @projectCode=P001 @projectName=订单系统 @tableName=t_order
CREATE TABLE t_order (
  id          VARCHAR2(50)   COMMENT '订单ID'   PRIMARY KEY,
  order_no    VARCHAR2(32)   COMMENT '订单号',
  amount      DECIMAL(12,2)  COMMENT '订单金额',
  status      TINYINT        COMMENT '状态 0=待支付 1=已支付'  NOT NULL,
  created_at  DATETIME       COMMENT '创建时间'
);`}
                    />
                  </div>
                  <Button
                    type="dashed"
                    block
                    style={{ marginTop: 8 }}
                    loading={autoFixing}
                    onClick={onAutoFix}
                  >
                    🤖 自动修改
                  </Button>
                </>
              ),
            },
            {
              key: 'list',
              label: '表结构清单识别',
              children: (
                <>
                  <div className="ptm-mode-desc">
                    <div><b>适用：</b>Navicat / DBeaver / DataGrip / MySQL Workbench 等数据库软件直接复制的「表结构清单」。</div>
                    <div><b>要求：</b>需包含 <b>列名</b>、<b>类型</b>、<b>注释</b> 三列；<b>表名</b>列可选，缺省时使用顶部「项目编号_序号」。支持 <code>\t</code> 分隔或 <code>|</code> 分隔的 Markdown 表格。</div>
                  </div>
                  <div className="ptm-script-area">
                    <Input.TextArea
                      value={importScript}
                      onChange={e => { setImportScript(e.target.value); setImportError(null); }}
                      rows={10}
                      showCount
                      placeholder={`列名        类型            注释        表名
id          VARCHAR2(50)    订单ID      t_order
order_no    VARCHAR2(32)    订单号      t_order
amount      DECIMAL(12,2)   订单金额    t_order
status      TINYINT         状态        t_order
created_at  DATETIME        创建时间    t_order`}
                    />
                  </div>
                  <Button
                    type="dashed"
                    block
                    style={{ marginTop: 8 }}
                    loading={autoFixing}
                    onClick={onAutoFix}
                  >
                    🤖 自动修改
                  </Button>
                </>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  );
}
