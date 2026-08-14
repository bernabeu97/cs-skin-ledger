package com.cs.skinledger.web;

import com.cs.skinledger.domain.Item;
import com.cs.skinledger.domain.Lot;
import com.cs.skinledger.domain.User;
import com.cs.skinledger.repository.AlertRepository;
import com.cs.skinledger.repository.AuditLogRepository;
import com.cs.skinledger.repository.ItemRepository;
import com.cs.skinledger.repository.LotRepository;
import com.cs.skinledger.repository.OtherCostRepository;
import com.cs.skinledger.repository.PriceSnapshotRepository;
import com.cs.skinledger.repository.SettingRepository;
import com.cs.skinledger.repository.TradeRepository;
import com.cs.skinledger.repository.UserRepository;
import com.cs.skinledger.domain.InviteCode;
import com.cs.skinledger.repository.InviteCodeRepository;
import com.cs.skinledger.util.SecurityTokens;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndIsolationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private ItemRepository itemRepository;
    @Autowired private LotRepository lotRepository;
    @Autowired private SettingRepository settingRepository;
    @Autowired private PriceSnapshotRepository snapshotRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private OtherCostRepository otherCostRepository;
    @Autowired private TradeRepository tradeRepository;
    @Autowired private InviteCodeRepository inviteCodeRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        inviteCodeRepository.deleteAll();
        settingRepository.deleteAll();
        snapshotRepository.deleteAll();
        alertRepository.deleteAll();
        otherCostRepository.deleteAll();
        tradeRepository.deleteAll();
        lotRepository.deleteAll();
        itemRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void invitedRegistrationDoesNotClaimLegacyDataAndCreatesSession() throws Exception {
        User legacy = saveUser("local", null);
        Item item = saveItem("Legacy Knife");
        saveLot(legacy, item, "100");
        User admin = saveUser("admin", passwordEncoder.encode("admin-password-123"));
        admin.setRole("ADMIN");
        admin.setTotpEnabled(true);
        userRepository.save(admin);
        createInvite(admin, "INVITE-ALICE");

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("alice", "password1234", "INVITE-ALICE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"))
                .andReturn();

        User alice = userRepository.findByUsername("alice").orElseThrow();
        assertNotEquals(legacy.getId(), alice.getId());
        assertTrue(passwordEncoder.matches("password1234", alice.getPasswordHash()));

        MockHttpSession session = (MockHttpSession) registered.getRequest().getSession(false);
        mockMvc.perform(get("/api/lots").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/lots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accountDataIsIsolated() throws Exception {
        User alice = saveUser("alice", passwordEncoder.encode("password123"));
        saveUser("bob", passwordEncoder.encode("password456"));
        Item item = saveItem("Private Knife");
        Lot aliceLot = saveLot(alice, item, "100");

        mockMvc.perform(get("/api/lots").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/lots/" + aliceLot.getId())
                        .with(user("bob")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "itemName", "Private Knife",
                                "buyPrice", "1",
                                "buyTime", "2026-01-01T10:00:00",
                                "buyPlatform", "uu"))))
                .andExpect(status().isBadRequest());

        assertEquals(0, new BigDecimal("100").compareTo(
                lotRepository.findById(aliceLot.getId()).orElseThrow().getBuyPrice()));
    }

    @Test
    void tokenIsEncryptedMaskedAndScopedToAccount() throws Exception {
        User alice = saveUser("alice", passwordEncoder.encode("password123"));
        saveUser("bob", passwordEncoder.encode("password456"));
        String token = "ABCDEF1234567890";

        mockMvc.perform(put("/api/settings/csqaq-token")
                        .with(user("alice")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", token))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedToken", endsWith("7890")))
                .andExpect(jsonPath("$.maskedToken", not(containsString(token))));

        String stored = settingRepository.findByUserIdAndKey(alice.getId(), "csqaq_token")
                .orElseThrow().getValue();
        assertFalse(stored.contains(token));
        assertNotEquals(token, stored);

        mockMvc.perform(get("/api/settings/csqaq-token").with(user("alice")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedToken", endsWith("7890")))
                .andExpect(jsonPath("$.source").value("account"));

        mockMvc.perform(get("/api/settings/csqaq-token").with(user("bob")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));

        mockMvc.perform(delete("/api/settings/csqaq-token")
                        .with(user("alice")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void registrationRequiresUnusedInvite() throws Exception {
        saveUser("local", null);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("alice", "password1234", "MISSING")))
                .andExpect(status().isBadRequest());

        User admin = saveUser("admin", passwordEncoder.encode("admin-password-123"));
        admin.setRole("ADMIN");
        admin.setTotpEnabled(true);
        userRepository.save(admin);
        createInvite(admin, "ONCE-ONLY");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("alice", "password1234", "ONCE-ONLY")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("bob", "password1234", "ONCE-ONLY")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ordinaryUserCannotOpenAdminApi() throws Exception {
        saveUser("alice", passwordEncoder.encode("password1234"));
        mockMvc.perform(get("/api/admin/users").with(user("alice").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminWithoutTotpCanUseBusinessApi() throws Exception {
        User admin = saveUser("admin-no-totp", passwordEncoder.encode("admin-password-123"));
        admin.setRole("ADMIN");
        admin.setTotpEnabled(false);
        userRepository.save(admin);
        // TOTP 已改为可选：未绑定 TOTP 的管理员不再被拦截，可直接访问业务 API
        mockMvc.perform(get("/api/lots").with(user("admin-no-totp").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    private String credentials(String username, String password, String inviteCode) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "username", username, "password", password, "inviteCode", inviteCode));
    }

    private User saveUser(String username, String passwordHash) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        return userRepository.save(user);
    }

    private Item saveItem(String marketHashName) {
        Item item = new Item();
        item.setMarketHashName(marketHashName);
        item.setNameZh(marketHashName);
        item.setSource("test");
        return itemRepository.save(item);
    }

    private Lot saveLot(User user, Item item, String buyPrice) {
        Lot lot = new Lot();
        lot.setUser(user);
        lot.setItem(item);
        lot.setBuyPrice(new BigDecimal(buyPrice));
        lot.setBuyTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        lot.setBuyPlatform("uu");
        return lotRepository.save(lot);
    }

    private void createInvite(User admin, String code) {
        InviteCode invite = new InviteCode();
        invite.setCodeHash(SecurityTokens.sha256(code.replace("-", "")));
        invite.setCreatedBy(admin);
        invite.setExpiresAt(LocalDateTime.now().plusDays(1));
        inviteCodeRepository.save(invite);
    }
}
