package com.echogallery.tag;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.echogallery.card.Card;
import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagDto> getTagList(){

        // 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        List<TagDto> tags = tagRepository.findTagsWithCardCount(userId);

        // 轉換為 Response DTO 列表回傳
        return tags;
    }

    @Transactional
    public TagDto updateTag(Long tagId, String name){

        Long userId = SecurityUtil.getCurrentUserId();

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "標籤不存在"));

        if(!tag.getUser().getId().equals(userId)){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "無權修改此標籤");
        }

        name = name.trim();
        // 名稱沒變，不需要更新
        if (name.equals(tag.getName())) {
            return TagDto.builder()
                    .id(tag.getId())
                    .name(tag.getName())
                    .cardCount((long) tag.getCards().size())
                    .build();
        }

        // 檢查是否已有同名標籤
        if (tagRepository.findByUserIdAndName(userId, name).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "標籤名稱已存在");
        }

        tag.setName(name);

        return TagDto.builder()
                .id(tag.getId())
                .name(tag.getName())
                .cardCount((long)tag.getCards().size())
                .build();
    }


    @Transactional
    public void deleteTag(Long tagId) {

        Long userId = SecurityUtil.getCurrentUserId();

        Tag tag = tagRepository.findById(tagId)
            .orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND));

        if(!tag.getUser().getId().equals(userId)){
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN);
        }

        for(Card card : tag.getCards()){
            card.getTags().remove(tag);
        }
        tagRepository.delete(tag);
    }

}
