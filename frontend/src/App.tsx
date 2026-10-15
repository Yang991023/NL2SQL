import { useState, useEffect } from 'react';
import { Layout, Menu, Button, App } from 'antd';
import { Link, Route, Routes, useLocation, Navigate } from 'react-router-dom';
import LoginPage from './pages/Login';
import DataSourcePage from './pages/DataSource';
import WorkspacePage from './pages/Workspace';
import QueryBuilderPage from './pages/QueryCanvas';
import QueryCanvasProjectPage from './pages/QueryCanvasProject';
import ModelConfigPage from './pages/ModelConfig';
import SkillConfigPage from './pages/SkillConfig';
import AuditPage from './pages/Audit';
import ProjectTableMetaPage from './pages/ProjectTableMeta';
import UserPage from './pages/UserPage';
import { getMe } from './api/user';

const { Header, Content, Sider } = Layout;

function Private({ children }: { children: React.ReactNode }) {
  if (!localStorage.getItem('token')) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

// 取时间段问候语
function getGreeting(): string {
  const h = new Date().getHours();
  if (h < 6)  return '凌晨好';
  if (h < 9)  return '早上好';
  if (h < 12) return '上午好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  if (h < 22) return '晚上好';
  return '夜深了';
}

function Shell() {
  const loc = useLocation();
  const [collapsed, setCollapsed] = useState(false);
  const [currentUser, setCurrentUser] = useState<{ nickname?: string; username: string } | null>(null);

  // 右上角加载当前用户
  useEffect(() => {
    getMe()
      .then((u) => setCurrentUser(u))
      .catch(() => setCurrentUser(null));
  }, []);

  const logout = () => {
    localStorage.removeItem('token');
    location.href = '/login';
  };

  // 根据路径决定是否显示侧边栏
  const isWorkspace = loc.pathname.startsWith('/workspace');

  const displayName = currentUser?.nickname || currentUser?.username || '用户';

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', background: '#001529' }}>
        <div style={{ color: '#fff', flex: 1, fontSize: 18 }}>可视化 NL2SQL 自助查询工具</div>
        <div style={{ color: 'rgba(255,255,255,0.65)', marginRight: 16, fontSize: 14 }}>
          {getGreeting()}，<span style={{ color: '#fff', fontWeight: 600 }}>{displayName}</span>
        </div>
        <Button onClick={logout}>退出</Button>
      </Header>
      <Layout>
        {!isWorkspace && (
          <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={180}>
            <Menu
              mode="inline"
              selectedKeys={[loc.pathname]}
              style={{ height: '100%' }}
              items={[
                { key: '/datasource', label: <Link to="/datasource">数据源</Link> },
                { key: '/project-table', label: <Link to="/project-table">项目表管理</Link> },
                { key: '/query', label: <Link to="/query">查询画布</Link> },
                { key: '/query-project', label: <Link to="/query-project">查询画布-项目表</Link> },
                { key: '/model', label: <Link to="/model">大模型配置</Link> },
                { key: '/skill', label: <Link to="/skill">Skill 模板</Link> },
                { key: '/audit', label: <Link to="/audit">操作审计</Link> },
                { key: '/user', label: <Link to="/user">用户管理</Link> },
              ]}
            />
          </Sider>
        )}
        <Content>
          <Routes>
            <Route path="/datasource" element={<DataSourcePage />} />
            <Route path="/project-table" element={<ProjectTableMetaPage />} />
            <Route path="/workspace" element={<WorkspacePage />} />
            <Route path="/query" element={<QueryBuilderPage />} />
            <Route path="/query-project" element={<QueryCanvasProjectPage />} />
            <Route path="/model" element={<ModelConfigPage />} />
            <Route path="/skill" element={<SkillConfigPage />} />
            <Route path="/audit" element={<AuditPage />} />
            <Route path="/user" element={<UserPage />} />
            <Route path="*" element={<Navigate to="/workspace" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}

export default function AppRoot() {
  return (
    <App>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/*" element={<Private><Shell /></Private>} />
      </Routes>
    </App>
  );
}
