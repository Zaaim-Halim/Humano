package per.hzaaim.empmanagement.core.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "country")
public class Country extends AbstractAuditingEntity<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 3)
    private String code; // ISO country code, e.g., "AL"

    @Column(name = "name", nullable = false)
    private String name; // e.g., "Albania"

    @Override
    public UUID getId() { return id; }
    // Getters and setters can be added as needed
}

