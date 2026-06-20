import request from "./request";

export const authApi = {
    login(data: { email: string, password: string }): Promise<any>{ // 使用者登入
        return request({
            url: "/auth/login", // 會自動拼接成 /api/auth/login
            method: "post",
            data // 
        });
    },
    register(data: { username: string, email: string, password: string }): Promise<any>{ // 使用者註冊
        return request({
            url: "/auth/register",
            method: "post",
            data // 
        })
    },
    logout(): Promise<any> {
        return request({
            url: "/auth/logout",
            method: "post"
        });
    }
}