package com.praise.auth.web;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerThenLoginReturnsTheSameUser() throws Exception {
        MvcResult registered = register("loginuser", "password123");
        Integer registeredId = JsonPath.read(registered.getResponse().getContentAsString(), "$.user.id");

        MvcResult loggedIn = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"LoginUser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("loginuser"))
                .andReturn();
        Integer loggedInId = JsonPath.read(loggedIn.getResponse().getContentAsString(), "$.user.id");

        assertEquals(registeredId, loggedInId);
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        register("wronguser", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"wronguser\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void duplicateUsernameIsRejected() throws Exception {
        register("dupuser", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"dupuser\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"shortuser\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", not(empty())));
    }

    @Test
    void gratitudesWithoutATokenAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/gratitudes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oneUserCannotReadAnotherUsersEntry() throws Exception {
        String tokenA = token("owner", "password123");
        MvcResult created = mockMvc.perform(post("/api/gratitudes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Morning\",\"content\":\"Quiet coffee\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Morning"))
                .andReturn();
        Integer entryId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        String tokenB = token("other", "password123");
        mockMvc.perform(get("/api/gratitudes/" + entryId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/gratitudes")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));

        mockMvc.perform(get("/api/gratitudes/" + entryId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Quiet coffee"));
    }

    @Test
    void ownerCanUpdateAndDelete() throws Exception {
        String tokenA = token("editor", "password123");
        MvcResult created = mockMvc.perform(post("/api/gratitudes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Walk\",\"content\":\"Leaves\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Integer entryId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/gratitudes/" + entryId)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Walk\",\"content\":\"Gold leaves\",\"moodTag\":\"calm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moodTag").value("calm"))
                .andExpect(jsonPath("$.title").value("Walk"));

        mockMvc.perform(delete("/api/gratitudes/" + entryId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/gratitudes/" + entryId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
    }

    private MvcResult register(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn();
    }

    private String token(String username, String password) throws Exception {
        return JsonPath.read(register(username, password).getResponse().getContentAsString(), "$.token");
    }
}
