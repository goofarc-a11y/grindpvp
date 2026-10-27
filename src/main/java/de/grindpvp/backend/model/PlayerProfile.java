package de.grindpvp.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

@Entity
@Table(name = "player_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_username", columnNames = "username")
})
public class PlayerProfile {
    @Id
    private UUID id;

    @Column(nullable = false, length = 16)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Role title;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean hasMod;

    protected PlayerProfile() {
    }

    public PlayerProfile(UUID id, String username) {
        this.id = id;
        this.username = username;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Role getTitle() {
        return title;
    }

    public void setTitle(Role title) {
        this.title = title;
    }

    public boolean hasMod() {
        return hasMod;
    }

    public void setHasMod(boolean hasMod) {
        this.hasMod = hasMod;
    }
}
