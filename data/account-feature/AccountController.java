package com.dudububu.store;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/account")
class AccountController {
    private static final String ACCOUNT_ID = "storeAccountId";
    // Cross-origin requests need CORS permission to send this header.
    private static final String SAME_ORIGIN_HEADER = "X-Requested-With=XMLHttpRequest";
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private final StoreAccountRepository accounts;

    AccountController(StoreAccountRepository accounts) {
        this.accounts = accounts;
    }

    @ModelAttribute
    void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
    }

    @GetMapping
    Profile profile(HttpServletRequest request) {
        return profileOf(currentAccount(request));
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, headers = SAME_ORIGIN_HEADER)
    @ResponseStatus(HttpStatus.CREATED)
    Profile register(@RequestBody Registration registration, HttpServletRequest request) {
        String email = normalizeEmail(registration.email());
        String name = registration.name() == null ? "" : registration.name().strip();
        String password = registration.password();
        if (name.isEmpty() || name.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a name of 1 to 80 characters.");
        }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password of 8 to 128 characters.");
        }
        if (accounts.findByEmail(email).isPresent()) {
            throw duplicateEmail();
        }
        StoreAccount account;
        try {
            account = accounts.saveAndFlush(new StoreAccount(email, name, StorePasswords.hash(password)));
        } catch (DataIntegrityViolationException error) {
            throw duplicateEmail();
        }
        startSession(request, account.getId());
        return profileOf(account);
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE, headers = SAME_ORIGIN_HEADER)
    Profile login(@RequestBody Credentials credentials, HttpServletRequest request) {
        String email = normalizeEmail(credentials.email());
        String password = credentials.password();
        if (email.length() > 254 || password == null || password.length() > 128) {
            throw invalidCredentials();
        }
        StoreAccount account = accounts.findByEmail(email).orElse(null);
        if (!StorePasswords.matches(password, account == null ? null : account.getPasswordHash())) {
            throw invalidCredentials();
        }
        startSession(request, account.getId());
        return profileOf(account);
    }

    @PostMapping(path = "/logout", headers = SAME_ORIGIN_HEADER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @PutMapping(path = "/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, headers = SAME_ORIGIN_HEADER)
    Profile uploadPicture(@RequestParam("picture") MultipartFile picture, HttpServletRequest request) {
        StoreAccount account = currentAccount(request);
        account.setPicture(ProfilePictures.thumbnail(picture));
        return profileOf(accounts.save(account));
    }

    @DeleteMapping(path = "/picture", headers = SAME_ORIGIN_HEADER)
    Profile removePicture(HttpServletRequest request) {
        StoreAccount account = currentAccount(request);
        account.setPicture(null);
        return profileOf(accounts.save(account));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> accountError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).cacheControl(CacheControl.noStore())
                .body(Map.of("error", error.getReason()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> malformedAccountRequest() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("error", "Enter your account details and try again."));
    }

    private StoreAccount currentAccount(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute(ACCOUNT_ID) instanceof UUID accountId) {
            return accounts.findById(accountId).orElseThrow(AccountController::signInRequired);
        }
        throw signInRequired();
    }

    private static void startSession(HttpServletRequest request, UUID accountId) {
        var existing = request.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        request.getSession(true).setAttribute(ACCOUNT_ID, accountId);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    private static Profile profileOf(StoreAccount account) {
        byte[] picture = account.getPicture();
        String pictureUrl = picture == null ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(picture);
        return new Profile(account.getDisplayName(), account.getEmail(), pictureUrl);
    }

    private static ResponseStatusException duplicateEmail() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses this email. Sign in instead.");
    }

    private static ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
    }

    private static ResponseStatusException signInRequired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to access your profile.");
    }

    record Profile(String name, String email, String pictureUrl) {}
    record Credentials(String email, String password) {}
    record Registration(String name, String email, String password) {}
}
