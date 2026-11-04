package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.StatusCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student through the API: the main success scenario and extensions 3a and 3b.
 *
 * <p>Seed data: John Smith (student 4) is on Team1 in course section 2023-2024 of COSC 40993. Bingyang Wei
 * (instructor 1) is that course's admin; Bill Gates (instructor 2) is an instructor of the course section;
 * Tim Cook (instructor 3) is the admin of another course; Eric Hudson is John's teammate.
 */
@DisplayName("Integration tests for UC-STU-deactivate-student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int STUDENT_JOHN_ID = 4;

    private static final int ADMIN_BINGYANG_ID = 1;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StudentRepository studentRepository;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;

    String instructorBillToken;

    String otherCourseAdminTimToken;

    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.otherCourseAdminTimToken = login("t.cook@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private Student john() {
        this.entityManager.flush();
        this.entityManager.clear();
        return this.studentRepository.findById(STUDENT_JOHN_ID).orElseThrow();
    }

    @Test
    void testSectionInstructorDeactivatesStudent() throws Exception {
        // T1, main success scenario
        Integer teamIdBefore = john().getTeam().getTeamId();

        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.enabled").value(false));

        Student john = john();
        assertThat(john.isEnabled()).isFalse();
        // POST-2 / BR-deactivation-keeps-team: still on the same team
        assertThat(john.getTeam()).isNotNull();
        assertThat(john.getTeam().getTeamId()).isEqualTo(teamIdBefore);
    }

    @Test
    void testCourseAdminNotAssignedToTheSectionDeactivatesStudent() throws Exception {
        // T2: take Bingyang off John's course section; he stays the course admin
        Section section = john().getSection();
        Instructor bingyang = section.getInstructors().stream()
                .filter(instructor -> instructor.getId().equals(ADMIN_BINGYANG_ID)).findFirst().orElseThrow();
        section.removeInstructor(bingyang);
        this.entityManager.flush();

        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, this.adminBingyangToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));

        assertThat(john().isEnabled()).isFalse();
    }

    @Test
    void testDeactivateStudentAlreadyDeactivated() throws Exception {
        // T3, extension 3a
        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isOk());

        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("John Smith is already deactivated."));

        assertThat(john().isEnabled()).isFalse();
    }

    @Test
    void testInstructorOfAnotherCourseCannotDeactivateStudent() throws Exception {
        // T4, extension 3b
        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, this.otherCourseAdminTimToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(john().isEnabled()).isTrue();
    }

    @Test
    void testStudentCannotDeactivateTeammate() throws Exception {
        // T4, extension 3b
        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID + "/deactivate").header(HttpHeaders.AUTHORIZATION, this.studentEricToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(john().isEnabled()).isTrue();
    }

    @Test
    void testGenericEditCannotDeactivateStudent() throws Exception {
        // T12: the edit form's Status field cannot bypass the confirmed flow
        String body = """
                {"id": 4, "username": "j.smith@abc.edu", "firstName": "John", "lastName": "Smith", "email": "j.smith@abc.edu", "enabled": false}
                """;

        this.mockMvc.perform(put(this.baseUrl + "/students/" + STUDENT_JOHN_ID).contentType("application/json").content(body).header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(john().isEnabled()).isTrue();
    }

}
