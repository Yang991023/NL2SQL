import { useEffect, useState } from 'react';
import { Table, Tag, Button } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { http } from '../api/http';

export default function AuditPage() {
  const [list, setList] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);

  const load = async () => {
    const r: any = await http.get('/audit/logs', { params: { page, size: 20 } });
    // 拦截器已解开 R 包装, r 是 { list, total, page, size }
    setList(r?.list ?? []);
    setTotal(r?.total ?? 0);
  };
  useEffect(() => { load(); }, [page]);

  return (
    <div style={{ padding: 24 }}>
      <Button icon={<ReloadOutlined />} onClick={load} style={{ marginBottom: 12 }}>刷新</Button>
      <Table
        rowKey="id"
        dataSource={list}
        pagination={{ current: page, pageSize: 20, total, onChange: setPage }}
        columns={[
          { title: '时间', dataIndex: 'createdAt' },
          { title: '用户ID', dataIndex: 'userId' },
          { title: '动作', dataIndex: 'action', render: (a) => <Tag color="blue">{a}</Tag> },
          { title: '目标', render: (_, r) => `${r.targetType ?? ''}#${r.targetId ?? ''}` },
          { title: 'IP', dataIndex: 'ip' },
          {
            title: '结果',
            dataIndex: 'result',
            render: (r) => <Tag color={(r ?? '').startsWith('SUCCESS') ? 'green' : 'red'}>{r}</Tag>,
          },
          { title: '详情', dataIndex: 'detail', ellipsis: true },
        ]}
      />
    </div>
  );
}
