package com.wdd.backend.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration coverage for {@code SpreadController} (specs/backend/spread.md): the read
 * endpoints for both {@code spread_default} and {@code spread_group} (unaffected by the audit
 * workflow), the audit-request submission behavior of every mutation, the resolver endpoint, and
 * full submit -> approve/reject round trips against the real {@code /api/audit-requests}
 * endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpreadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long brandAuId;
    private Long brandPugId;
    private Long usdId;
    private Long jpyId;
    private Long eurId;
    private Long cpAuUsdJpyId;
    private Long cpAuUsdEurId;
    private Long cpPugUsdJpyId;
    private Long spreadDefaultAuId;
    private Long spreadDefaultPugId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_request");
        jdbcTemplate.update("DELETE FROM spread_group_member");
        jdbcTemplate.update("DELETE FROM spread_group");
        jdbcTemplate.update("DELETE FROM spread_default");
        jdbcTemplate.update("DELETE FROM currency_pair");
        jdbcTemplate.update("DELETE FROM brand");
        jdbcTemplate.update("DELETE FROM currency");

        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "USD", "US Dollar", 2);
        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "JPY", "Japanese Yen", 0);
        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "EUR", "Euro", 2);
        usdId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'USD'", Long.class);
        jpyId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'JPY'", Long.class);
        eurId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'EUR'", Long.class);

        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "AU", "AU", true);
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "PUG", "PUG", true);
        brandAuId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        brandPugId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'PUG'", Long.class);

        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandAuId, usdId, jpyId, "1.0", "MANUAL", true);
        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandAuId, usdId, eurId, "1.0", "MANUAL", true);
        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandPugId, usdId, jpyId, "1.0", "MANUAL", true);
        cpAuUsdJpyId = jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Long.class, brandAuId, usdId, jpyId);
        cpAuUsdEurId = jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Long.class, brandAuId, usdId, eurId);
        cpPugUsdJpyId = jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Long.class, brandPugId, usdId, jpyId);

        jdbcTemplate.update("INSERT INTO spread_default (brand_id, deposit_spread, withdraw_spread) VALUES (?, ?, ?)",
                brandAuId, "0", "0");
        jdbcTemplate.update("INSERT INTO spread_default (brand_id, deposit_spread, withdraw_spread) VALUES (?, ?, ?)",
                brandPugId, "0", "0");
        spreadDefaultAuId = jdbcTemplate.queryForObject("SELECT id FROM spread_default WHERE brand_id = ?", Long.class,
                brandAuId);
        spreadDefaultPugId = jdbcTemplate.queryForObject("SELECT id FROM spread_default WHERE brand_id = ?", Long.class,
                brandPugId);
    }

    private Long extractId(String json) {
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }

    private Long extractEntityId(String json) {
        Number id = JsonPath.read(json, "$.entityId");
        return id == null ? null : id.longValue();
    }

    private String approve(Long requestId) throws Exception {
        return mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn().getResponse().getContentAsString();
    }

    // ================= /api/spread-defaults =================

    @Test
    void listDefaults_returnsAllRows() throws Exception {
        mockMvc.perform(get("/api/spread-defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void listDefaults_filtersByBrandId() throws Exception {
        mockMvc.perform(get("/api/spread-defaults").param("brandId", String.valueOf(brandAuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].brandCode").value("AU"));
    }

    @Test
    void getDefaultById_returns200WhenFound() throws Exception {
        mockMvc.perform(get("/api/spread-defaults/" + spreadDefaultAuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandCode").value("AU"))
                .andExpect(jsonPath("$.depositSpread").value(0))
                .andExpect(jsonPath("$.withdrawSpread").value(0));
    }

    @Test
    void getDefaultById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/spread-defaults/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Spread default not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void postSpreadDefaults_isNotMapped() throws Exception {
        mockMvc.perform(post("/api/spread-defaults").contentType("application/json").content("{}"))
                .andExpect(status().is(HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    @Test
    void deleteSpreadDefaults_isNotMapped() throws Exception {
        mockMvc.perform(delete("/api/spread-defaults/" + spreadDefaultAuId))
                .andExpect(status().is(HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    @Test
    void updateDefault_returns202_andLeavesLiveRowUnchanged() throws Exception {
        mockMvc.perform(put("/api/spread-defaults/" + spreadDefaultAuId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.1,\"withdrawSpread\":0.2,\"requestedBy\":\"Alice\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("SPREAD_DEFAULT"))
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.entityId").value(spreadDefaultAuId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedBy").value("Alice"))
                .andExpect(jsonPath("$.before.depositSpread").value(0))
                .andExpect(jsonPath("$.after.depositSpread").value(0.1))
                .andExpect(jsonPath("$.after.withdrawSpread").value(0.2))
                .andExpect(jsonPath("$.after.brandCode").value("AU"));

        mockMvc.perform(get("/api/spread-defaults/" + spreadDefaultAuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositSpread").value(0));
    }

    @Test
    void updateDefault_returns404WhenMissing() throws Exception {
        mockMvc.perform(put("/api/spread-defaults/999999")
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.1,\"withdrawSpread\":0.2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDefault_returns400WhenNegativeSpread() throws Exception {
        mockMvc.perform(put("/api/spread-defaults/" + spreadDefaultAuId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":-1,\"withdrawSpread\":0.2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDefault_returns400WhenMissingField() throws Exception {
        mockMvc.perform(put("/api/spread-defaults/" + spreadDefaultAuId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDefault_secondPendingUpdateReturns409() throws Exception {
        mockMvc.perform(put("/api/spread-defaults/" + spreadDefaultAuId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.1,\"withdrawSpread\":0.2}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/spread-defaults/" + spreadDefaultAuId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.3,\"withdrawSpread\":0.4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A pending audit request already exists for this entity"));
    }

    @Test
    void approve_updateDefaultRequest_overwritesLiveRow() throws Exception {
        String response = mockMvc.perform(put("/api/spread-defaults/" + spreadDefaultAuId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.1,\"withdrawSpread\":0.2}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(response);

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/spread-defaults/" + spreadDefaultAuId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositSpread").value(0.1))
                .andExpect(jsonPath("$.withdrawSpread").value(0.2));
    }

    // ================= /api/spread-groups: GET =================

    private Long createLiveGroup(Long brandId, String name, String deposit, String withdraw) {
        jdbcTemplate.update(
                "INSERT INTO spread_group (brand_id, name, deposit_spread, withdraw_spread) VALUES (?, ?, ?, ?)",
                brandId, name, deposit, withdraw);
        return jdbcTemplate.queryForObject("SELECT id FROM spread_group WHERE brand_id = ? AND name = ?", Long.class,
                brandId, name);
    }

    private void addMember(Long groupId, Long currencyPairId) {
        jdbcTemplate.update("INSERT INTO spread_group_member (spread_group_id, currency_pair_id) VALUES (?, ?)",
                groupId, currencyPairId);
    }

    @Test
    void listGroups_returnsGroupsWithMembers() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);

        mockMvc.perform(get("/api/spread-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Group A"))
                .andExpect(jsonPath("$[0].members.length()").value(1))
                .andExpect(jsonPath("$[0].members[0].currencyPairId").value(cpAuUsdJpyId))
                .andExpect(jsonPath("$[0].members[0].baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$[0].members[0].quoteCurrencyCode").value("JPY"));
    }

    @Test
    void listGroups_filtersByBrandId() throws Exception {
        createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        createLiveGroup(brandPugId, "Group B", "0.3", "0.4");

        mockMvc.perform(get("/api/spread-groups").param("brandId", String.valueOf(brandPugId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Group B"));
    }

    @Test
    void getGroupById_returns200WhenFound() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");

        mockMvc.perform(get("/api/spread-groups/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Group A"));
    }

    @Test
    void getGroupById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/spread-groups/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Spread group not found"));
    }

    // ================= /api/spread-groups: POST (CREATE) =================

    private String createGroupBody(Long brandId, String name, String deposit, String withdraw, String currencyPairIdsJson) {
        return String.format(
                "{\"brandId\":%d,\"name\":\"%s\",\"depositSpread\":%s,\"withdrawSpread\":%s,\"currencyPairIds\":%s}",
                brandId, name, deposit, withdraw, currencyPairIdsJson);
    }

    @Test
    void createGroup_returns202_withEnrichedAfterSnapshot() throws Exception {
        String body = createGroupBody(brandAuId, "Group A", "0.1", "0.2",
                "[" + cpAuUsdJpyId + "," + cpAuUsdEurId + "]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("SPREAD_GROUP"))
                .andExpect(jsonPath("$.actionType").value("CREATE"))
                .andExpect(jsonPath("$.entityId").doesNotExist())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before").doesNotExist())
                .andExpect(jsonPath("$.after.brandCode").value("AU"))
                .andExpect(jsonPath("$.after.name").value("Group A"))
                .andExpect(jsonPath("$.after.members.length()").value(2));

        // Nothing inserted yet.
        Integer groupCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spread_group", Integer.class);
        org.assertj.core.api.Assertions.assertThat(groupCount).isZero();
    }

    @Test
    void createGroup_returns400WhenMissingRequiredField() throws Exception {
        mockMvc.perform(post("/api/spread-groups").contentType("application/json")
                        .content("{\"brandId\":" + brandAuId + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createGroup_returns404WhenBrandMissing() throws Exception {
        String body = createGroupBody(999999L, "Group A", "0.1", "0.2", "[]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"));
    }

    @Test
    void createGroup_returns409WhenNameCollidesWithLiveGroup() throws Exception {
        createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        String body = createGroupBody(brandAuId, "Group A", "0.3", "0.4", "[]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Spread group name already exists for this brand"));
    }

    @Test
    void createGroup_returns409WhenPendingCreateExistsForSameBrandAndName() throws Exception {
        String body = createGroupBody(brandAuId, "Group A", "0.1", "0.2", "[]");
        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("A pending create request already exists for this brand/name combination"));
    }

    @Test
    void createGroup_sameNameDifferentBrand_succeeds() throws Exception {
        String bodyAu = createGroupBody(brandAuId, "Group A", "0.1", "0.2", "[]");
        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(bodyAu))
                .andExpect(status().isAccepted());

        String bodyPug = createGroupBody(brandPugId, "Group A", "0.1", "0.2", "[]");
        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(bodyPug))
                .andExpect(status().isAccepted());
    }

    @Test
    void createGroup_returns400WhenDuplicateCurrencyPairIds() throws Exception {
        String body = createGroupBody(brandAuId, "Group A", "0.1", "0.2",
                "[" + cpAuUsdJpyId + "," + cpAuUsdJpyId + "]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Duplicate currency pair id in currencyPairIds"));
    }

    @Test
    void createGroup_returns404WhenCurrencyPairMissing() throws Exception {
        String body = createGroupBody(brandAuId, "Group A", "0.1", "0.2", "[999999]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"));
    }

    @Test
    void createGroup_returns400WhenCurrencyPairBelongsToDifferentBrand() throws Exception {
        String body = createGroupBody(brandAuId, "Group A", "0.1", "0.2", "[" + cpPugUsdJpyId + "]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Currency pair does not belong to the group's brand"));
    }

    @Test
    void createGroup_currencyPairAlreadyInAnotherGroup_isNotRejected() throws Exception {
        Long existingGroupId = createLiveGroup(brandAuId, "Old Group", "0.05", "0.05");
        addMember(existingGroupId, cpAuUsdJpyId);

        String body = createGroupBody(brandAuId, "New Group", "0.1", "0.2", "[" + cpAuUsdJpyId + "]");

        mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isAccepted());
    }

    // ---------- approve CREATE ----------

    @Test
    void approve_createGroupRequest_insertsGroupAndMemberships_andSetsEntityId() throws Exception {
        String body = createGroupBody(brandAuId, "Group A", "0.1", "0.2",
                "[" + cpAuUsdJpyId + "," + cpAuUsdEurId + "]");
        String submitResponse = mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(submitResponse);

        String approveResponse = approve(requestId);
        Long newGroupId = extractEntityId(approveResponse);
        org.assertj.core.api.Assertions.assertThat(newGroupId).isNotNull();

        mockMvc.perform(get("/api/spread-groups/" + newGroupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Group A"))
                .andExpect(jsonPath("$.members.length()").value(2));
    }

    @Test
    void approve_createGroupRequest_movesPairFromExistingGroup() throws Exception {
        Long oldGroupId = createLiveGroup(brandAuId, "Old Group", "0.05", "0.05");
        addMember(oldGroupId, cpAuUsdJpyId);

        String body = createGroupBody(brandAuId, "New Group", "0.1", "0.2", "[" + cpAuUsdJpyId + "]");
        String submitResponse = mockMvc.perform(post("/api/spread-groups").contentType("application/json").content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(submitResponse);

        approve(requestId);

        mockMvc.perform(get("/api/spread-groups/" + oldGroupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(0));
    }

    // ================= /api/spread-groups: PUT (UPDATE) =================

    @Test
    void updateGroup_returns202_withMergedAfterSnapshot() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);

        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"name\":\"Group A Renamed\",\"requestedBy\":\"Alice\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("SPREAD_GROUP"))
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.entityId").value(groupId))
                .andExpect(jsonPath("$.before.name").value("Group A"))
                .andExpect(jsonPath("$.after.name").value("Group A Renamed"))
                .andExpect(jsonPath("$.after.depositSpread").value(0.1))
                // currencyPairIds omitted from the request -> frozen from live membership.
                .andExpect(jsonPath("$.after.currencyPairIds.length()").value(1));

        mockMvc.perform(get("/api/spread-groups/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Group A"));
    }

    @Test
    void updateGroup_returns404WhenMissing() throws Exception {
        mockMvc.perform(put("/api/spread-groups/999999")
                        .contentType("application/json")
                        .content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateGroup_returns409WhenRenameCollidesWithLiveGroup() throws Exception {
        createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        Long groupId = createLiveGroup(brandAuId, "Group B", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"name\":\"Group A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Spread group name already exists for this brand"));
    }

    @Test
    void updateGroup_renamingToOwnCurrentName_succeeds() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"name\":\"Group A\",\"depositSpread\":0.5,\"withdrawSpread\":0.5}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void updateGroup_secondPendingUpdateReturns409() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.5}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.6}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A pending audit request already exists for this entity"));
    }

    @Test
    void updateGroup_returns400WhenCurrencyPairBrandMismatch() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"currencyPairIds\":[" + cpPugUsdJpyId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Currency pair does not belong to the group's brand"));
    }

    // ---------- approve UPDATE ----------

    @Test
    void approve_updateGroupRequest_replacesMembership_removedFallsBackToDefault_addedIsDetached() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);
        Long otherGroupId = createLiveGroup(brandAuId, "Group B", "0.05", "0.05");
        addMember(otherGroupId, cpAuUsdEurId);

        // Replace membership: drop cpAuUsdJpyId, add cpAuUsdEurId (detaching it from Group B).
        String submitResponse = mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"currencyPairIds\":[" + cpAuUsdEurId + "]}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(submitResponse);

        approve(requestId);

        mockMvc.perform(get("/api/spread-groups/" + groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(1))
                .andExpect(jsonPath("$.members[0].currencyPairId").value(cpAuUsdEurId));

        mockMvc.perform(get("/api/spread-groups/" + otherGroupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(0));

        // The dropped pair falls back to the default spread.
        mockMvc.perform(get("/api/spread-groups/resolve/" + cpAuUsdJpyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    @Test
    void approve_updateGroupRequest_returns409_andLeavesPending_whenNameCollidesAtApprovalTime() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");

        String submitResponse = mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"name\":\"Group B\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(submitResponse);

        // Another live group acquires the target name in the meantime.
        createLiveGroup(brandAuId, "Group B", "0.3", "0.4");

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/audit-requests/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ================= /api/spread-groups: DELETE =================

    @Test
    void deleteGroup_returns202_andLeavesLiveGroupUnchanged() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);

        mockMvc.perform(delete("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"requestedBy\":\"Bob\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("SPREAD_GROUP"))
                .andExpect(jsonPath("$.actionType").value("DELETE"))
                .andExpect(jsonPath("$.entityId").value(groupId))
                .andExpect(jsonPath("$.before.name").value("Group A"))
                .andExpect(jsonPath("$.after").doesNotExist())
                .andExpect(jsonPath("$.requestedBy").value("Bob"));

        mockMvc.perform(get("/api/spread-groups/" + groupId)).andExpect(status().isOk());
    }

    @Test
    void deleteGroup_returns404WhenMissing() throws Exception {
        mockMvc.perform(delete("/api/spread-groups/999999")).andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_secondPendingDeleteReturns409() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");

        mockMvc.perform(delete("/api/spread-groups/" + groupId)).andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/spread-groups/" + groupId)).andExpect(status().isConflict());
    }

    @Test
    void approve_deleteGroupRequest_removesGroupAndMemberships_fallsBackToDefault() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);

        String submitResponse = mockMvc.perform(delete("/api/spread-groups/" + groupId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(submitResponse);

        approve(requestId);

        mockMvc.perform(get("/api/spread-groups/" + groupId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/spread-groups/resolve/" + cpAuUsdJpyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    // ================= /api/spread-groups/resolve =================

    @Test
    void resolve_returnsGroupSpread_whenPairIsMember() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);

        mockMvc.perform(get("/api/spread-groups/resolve/" + cpAuUsdJpyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyPairId").value(cpAuUsdJpyId))
                .andExpect(jsonPath("$.brandId").value(brandAuId))
                .andExpect(jsonPath("$.source").value("GROUP"))
                .andExpect(jsonPath("$.spreadGroupId").value(groupId))
                .andExpect(jsonPath("$.spreadGroupName").value("Group A"))
                .andExpect(jsonPath("$.depositSpread").value(0.1))
                .andExpect(jsonPath("$.withdrawSpread").value(0.2));
    }

    @Test
    void resolve_returnsDefaultSpread_whenPairHasNoGroup() throws Exception {
        mockMvc.perform(get("/api/spread-groups/resolve/" + cpAuUsdJpyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"))
                .andExpect(jsonPath("$.spreadGroupId").doesNotExist())
                .andExpect(jsonPath("$.spreadGroupName").doesNotExist())
                .andExpect(jsonPath("$.depositSpread").value(0))
                .andExpect(jsonPath("$.withdrawSpread").value(0));
    }

    @Test
    void resolve_returns404WhenCurrencyPairMissing() throws Exception {
        mockMvc.perform(get("/api/spread-groups/resolve/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void resolve_isUnaffectedByPendingProposal() throws Exception {
        Long groupId = createLiveGroup(brandAuId, "Group A", "0.1", "0.2");
        addMember(groupId, cpAuUsdJpyId);

        // Submit (but do not approve) an UPDATE changing the group's spreads.
        mockMvc.perform(put("/api/spread-groups/" + groupId)
                        .contentType("application/json")
                        .content("{\"depositSpread\":0.9,\"withdrawSpread\":0.9}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/spread-groups/resolve/" + cpAuUsdJpyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depositSpread").value(0.1))
                .andExpect(jsonPath("$.withdrawSpread").value(0.2));
    }
}
