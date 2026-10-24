package com.reconcilex.service;

import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.dto.CreateUserRequest;
import com.reconcilex.dto.LoginRequest;
import com.reconcilex.dto.LoginResponse;
import com.reconcilex.dto.UserResponse;
import com.reconcilex.entity.AppUser;
import com.reconcilex.exception.ConflictException;
import com.reconcilex.repository.AppUserRepository;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Timing-attack dummy hash.
     *
     * <p>Generated once at startup by encoding a random UUID with the application's own
     * PasswordEncoder. This guarantees the dummy check uses the same cost factor as real
     * password hashes without embedding a known, hardcoded literal in the source code.
     * Cost factor: BCryptPasswordEncoder default (strength 10, ~100 ms per check on modern hardware).
     */
    private String dummyHash;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Eagerly generates the timing-attack dummy hash at application startup.
     * Runs once; the result is kept in memory for the lifetime of the application.
     */
    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        log.debug("Timing-attack dummy hash initialised (cost=10, length={})", dummyHash.length());
    }

    /**
     * Authenticates a user and issues a signed JWT.
     * Hardened against timing attacks: both the unknown-user and wrong-password paths
     * execute a real BCrypt comparison so response times are indistinguishable.
     * Never logs raw passwords or issued tokens.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Optional<AppUser> userOpt = userRepository.findByUsername(request.getUsername());

        if (userOpt.isEmpty() || !userOpt.get().isEnabled()) {
            // Real BCrypt check against the startup-generated dummy hash;
            // result is intentionally discarded — its sole purpose is to consume CPU time.
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new BadCredentialsException("Invalid username or password");
        }

        AppUser user = userOpt.get();
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        String token = jwtTokenService.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, JwtTokenService.EXPIRY_SECONDS, "Bearer");
    }

    /**
     * Creates a new user (ADMIN only).
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Username '" + request.getUsername() + "' already exists");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        AppUser newUser = new AppUser(request.getUsername(), passwordHash, request.getRole());
        AppUser saved = userRepository.save(newUser);

        log.info("New user created successfully: username={}, role={}", saved.getUsername(), saved.getRole());
        return UserResponse.fromEntity(saved);
    }
}
