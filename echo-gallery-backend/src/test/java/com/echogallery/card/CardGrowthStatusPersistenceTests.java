package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

class CardGrowthStatusPersistenceTests extends IntegrationTestBase {

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    void newCardDefaultsToUnmarked() {
        Card card = createCard("unmarked-default");

        Card savedCard = cardRepository.saveAndFlush(card);

        assertThat(savedCard.getGrowthStatus()).isEqualTo(CardGrowthStatus.UNMARKED);
    }

    @Test
    @Transactional
    void persistsEveryGrowthStatus() {
        for (CardGrowthStatus status : CardGrowthStatus.values()) {
            Card card = createCard("status-" + status.name().toLowerCase());
            card.setGrowthStatus(status);

            Long cardId = cardRepository.saveAndFlush(card).getId();
            entityManager.clear();

            assertThat(cardRepository.findById(cardId))
                    .get()
                    .extracting(Card::getGrowthStatus)
                    .isEqualTo(status);
        }
    }

    @Test
    void mapsGrowthStatusToVarcharWithUnmarkedDefault() {
        var column = jdbcTemplate.queryForMap("""
                SELECT data_type, udt_name, column_default
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'cards'
                  AND column_name = 'growth_status'
                """);

        assertThat(column.get("data_type")).isEqualTo("character varying");
        assertThat(column.get("udt_name")).isEqualTo("varchar");
        assertThat(column.get("column_default").toString()).contains("UNMARKED");
    }

    private Card createCard(String suffix) {
        User user = new User();
        user.setUsername("growth-" + suffix);
        user.setEmail("growth-" + suffix + "@example.com");
        user.setPasswordHash("password-hash");
        userRepository.saveAndFlush(user);

        return Card.builder()
                .user(user)
                .type("note")
                .title("Growth status test")
                .build();
    }
}
