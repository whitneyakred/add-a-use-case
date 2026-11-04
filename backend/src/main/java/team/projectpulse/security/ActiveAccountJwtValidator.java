package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Refuses a bearer token whose account is no longer active (FR-SEC-active-account).
 *
 * <p>A token is signed at login and stays valid for its whole lifetime, so a signature check alone would let a
 * student or instructor deactivated after logging in keep working until the token expired. This validator runs on
 * every request, after the signature and expiry checks, and fails the token when the account named by its
 * {@code userId} claim is deactivated or no longer exists. A failed token surfaces as a 401 through the bearer-token
 * entry point, and the frontend's centralized logout takes the user back to the login page.
 */
@Component
public class ActiveAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INACTIVE_ACCOUNT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The account for this token is deactivated or no longer exists.", null);

    private final UserRepository userRepository;


    public ActiveAccountJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Integer userId = readUserId(jwt.getClaim("userId"));
        if (userId == null) {
            return OAuth2TokenValidatorResult.failure(INACTIVE_ACCOUNT);
        }
        boolean active = this.userRepository.findById(userId)
                .map(PeerEvaluationUser::isEnabled)
                .orElse(false);
        return active ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INACTIVE_ACCOUNT);
    }

    // The claim is written as an Integer, but a decoded JSON number may come back as a Long.
    private static Integer readUserId(Object claim) {
        if (claim instanceof Number number) {
            return number.intValue();
        }
        if (claim instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
