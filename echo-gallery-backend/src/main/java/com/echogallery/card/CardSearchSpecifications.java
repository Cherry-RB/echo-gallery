package com.echogallery.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import com.echogallery.tag.Tag;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

final class CardSearchSpecifications {

    private CardSearchSpecifications() {
    }

    static Specification<Card> from(Long userId, CardSearchRequest request) {
        List<Long> tagIds = request.getTagIds().stream().distinct().toList();
        List<CardGrowthStatus> growthStatuses = request.getGrowthStatuses().stream().distinct().toList();
        String title = request.getTitle() == null ? null : request.getTitle().trim();

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));

            if (request.getArchiveStatus() == CardSearchArchiveStatus.ACTIVE) {
                predicates.add(criteriaBuilder.isFalse(root.get("isArchived")));
            } else if (request.getArchiveStatus() == CardSearchArchiveStatus.ARCHIVED) {
                predicates.add(criteriaBuilder.isTrue(root.get("isArchived")));
            }
            if (request.getId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("id"), request.getId()));
            }
            if (title != null && !title.isEmpty()) {
                String escapedTitle = title.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        "%" + escapedTitle + "%",
                        '\\'));
            }
            if (!growthStatuses.isEmpty()) {
                predicates.add(root.get("growthStatus").in(growthStatuses));
            }
            if (!tagIds.isEmpty()) {
                predicates.add(request.getTagMode() == CardSearchTagMode.AND
                        ? hasAllTags(root, query.subquery(Long.class), criteriaBuilder, tagIds)
                        : hasAnyTag(root, query.subquery(Integer.class), criteriaBuilder, tagIds));
            }

            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                query.orderBy(buildOrders(root, criteriaBuilder, request));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate hasAnyTag(
            Root<Card> root,
            Subquery<Integer> subquery,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Long> tagIds) {
        Root<Card> subCard = subquery.from(Card.class);
        Join<Card, Tag> tag = subCard.join("tags");
        subquery.select(criteriaBuilder.literal(1));
        subquery.where(
                criteriaBuilder.equal(subCard.get("id"), root.get("id")),
                tag.get("id").in(tagIds));
        return criteriaBuilder.exists(subquery);
    }

    private static Predicate hasAllTags(
            Root<Card> root,
            Subquery<Long> subquery,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            List<Long> tagIds) {
        Root<Card> subCard = subquery.from(Card.class);
        Join<Card, Tag> tag = subCard.join("tags");
        subquery.select(criteriaBuilder.countDistinct(tag.get("id")));
        subquery.where(
                criteriaBuilder.equal(subCard.get("id"), root.get("id")),
                tag.get("id").in(tagIds));
        return criteriaBuilder.equal(subquery, (long) tagIds.size());
    }

    private static List<Order> buildOrders(
            Root<Card> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            CardSearchRequest request) {
        String property = switch (request.getSortBy()) {
            case UPDATED_AT -> "updatedAt";
            case CREATED_AT -> "createdAt";
            case NEXT_SHOW_AT -> "nextShowAt";
            case ID -> "id";
        };
        boolean ascending = request.getDirection() == CardSearchDirection.ASC;
        List<Order> orders = new ArrayList<>();
        if (request.getSortBy() == CardSearchSortBy.NEXT_SHOW_AT) {
            orders.add(criteriaBuilder.asc(criteriaBuilder.selectCase()
                    .when(criteriaBuilder.isNull(root.get(property)), 1)
                    .otherwise(0)));
        }
        orders.add(ascending ? criteriaBuilder.asc(root.get(property)) : criteriaBuilder.desc(root.get(property)));
        if (request.getSortBy() != CardSearchSortBy.ID) {
            orders.add(ascending ? criteriaBuilder.asc(root.get("id")) : criteriaBuilder.desc(root.get("id")));
        }
        return orders;
    }
}
