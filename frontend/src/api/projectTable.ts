import { http } from './http';

/** 列表 VO（与后端 ProjectTableMetaVO 对应） */
export interface FieldItemVO {
  id?: number;
  columnName: string;
  columnType: string;
  columnComment: string;
  bizDesc: string;
}

export interface ProjectTableMetaVO {
  id: number;
  projectCode: string;
  projectName: string;
  tableName: string;
  creatorName?: string;
  createdAt: string;
  updatedAt: string;
  fields: FieldItemVO[];
}

export interface FieldItemRequest {
  id?: number;
  columnName: string;
  columnType: string;
  columnComment: string;
  bizDesc: string;
}

export interface ProjectTableMetaRequest {
  id?: number;
  projectCode: string;
  projectName: string;
  tableName: string;
  fields: FieldItemRequest[];
}

/** 拦截器已解开 R 包装, http 返回的直接是 data, 泛型 T 即为 data 类型 */
type ApiResp<T> = Promise<T>;

export const projectTableApi = {
  /** 列表搜索 */
  list(params: { projectName?: string; tableName?: string }): ApiResp<ProjectTableMetaVO[]> {
    return http.get('/project-table/list', { params });
  },

  /** 详情 */
  detail(id: number): ApiResp<ProjectTableMetaVO> {
    return http.get(`/project-table/${id}`);
  },

  /** 新增 / 编辑 */
  save(data: ProjectTableMetaRequest): ApiResp<ProjectTableMetaVO> {
    return http.post('/project-table/save', data);
  },

  /** 删除 */
  delete(id: number): ApiResp<void> {
    return http.delete(`/project-table/${id}`);
  },

  /** 导入脚本（mode: 'ddl' = 建表语句；'list' = 数据库软件表结构清单） */
  importScript(
    script: string,
    projectCode: string,
    projectName: string,
    mode: 'ddl' | 'list' = 'ddl',
  ): ApiResp<ProjectTableMetaVO[]> {
    return http.post('/project-table/import', { projectCode, projectName, script, mode });
  },

  /** 自动修复脚本（返回修复后的脚本文本） */
  autoFix(
    script: string,
    mode: 'ddl' | 'list' = 'ddl',
    projectCode?: string,
    projectName?: string,
  ): ApiResp<string> {
    return http.post('/project-table/import/auto-fix', { script, mode, projectCode, projectName });
  },
};
