package com.ledgerflow.ledger.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.ledger.adapter.in.web.model.ProblemDto;
import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The contract's Problem schema is generated into the web adapter and speaks RFC 9457 over Jackson 3 (ADR 0008). */
class ProblemContractTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void generatedProblemModelLivesInTheInboundWebAdapter() {
        assertThat(ProblemDto.class.getPackageName()).isEqualTo("com.ledgerflow.ledger.adapter.in.web.model");
    }

    @Test
    void problemTypeDefaultsToAboutBlank() {
        assertThat(new ProblemDto().getType()).isEqualTo(URI.create("about:blank"));
    }

    @Test
    void problemSerializesWithRfc9457MemberNames() {
        var problem = new ProblemDto()
                .title("Not Found")
                .status(404)
                .detail("Account does not exist")
                .instance(URI.create("/accounts/3f1c2b7e-0000-4000-8000-000000000001/balance"));

        Map<String, Object> members = json.readValue(json.writeValueAsString(problem), new TypeReference<>() {});

        assertThat(members)
                .containsEntry("type", "about:blank")
                .containsEntry("title", "Not Found")
                .containsEntry("status", 404)
                .containsEntry("detail", "Account does not exist")
                .containsEntry("instance", "/accounts/3f1c2b7e-0000-4000-8000-000000000001/balance");
    }
}
