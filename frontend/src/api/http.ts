import axios from 'axios';

/**
 * 统一响应结构:
 *   后端所有接口返回 { code, message, data }
 *   拦截器负责:
 *   1. code === 0 : 解开 R 包装,返回 data 给调用方
 *   2. code !== 0 : 抛出后端的 message 给调用方的 catch
 *   3. 网络/HTTP 错误 : 直接抛出
 */
export const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (res) => {
    const payload = res.data;
    if (payload?.code === 0) {
      return payload.data; // 解开 R 包装,只把 data 发给调用方
    }
    // 后端返回业务错误 code !== 0
    return Promise.reject(new Error(payload?.message ?? '未知错误'));
  },
  (err) => {
    // eslint-disable-next-line no-console
    console.error('[http error]', err?.response?.status, err?.response?.data ?? err?.message);
    if (err.response?.status === 401) {
      localStorage.removeItem('token');
      location.href = '/login';
    }
    const serverMsg = err?.response?.data?.message;
    if (serverMsg) {
      return Promise.reject(new Error(serverMsg));
    }
    return Promise.reject(err);
  }
);
