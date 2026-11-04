package team.projectpulse.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.projectpulse.course.Course;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.UserUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Who may deactivate a student (BR-student-lifecycle): the course admin of her course, or an instructor assigned to
 * her course section, and nobody else.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StudentSecurityService.isCurrentUserLifecycleManagerOfStudent (BR-student-lifecycle)")
class StudentSecurityServiceTest {

    private static final int STUDENT_ID = 4;

    @Mock
    StudentRepository studentRepository;

    @Mock
    UserUtils userUtils;

    @InjectMocks
    StudentSecurityService studentSecurityService;

    Instructor courseAdmin;       // id 1: admin of the course, NOT assigned to the student's course section

    Instructor sectionInstructor; // id 2: assigned to the student's course section

    Instructor otherInstructor;   // id 3: neither


    @BeforeEach
    void setUp() {
        this.courseAdmin = new Instructor("b.wei@abc.edu", "Bingyang", "Wei", "b.wei@abc.edu", "123456", true, "admin instructor");
        this.courseAdmin.setId(1);
        this.sectionInstructor = new Instructor("b.gates@abc.edu", "Bill", "Gates", "b.gates@abc.edu", "123456", true, "instructor");
        this.sectionInstructor.setId(2);
        this.otherInstructor = new Instructor("t.cook@abc.edu", "Tim", "Cook", "t.cook@abc.edu", "123456", true, "admin instructor");
        this.otherInstructor.setId(3);

        Course course = new Course("COSC 40993 Senior Design", "Senior design");
        course.setCourseAdmin(this.courseAdmin);

        Section section = new Section("2023-2024", LocalDate.of(2023, 7, 31), LocalDate.of(2024, 10, 6), true, DayOfWeek.MONDAY, LocalTime.of(23, 59), DayOfWeek.TUESDAY, LocalTime.of(23, 59));
        section.setCourse(course);
        section.addInstructor(this.sectionInstructor);

        Student john = new Student("j.smith@abc.edu", "John", "Smith", "j.smith@abc.edu", "123456", true, "student");
        john.setId(STUDENT_ID);
        section.addStudent(john);

        given(this.studentRepository.findById(STUDENT_ID)).willReturn(Optional.of(john));
    }

    @Test
    void testInstructorOfTheStudentsSectionIsAdmitted() {
        given(this.userUtils.getUserId()).willReturn(2);

        assertThat(this.studentSecurityService.isCurrentUserLifecycleManagerOfStudent(STUDENT_ID)).isTrue();
    }

    @Test
    void testCourseAdminNotAssignedToTheSectionIsAdmitted() {
        given(this.userUtils.getUserId()).willReturn(1);

        assertThat(this.studentSecurityService.isCurrentUserLifecycleManagerOfStudent(STUDENT_ID)).isTrue();
    }

    @Test
    void testOtherInstructorIsRefused() {
        given(this.userUtils.getUserId()).willReturn(3);

        assertThat(this.studentSecurityService.isCurrentUserLifecycleManagerOfStudent(STUDENT_ID)).isFalse();
    }

    @Test
    void testTheStudentHerselfIsRefused() {
        given(this.userUtils.getUserId()).willReturn(STUDENT_ID);

        assertThat(this.studentSecurityService.isCurrentUserLifecycleManagerOfStudent(STUDENT_ID)).isFalse();
    }

}
