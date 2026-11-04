package team.projectpulse.security;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;
import team.projectpulse.system.StatusCode;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account through the real filter chain: a token issued while the account was active stops working
 * on the first request after the account is deactivated, for a student and for an instructor alike, and a
 * deactivated account cannot log in again.
 *
 * <p>Seed data: John Smith is student 4, Bill Gates is instructor 2.
 */
@DisplayName("Integration tests for FR-SEC-active-account")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    private static final int STUDENT_JOHN_ID = 4;

    private static final int INSTRUCTOR_BILL_ID = 2;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private void deactivate(int userId) {
        PeerEvaluationUser user = this.userRepository.findById(userId).orElseThrow();
        user.setEnabled(false);
        this.userRepository.saveAndFlush(user);
    }

    @Test
    void testStudentTokenStillWorksWhileActive() throws Exception {
        String johnToken = login("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/" + STUDENT_JOHN_ID).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    void testStudentTokenIssuedBeforeDeactivationIsRefused() throws Exception {
        String johnToken = login("j.smith@abc.edu");

        deactivate(STUDENT_JOHN_ID);

        this.mockMvc.perform(get(this.baseUrl + "/students/" + STUDENT_JOHN_ID).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    void testInstructorTokenIssuedBeforeDeactivationIsRefused() throws Exception {
        String billToken = login("b.gates@abc.edu");

        deactivate(INSTRUCTOR_BILL_ID);

        this.mockMvc.perform(get(this.baseUrl + "/instructors/" + INSTRUCTOR_BILL_ID).header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    void testDeactivatedStudentCannotLogIn() throws Exception {
        deactivate(STUDENT_JOHN_ID);

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

}
