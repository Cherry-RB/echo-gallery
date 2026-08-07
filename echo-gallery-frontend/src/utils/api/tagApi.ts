import type { TagDto, TagRequest } from "../../types/tag";
import request from "./request"

export const tagApi = {
    // 取得標籤列表
    getTags(): Promise<any>{
        return request({
            url: "/tags/list",
            method: "GET"
        });
    },
    // 更新標籤
    updateTag(id: string | number, data: TagRequest): Promise<TagDto>{
        return request({
            url: `/tags/${id}`,
            method: "PUT",
            data
        })
    },
    // 刪除標籤
    deleteTag(id: string | number): Promise<TagDto>{
        return request({
            url: `/tags/${id}`,
            method: "DELETE",
        })
    },
}
