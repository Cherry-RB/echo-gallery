import axios from "axios";
import router from "../../router";

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
            switch (error.response.status) {
                case 401:
                    alert("登入過期，請重新登入");
                    // 這裡可以實作清空 token 並導向登入頁
                    // 清空本地 Token
                    localStorage.removeItem("token");
                    localStorage.removeItem("username"); // 如果有存的話
                    // 強制跳轉回登入頁
                    router.push("/login");
                    break;
                case 403:
                    alert("權限不足，拒絕存取");
                    break;
                case 500:
                    alert("後端伺服器異常");
                    break;
                default:
                    alert(`連線錯誤: ${error.response.status}`);
            }
        } else {
            alert("網路連線失敗，請檢查網路設定");
        }
    return Promise.reject(error);
    }
)

export default service;