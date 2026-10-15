package com.nl2sql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nl2sql.datasource.dto.DataSourceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DataSourceControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void list_requiresAuth() throws Exception {
        // Spring Security 默认对未认证访问返回 403
        mvc.perform(get("/datasource/list"))
                .andExpect(status().isForbidden());
    }

    /**
     * 验证登录后可以成功调用列表接口
     */
    @Test
    void list_okAfterLogin() throws Exception {
        String token = loginAndGetToken("admin", "admin123");

        mvc.perform(get("/datasource/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void login_badPassword_401() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"wrong\"}";
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void login_ok() throws Exception {
        String body = "{\"username\":\"admin\",\"password\":\"admin123\"}";
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    private String loginAndGetToken(String u, String p) throws Exception {
        String body = "{\"username\":\"" + u + "\",\"password\":\"" + p + "\"}";
        String resp = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("data").get("token").asText();
    }
}
