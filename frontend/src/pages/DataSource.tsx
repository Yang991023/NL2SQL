import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, message, Space } from 'antd';
import { PlusOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import { http } from '../api/http';

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
}

export default function DataSourcePage() {
  const [list, setList] = useState<DataSourceVO[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = async () => {
    try {
      const r: any = await http.get('/datasource/list');
      // 拦截器已解开 R 包装, r 就是后端 data (数组)
      setList(r ?? []);
    } catch (err) {
      const msg = (err as any)?.message ?? '';
      message.error('加载数据源失败：' + msg);
      setList([]);
    }
  };

  useEffect(() => { load(); }, []);

  const onTest = async () => {
    const v = await form.validateFields();
    try {
      // 拦截器已解开 R 包装, r.data 就是后端 data
      const r: any = await http.post('/datasource/test', v);
      if (r?.success) {
        message.success(`连接成功！自动识别: ${r.dbType} ${r.version}`);
        form.setFieldsValue({ dbType: r.dbType });
      } else {
        message.error(r?.message ?? '连接失败');
      }
    } catch (e: any) {
      message.error(e?.message ?? '测试异常');
    }
  };

  const onSave = async () => {
    let v;
    try {
      v = await form.validateFields();
    } catch {
      // 表单校验失败, antd 会自己红字提示
      return;
    }
    try {
      // 拦截器已解开 R 包装, save 成功则直接 resolve, 失败在 catch
      await http.post('/datasource/save', v);
      message.success('保存成功！已自动触发元数据同步');
    } catch (e: any) {
      message.error('保存失败：' + (e?.message ?? '未知错误'));
    } finally {
      // 无论成功失败,都要关闭 Modal 并重置表单
      setOpen(false);
      form.resetFields();
      load();
    }
  };

  const [syncingIds, setSyncingIds] = useState<Record<number, boolean>>({});

  const onRefreshMeta = async (id: number, name: string) => {
    setSyncingIds((s) => ({ ...s, [id]: true }));
    const hide = message.loading(`正在同步 ${name} 的元数据...`, 0);
    try {
      // 拦截器已解开 R 包装, r 是 SyncResultVO
      const r: any = await http.post(`/datasource/${id}/refresh-meta`);
      hide();
      if (r?.errorMessage) {
        message.error('同步失败：' + r.errorMessage);
      } else {
        message.success(
          `同步完成！新增表 ${r?.tableCount ?? 0} 张, 字段 ${r?.columnCount ?? 0} 个, 耗时 ${r?.durationMs ?? 0}ms`
        );
      }
      load();
    } catch (e: any) {
      hide();
      message.error('同步失败：' + (e?.message ?? '网络异常'));
    } finally {
      setSyncingIds((s) => ({ ...s, [id]: false }));
    }
  };

  const onDelete = async (id: number) => {
    Modal.confirm({
      title: '确认删除?',
      content: '⚠️ 仅移除本系统配置，不会操作目标数据库',
      okType: 'danger',
      onOk: async () => {
        await http.delete(`/datasource/${id}`);
        message.success('已移除');
        load();
      },
    });
  };

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
          新增数据源
        </Button>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={list}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '类型', dataIndex: 'dbType' },
          { title: 'Host', dataIndex: 'host' },
          { title: '端口', dataIndex: 'port' },
          { title: '数据库', dataIndex: 'databaseName' },
          { title: '账号', dataIndex: 'username' },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button size="small">详情</Button>
                <Button size="small" icon={<ReloadOutlined />}
                  loading={syncingIds[r.id]}
                  onClick={() => onRefreshMeta(r.id, r.name)}>
                  刷新元数据
                </Button>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => onDelete(r.id)}>
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />

      <Modal title="新增/编辑数据源" open={open} onCancel={() => setOpen(false)} onOk={onSave} width={600} okText="保存">
        <Form form={form} layout="vertical" initialValues={{ enabled: true }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input style={{ width: '100%' }} /></Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="host" label="IP地址" rules={[{ required: true }]} style={{ flex: 1 }}><Input placeholder="127.0.0.1" /></Form.Item>
            <Form.Item name="port" label="端口" rules={[{ required: true }]} style={{ width: 140 }}><Input placeholder="3306" /></Form.Item>
          </div>
          <Form.Item name="databaseName" label="数据库名"><Input style={{ width: '100%' }} /></Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="username" label="账号" rules={[{ required: true }]} style={{ flex: 1 }}><Input /></Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true }]} style={{ flex: 1 }}><Input.Password /></Form.Item>
          </div>
          <Form.Item name="dbType" label="数据库类型（可留空自动识别）">
            <Select showSearch filterOption={(input, option) =>
              (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            } options={[
              { value: 'POSTGRESQL', label: 'PostgreSQL' },
              { value: 'MYSQL', label: 'MySQL' },
              { value: 'ORACLE', label: 'Oracle' },
              { value: 'CLICKHOUSE', label: 'ClickHouse' },
              { value: 'DAMENG', label: '达梦' },
              { value: 'KINGBASE', label: '人大金仓' },
            ]} allowClear style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注"><Input.TextArea rows={2} /></Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
        </Form>
        <Button onClick={onTest} block style={{ marginTop: 8 }}>测试连接</Button>
      </Modal>
    </div>
  );
}
