package team.projectpulse.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * FR-SEC-active-account: a token whose account is deactivated, or gone, is refused on every request.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActiveAccountJwtValidator (FR-SEC-active-account)")
class ActiveAccountJwtValidatorTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActiveAccountJwtValidator validator;


    private static Jwt tokenFor(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("j.smith@abc.edu");
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    private static Student student(boolean enabled) {
        return new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", enabled, "student");
    }

    @Test
    void testActiveAccountIsAccepted() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(student(true)));

        assertThat(this.validator.validate(tokenFor(4)).hasErrors()).isFalse();
    }

    @Test
    void testDeactivatedAccountIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(student(false)));

        assertThat(this.validator.validate(tokenFor(4)).hasErrors()).isTrue();
    }

    @Test
    void testAccountThatNoLongerExistsIsRefused() {
        given(this.userRepository.findById(4)).willReturn(Optional.empty());

        assertThat(this.validator.validate(tokenFor(4)).hasErrors()).isTrue();
    }

    @Test
    void testUserIdDecodedAsLongIsRead() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(student(true)));

        assertThat(this.validator.validate(tokenFor(4L)).hasErrors()).isFalse();
    }

    @Test
    void testTokenWithoutUserIdIsRefused() {
        assertThat(this.validator.validate(tokenFor(null)).hasErrors()).isTrue();
    }

}
