import type { CardDto } from "../types/card";

export const generateCards = (pageNumber: number, pageSize: number) : CardDto[] => {

    const randomText = (length: number) => {
        const chars = "某一些繁體中文字隨機挑選測試排版字元長度混合符號abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789，。！？,!?. ";
        let result = '';
        for (let i = 0; i < length; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    };

    return Array.from({length: pageSize}, (_, index)=>{
        const id = (pageNumber-1)*pageSize + (index+1);
        const idStr = id.toString()
        return {
            id: idStr,
            type: index%2==0 ? "note": "link",
            title: `這是第${idStr}篇測試筆記-${randomText(id)}`,

            url: "http://localhost:5173/111111111111111111",
            sourceType: "other",

            summary: "(summary)" + randomText(Math.random()*100),
            content: "(content)" + randomText(Math.random()*100),
            reason: "(reason)" + randomText(Math.random()*100),

            showContentPreview: true,

            coverImageUrl: "https://picsum.photos/800/400" ,

            tags: ["筆記","test","123","56777222"],

            intervalDays: 10,
            nextShowAt: "2026-05-06T00:00:00Z",

            lastOpenAt: "2026-05-01T00:00:00Z",
            openCount: Math.floor(Math.random() * 100), // 隨機數 (0-99)

            likeCount: Math.floor(Math.random() * 100), // 隨機按讚數 (0-99)
            lastLikedAt: "2026-05-01T00:00:00Z",
            likeAvailableAt: "2026-05-06T00:00:00Z",

            lastInteractionAt: "2026-05-01T00:00:00Z",

            isArchived: true,

            isShowContentPreview: true,

            createdAt: "2026-05-06T09:14:00Z",
            updatedAt: "2026-05-06T09:14:00Z"
        }

    })
    
}