import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Switch, Space, message, Popconfirm } from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { http } from '../api/http';

export default function SkillConfigPage() {
  const [list, setList] = useState<any[]>([]);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<any>(null);
  const [form] = Form.useForm();

  const load = async () => {
    const r: any = await http.get('/skill/list');
    setList(r ?? []);
  };
  useEffect(() => { load(); }, []);

  const onEdit = (row: any) => {
    setEditing(row);
    form.setFieldsValue(row);
    setOpen(true);
  };

  const onAdd = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  const onSave = async () => {
    const v = await form.validateFields();
    try {
      await http.post('/skill/save', { ...v, id: editing?.id });
      message.success('已保存');
    } catch (e: any) {
      message.error(e?.message ?? '保存失败');
    } finally {
      setOpen(false);
      load();
    }
  };

  const onDelete = async (id: number) => {
    try {
      await http.delete(`/skill/${id}`);
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
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>新增模板</Button>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
      </Space>
      <Table
        rowKey="id"
        dataSource={list}
        columns={[
          { title: '名称', dataIndex: 'name' },
          { title: '描述', dataIndex: 'description' },
          { title: '默认', dataIndex: 'isDefault', render: (v) => v ? '✓' : '' },
          { title: '启用', dataIndex: 'enabled', render: (v) => v ? '✓' : '' },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(r)}>编辑</Button>
                <Popconfirm title="确认删除?" onConfirm={() => onDelete(r.id)}>
                  <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
      <Modal title={editing ? '编辑模板' : '新增模板'} open={open} onCancel={() => setOpen(false)} onOk={onSave} width={800}>
        <Form form={form} layout="vertical" initialValues={{ enabled: true }}>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="name" label="名称" rules={[{ required: true }]} style={{ flex: 1 }}><Input /></Form.Item>
            <Form.Item name="description" label="描述" style={{ flex: 2 }}><Input /></Form.Item>
          </div>
          <Form.Item name="content" label="内容" rules={[{ required: true }]}>
            <Input.TextArea rows={15} style={{ fontFamily: 'JetBrains Mono, Consolas, monospace' }} />
          </Form.Item>
          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="isDefault" label="设为默认" valuePropName="checked"><Switch /></Form.Item>
            <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
          </div>
        </Form>
      </Modal>
    </div>
  );
}
