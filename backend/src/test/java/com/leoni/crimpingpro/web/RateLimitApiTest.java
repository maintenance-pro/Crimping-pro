package com.leoni.crimpingpro.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leoni.crimpingpro.support.FakeTokenVerifier;
import com.leoni.crimpingpro.support.InMemoryUserDirectory;
import com.leoni.crimpingpro.support.TestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {"app.firebase.enabled=false", "app.rate-limit.admin-per-minute=2"})
@Import(TestSupport.class)
class RateLimitApiTest {

  @Autowired WebApplicationContext context;
  @Autowired FakeTokenVerifier tokens;
  @Autowired InMemoryUserDirectory directory;

  @Test
  void trop_d_operations_d_administration_429() throws Exception {
    directory.reset();
    directory.seed("admin1", "admin", true);
    directory.seed("labo1", "labo", true);
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    String auth = "Bearer " + tokens.tokenFor("admin1");

    mvc.perform(post("/api/admin/users/labo1/sessions/revoke").header("Authorization", auth))
        .andExpect(status().isNoContent());
    mvc.perform(post("/api/admin/users/labo1/sessions/revoke").header("Authorization", auth))
        .andExpect(status().isNoContent());
    mvc.perform(post("/api/admin/users/labo1/sessions/revoke").header("Authorization", auth))
        .andExpect(status().isTooManyRequests());
  }
}
