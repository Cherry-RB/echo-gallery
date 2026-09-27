import type { TagDto, TagPage, TagRequest } from "../../types/tag";
import request from "./request"

export const tagApi = {
    // 取得標籤列表
    getTags(keyword = '', page = 0, size = 20): Promise<TagPage>{
        return request({
            url: "/tags/list",
            method: "GET",
            params: { keyword, page, size },
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
