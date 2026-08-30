package com.pavansingerreddy.note.services;

import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.pavansingerreddy.note.dto.UserDto;
import com.pavansingerreddy.note.entity.PasswordResetToken;
import com.pavansingerreddy.note.entity.Role;
import com.pavansingerreddy.note.entity.Users;
import com.pavansingerreddy.note.entity.VerificationToken;
import com.pavansingerreddy.note.exception.InvalidUserDetailsException;
import com.pavansingerreddy.note.exception.PasswordDoesNotMatchException;
import com.pavansingerreddy.note.exception.UserAlreadyExistsException;
import com.pavansingerreddy.note.exception.UserNotFoundException;
import com.pavansingerreddy.note.model.ChangePasswordModel;
import com.pavansingerreddy.note.model.NormalUserModel;
import com.pavansingerreddy.note.model.ResetPasswordModel;
import com.pavansingerreddy.note.model.UserModel;
import com.pavansingerreddy.note.repository.PasswordResetTokenRepository;
import com.pavansingerreddy.note.repository.UserRepository;
import com.pavansingerreddy.note.repository.VerificationTokenRepository;
import com.pavansingerreddy.note.utils.DTOConversionUtil;
import com.pavansingerreddy.note.utils.JWTUtil;

import jakarta.validation.Valid;

@Service
public class UserServiceImplementation implements UserService {
    // Value annotation is used to inject the value of the property
    // "verification.Token.expiry.seconds" from our application.yml into the field
    // "TokenExpireTimeInSeconds".
    @Value("${verification.Token.expiry.seconds}")
    private Long TokenExpireTimeInSeconds;

    // autowiring our JWTUtil so that the bean or instance of JWTUtil will be
    // injected here by the spring IOC container.so that we can perform operations
    // related to Jwt like creating them validating them etc...
    @Autowired
    private JWTUtil jwtUtil;
    // autowiring our AuthenticationManager so that the bean or instance of
    // AuthenticationManager will be injected here by the spring IOC
    // container.AuthenticationManager is used for authenticating the user it
    // contains different authentication provider's in it for authentication.
    @Autowired
    private AuthenticationManager authenticationManager;
    // making the UserRepository as final so it can be assigned a value only once so
    // any other accidental assigning of other objects does not happen to it.
    private final UserRepository userRepository;

    // making the constructor for UserServiceImplementation class and here the
    // spring injects our UserRepository object or bean in the constructor by using
    // dependency injection in our parameter automatically so that we can use that
    // object later. Instead of all these we can also use autowired annotation
    UserServiceImplementation(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // autowiring our VerificationTokenRepository so that the bean or instance of
    // VerificationTokenRepository will be injected here by the spring IOC container
    @Autowired
    private VerificationTokenRepository verificationTokenRepository;
    // autowiring our PasswordResetTokenRepository so that the bean or instance of
    // PasswordResetTokenRepository will be injected here by the spring IOC
    // container
    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    // autowiring our PasswordEncoder so that the bean or instance of
    // PasswordEncoder will be injected here by the spring IOC container
    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    // This method is used to create user when the user get's registered or signs up
    public Users createUser(UserModel userModel, int mailNoToUse)
            throws UserAlreadyExistsException, PasswordDoesNotMatchException {

        // findByEmail method of user repository gives us the user object from the
        // user's email.findByEmail method returns optional User because it is not null
        // and we can check if the use is present or not easily
        Optional<Users> userOptional = userRepository.findByEmail(userModel.getEmail());

        // checks if the password and retyped password matches and also checks if the
        // user is not already present in the database
        if (userModel.ValidatePasswordAndRetypedPassword()) {

            // checking if the user is already present and the user is already enabled then
            // we throw the UserAlreadyExistsException
            if (userOptional.isPresent() && userOptional.get().isEnabled()) {
                throw new UserAlreadyExistsException("User already exists in the database");
            }
            // else if user already present and is not enabled then we delete the user so
            // that we can create a new user below with the new details.here we are deleting
            // the old user if he is not enabled and creating a new one with new details or
            // else we can also update the existing user with new details but here we
            // deleted the old user and created a new user with new details
            else if (userOptional.isPresent() && !userOptional.get().isEnabled()) {
                userRepository.delete(userOptional.get());
            }
            // creating the new User object
            Users user = new Users();
            // copying the details of the user from the user model to the user using the in
            // Built BeanUtils class and using it's copyProperties
            BeanUtils.copyProperties(userModel, user);
            // creating a new role object
            Role role = new Role();
            // setting the role name to the "ROLE_USER".Here we are prefixing our role with
            // "ROLE_" because we can use @RolesAllowed("USER") directly in our controller
            // methods.If we use "USER" here then we have to use @RolesAllowed("ROLE_USER")
            // in the controller.These roles can be used for protecting our api's so that
            // only authorized users who have permission to access our resource can access
            // it
            role.setName("ROLE_USER");
            // This line creates a new HashSet of Role objects. A HashSet is a collection
            // that does not allow duplicate elements, and a Role is a custom class that
            // represents a user’s role in the system (like “ROLE_ADMIN”, “ROLE_USER”,
            // etc.).
            Set<Role> roles = new HashSet<>();
            // adding our role to the hash set.
            roles.add(role);
            // setting our roles for the user
            user.setRoles(roles);
            // setting our password for the new user by making our already existing password
            // and encoding it to an unreadable value so that even though the database is
            // compromised user's password should not be leaked
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            // This method assigns the current time to the userCreatedAtTime variable of the
            // user object and then assigns a random time which is between 5 to 10 minutes
            // ahead of the current time to the newUserCanBeCreatedAtTime variable of the
            // user object so that when we create a next new user we can check for this time
            // and send email based on this time for verifying the new user we don't get
            // blocked by our email provider for spam
            user.updateUserCreatedAtTimeAndNewUserCanBeCreatedAtTime();
            // setting the mailNoToUseForSendingEmail of the user which represents which
            // mail we have used from our mail providers list for sending the verification
            // email for this user
            user.setMailNoToUseForSendingEmail(mailNoToUse);
            // saving our new user to the database using our userRepository
            userRepository.save(user);
            // after saving our user in the database we are returning our new user object
            return user;
        }

        // if the password and retyped password does not match then we throw an
        // exception
        throw new PasswordDoesNotMatchException("Passwords do not match");

    }

    @Override
    @Cacheable(value = "users", key = "#userEmail")
    // This method is used to get the user details by his/her email address
    public Users getUserDetailsByEmail(String userEmail) throws UserNotFoundException {
        // it get's the optional user from the userRepository we want Optional user
        // because it is easy to check if the user is present or not and it does not
        // contains null
        Optional<Users> userOptional = userRepository.findByEmail(userEmail);

        // if the user is present then we get the user and return the user
        if (userOptional.isPresent()) {
            Users user = userOptional.get();
            return user;
        }
        // if the user is not present then we throw the UserNotFoundException
        throw new UserNotFoundException("User Does not exists");

    }

    @Override
    @CacheEvict(value = "users", key = "#userEmail")
    // This method updates the information of a user identified by their email. It
    // takes the user's email and a NormalUserModel object containing the new user
    // information as parameters. It returns a UserDto object representing the
    // updated user. If the user is not found, it throws a UserNotFoundException.
    public UserDto updateUserInformationByEmail(String userEmail, NormalUserModel normalUserModel)
            throws UserNotFoundException {
        // Call a method in userRepository to find the User object associated with the
        // email. The method returns an Optional, which can either contain the User
        // object (if found) or be empty (if not found).
        Optional<Users> userOptional = userRepository.findByEmail(userEmail);
        // Check if the Optional contains a User object.
        if (userOptional.isPresent()) {
            // Get the User object from the Optional.
            Users user = userOptional.get();
            // If the NormalUserModel object contains a non-empty email, update the user's
            // email.
            if (normalUserModel.getEmail() != null && !normalUserModel.getEmail().isEmpty()) {
                user.setEmail(normalUserModel.getEmail());
            }
            // If the NormalUserModel object contains a non-empty username, update the
            // user's username.
            if (normalUserModel.getUsername() != null && !normalUserModel.getUsername().isEmpty()) {
                user.setUsername(normalUserModel.getUsername());
            }
            // If the NormalUserModel object contains a non-empty password, update the
            // user's password by encoding it so that plaintext is never stored.
            if (normalUserModel.getPassword() != null && !normalUserModel.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(normalUserModel.getPassword()));
            }
            // Save the updated User object to the database.
            userRepository.save(user);
            // Convert the User object to a UserDto object and return it.
            return DTOConversionUtil.userToUserDTO(user);

        }
        // If the Optional does not contain a User object (i.e., the user was not
        // found), throw a UserNotFoundException.
        throw new UserNotFoundException("User Does not exists");
    }

    @Override
    @CacheEvict(value = "users", key = "#userEmail")
    // This method deletes a user identified by their email. It takes the user's
    // email as a parameter. It returns a UserDto object representing the deleted
    // user. If the user is not found, it throws a UserNotFoundException.
    public UserDto deleteUserByEmail(String userEmail) throws UserNotFoundException {

        // Call a method in userRepository to find the User object associated with the
        // email.The method returns an Optional, which can either contain the User
        // object (if found) or be empty (if not found).
        Optional<Users> userOptional = userRepository.findByEmail(userEmail);
        // Check if the Optional contains a User object.
        if (userOptional.isPresent()) {
            // Get the User object from the Optional.
            Users user = userOptional.get();
            // Call a method in userRepository to delete the User object associated with the
            // email.
            userRepository.deleteByEmail(userEmail);
            // Convert the User object to a UserDto object and return it.
            return DTOConversionUtil.userToUserDTO(user);
        }
        // If the Optional does not contain a User object (i.e., the user was not
        // found), throw a UserNotFoundException.
        throw new UserNotFoundException("User Does not exists");
    }

    @Override
    // This method is used to login user using the user's email and password
    public Map<String, String> loginUser(NormalUserModel normalUserModel) {

        // creating a username password authentication token using the user's email and
        // user's password.here we are using only two parameter constructor of
        // UsernamePasswordAuthenticationToken because if we use the three parameter
        // constructor then the authenticated will become true
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                normalUserModel.getEmail(), normalUserModel.getPassword());

        // we are using the authentication manager and passing our
        // usernamePasswordAuthenticationToken with our user details
        Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);

        // if the authentication is successful then we generate the jwt token using our
        // authentication object
        String jwt = jwtUtil.generateJwt(authentication);
        // creating a new hashMap which contains the key value pairs
        Map<String, String> jwtToken = new HashMap<>();
        // we are keeping our generated jwt value with the key "jwt" in the jwtToken
        // hash map
        jwtToken.put("jwt", jwt);
        // After that we are returning the jwt token
        return jwtToken;
    }

    @Override
    // This method takes the random unique UUID as a verification token and the User
    // object and links the verification token to a particular user
    public void saveVerificationTokenForUser(String token, Users user) {

        // creating a new verification token object the token string ,User object and
        // the Token expire time in seconds which we get from the application.yml file
        VerificationToken verificationToken = new VerificationToken(user, token, TokenExpireTimeInSeconds);
        // saving the verification token using the verification token repository
        verificationTokenRepository.save(verificationToken);

    }

    @Override
    // This method is used to validate the verification token sent by the user and
    // after the verification we are enabling the user
    public boolean validateVerificationToken(String token) {

        // getting the verification token by it's value from the verification token
        // repository
        VerificationToken verificationToken = verificationTokenRepository.findByToken(token);
        // if the verification token is null then we return false which indicates that
        // the verification token is not valid as the verification token is not present
        if (verificationToken == null) {
            return false;
        }

        // we are getting the User from the Verification token
        Users user = verificationToken.getUser();
        // we are getting the calendar instance from the Calendar.getInstance() method
        Calendar cal = Calendar.getInstance();

        // if the verification token time is less than the current time then the token
        // is expired so we delete the token from the database and return false
        if ((verificationToken.getExpirationTime().getTime() - cal.getTime().getTime()) <= 0) {
            // as we have bidirectional one to one relationship between user and
            // verification token we should remove the reference of the verification token
            // from user object and then delete the verification instead of directly
            // deleting the verification token because if we directly delete the
            // verification token as we have bidirectional one to one relationship with user
            // the
            // verification token still exists in the database

            user.setVerificationToken(null);
            // deleting the verification token after removing the reference of the
            // verification token from the User object
            verificationTokenRepository.delete(verificationToken);
            // we are returning false as the token got expired so it is not a valid token
            return false;
        }

        // enabling the user as the verification is successful so now user can login to
        // his account
        user.setEnabled(true);
        // saving the user with his modified details in to the database
        userRepository.save(user);
        // as we have bidirectional one to one relationship between user and
        // verification token we should remove the reference of the verification token
        // from user object and then delete the verification instead of directly
        // deleting the verification token because if we directly delete the
        // verification token as we have bidirectional one to one relationship with user
        // the
        // verification token still exists in the database
        user.setVerificationToken(null);
        // removing the verification token from the database
        verificationTokenRepository.delete(verificationToken);
        // returning true as the token is valid and the user is enabled
        return true;

    }

    @Override
    // deleting the previous verification token if it exists it takes the user
    // object as it's parameter
    public void deletePreviousTokenIfExists(Users user) {

        // getting the optional verification token based on the User object. we want the
        // optional verification token because it is easy to check if the verification
        // token exists or not and also it does not have null values in it
        Optional<VerificationToken> verificationToken = verificationTokenRepository.findByUser(user);

        // checking if the verification token is present if the verification token is
        // present then we are deleting the verification token
        if (verificationToken.isPresent()) {
            // as we have bidirectional one to one relationship between user and
            // verification token we should remove the reference of the verification token
            // from user object and then delete the verification instead of directly
            // deleting the verification token because if we directly delete the
            // verification token as we bidirectional one to one relationship with user the
            // verification token still exists in the database
            user.setVerificationToken(null);
            verificationTokenRepository.delete(verificationToken.get());
        }

    }

    @Override
    // deletes the previous password reset token for a user if the token already
    // exists it takes the user object as a parameter
    public void deletePreviousPasswordResetTokenIfExists(Users user) {

        // getting the optional password reset token based on the User object. we want
        // the optional password reset token because it is easy to check if the
        // password reset token exists or not and also it does not have null values in
        // it
        Optional<PasswordResetToken> passwordToken = passwordResetTokenRepository.findByUser(user);
        // checking if the password reset token is present if the password reset token
        // is present then we are deleting the password reset token
        if (passwordToken.isPresent()) {
            passwordResetTokenRepository.delete(passwordToken.get());
        }
    }

    // The method takes the random UUID token and the user object and saves the
    // password reset token in the database with that user id
    @Override
    public void savePasswordResetToken(String token, Users user) {
        // calling the constructor of the PasswordResetToken entity which set's the user
        // ,token, and Token expiry time in the PasswordResetToken
        PasswordResetToken passwordResetToken = new PasswordResetToken(user, token, TokenExpireTimeInSeconds);
        // after saving the password reset token details in the password reset token
        // object now it saves that object to the database using
        // passwordResetTokenRepository
        passwordResetTokenRepository.save(passwordResetToken);
    }

    @Override
    // This method takes the UUID token which came as a query parameter with the url
    // and checks if the token exists and is not expired and returns the user
    // associated with the token
    public Users validatePasswordResetToken(String token) throws InvalidUserDetailsException {
        // getting the passwordResetToken token from the database
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token);
        // if the token does not exists we are throwing an exception
        if (passwordResetToken == null) {
            throw new InvalidUserDetailsException("Not a valid password reset Token");
        }

        // getting the calendar instance to get the current time
        Calendar cal = Calendar.getInstance();
        // getting the user object to which the password reset token is associated to
        Users user = passwordResetToken.getUser();

        // if the user does not exists then we are throwing exception.This case
        // generally does not occur but we are specifying here just for the safety
        if (user == null) {
            throw new InvalidUserDetailsException("User does not exists");
        }

        // if the passwordResetToken token time is less than the current time then the
        // token is expired so we delete the token from the database and throw an
        // exception
        if ((passwordResetToken.getExpirationTime().getTime() - cal.getTime().getTime()) <= 0) {
            passwordResetTokenRepository.delete(passwordResetToken);
            throw new InvalidUserDetailsException("Password Reset Token is expired request a new one");
        }
        // returning the user associated with the token
        return user;
    }

    // taking the user object and the password model as the input parameters and
    // then checking if the newpassword and retypednewpassword matches and if they
    // both matches then we are setting the new password of the user
    @Override
    @CacheEvict(value = "users", key = "#user.email")
    public String resetPassword(Users user, ResetPasswordModel resetPasswordModel) throws InvalidUserDetailsException {
        // checking if the newpassword and retypednewpassword matches if they match then
        // we save the newpassword to the database else we throw an exception
        if (resetPasswordModel.ValidatePasswordAndRetypedPassword()) {
            // setting the new password of the user using the password encoder which hashes
            // our password so that they remain secure
            user.setPassword(passwordEncoder.encode(resetPasswordModel.getNewpassword()));
            // saving our new user details to the database and returning "password reset
            // successful" string as a response
            userRepository.save(user);
            return "password reset successful";
        }
        // if the newpassword and retypednewpassword does not match then we throw an
        // exception
        throw new InvalidUserDetailsException("new Password and retyped new password does not match");
    }

    // this method is used to change the password of the logged in user it takes the
    // user object and the change password model which is given to us by the user
    // and it contains old password , new password and retyped new password
    @Override
    @CacheEvict(value = "users", key = "#user.email")
    public String changePassword(Users user, @Valid ChangePasswordModel changePasswordModel)
            throws InvalidUserDetailsException {

        // checking if the old password which the user sent and the password in the
        // database match if they doesn't match then we throw an exception
        if (!passwordEncoder.matches(changePasswordModel.getOldpassword(), user.getPassword())) {
            throw new InvalidUserDetailsException("old password does not match please verify and try again");
        }

        // checking if the new password and retyped new password matches if they doesn't
        // match then we throw an exception
        if (!changePasswordModel.ValidatePasswordAndRetypedPassword()) {
            throw new InvalidUserDetailsException("new Password and retyped new password does not match");
        }

        // if all the above conditions satisfy then we save the user to the database by
        // changing his password
        user.setPassword(passwordEncoder.encode(changePasswordModel.getNewpassword()));
        userRepository.save(user);

        // if the password saved successfully then we send a string named "Password
        // changed successfully"
        return "Password changed successfully";

    }

    @Override
    // This method checks for password reset token and deletes the password reset
    // token if it is present
    public void deletePasswordResetToken(String token) throws InvalidUserDetailsException {
        // getting the passwordResetToken token from the database
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token);
        // if the token does not exists we are throwing an exception
        if (passwordResetToken == null) {
            throw new InvalidUserDetailsException("Not a valid password reset Token");
        }
        // deleting the password reset token after verifying it
        passwordResetTokenRepository.delete(passwordResetToken);
    }

    @Override
    // we have multiple mail senders in our application.yml file so this method
    // getLatestEmailToUse() returns us the appropriate mail sender to use to send
    // email so that we don't get marked as spam by our email service provider like
    // gmail or outlook.
    public Integer getLatestEmailToUse() throws InvalidUserDetailsException {

        // This findTheUserWhoContainsTheAppropriateEmailToSendSignUpUrl() gets the
        // latest signed up user who's mailNoToUseForSendingEmail has the lowest
        // newUserCanBeCreatedAtTime
        Optional<Users> userOptional = userRepository.findTheUserWhoContainsTheAppropriateEmailToSendSignUpUrl();
        // if any user is present then we get the user and check if the
        // getNewUserCanBeCreatedAtTime for that user is greater than the current time
        // if it is greater then we throw an InvalidUserDetailsException else we return
        // 1 which indicates to choose the first email provider as we will be having at
        // least one email provider to send emails
        if (userOptional.isPresent()) {
            // getting the user from the optional user
            Users user = userOptional.get();
            // getting the current date
            Date currentDate = Date.from(Instant.now());
            // user.getNewUserCanBeCreatedAtTime().compareTo(currentDate) returns 0 if the
            // dates are equal, a value less than 0 if user.getNewUserCanBeCreatedAtTime()
            // is before currentDate, and a value greater than 0 if
            // user.getNewUserCanBeCreatedAtTime() is after currentDate
            if (user.getNewUserCanBeCreatedAtTime().compareTo(currentDate) > 0) {
                // Calculate difference in milliseconds
                long diffInMilli = Math.abs(user.getNewUserCanBeCreatedAtTime().getTime() - currentDate.getTime());

                // Convert to minutes and seconds
                long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMilli);
                long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(diffInMilli) -
                        TimeUnit.MINUTES.toSeconds(diffInMinutes);

                // throw an Exception with message which indicates when the new user can be
                // created so that our email service provider cannot be suspected for spam
                throw new InvalidUserDetailsException("Email service is busy right now try after " + diffInMinutes
                        + " minutes and " + diffInSeconds + " seconds");
            }
            // if the user.getNewUserCanBeCreatedAtTime() is less than the current time then
            // we can return the user's mailNoToUseForSendingEmail so that our controller
            // can use this and register event with this value and our event listener will
            // choose the email provider and send the email based on this value
            return user.getMailNoToUseForSendingEmail();
        }
        return 1;
    }

}
