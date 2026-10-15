import { http } from './http';

export interface UserVO {
  id: number;
  username: string;
  nickname?: string;
  email?: string;
  phone?: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserSaveRequest {
  id?: number;
  username: string;
  password?: string;
  nickname?: string;
  email?: string;
  phone?: string;
  enabled?: boolean;
}

export interface PageResult {
  list: UserVO[];
  total: number;
  page: number;
  size: number;
}

/** 当前登录用户信息（右上角用） */
export const getMe = () => http.get<any, UserVO>('/auth/me');

/** 用户列表（分页 + 模糊搜索） */
export const listUsers = (params: { keyword?: string; page?: number; size?: number }) =>
  http.get<any, PageResult>('/auth/user/list', { params });

/** 用户详情 */
export const getUser = (id: number) => http.get<any, UserVO>(`/auth/user/${id}`);

/** 新增 / 编辑用户 */
export const saveUser = (data: UserSaveRequest) =>
  http.post<any, UserVO>('/auth/user/save', data);

/** 删除用户 */
export const deleteUser = (id: number) => http.delete(`/auth/user/${id}`);
