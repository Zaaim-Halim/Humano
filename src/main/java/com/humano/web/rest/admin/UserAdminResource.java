package com.humano.web.rest.admin;

import com.humano.config.Constants;
import com.humano.domain.shared.User;
import com.humano.dto.admin.requests.CreateUserRequest;
import com.humano.dto.admin.requests.UpdateUserRequest;
import com.humano.dto.admin.responses.UserResponse;
import com.humano.repository.shared.UserRepository;
import com.humano.security.annotation.RequireAdmin;
import com.humano.service.MailService;
import com.humano.service.admin.UserAccountService;
import com.humano.web.rest.errors.BadRequestAlertException;
import com.humano.web.rest.errors.EmailAlreadyUsedException;
import com.humano.web.rest.errors.LoginAlreadyUsedException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * Admin-only user management — create, update, list, fetch, delete users.
 * Successor to the JHipster {@code UserResource}; mounted at the same path
 * ({@code /api/admin/users}) so existing admin tooling keeps working.
 */
@RestController
@RequestMapping("/api/admin")
public class UserAdminResource {

    private static final Logger LOG = LoggerFactory.getLogger(UserAdminResource.class);

    private static final List<String> ALLOWED_ORDERED_PROPERTIES = Collections.unmodifiableList(
        Arrays.asList(
            "id",
            "login",
            "firstName",
            "lastName",
            "email",
            "activated",
            "langKey",
            "createdBy",
            "createdDate",
            "lastModifiedBy",
            "lastModifiedDate"
        )
    );

    private final UserAccountService userAccountService;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public UserAdminResource(UserAccountService userAccountService, UserRepository userRepository, MailService mailService) {
        this.userAccountService = userAccountService;
        this.userRepository = userRepository;
        this.mailService = mailService;
    }

    @PostMapping("/users")
    @RequireAdmin
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) throws URISyntaxException {
        LOG.debug("REST request to save User: {}", request.login());
        if (userRepository.findOneByLogin(request.login().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        }
        if (userRepository.findOneByEmailIgnoreCase(request.email()).isPresent()) {
            throw new EmailAlreadyUsedException();
        }
        User newUser = userAccountService.createUser(request);
        mailService.sendCreationEmail(newUser);
        return ResponseEntity.created(new URI("/api/admin/users/" + newUser.getLogin()))
            .headers(HeaderUtil.createAlert(applicationName, "userManagement.created", newUser.getLogin()))
            .body(UserResponse.fromUser(newUser));
    }

    @PutMapping({ "/users", "/users/{login}" })
    @RequireAdmin
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable(name = "login", required = false) @Pattern(regexp = Constants.LOGIN_REGEX) String login,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        LOG.debug("REST request to update User: {}", request.login());
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(request.email());
        if (existingUser.isPresent() && !existingUser.orElseThrow().getId().equals(request.id())) {
            throw new EmailAlreadyUsedException();
        }
        existingUser = userRepository.findOneByLogin(request.login().toLowerCase());
        if (existingUser.isPresent() && !existingUser.orElseThrow().getId().equals(request.id())) {
            throw new LoginAlreadyUsedException();
        }
        Optional<UserResponse> updatedUser = userAccountService.updateUser(request).map(UserResponse::fromUser);
        return ResponseUtil.wrapOrNotFound(updatedUser, HeaderUtil.createAlert(applicationName, "userManagement.updated", request.login()));
    }

    @GetMapping("/users")
    @RequireAdmin
    public ResponseEntity<List<UserResponse>> getAllUsers(@org.springdoc.core.annotations.ParameterObject Pageable pageable) {
        LOG.debug("REST request to get all Users (admin)");
        if (!onlyContainsAllowedProperties(pageable)) {
            return ResponseEntity.badRequest().build();
        }
        Page<UserResponse> page = userAccountService.getAllManagedUsers(pageable).map(UserResponse::fromUser);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/users/{login}")
    @RequireAdmin
    public ResponseEntity<UserResponse> getUser(@PathVariable("login") @Pattern(regexp = Constants.LOGIN_REGEX) String login) {
        LOG.debug("REST request to get User: {}", login);
        return ResponseUtil.wrapOrNotFound(userAccountService.getUserWithAuthoritiesByLogin(login).map(UserResponse::fromUser));
    }

    @DeleteMapping("/users/{login}")
    @RequireAdmin
    public ResponseEntity<Void> deleteUser(@PathVariable("login") @Pattern(regexp = Constants.LOGIN_REGEX) String login) {
        LOG.debug("REST request to delete User: {}", login);
        userAccountService.deleteUser(login);
        return ResponseEntity.noContent().headers(HeaderUtil.createAlert(applicationName, "userManagement.deleted", login)).build();
    }

    @GetMapping("/users/authorities")
    @RequireAdmin
    public List<String> getAuthorities() {
        return userAccountService.getAuthorities();
    }

    private boolean onlyContainsAllowedProperties(Pageable pageable) {
        return pageable.getSort().stream().map(Sort.Order::getProperty).allMatch(ALLOWED_ORDERED_PROPERTIES::contains);
    }
}
