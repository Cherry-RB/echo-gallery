import request from "./request"


export const userApi = {
    // 登入
    login(data: any){
        return request({
            url: "/auth/login",
            method: "POST",
            data
        });
    },
    // 取得使用者資訊
    getUserInfo(){
        return request({
            url: "/user/profile",
            method: "GET",
        })
    },
    // 更新使用者資料
    updateProfile(data: any){
        return request({
            url: "/user/update",
            method: "PUT",
            data
        })
    }

}