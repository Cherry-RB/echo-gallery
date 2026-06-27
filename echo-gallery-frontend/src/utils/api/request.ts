import axios from "axios";
import router from "../../router";
import { ElMessage } from "element-plus";

// 建立 axios 實例
const service = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "/api", // 讀取 Vite 環境變數
    timeout: 10000,
    headers: { "Content-Type": "application/json" }
});

// 請求攔截器 (Request Interceptor)
service.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem("token");
        if(token){
            config.headers["Authorization"] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
)

// 回應攔截器 (Response Interceptor)
service.interceptors.response.use(
    (response) => {
        return response.data;
    },
    (error) => {
        // 統一處理全域錯誤碼
        if (error.response) {
            const { status, data } = error.response;

            switch (status) {
                case 400: // 格式錯誤
                  // alert(data.message || "輸入格式有誤");
                  ElMessage.error(data.message || "輸入格式有誤")
                  break;
                case 409: // 衝突 (重複註冊)
                  ElMessage.error(data.message || "資料已存在");
                  break;
                case 401:
                  ElMessage.error(data.message || "登入過期，請重新登入");
                  // 如果 URL 包含 '/login' 或 '/register'，就不執行強制登出與跳轉
                  const isAuthRequest = error.config.url?.includes('/login') || error.config.url?.includes('/register');

                  if (!isAuthRequest) {
                      // 只有在「不是」登入/註冊的請求時，才進行清理與跳轉
                      localStorage.removeItem("token");
                      localStorage.removeItem("username");
                      router.push("/login");
                  }
                  break;
                case 403:
                    ElMessage.error(data.message || "權限不足，拒絕存取");
                    localStorage.removeItem("token");
                    router.push("/login");
                    break;
                // 在 axios 攔截器中
                case 500:
                    ElMessage.error(data.message || "伺服器發生錯誤，請稍後再試");
                    break;
                default:
                    ElMessage.error(`連線錯誤: ${error.response.status}`);
            }
        } else {
            ElMessage.error("網路連線失敗，請檢查網路設定");
        }
    return Promise.reject(error);
    }
)

export default service;
