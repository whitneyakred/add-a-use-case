package team.projectpulse.system.exception;

/**
 * UC-STU-deactivate-student extension 3a: the student named is already deactivated, so nothing is changed.
 */
public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException(String studentName) {
        super(studentName + " is already deactivated.");
    }

}
