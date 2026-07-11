import type { BoardType } from "../../types/board";
import type { CardDto } from "../../types/card";
import request from "./request"

export const cardApi = {
    // 取得卡片瀑布流
    getCards(data: { pageNumber: number, pageSize: number, boardType: BoardType, threshold?: number }): Promise<any>{
        return request({
            url: "/cards/list",
            method: "POST",
            data
        });
    },
    // 取得單張卡片資訊
    getCard(id: string | number): Promise<CardDto>{
        return request({
            url: `/cards/${id}`,
            method: "GET",
        })

        // // 🚧 暫時模擬後端：直接包成 Promise 回傳 Mock 資料
        // const mockData = getCardByIdFromMock(String(id));
        // if (!mockData) {
        //     return Promise.reject(new Error("找不到該卡片"));
        // }
        // return Promise.resolve(mockData);
    },
    // 新建卡片資料
    createCard(data: any): Promise<CardDto>{
        return request({
            url: `/cards`,
            method: "POST",
            data
        })
    },
    // 更新卡片資料
    updateCard(id: string | number, data: any): Promise<CardDto>{
        return request({
            url: `/cards/${id}`,
            method: "PUT",
            data
        })
    },
    // 刪除卡片
    deleteCard(id: string | number): Promise<any>{
        return request({
            url: `/cards/${id}`,
            method: "DELETE",
        })
    },
    ///////////// 卡片互動 //////////////
    toggleCardStar(id: string | number, data: { starStatus: boolean }): Promise<any>{
        return request({
            url: `/cards/${id}/star`,
            method: "PUT",
            data
        })
    },
    toggleArchive(id: string | number, data: { archivedStatus: boolean }): Promise<any>{
        return request({
            url: `/cards/${id}/archive`,
            method: "PUT",
            data
        })
    },
    snoozeCard(id: string | number, data: { nextIntervalDays: number | 0 }): Promise<any>{
        return request({
            url: `/cards/${id}/snooze`,
            method: "PUT",
            data
        })
    },
    readCard(id: string | number): Promise<CardDto> {
        return request({
            url: `/cards/${id}/read`,
            method: "PUT"
    });
}
}
