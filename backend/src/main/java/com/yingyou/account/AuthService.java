package com.yingyou.account;

import com.yingyou.shared.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;

    private final LearnerRepository learnerRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final AccountSessionRepository accountSessionRepository;
    private final MailOutboxRepository mailOutboxRepository;
    private final GuestRecordMigrationService migrationService;
    private final PasswordEncoder passwordEncoder;
    private final RequestThrottle throttle;
    private final JdbcClient jdbcClient;
    private final SecureRandom secureRandom = new SecureRandom();

    private final boolean exposeDevTokens;

    public AuthService(LearnerRepository learnerRepository,
                       UserAccountRepository userAccountRepository,
                       AccountTokenRepository accountTokenRepository,
                       AccountSessionRepository accountSessionRepository,
                       MailOutboxRepository mailOutboxRepository,
                       GuestRecordMigrationService migrationService,
                       PasswordEncoder passwordEncoder,
                       RequestThrottle throttle,
                       JdbcClient jdbcClient,
                       @Value("${app.auth.expose-dev-tokens:false}") boolean exposeDevTokens) {
        this.learnerRepository = learnerRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountTokenRepository = accountTokenRepository;
        this.accountSessionRepository = accountSessionRepository;
        this.mailOutboxRepository = mailOutboxRepository;
        this.migrationService = migrationService;
        this.passwordEncoder = passwordEncoder;
        this.throttle = throttle;
        this.jdbcClient = jdbcClient;
        this.exposeDevTokens = exposeDevTokens;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request, String ip) {
        String email = normalizeEmail(request.email());
        throttle.check("register:ip:" + ip, 10, HOUR);
        throttle.check("register:email:" + email, 5, HOUR);

        if (userAccountRepository.existsByEmailNormalized(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_IN_USE", "该邮箱已注册");
        }

        Instant now = Instant.now();
        UUID learnerId = UUID.randomUUID();
        Learner learner = new Learner(learnerId, LearnerKind.ACCOUNT, now);
        learnerRepository.saveAndFlush(learner);

        UserAccount account = new UserAccount(learnerId, email, request.email().trim(),
                passwordEncoder.encode(request.password()), request.nickname().trim(),
                request.termsVersion(), now);
        userAccountRepository.save(account);

        String token = issueToken(learnerId, AccountTokenPurpose.VERIFY_EMAIL, VERIFICATION_TTL, now);
        String body = "欢迎使用英语整句打字训练。请在 24 小时内打开链接完成邮箱验证：\n"
                + "/verify-email?token=" + token;
        sendMail(email, "验证你的邮箱", body, now);

        return new RegisterResponse(learnerId, true, exposeDevTokens ? token : null);
    }

    @Transactional
    public void resendVerification(VerificationRequest request, String ip) {
        String email = normalizeEmail(request.email());
        throttle.check("resend:ip:" + ip, 20, HOUR);
        throttle.check("resend:email:" + email, 5, HOUR);

        userAccountRepository.findByEmailNormalized(email)
                .filter(account -> !account.isEmailVerified())
                .ifPresent(account -> {
                    Instant now = Instant.now();
                    String token = issueToken(account.getLearnerId(), AccountTokenPurpose.VERIFY_EMAIL,
                            VERIFICATION_TTL, now);
                    sendMail(email, "验证你的邮箱",
                            "/verify-email?token=" + token, now);
                });
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request, String ip) {
        throttle.check("verify:ip:" + ip, 30, 10 * MINUTE);
        AccountToken token = requireToken(request.token(), AccountTokenPurpose.VERIFY_EMAIL);
        Instant now = Instant.now();
        if (token.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_USED", "该验证链接已使用");
        }
        if (token.isExpired(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "验证链接已过期，请重新发送");
        }

        UserAccount account = userAccountRepository.findById(token.getLearnerId())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账号不存在"));
        account.markEmailVerified(now);
        token.markUsed(now);
    }

    @Transactional
    public LoginOutcome login(LoginRequest request, UUID guestLearnerId, String ip) {
        String email = normalizeEmail(request.email());
        throttle.check("login:ip:" + ip, 30, 15 * MINUTE);
        throttle.check("login:email:" + email + ":" + ip, 10, 15 * MINUTE);

        UserAccount account = userAccountRepository.findByEmailNormalized(email)
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!account.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_VERIFIED", "请先完成邮箱验证");
        }

        Learner learner = learnerRepository.findById(account.getLearnerId())
                .filter(found -> found.getStatus() == LearnerStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE", "账号当前不可用"));

        boolean migrated = migrationService.migrate(guestLearnerId, learner.getId());
        return new LoginOutcome(learner.getId(), account.getNickname(), account.getEmailDisplay(),
                account.getEmailVerifiedAt() != null, migrated);
    }

    @Transactional
    public PasswordResetRequestResponse requestPasswordReset(PasswordResetRequest request, String ip) {
        String email = normalizeEmail(request.email());
        throttle.check("reset-request:ip:" + ip, 10, HOUR);
        throttle.check("reset-request:email:" + email, 3, HOUR);

        Optional<UserAccount> account = userAccountRepository.findByEmailNormalized(email);
        String devToken = null;
        if (account.isPresent()) {
            Instant now = Instant.now();
            String token = issueToken(account.get().getLearnerId(), AccountTokenPurpose.PASSWORD_RESET,
                    RESET_TTL, now);
            sendMail(email, "重置密码",
                    "请在 1 小时内打开链接重置密码：\n/reset-password?token=" + token, now);
            devToken = exposeDevTokens ? token : null;
        }
        return new PasswordResetRequestResponse(true, devToken);
    }

    @Transactional
    public void resetPassword(PasswordResetSubmitRequest request, String ip) {
        throttle.check("reset-submit:ip:" + ip, 10, HOUR);
        AccountToken token = requireToken(request.token(), AccountTokenPurpose.PASSWORD_RESET);
        Instant now = Instant.now();
        if (token.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_USED", "该重置链接已使用");
        }
        if (token.isExpired(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", "重置链接已过期，请重新申请");
        }

        UserAccount account = userAccountRepository.findById(token.getLearnerId())
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账号不存在"));
        account.changePasswordHash(passwordEncoder.encode(request.newPassword()), now);
        token.markUsed(now);
        revokeAccountSessions(account.getLearnerId());
    }

    @Transactional(readOnly = true)
    public MeResponse profile(UUID learnerId) {
        Learner learner = learnerRepository.findById(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习身份"));
        if (learner.getKind() == LearnerKind.GUEST) {
            return new MeResponse("GUEST", learnerId, null, null, null, null);
        }
        UserAccount account = userAccountRepository.findById(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号资料不存在"));
        return new MeResponse("ACCOUNT", learnerId, account.getNickname(), account.getEmailDisplay(),
                account.isEmailVerified(), account.getCreatedAt());
    }

    @Transactional
    public MeResponse updateProfile(UUID learnerId, UpdateProfileRequest request) {
        Learner learner = learnerRepository.findById(learnerId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习身份"));
        if (learner.getKind() != LearnerKind.ACCOUNT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_REQUIRED", "访客不能修改账号资料");
        }
        UserAccount account = userAccountRepository.findById(learnerId)
                .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "账号不存在"));
        account.changeNickname(request.nickname().trim(), Instant.now());
        return new MeResponse("ACCOUNT", learnerId, account.getNickname(), account.getEmailDisplay(),
                account.isEmailVerified(), account.getCreatedAt());
    }

    @Transactional
    public void registerAccountSession(String sessionId, UUID learnerId) {
        accountSessionRepository.save(new AccountSession(sessionId, learnerId, Instant.now()));
    }

    @Transactional
    public void removeAccountSession(String sessionId) {
        accountSessionRepository.deleteById(sessionId);
    }

    private void revokeAccountSessions(UUID learnerId) {
        for (AccountSession session : accountSessionRepository.findByLearnerId(learnerId)) {
            jdbcClient.sql("delete from spring_session where session_id = :sessionId")
                    .param("sessionId", session.getSessionId())
                    .update();
        }
        accountSessionRepository.deleteByLearnerId(learnerId);
    }

    private AccountToken requireToken(String rawToken, AccountTokenPurpose purpose) {
        String hash = sha256(rawToken.trim());
        return accountTokenRepository.findByTokenHashAndPurpose(hash, purpose)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN",
                        "链接无效或已失效"));
    }

    private String issueToken(UUID learnerId, AccountTokenPurpose purpose, Duration ttl, Instant now) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        accountTokenRepository.save(new AccountToken(UUID.randomUUID(), learnerId, purpose,
                sha256(raw), now.plus(ttl), now));
        return raw;
    }

    private void sendMail(String recipient, String subject, String body, Instant now) {
        mailOutboxRepository.save(new MailOutboxMessage(UUID.randomUUID(), recipient, subject, body, now));
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码不正确");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record LoginOutcome(
            UUID learnerId,
            String nickname,
            String email,
            boolean emailVerified,
            boolean guestRecordsMigrated
    ) {
    }
}
