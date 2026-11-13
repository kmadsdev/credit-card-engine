package io.github.kmadsdev.cardengine.persistence;

import io.github.kmadsdev.cardengine.domain.policy.Reason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit row: the inputs the policy saw, the outcome, every reason and
 * the policy version. Inputs never contain the CPF or the card number.
 */
@Entity
@Immutable
@Table(name = "decision_record")
public class DecisionRecord {

    public enum SubjectType { APPLICATION, UPGRADE_REQUEST }

    @Id
    private UUID id;

    @Column(name = "subject_type", nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "policy_version", nullable = false)
    private String policyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> inputs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<Reason> reasons;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DecisionRecord() {
    }

    public DecisionRecord(SubjectType subjectType, UUID subjectId, String outcome, String policyVersion,
                          Map<String, Object> inputs, List<Reason> reasons, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.outcome = outcome;
        this.policyVersion = policyVersion;
        this.inputs = inputs;
        this.reasons = List.copyOf(reasons);
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public List<Reason> getReasons() {
        return reasons;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
