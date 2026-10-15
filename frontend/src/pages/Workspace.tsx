import { useEffect, useState } from 'react';
import { Tree, Button, Modal, Drawer, Space, Empty, Spin, Dropdown, message, Card, Divider, Tag } from 'antd';
import { PlusOutlined, ReloadOutlined, DatabaseOutlined, TableOutlined, MoreOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import DataSourcePage from './DataSource';

interface DataSourceVO {
  id: number;
  name: string;
  dbType: string;
  host: string;
  port: string;
  databaseName: string;
  username: string;
  enabled: boolean;
  lastMetaSyncAt: string;
  remark: string;
}

interface TreeNode {
  key: string;
  title: React.ReactNode;
  icon?: React.ReactNode;
  children?: TreeNode[];
  isLeaf?: boolean;
  data?: any;
}

interface SelectedInfo {
  type: 'ds' | 'db' | 'tb';
  dsId: number;
  dbName?: string;
  tableName?: string;
  ds?: DataSourceVO;
  tableDetail?: any;
}

export default function WorkspacePage() {
  const [dataSources, setDataSources] = useState<DataSourceVO[]>([]);
  const [treeData, setTreeData] = useState<TreeNode[]>([]);
  const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [selected, setSelected] = useState<SelectedInfo | null>(null);
  const [loading, setLoading] = useState(false);
  const [dsModalOpen, setDsModalOpen] = useState(false);
  const [dbDrawerOpen, setDbDrawerOpen] = useState(false);
  const [dbDetail, setDbDetail] = useState<any>(null);
  const [tableModalOpen, setTableModalOpen] = useState(false);
  const [tableDetail, setTableDetail] = useState<any>(null);

  useEffect(() => { loadDataSources(); }, []);

  const loadDataSources = async () => {
    setLoading(true);
    try {
      const r: any = await http.get('/datasource/list');
      const list: DataSourceVO[] = r ?? [];
      setDataSources(list);
      // 默认展开所有数据源
      const nodes = list.map(ds => ({
        key: 'ds-' + ds.id,
        title: <span><b>{ds.name}</b> <small style={{ color: '#888' }}>({ds.dbType})</small></span>,
        icon: <DatabaseOutlined />,
        data: { type: 'ds', id: ds.id, ds },
        children: [],
      }));
      setTreeData(nodes);
      setExpanded(nodes.map(n => n.key));
    } finally {
      setLoading(false);
    }
  };

  const loadDatabases = async (dsId: number) => {
    try {
      const r: any = await http.get(`/meta/${dsId}/databases`);
      const dbs = r ?? [];
      setTreeData(curr => curr.map(n => {
        if (n.key === 'ds-' + dsId) {
          return {
            ...n,
            children: dbs.map((db: any) => ({
              key: 'db-' + dsId + '-' + db.name,
              title: db.name,
              icon: <DatabaseOutlined />,
              data: { type: 'db', dsId, dbName: db.name },
              children: [],
              isLeaf: false,
            })),
          };
        }
        return n;
      }));
    } catch (e: any) {
      message.error('加载数据库失败: ' + (e?.message ?? ''));
    }
  };

  const loadTables = async (dsId: number, dbName: string) => {
    try {
      const r: any = await http.get(`/meta/${dsId}/tables`, { params: { database: dbName } });
      const tables: string[] = r ?? [];
      setTreeData(curr => curr.map(n => {
        const dbKey = 'db-' + dsId + '-' + dbName;
        if (n.key.startsWith('ds-')) {
          return {
            ...n,
            children: n.children?.map(c => {
              if (c.key === dbKey) {
                return {
                  ...c,
                  children: tables.map(t => ({
                    key: 'tb-' + dsId + '-' + dbName + '-' + t,
                    title: t,
                    icon: <TableOutlined />,
                    isLeaf: true,
                    data: { type: 'tb', dsId, dbName, tableName: t },
                  })),
                };
              }
              return c;
            }),
          };
        }
        return n;
      }));
    } catch (e: any) {
      message.error('加载表失败: ' + (e?.message ?? ''));
    }
  };

  // 展开节点 → 自动加载子节点
  const onExpand = (keys: React.Key[]) => {
    setExpanded(keys as string[]);
    for (const k of keys as string[]) {
      if (k.startsWith('ds-')) {
        const id = Number(k.slice(3));
        const node = treeData.find(n => n.key === k);
        if (node && !node.children?.length) loadDatabases(id);
      } else if (k.startsWith('db-')) {
        const parts = k.split('-');
        const dsId = Number(parts[1]);
        const db = parts.slice(2).join('-'); // 库名可能含-
        const dsNode = treeData.find(n => n.key === 'ds-' + dsId);
        const dbNode = dsNode?.children?.find(c => c.key === k);
        if (dbNode && !dbNode.children?.length) loadTables(dsId, db);
      }
    }
  };

  // 点击节点 → 选中 + 加载内容
  const onSelect = (_: React.Key[], info: any) => {
    if (!info.node?.data) return;
    const { type, dsId, dbName, tableName } = info.node.data;
    const ds = dataSources.find(d => d.id === dsId);
    if (type === 'tb') {
      // 加载表详情
      http.get(`/meta/${dsId}/table`, { params: { database: dbName, table: tableName } })
        .then((r: any) => {
          setSelected({ type: 'tb', dsId, dbName, tableName, ds, tableDetail: r });
        });
    } else if (type === 'db') {
      setSelected({ type: 'db', dsId, dbName, ds });
    } else if (type === 'ds') {
      setSelected({ type: 'ds', dsId, ds });
    }
  };

  const triggerSync = async (dsId: number) => {
    await http.post(`/meta/${dsId}/sync`);
    message.success('已开始后台同步');
    loadDatabases(dsId);
  };

  const showDbDetail = async (dsId: number) => {
    const r: any = await http.get(`/datasource/${dsId}`);
    setDbDetail(r);
    setDbDrawerOpen(true);
  };

  const renderMenu = (nodeData: any) => {
    if (nodeData.type === 'ds') {
      return {
        items: [
          { key: 'detail', label: '查看详情' },
          { key: 'sync', label: '刷新元数据' },
          { type: 'divider' as const },
          { key: 'delete', label: <span style={{ color: 'red' }}>删除</span>, danger: true },
        ],
        onClick: async ({ key }: { key: string }) => {
          const id = nodeData.id;
          if (key === 'detail') showDbDetail(id);
          else if (key === 'sync') triggerSync(id);
          else if (key === 'delete') {
            Modal.confirm({
              title: '确认删除?',
              content: '⚠️ 仅移除本系统配置,不操作目标数据库',
              okType: 'danger',
              onOk: async () => {
                try {
                  await http.delete(`/datasource/${id}`);
                  message.success('已移除');
                } catch (e: any) {
                  message.error(e?.message ?? '删除失败');
                } finally {
                  loadDataSources();
                }
              },
            });
          }
        },
      };
    }
    if (nodeData.type === 'tb') {
      return {
        items: [{ key: 'detail', label: '查看表详情' }],
        onClick: () => {
          http.get(`/meta/${nodeData.dsId}/table`, { params: { database: nodeData.dbName, table: nodeData.tableName } })
            .then((r: any) => setTableDetail(r));
          setTableModalOpen(true);
        },
      };
    }
    return { items: [] };
  };

  // 渲染右侧内容区
  const renderContent = () => {
    if (!selected) {
      return (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
          <Empty description="👈 请在左侧选择数据源/数据库/数据表" />
        </div>
      );
    }
    if (selected.type === 'ds') {
      return (
        <div style={{ maxWidth: 600 }}>
          <Card title="📦 数据源详情" size="small">
            <p><b>名称:</b> {selected.ds?.name}</p>
            <p><b>类型:</b> <Tag color="blue">{selected.ds?.dbType}</Tag></p>
            <p><b>地址:</b> {selected.ds?.host}:{selected.ds?.port}</p>
            <p><b>数据库名:</b> {selected.ds?.databaseName}</p>
            <p><b>账号:</b> {selected.ds?.username}</p>
            <p><b>状态:</b> {selected.ds?.enabled ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag>}</p>
            <p><b>最近同步:</b> {selected.ds?.lastMetaSyncAt || '—'}</p>
            {selected.ds?.remark && <p><b>备注:</b> {selected.ds.remark}</p>}
          </Card>
          <Divider />
          <Space>
            <Button type="primary" icon={<ReloadOutlined />} onClick={() => triggerSync(selected!.dsId!)}>
              刷新元数据
            </Button>
            <Button onClick={() => showDbDetail(selected!.dsId!)}>查看详情</Button>
            <Link to={`/query?dsId=${selected.dsId}`}>
              <Button icon={<ThunderboltOutlined />}>进入查询</Button>
            </Link>
          </Space>
        </div>
      );
    }
    if (selected.type === 'db') {
      return (
        <div>
          <Card title={`📁 ${selected.dbName}`} size="small" extra={
            <Space>
              <Button size="small" icon={<ReloadOutlined />} onClick={() => loadTables(selected!.dsId!, selected!.dbName!)}>
                刷新表
              </Button>
            </Space>
          }>
            <p><b>数据源:</b> {selected.ds?.name}</p>
            <p><b>数据库名:</b> {selected.dbName}</p>
          </Card>
          <Divider />
          <p style={{ color: '#888' }}>点击左侧数据库节点展开查看表,或点击表节点查看详情</p>
        </div>
      );
    }
    if (selected.type === 'tb') {
      const t = selected.tableDetail;
      return (
        <div>
          <Card
            title={`📋 ${selected.tableName}`}
            size="small"
            extra={<Link to={`/query?dsId=${selected.dsId}&db=${selected.dbName}&table=${selected.tableName}`}><Button size="small" icon={<ThunderboltOutlined />}>查这张表</Button></Link>}
          >
            <p><b>数据库:</b> {selected.dbName}</p>
            <p><b>表注释:</b> {t?.tableComment || '(无)'}</p>
            <p><b>同步时间:</b> {t?.syncedAt || '—'}</p>
          </Card>
          {t?.columns?.length > 0 && (
            <>
              <Divider />
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ background: '#fafafa' }}>
                    <th style={th}>字段名</th><th style={th}>类型</th><th style={th}>长度</th>
                    <th style={th}>可空</th><th style={th}>主键</th><th style={th}>注释</th>
                  </tr>
                </thead>
                <tbody>
                  {t.columns.map((c: any) => (
                    <tr key={c.name}>
                      <td style={td}><b>{c.name}</b></td>
                      <td style={td}>{c.type}</td>
                      <td style={td}>{c.length ?? '-'}</td>
                      <td style={td}>{c.nullable ? 'Y' : 'N'}</td>
                      <td style={td}>{c.isPk ? '✓' : ''}</td>
                      <td style={td}>{c.comment || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </div>
      );
    }
    return null;
  };

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 96px)' }}>
      {/* 左侧树 */}
      <div style={{ width: 300, borderRight: '1px solid #f0f0f0', padding: 12, overflow: 'auto' }}>
        <Space style={{ marginBottom: 8 }}>
          <Button type="primary" icon={<PlusOutlined />} size="small" onClick={() => setDsModalOpen(true)}>
            新增数据源
          </Button>
          <Button icon={<ReloadOutlined />} size="small" onClick={loadDataSources}>刷新</Button>
        </Space>
        {loading ? <Spin /> : (treeData.length === 0 ? <Empty description="暂无数据源" /> :
          <Tree
            treeData={treeData}
            expandedKeys={expanded}
            onExpand={onExpand}
            onSelect={onSelect}
            showLine
            showIcon
            titleRender={(node: any) => (
              <span onContextMenu={(e) => { e.preventDefault(); }}>
                <Dropdown menu={renderMenu(node.data)} trigger={['contextMenu']}>
                  <Space size={4}>
                    {node.title as any}
                    <MoreOutlined style={{ color: '#aaa' }} />
                  </Space>
                </Dropdown>
              </span>
            )}
          />
        )}
      </div>

      {/* 右侧内容区 */}
      <div style={{ flex: 1, padding: 24, overflow: 'auto' }}>
        {renderContent()}
      </div>

      {/* 新增数据源弹窗 */}
      <Modal title="新增数据源" open={dsModalOpen} footer={null} onCancel={() => setDsModalOpen(false)} width={700}>
        <DataSourcePage />
      </Modal>

      {/* 数据库详情抽屉 */}
      <Drawer title="数据库详情" open={dbDrawerOpen} onClose={() => setDbDrawerOpen(false)} width={500}>
        {dbDetail && (
          <div>
            <p><b>名称:</b> {dbDetail.name}</p>
            <p><b>类型:</b> {dbDetail.dbType}</p>
            <p><b>Host:</b> {dbDetail.host}:{dbDetail.port}</p>
            <p><b>数据库:</b> {dbDetail.databaseName}</p>
            <p><b>账号:</b> {dbDetail.username}</p>
            <p><b>密码:</b> <span style={{ color: '#888' }}>●●●●●●●●</span> (已脱敏)</p>
            <p><b>启用:</b> {dbDetail.enabled ? '是' : '否'}</p>
            <p><b>最近同步:</b> {dbDetail.lastMetaSyncAt || '—'}</p>
            <p><b>备注:</b> {dbDetail.remark || '—'}</p>
          </div>
        )}
      </Drawer>

      {/* 表详情弹窗 */}
      <Modal title={tableDetail ? `${tableDetail.tableName}` : '表详情'} open={tableModalOpen} footer={null} onCancel={() => setTableModalOpen(false)} width={800}>
        {tableDetail && (
          <div>
            <p><b>表注释:</b> {tableDetail.tableComment || '(无)'}</p>
            <p><b>同步时间:</b> {tableDetail.syncedAt}</p>
            <table style={{ width: '100%', borderCollapse: 'collapse', marginTop: 8 }}>
              <thead>
                <tr style={{ background: '#fafafa' }}>
                  <th style={th}>字段名</th><th style={th}>类型</th><th style={th}>长度</th>
                  <th style={th}>可空</th><th style={th}>主键</th><th style={th}>注释</th>
                </tr>
              </thead>
              <tbody>
                {tableDetail.columns?.map((c: any) => (
                  <tr key={c.name}>
                    <td style={td}>{c.name}</td><td style={td}>{c.type}</td><td style={td}>{c.length ?? '-'}</td>
                    <td style={td}>{c.nullable ? 'Y' : 'N'}</td><td style={td}>{c.isPk ? '✓' : ''}</td><td style={td}>{c.comment || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>
    </div>
  );
}

const th: React.CSSProperties = { border: '1px solid #e8e8e8', padding: 8, textAlign: 'left', fontSize: 12 };
const td: React.CSSProperties = { border: '1px solid #e8e8e8', padding: 8, fontSize: 12 };
