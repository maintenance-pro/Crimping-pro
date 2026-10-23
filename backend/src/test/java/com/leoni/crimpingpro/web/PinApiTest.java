package com.leoni.crimpingpro.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.leoni.crimpingpro.security.PrincipalResolver;
import com.leoni.crimpingpro.support.FakeTokenVerifier;
import com.leoni.crimpingpro.support.InMemoryStore;
import com.leoni.crimpingpro.support.InMemoryUserDirectory;
import com.leoni.crimpingpro.support.TestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
    "app.firebase.enabled=false",
    "app.pin.pepper=poivre-de-test-0123456789abcdef-0123",
    "app.pin.bcrypt-cost=4",
    "app.rate-limit.admin-per-minute=100"
})
@Import(TestSupport.class)
class PinApiTest {

  @Autowired WebApplicationContext context;
  @Autowired FakeTokenVerifier tokens;
  @Autowired InMemoryUserDirectory directory;
  @Autowired InMemoryStore store;
  @Autowired PrincipalResolver resolver;

  MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(context.getBean(RequestSizeLimitFilter.class))
        .apply(springSecurity()).build();
    directory.reset();
    store.reset();
    resolver.invalidateAll();
    directory.seed("admin1", "admin", true);
    directory.seed("op1", "crimp", true);
    directory.profiles.get("op1").put("matricule", "M7");
  }

  private String bearer(String uid) {
    return "Bearer " + tokens.tokenFor(uid);
  }

  private static JsonNode json(String body) throws Exception {
    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
  }

  /** L'administrateur enrôle un poste et définit le PIN de op1 ; renvoie {deviceId, deviceSecret}. */
  private JsonNode enrollAndSetPin() throws Exception {
    String created = mvc.perform(post("/api/admin/devices").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"Poste atelier 1\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    mvc.perform(put("/api/admin/users/op1/pin").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"583092\"}"))
        .andExpect(status().isNoContent());
    return json(created);
  }

  private static String loginBody(JsonNode device, String pin) {
    return "{\"deviceId\":\"" + device.get("deviceId").asText() + "\",\"deviceSecret\":\""
        + device.get("deviceSecret").asText() + "\",\"matricule\":\"M7\",\"pin\":\"" + pin + "\"}";
  }

  @Test
  void connexion_par_pin_sans_session_prealable() throws Exception {
    JsonNode device = enrollAndSetPin();
    mvc.perform(post("/api/pin/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(device, "583092")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customToken").value("custom:op1:{pin_device=" + device.get("deviceId").asText() + "}"));
    mvc.perform(post("/api/pin/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(device, "000111")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Matricule ou PIN incorrect."));
  }

  @Test
  void administration_des_postes_et_pin_reservee_aux_administrateurs() throws Exception {
    mvc.perform(post("/api/admin/devices").header("Authorization", bearer("op1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"label\":\"x\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/admin/users/op1/pin").header("Authorization", bearer("op1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"583092\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/devices")).andExpect(status().isUnauthorized());
    assertThat(store.read("secure")).isNull();
  }

  @Test
  void la_liste_des_postes_ne_contient_aucun_secret() throws Exception {
    JsonNode device = enrollAndSetPin();
    String list = mvc.perform(get("/api/admin/devices").header("Authorization", bearer("admin1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].label").value("Poste atelier 1"))
        .andExpect(jsonPath("$[0].active").value(true))
        .andReturn().getResponse().getContentAsString();
    assertThat(list).doesNotContain(device.get("deviceSecret").asText()).doesNotContain("secretHash");
  }

  @Test
  void session_pin_d_un_poste_revoque_refusee_par_l_api() throws Exception {
    JsonNode device = enrollAndSetPin();
    String deviceId = device.get("deviceId").asText();
    String pinSession = "Bearer " + tokens.tokenFor("op1",
        Map.of("pin_device", deviceId, "auth_time", System.currentTimeMillis() / 1000));
    mvc.perform(get("/api/me").header("Authorization", pinSession)).andExpect(status().isOk());

    mvc.perform(post("/api/admin/devices/" + deviceId + "/revoke").header("Authorization", bearer("admin1")))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/me").header("Authorization", pinSession))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.detail").value("Session PIN expirée ou poste révoqué. Reconnectez-vous."));
    mvc.perform(post("/api/pin/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(device, "583092")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void pin_trop_simple_refuse_avec_message_clair() throws Exception {
    mvc.perform(put("/api/admin/users/op1/pin").header("Authorization", bearer("admin1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"123456\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("suites")));
  }
}
