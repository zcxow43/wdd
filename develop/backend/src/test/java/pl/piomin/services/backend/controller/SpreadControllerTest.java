package pl.piomin.services.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import pl.piomin.services.backend.audit.AuditRequestMapper;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.mapper.SpreadDefaultMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMemberMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.SpreadDefault;
import pl.piomin.services.backend.model.SpreadGroup;
import pl.piomin.services.backend.model.SpreadGroupMember;

/**
 * Integration tests for {@code /api/spread-defaults} and {@code /api/spread-groups}.
 * Per specs/backend/spread.md, every mutation on either concept submits a
 * PENDING audit request (202) instead of mutating directly; the change only
 * lands once approved via the generic {@code /api/audit-requests/{id}/approve}
 * endpoint. GET endpoints (including the resolver) are unaffected.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpreadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private CurrencyMapper currencyMapper;

    @Autowired
    private CurrencyPairMapper currencyPairMapper;

    @Autowired
    private SpreadDefaultMapper spreadDefaultMapper;

    @Autowired
    private SpreadGroupMapper spreadGroupMapper;

    @Autowired
    private SpreadGroupMemberMapper spreadGroupMemberMapper;

    @Autowired
    private AuditRequestMapper auditRequestMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Long brandAId;
    private Long brandBId;
    private Long usdId;
    private Long jpyId;
    private Long eurId;
    private Long pair1Id; // brandA, USD/JPY
    private Long pair2Id; // brandA, USD/EUR
    private Long pair3Id; // brandB, USD/JPY
    private Long sdAId;
    private Long sdBId;

    @BeforeEach
    void setUp() {
        for (Long id : spreadGroupMemberMapper.findAllIds()) {
            spreadGroupMemberMapper.deleteById(id);
        }
        for (Long id : spreadGroupMapper.findAllIds()) {
            spreadGroupMapper.deleteById(id);
        }
        for (Long id : spreadDefaultMapper.findAllIds()) {
            spreadDefaultMapper.deleteById(id);
        }
        for (Long id : auditRequestMapper.findAllIds()) {
            auditRequestMapper.deleteById(id);
        }
        for (Long id : currencyPairMapper.findAllIds()) {
            currencyPairMapper.deleteById(id);
        }
        for (Currency currency : currencyMapper.findAll(null)) {
            currencyMapper.deleteById(currency.getId());
        }
        for (Brand brand : brandMapper.findAll(null)) {
            brandMapper.deleteById(brand.getId());
        }

        brandAId = insertBrand("AU");
        brandBId = insertBrand("STAR");
        usdId = insertCurrency("USD");
        jpyId = insertCurrency("JPY");
        eurId = insertCurrency("EUR");

        pair1Id = insertPair(brandAId, usdId, jpyId);
        pair2Id = insertPair(brandAId, usdId, eurId);
        pair3Id = insertPair(brandBId, usdId, jpyId);

        sdAId = insertSpreadDefault(brandAId, "0", "0");
        sdBId = insertSpreadDefault(brandBId, "0.01", "0.02");
    }

    private Long insertBrand(String code) {
        Brand brand = new Brand();
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        brandMapper.insert(brand);
        return brand.getId();
    }

    private Long insertCurrency(String code) {
        Currency currency = new Currency();
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(2);
        currency.setActive(true);
        currencyMapper.insert(currency);
        return currency.getId();
    }

    private Long insertPair(Long brandId, Long baseId, Long quoteId) {
        CurrencyPair pair = new CurrencyPair();
        pair.setBrandId(brandId);
        pair.setBaseCurrencyId(baseId);
        pair.setQuoteCurrencyId(quoteId);
        pair.setRateType("AUTO");
        pair.setActive(true);
        currencyPairMapper.insert(pair);
        return pair.getId();
    }

    private Long insertSpreadDefault(Long brandId, String deposit, String withdraw) {
        SpreadDefault spreadDefault = new SpreadDefault();
        spreadDefault.setBrandId(brandId);
        spreadDefault.setDepositSpread(new BigDecimal(deposit));
        spreadDefault.setWithdrawSpread(new BigDecimal(withdraw));
        spreadDefaultMapper.insert(spreadDefault);
        return spreadDefault.getId();
    }

    private Long insertGroup(Long brandId, String name, String deposit, String withdraw) {
        SpreadGroup group = new SpreadGroup();
        group.setBrandId(brandId);
        group.setName(name);
        group.setDepositSpread(new BigDecimal(deposit));
        group.setWithdrawSpread(new BigDecimal(withdraw));
        spreadGroupMapper.insert(group);
        return group.getId();
    }

    private void insertMember(Long groupId, Long pairId) {
        SpreadGroupMember member = new SpreadGroupMember();
        member.setSpreadGroupId(groupId);
        member.setCurrencyPairId(pairId);
        spreadGroupMemberMapper.insert(member);
    }

    // ==== GET /api/spread-defaults (unaffected by audit workflow) ==============

    @Test
    void listDefaults_returnsAllRows() throws Exception {
        mockMvc.perform(get("/api/spread-defaults"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listDefaults_filtersByBrandId() throws Exception {
        mockMvc.perform(get("/api/spread-defaults").param("brandId", String.valueOf(brandBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].brandCode").value("STAR"))
                .andExpect(jsonPath("$[0].depositSpread").value(0.01));
    }

    @Test
    void getDefault_returns200_whenFound() throws Exception {
        mockMvc.perform(get("/api/spread-defaults/{id}", sdAId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandCode").value("AU"));
    }

    @Test
    void getDefault_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/spread-defaults/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Spread default not found"));
    }

    // ==== PUT /api/spread-defaults/{id} (submits an UPDATE audit request) ======

    @Test
    void updateDefault_returns202_withBeforeAndAfter_andLiveUnchanged() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
            put("requestedBy", "Alice");
        }});

        mockMvc.perform(put("/api/spread-defaults/{id}", sdAId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("SPREAD_DEFAULT"))
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.entityId").value(sdAId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before.depositSpread").value(0))
                .andExpect(jsonPath("$.after.depositSpread").value(0.1))
                .andExpect(jsonPath("$.requestedBy").value("Alice"));

        SpreadDefault live = spreadDefaultMapper.findById(sdAId);
        org.assertj.core.api.Assertions.assertThat(live.getDepositSpread()).isEqualByComparingTo("0");
    }

    @Test
    void updateDefault_returns404_whenNotFound() throws Exception {
        String body = "{\"depositSpread\":0.1,\"withdrawSpread\":0.2}";

        mockMvc.perform(put("/api/spread-defaults/{id}", 999999).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateDefault_returns400_whenNegativeSpread() throws Exception {
        String body = "{\"depositSpread\":-1,\"withdrawSpread\":0.2}";

        mockMvc.perform(put("/api/spread-defaults/{id}", sdAId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDefault_returns400_whenFieldsMissing() throws Exception {
        mockMvc.perform(put("/api/spread-defaults/{id}", sdAId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.depositSpread").exists())
                .andExpect(jsonPath("$.details.withdrawSpread").exists());
    }

    @Test
    void updateDefault_returns409_whenPendingAlreadyExists() throws Exception {
        String body = "{\"depositSpread\":0.1,\"withdrawSpread\":0.2}";

        mockMvc.perform(put("/api/spread-defaults/{id}", sdAId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/spread-defaults/{id}", sdAId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A pending audit request already exists for this entity"));
    }

    @Test
    void approve_updateDefaultRequest_updatesLiveRow() throws Exception {
        String body = "{\"depositSpread\":0.15,\"withdrawSpread\":0.25}";

        String response = mockMvc.perform(put("/api/spread-defaults/{id}", sdAId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        SpreadDefault live = spreadDefaultMapper.findById(sdAId);
        org.assertj.core.api.Assertions.assertThat(live.getDepositSpread()).isEqualByComparingTo("0.15");
        org.assertj.core.api.Assertions.assertThat(live.getWithdrawSpread()).isEqualByComparingTo("0.25");
    }

    // ==== GET /api/spread-groups (unaffected by audit workflow) ================

    @Test
    void listGroups_returnsGroupsWithMembers() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertMember(groupId, pair1Id);

        mockMvc.perform(get("/api/spread-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Group A"))
                .andExpect(jsonPath("$[0].members", hasSize(1)))
                .andExpect(jsonPath("$[0].members[0].currencyPairId").value(pair1Id))
                .andExpect(jsonPath("$[0].members[0].baseCurrencyCode").value("USD"));
    }

    @Test
    void listGroups_filtersByBrandId() throws Exception {
        insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertGroup(brandBId, "Group B", "0.3", "0.4");

        mockMvc.perform(get("/api/spread-groups").param("brandId", String.valueOf(brandBId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Group B"));
    }

    @Test
    void getGroup_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/spread-groups/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Spread group not found"));
    }

    // ==== POST /api/spread-groups (submits a CREATE audit request) ==============

    @Test
    void createGroup_returns202_withEnrichedAfter_andNothingInserted() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
            put("currencyPairIds", java.util.List.of(pair1Id, pair2Id));
            put("requestedBy", "Alice");
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("SPREAD_GROUP"))
                .andExpect(jsonPath("$.actionType").value("CREATE"))
                .andExpect(jsonPath("$.entityId").value(nullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.after.brandCode").value("AU"))
                .andExpect(jsonPath("$.after.members", hasSize(2)))
                .andExpect(jsonPath("$.requestedBy").value("Alice"));

        org.assertj.core.api.Assertions.assertThat(spreadGroupMapper.findAll(null)).isEmpty();
    }

    @Test
    void createGroup_defaultsCurrencyPairIdsToEmptyList_whenOmitted() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Empty Group");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.after.members", hasSize(0)));
    }

    @Test
    void createGroup_returns400_whenRequiredFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.brandId").exists())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.depositSpread").exists())
                .andExpect(jsonPath("$.details.withdrawSpread").exists());
    }

    @Test
    void createGroup_returns404_whenBrandMissing() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", 999999);
            put("name", "Group A");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"));
    }

    @Test
    void createGroup_returns400_whenDuplicateCurrencyPairIds() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
            put("currencyPairIds", java.util.List.of(pair1Id, pair1Id));
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Duplicate currency pair id in currencyPairIds"));
    }

    @Test
    void createGroup_returns404_whenCurrencyPairMissing() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
            put("currencyPairIds", java.util.List.of(999999));
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"));
    }

    @Test
    void createGroup_returns400_whenCurrencyPairBrandMismatch() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
            put("currencyPairIds", java.util.List.of(pair3Id)); // belongs to brandB
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Currency pair does not belong to the group's brand"));
    }

    @Test
    void createGroup_returns409_whenLiveNameAlreadyExistsInBrand() throws Exception {
        insertGroup(brandAId, "Group A", "0.1", "0.2");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.5);
            put("withdrawSpread", 0.6);
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Spread group name already exists for this brand"));
    }

    @Test
    void createGroup_succeeds_whenSameNameUnderDifferentBrand() throws Exception {
        insertGroup(brandBId, "Group A", "0.1", "0.2");

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.5);
            put("withdrawSpread", 0.6);
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    @Test
    void createGroup_returns409_whenPendingCreateAlreadyExistsForSameBrandAndName() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("A pending create request already exists for this brand/name combination"));
    }

    @Test
    void approve_createGroupRequest_insertsGroupAndMembers_movingPairFromPriorGroup() throws Exception {
        Long priorGroupId = insertGroup(brandAId, "Old Group", "0.9", "0.9");
        insertMember(priorGroupId, pair1Id);

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "New Group");
            put("depositSpread", 0.1);
            put("withdrawSpread", 0.2);
            put("currencyPairIds", java.util.List.of(pair1Id));
        }});

        String response = mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.entityId").exists());

        org.assertj.core.api.Assertions.assertThat(spreadGroupMemberMapper.findByGroupId(priorGroupId)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(spreadGroupMemberMapper.findByCurrencyPairId(pair1Id))
                .isNotNull();
    }

    // ==== PUT /api/spread-groups/{id} (submits an UPDATE audit request) ========

    @Test
    void updateGroup_returns202_withMergedAfter_andLiveUnchanged() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertMember(groupId, pair1Id);

        String body = "{\"name\":\"Group A Renamed\"}";

        mockMvc.perform(put("/api/spread-groups/{id}", groupId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.entityId").value(groupId))
                .andExpect(jsonPath("$.before.name").value("Group A"))
                .andExpect(jsonPath("$.after.name").value("Group A Renamed"))
                .andExpect(jsonPath("$.after.depositSpread").value(0.1))
                // omitted currencyPairIds -> frozen from current live membership
                .andExpect(jsonPath("$.after.currencyPairIds", hasSize(1)));

        SpreadGroup live = spreadGroupMapper.findById(groupId);
        org.assertj.core.api.Assertions.assertThat(live.getName()).isEqualTo("Group A");
    }

    @Test
    void updateGroup_returns404_whenNotFound() throws Exception {
        mockMvc.perform(put("/api/spread-groups/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateGroup_returns400_whenNameBlank() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGroup_returns409_whenRenameCollidesWithLiveGroup() throws Exception {
        insertGroup(brandAId, "Group B", "0.1", "0.2");
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Group B\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Spread group name already exists for this brand"));
    }

    @Test
    void updateGroup_returns409_whenPendingUpdateAlreadyExists() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");

        mockMvc.perform(put("/api/spread-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"depositSpread\":0.5}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/spread-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"depositSpread\":0.6}"))
                .andExpect(status().isConflict());
    }

    @Test
    void approve_updateGroupRequest_replacesMembership_removedPairsRevertToDefault() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertMember(groupId, pair1Id);
        insertMember(groupId, pair2Id);

        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("currencyPairIds", java.util.List.of(pair2Id)); // drop pair1, keep pair2
        }});

        String response = mockMvc.perform(put("/api/spread-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(spreadGroupMemberMapper.findByCurrencyPairId(pair1Id)).isNull();
        org.assertj.core.api.Assertions.assertThat(spreadGroupMemberMapper.findByCurrencyPairId(pair2Id)).isNotNull();

        // pair1 now resolves back to the brand's default spread
        mockMvc.perform(get("/api/spread-groups/resolve/{currencyPairId}", pair1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    @Test
    void approve_updateGroupRequest_returns409_andLeavesPending_whenNameCollidesAtApprovalTime() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");

        String response = mockMvc.perform(put("/api/spread-groups/{id}", groupId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Group B\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        // Someone else creates the colliding live name directly after submission.
        insertGroup(brandAId, "Group B", "0.1", "0.2");

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/audit-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ==== DELETE /api/spread-groups/{id} (submits a DELETE audit request) ======

    @Test
    void deleteGroup_returns202_withBeforeSnapshot_andGroupStillExists() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertMember(groupId, pair1Id);

        mockMvc.perform(delete("/api/spread-groups/{id}", groupId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.actionType").value("DELETE"))
                .andExpect(jsonPath("$.entityId").value(groupId))
                .andExpect(jsonPath("$.before.name").value("Group A"))
                .andExpect(jsonPath("$.after").value(nullValue()));

        mockMvc.perform(get("/api/spread-groups/{id}", groupId))
                .andExpect(status().isOk());
    }

    @Test
    void deleteGroup_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/spread-groups/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_returns409_whenPendingDeleteAlreadyExists() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");

        mockMvc.perform(delete("/api/spread-groups/{id}", groupId))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/spread-groups/{id}", groupId))
                .andExpect(status().isConflict());
    }

    @Test
    void approve_deleteGroupRequest_removesGroupAndMembers_pairFallsBackToDefault() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertMember(groupId, pair1Id);

        String response = mockMvc.perform(delete("/api/spread-groups/{id}", groupId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/spread-groups/{id}", groupId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/spread-groups/resolve/{currencyPairId}", pair1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }

    // ==== GET /api/spread-groups/resolve/{currencyPairId} (unaffected by audit workflow) ====

    @Test
    void resolve_returnsDefaultSource_whenPairHasNoGroup() throws Exception {
        mockMvc.perform(get("/api/spread-groups/resolve/{currencyPairId}", pair1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currencyPairId").value(pair1Id))
                .andExpect(jsonPath("$.brandId").value(brandAId))
                .andExpect(jsonPath("$.source").value("DEFAULT"))
                .andExpect(jsonPath("$.spreadGroupId").value(nullValue()))
                .andExpect(jsonPath("$.depositSpread").value(0));
    }

    @Test
    void resolve_returnsGroupSource_whenPairIsMember() throws Exception {
        Long groupId = insertGroup(brandAId, "Group A", "0.1", "0.2");
        insertMember(groupId, pair1Id);

        mockMvc.perform(get("/api/spread-groups/resolve/{currencyPairId}", pair1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("GROUP"))
                .andExpect(jsonPath("$.spreadGroupId").value(groupId))
                .andExpect(jsonPath("$.spreadGroupName").value("Group A"))
                .andExpect(jsonPath("$.depositSpread").value(0.1));
    }

    @Test
    void resolve_returns404_whenCurrencyPairMissing() throws Exception {
        mockMvc.perform(get("/api/spread-groups/resolve/{currencyPairId}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void resolve_unaffectedByPendingCreateRequest() throws Exception {
        String body = objectMapper.writeValueAsString(new HashMap<>() {{
            put("brandId", brandAId);
            put("name", "Group A");
            put("depositSpread", 0.9);
            put("withdrawSpread", 0.9);
            put("currencyPairIds", java.util.List.of(pair1Id));
        }});

        mockMvc.perform(post("/api/spread-groups").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        // still PENDING -> resolve must still report DEFAULT
        mockMvc.perform(get("/api/spread-groups/resolve/{currencyPairId}", pair1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"));
    }
}
