import type { CardDto } from "../types/card";

export const getDefaultCardData = (): CardDto => {
    return {
        id: "",
        type: "note",
        title: "",

        url: "",
        sourceType: "other",

        cardNote: "",
        content: "",

        showContentPreview: true,

        coverImageUrl: "" ,

        tags: [],

        intervalDays: 10,
        nextShowAt: "",

        lastOpenAt: "",
        openCount: 0, // 隨機數 (0-99)

        likeCount: 0, // 隨機按讚數 (0-99)
        lastLikedAt: "",
        likeAvailableAt: "",

        lastInteractionAt: "",

        isArchived: true,


        createdAt: "",
        updatedAt: ""
    }

}
