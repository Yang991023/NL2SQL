import QueryCanvas from './QueryCanvas';

/**
 * 查询画布 - 项目表
 * 与"查询画布"功能基本一致，区别：
 *  - 顶部选择改为【项目名称 / 表】两级
 *  - 表结构来自【项目表管理】中已配置的字段
 *  - 移除【执行 SQL】【导出表结构】【查询结果】三块
 */
export default function QueryCanvasProjectPage() {
  return <QueryCanvas mode="project" />;
}
