package pl.mkn.tdw.common;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformSourceCodePropertiesTest {

    @Test
    void shouldRequireOnePlatformDefaultBranch() {
        var properties = new PlatformSourceCodeProperties();

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var violations = validatorFactory.getValidator().validate(properties);

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("defaultBranch");
        }
    }

    @Test
    void shouldAcceptAnAnonymizedCrmBranch() {
        var properties = new PlatformSourceCodeProperties();
        properties.setDefaultBranch("crm-release");

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties)).isEmpty();
        }
    }
}
