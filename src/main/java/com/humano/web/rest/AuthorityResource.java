package com.humano.web.rest;

import com.humano.domain.shared.Authority;
import com.humano.repository.shared.AuthorityRepository;
import com.humano.security.AuthorityPermissionService;
import com.humano.security.annotation.RequireAdmin;
import com.humano.service.admin.UserAccountService;
import com.humano.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link Authority}.
 */
@RestController
@RequestMapping("/api/authorities")
@Transactional
public class AuthorityResource {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorityResource.class);

    private static final String ENTITY_NAME = "adminAuthority";
    private final AuthorityRepository authorityRepository;
    private final AuthorityPermissionService authorityPermissionService;
    private final UserAccountService userAccountService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public AuthorityResource(
        AuthorityRepository authorityRepository,
        AuthorityPermissionService authorityPermissionService,
        UserAccountService userAccountService
    ) {
        this.authorityRepository = authorityRepository;
        this.authorityPermissionService = authorityPermissionService;
        this.userAccountService = userAccountService;
    }

    /**
     * {@code POST  /authorities} : Create a new authority.
     *
     * @param authority the authority to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with body the new authority, or with status {@code 400 (Bad Request)} if the authority has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    @RequireAdmin
    public ResponseEntity<Authority> createAuthority(@Valid @RequestBody Authority authority) throws URISyntaxException {
        LOG.debug("REST request to save Authority : {}", authority);
        if (authorityRepository.existsById(authority.getName())) {
            throw new BadRequestAlertException("authority already exists", ENTITY_NAME, "idexists");
        }
        authority = authorityRepository.save(authority);
        return ResponseEntity.created(new URI("/api/authorities/" + authority.getName()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, authority.getName()))
            .body(authority);
    }

    /**
     * {@code GET  /authorities} : get all the authorities.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of authorities in body.
     */
    @GetMapping("")
    @RequireAdmin
    public List<Authority> getAllAuthorities() {
        LOG.debug("REST request to get all Authorities");
        return authorityRepository.findAll();
    }

    /** One role and the permissions it grants (for the roles &amp; permissions matrix). */
    public record RolePermissions(String name, java.util.List<String> permissions, long userCount) {}

    /**
     * {@code GET  /authorities/overview} : every role with the permissions it grants, sorted —
     * read-only, for the settings screen's roles &amp; permissions view.
     */
    @GetMapping("/overview")
    @RequireAdmin
    public java.util.List<RolePermissions> getOverview() {
        java.util.Map<String, Long> holders = userAccountService.countUsersByRole();
        return authorityRepository
            .findAll()
            .stream()
            .map(a ->
                new RolePermissions(
                    a.getName(),
                    authorityPermissionService.getPermissionsForAuthority(a.getName()).stream().sorted().toList(),
                    holders.getOrDefault(a.getName(), 0L)
                )
            )
            .sorted(java.util.Comparator.comparing(RolePermissions::name))
            .toList();
    }

    /**
     * {@code GET  /authorities/:id} : get the "id" authority.
     *
     * @param id the id of the authority to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the authority, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    @RequireAdmin
    public ResponseEntity<Authority> getAuthority(@PathVariable("id") String id) {
        LOG.debug("REST request to get Authority : {}", id);
        Optional<Authority> authority = authorityRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(authority);
    }

    /**
     * {@code DELETE  /authorities/:id} : delete the "id" authority.
     *
     * @param id the id of the authority to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @RequireAdmin
    public ResponseEntity<Void> deleteAuthority(@PathVariable("id") String id) {
        LOG.debug("REST request to delete Authority : {}", id);
        authorityRepository.deleteById(id);
        return ResponseEntity.noContent().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id)).build();
    }
}
