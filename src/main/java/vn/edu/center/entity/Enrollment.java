package vn.edu.center.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "dang_ky_hoc_phan", uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"student_id", "class_id"}))
public class Enrollment {

    @Id
    private String id;

    private String studentId;

    private String classId;

    private LocalDateTime registeredAt;

    private BigDecimal appliedTuition;

    private String status;

    public Enrollment() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getClassId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    public BigDecimal getAppliedTuition() {
        return appliedTuition;
    }

    public void setAppliedTuition(BigDecimal appliedTuition) {
        this.appliedTuition = appliedTuition;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
