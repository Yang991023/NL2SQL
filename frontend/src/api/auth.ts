import { http } from './http';

export interface LoginResp {
  token: string;
  userId: number;
  username: string;
  nickname: string;
}

export const login = (username: string, password: string) =>
  http.post<any, LoginResp>('/auth/login', { username, password });
