import { useEffect, useState } from 'react';
import {
  Table, Button, Modal, Form, Input, Switch, Space, message,
  Popconfirm, Tag, Pagination
} from 'antd';
import { PlusOutlined, ReloadOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import { UserVO, UserSaveRequest, listUsers, saveUser, deleteUser } from '../api/user';

export default function UserPage() {
  const [list, setList] = useState<UserVO[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<UserVO | null>(null);
  const [form] = Form.useForm();

  const load = async () => {
    setLoading(true);
    try {
      const r = await listUsers({ keyword: keyword || undefined, page, size });
      setList(r.list ?? []);
      setTotal(r.total ?? 0);
    } catch (e: any) {
      message.error('加载失败：' + (e?.message ?? ''));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [page, keyword]);

  const onSearch = (v: string) => {
    setKeyword(v);
    setPage(1);
  };

  const onAdd = () => {
    setEditing(null);
    form.resetFields();
    setOpen(true);
  };

  const onEdit = (row: UserVO) => {
    setEditing(row);
    form.setFieldsValue({
      ...row,
      password: '', // 编辑时密码留空
    });
    setOpen(true);
  };

  const onSave = async () => {
    try {
      const v: UserSaveRequest = await form.validateFields();
      // 编辑时如果密码为空则删掉
      if (editing && !v.password) delete v.password;
      await saveUser(v);
      message.success(editing ? '修改成功' : '新增成功');
      setOpen(false);
      load();
    } catch (e: any) {
      message.error(e?.message ?? '保存失败');
    }
  };

  const onDelete = async (id: number) => {
    try {
      await deleteUser(id);
      message.success('已删除');
      load();
    } catch (e: any) {
      message.error(e?.message ?? '删除失败');
    }
  };

  const title = editing ? '编辑用户' : '新增用户';

  return (
    <div style={{ padding: 24 }}>
      {/* 顶部工具栏 */}
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>新增用户</Button>
        <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
        <Input.Search
          placeholder="搜索账号 / 姓名"
          allowClear
          style={{ width: 220 }}
          onSearch={onSearch}
          prefix={<SearchOutlined style={{ color: '#aaa' }} />}
        />
      </Space>

      <Table
        rowKey="id"
        loading={loading}
        dataSource={list}
        pagination={false}
        columns={[
          {
            title: '序号',
            render: (_, __, i) => (page - 1) * size + i + 1,
            width: 60,
          },
          {
            title: '账号',
            dataIndex: 'username',
            render: (u) => <b>{u}</b>,
          },
          {
            title: '姓名',
            dataIndex: 'nickname',
            render: (n) => n || <span style={{ color: '#ccc' }}>—</span>,
          },
          {
            title: '邮箱',
            dataIndex: 'email',
            render: (e) => e || <span style={{ color: '#ccc' }}>—</span>,
          },
          {
            title: '手机',
            dataIndex: 'phone',
            render: (p) => p || <span style={{ color: '#ccc' }}>—</span>,
          },
          {
            title: '状态',
            dataIndex: 'enabled',
            render: (e) => e
              ? <Tag color="green">启用</Tag>
              : <Tag color="red">禁用</Tag>,
            width: 80,
          },
          {
            title: '创建时间',
            dataIndex: 'createdAt',
            width: 160,
          },
          {
            title: '操作',
            width: 140,
            render: (_, r) => (
              <Space size={4}>
                <Button size="small" icon={<EditOutlined />} onClick={() => onEdit(r)}>
                  编辑
                </Button>
                <Popconfirm
                  title="确认删除此用户？"
                  description="删除后不可恢复，请确认。"
                  onConfirm={() => onDelete(r.id)}
                  okText="删除"
                  cancelText="取消"
                  okButtonProps={{ danger: true }}
                >
                  <Button size="small" danger icon={<DeleteOutlined />}>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />

      <div style={{ marginTop: 16, display: 'flex', justifyContent: 'flex-end' }}>
        <Pagination
          current={page}
          pageSize={size}
          total={total}
          showSizeChanger
          showQuickJumper
          showTotal={(t) => `共 ${t} 条`}
          onChange={(p, s) => {
            setPage(p);
            setSize(s);
          }}
        />
      </div>

      {/* 新增 / 编辑弹窗 */}
      <Modal
        title={title}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={onSave}
        width={520}
        okText="保存"
        cancelText="取消"
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ enabled: true }}
          style={{ marginTop: 8 }}
        >
          <Form.Item
            name="username"
            label="账号"
            rules={[
              { required: true, message: '请输入账号' },
              { min: 3, max: 50, message: '账号长度 3~50 个字符' },
              { pattern: /^\w+$/, message: '账号只能包含字母、数字、下划线' },
            ]}
          >
            <Input placeholder="登录用账号，唯一" maxLength={50} />
          </Form.Item>

          <Form.Item
            name="nickname"
            label="姓名"
            rules={[{ max: 100, message: '姓名最多 100 字' }]}
          >
            <Input placeholder="显示在右上角，如：张三" maxLength={100} />
          </Form.Item>

          <Form.Item
            name="password"
            label={editing ? '密码（留空则不修改）' : '密码'}
            rules={editing ? [] : [{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              placeholder={editing ? '不修改请留空' : '登录密码'}
              autoComplete="new-password"
            />
          </Form.Item>

          <div style={{ display: 'flex', gap: 12 }}>
            <Form.Item name="email" label="邮箱" style={{ flex: 1 }}>
              <Input placeholder="选填" />
            </Form.Item>
            <Form.Item name="phone" label="手机" style={{ width: 160 }}>
              <Input placeholder="选填" />
            </Form.Item>
          </div>

          <Form.Item name="enabled" label="启用状态" valuePropName="checked">
            <Switch checkedChildren="启用" unCheckedChildren="禁用" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
