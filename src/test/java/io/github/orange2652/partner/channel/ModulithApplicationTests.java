package io.github.orange2652.partner.channel;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModulithApplicationTests {

    private final ApplicationModules modules = ApplicationModules.of(PartnerChannelModulithApplication.class);

    @Test
    void verifyModuleBoundaries() {
        modules.verify();
    }

    @Test
    void writeDocumentationSnippets() {
        new Documenter(modules).writeDocumentation();
    }
}
