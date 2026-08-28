package com.pavansingerreddy.note.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// Entity annotation tells Hibernate to make a table out of this class.
@Entity
// Getter annotation from Lombok generates getters for all fields.
@Getter
// Setter annotation from Lombok generates setters for all fields.
@Setter
// NoArgsConstructor annotation from Lombok generates a no-args constructor.
@NoArgsConstructor
// AllArgsConstructor annotation from Lombok generates a constructor with one
// parameter for each field in your class. Fields are initialized in the order
// they are declared.
@AllArgsConstructor
// JsonIdentityInfo annotation is used to handle serialization and
// deserialization of related entities. It helps in handling infinite recursion
// problems by referring to the object id during serialization.
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "userId")
// ToString annotation from Lombok generates a toString method.
@ToString
// This class implements the UserDetails interface, which means it can be used
// to represent a user in the Spring Security framework and it is used by the
// UserDetailsService to load the user details for the Authentication provider.
public class Users implements UserDetails {
    // Id annotation specifies the primary key of an entity.
    @Id
    // GeneratedValue annotation provides for the specification of generation
    // strategies for the values of primary keys.
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // userId field holds the user ID.
    private long userId;
    // username field holds the username.
    private String username;
    // password field holds the password.
    private String password;
    // Column annotation is used to specify the mapped column for a persistent
    // property or field. The unique attribute indicates that the column values
    // should be unique.
    @Column(unique = true)
    // email field holds the email.
    private String email;
    // ManyToMany annotation defines a many-to-many relationship between the User
    // and Role entities. FetchType.EAGER means that the related entities will be
    // fetched immediately. CascadeType.ALL means that all operations (persist,
    // remove, refresh, merge, detach) that are applied to the User entity will also
    // be applied to the Role entity.
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    // JoinTable annotation specifies the join table, join columns and inverse join
    // columns of a many-to-many relationship.joinColumn's name attribute refers to
    // the name of the column in the join table which refers to this current tables
    // primary key which is annotated with @Id annotation and the
    // inverseJoinColumn's name attribute refers to the name of the column in the
    // join table which refers to the Role tables primary key which is annotated
    // with @Id annotation.
    @JoinTable(name = "User_Role", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    // roles field holds the roles associated with the user.
    private Set<Role> roles;
    // Time which reflects when the user is created
    private Date userCreatedAtTime;
    // when we create a next new user we can check for this time and send email
    // based on this time for verifying the new user we don't get blocked by our
    // email provider for spam
    @Column(name = "new_user_can_be_created_at_time")
    private Date newUserCanBeCreatedAtTime;
    // mailNoToUseForSendingEmail contains the email number from the email service
    // providers in the application.yml which is used to verify this user
    private int mailNoToUseForSendingEmail;
    // enabled field indicates whether the user is enabled.
    private boolean enabled = false;
    // OneToOne annotation defines a one-to-one relationship between the User and
    // VerificationToken entities. The mappedBy attribute indicates that the
    // VerificationToken entity owns the relationship. CascadeType.ALL means that
    // all operations (persist, remove, refresh, merge, detach) that are applied to
    // the User entity will also be applied to the VerificationToken entity.
    // orphanRemoval = true means that when a User entity is removed, its associated
    // VerificationToken entity will also be removed.
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private VerificationToken verificationToken;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Note> notes;

    @Override
    // This method returns the authorities granted to the user.
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Return the roles of the user as Role also implements GrantedAuthority.
        return this.roles;
    }

    @Override
    // This method checks if the user's account has not expired.
    public boolean isAccountNonExpired() {
        // Return true as default value, indicating that the account has not expired.
        return true;
    }

    @Override
    // This method checks if the user's account is not locked.
    public boolean isAccountNonLocked() {
        // Return true as default value, indicating that the account is not locked.
        return true;
    }

    @Override
    // This method checks if the user's credentials have not expired.
    public boolean isCredentialsNonExpired() {
        // Return true as default value, indicating that the credentials have not
        // expired.
        return true;
    }

    @Override
    // This method checks if the user is enabled.
    public boolean isEnabled() {
        // Return the enabled status of the user.
        return this.enabled;
    }

    // This method assigns the current time to the userCreatedAtTime variable and
    // then assigns a random time which is between 5 to 10 minutes ahead of the
    // current time to the newUserCanBeCreatedAtTime so that when we create a next
    // new user we can check for this time and send email based on this time for
    // verifying the new user we don't get blocked by our email provider for spam
    public void updateUserCreatedAtTimeAndNewUserCanBeCreatedAtTime() {
        // assigning the userCreatedAtTime variable the current time
        this.userCreatedAtTime = Date.from(Instant.now());
        // generating a random number between 5(inclusive) to 11(exclusive) so that we
        // may get the number from 5-10 which we assign to the randomMinutes variable
        int randomMinutes = ThreadLocalRandom.current().nextInt(5, 11);
        // generating a random number between 0(inclusive) to 60(exclusive) so that we
        // may get the number from 0-59 which we assign to the randomSeconds variable
        int randomSeconds = ThreadLocalRandom.current().nextInt(0, 60);
        // we are getting the minutes from integer variable
        Duration minutesToAdd = Duration.ofMinutes(randomMinutes);
        // we are adding our newly generated minutes and seconds and assigning the newly
        // created date object to the newUserCanBeCreatedAtTime so that when we are
        // creating the next new user we can check this time and decide when the new
        // user can be created so that when we are sending the email when we create a
        // next new user we don't banned by our email service provider for spam
        this.newUserCanBeCreatedAtTime = Date.from(Instant.now().plus(minutesToAdd).plusSeconds(randomSeconds));
    }

}
