import request from "./request"

export const tagApi = {
    // 取得卡片瀑布流
    getTags(): Promise<any>{
        return request({
            url: "/tags/list",
            method: "GET"
        });
    },
}
