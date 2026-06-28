import type { SidebarStats, TagRanking } from "../../types/sidebar";
import request from "./request"

export const sidebarApi = {
    // 取得右方側邊欄 卡片統計資訊
    getSidebarStatus(): Promise<SidebarStats>{
        return request({
            url: "/sidebar/stats",
            method: "GET"
        });
    },
    // 取得右方側邊欄 標籤熱門排行
    getSidebarTagsTop(): Promise<TagRanking[]>{
        return request({
            url: "/sidebar/tags/top",
            method: "GET"
        })
    }
}
