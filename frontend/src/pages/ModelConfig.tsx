import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Switch, Space, message, Popconfirm } from 'antd';
import { PlusOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons';
import { http } from '../api/http';

export default function ModelConfigPage() {
  const [list, setList] = useState<any[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = async () => {
    const r: any = await http.get('/model/list');
    setList(r ?? []);
  };
  useEffect(() => { load(); }, []);

  const onSave = async () => {
    const v = await form.validateFields();
    try {
      await http.post('/model/save', v);
      message.success('已保存');
    } catch (e: any) {
      message.error(e?.message ?? '保存失败');
    } finally {
      setOpen(false);
      form.resetFields();
      load();
    }
  };

  const onDelete = async (id: number) => {
    try {
      await http.delete(`/model/${id}`);
      message.success('已删除');
    } catch (e: any) {
      message.error(e?.message ?? '删除失败');
    } finally {
      load();
    }
  };

  return (
    <div style={{ padding: 24 }}>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>新增模型</Button>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={list}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: 'Base URL', dataIndex: 'baseUrl' },
          { title: 'Model', dataIndex: 'model' },
          { title: '超时(s)', dataIndex: 'timeoutS' },
          { title: '默认', dataIndex: 'isDefault', render: (v) => v ? '✓' : '' },
          { title: '启用', dataIndex: 'enabled', render: (v) => v ? '✓' : '' },
          {
            title: '操作',
            render: (_, r) => <Popconfirm title="确认删除?" onConfirm={() => onDelete(r.id)}>
              <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>,
          },
        ]}
      />
      <Modal title="新增/编辑大模型" open={open} onCancel={() => setOpen(false)} onOk={onSave} width={700}>
        <Form form={form} layout="vertical" initialValues={{ enabled: true, timeoutS: 60 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input style={{ width: '100%' }} /></Form.Item>
          <Form.Item name="baseUrl" label="Base URL (OpenAI 兼容)" rules={[{ required: true }]}>
            <Input placeholder="http://localhost:11434  或  https://api.openai.com" />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="model" label="模型名" style={{ flex: 1 }}><Input placeholder="qwen2.5-coder:7b / gpt-4o / ..." /></Form.Item>
            <Form.Item name="timeoutS" label="超时(秒)" style={{ width: 120 }}><Input type="number" /></Form.Item>
          </div>
          <Form.Item name="apiKey" label="API Key"><Input.Password style={{ width: '100%' }} /></Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="isDefault" label="设为默认" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
