package com.echogallery.tag;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.util.SecurityUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TagResponse> getTagList(){

        // 安全地從安全上下文取得目前登入的 userId，落實多租戶資料隔離
        Long userId = SecurityUtil.getCurrentUserId();

        List<Tag> tags = tagRepository.findByUserIdOrderByUpdatedAtDesc(userId);

        // 轉換為 Response DTO 列表回傳
        return tags.stream()
                .map(tag -> TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build())
                .toList();
    }

}
