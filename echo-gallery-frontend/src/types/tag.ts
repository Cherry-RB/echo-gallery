import type { PageResponse } from './card'

export interface TagDto {
    id: number;
    name: string;
    cardCount: number;
}

export type TagPage = PageResponse<TagDto>
export interface TagRequest {
    name: string
}
