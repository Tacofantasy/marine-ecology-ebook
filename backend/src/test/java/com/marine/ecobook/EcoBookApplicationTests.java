package com.marine.ecobook;

import com.marine.ecobook.common.api.ResultCode;
import com.marine.ecobook.common.exception.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(EcoBookApplicationTests.ExceptionFixtureController.class)
class EcoBookApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUpStatus() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.service").value("marine-ebook-api"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void businessExceptionReturnsItsBusinessCodeAndHttpStatus() throws Exception {
        mockMvc.perform(get("/api/test/business"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_FOUND.code()))
                .andExpect(jsonPath("$.message").value("电子书不存在"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidRequestBodyReturnsValidationMessage() throws Exception {
        mockMvc.perform(post("/api/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.message").value("书名不能为空"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void missingRequestParameterReturnsBadRequestEnvelope() throws Exception {
        mockMvc.perform(get("/api/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.message").value(ResultCode.BAD_REQUEST.message()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void invalidMethodParameterReturnsValidationEnvelope() throws Exception {
        mockMvc.perform(get("/api/test/constraint").param("keyword", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.BAD_REQUEST.code()))
                .andExpect(jsonPath("$.message").value("关键词不能为空"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unsupportedHttpMethodReturnsMethodNotAllowedEnvelope() throws Exception {
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(ResultCode.METHOD_NOT_ALLOWED.code()))
                .andExpect(jsonPath("$.message").value(ResultCode.METHOD_NOT_ALLOWED.message()))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() throws Exception {
        mockMvc.perform(get("/api/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ResultCode.INTERNAL_ERROR.code()))
                .andExpect(jsonPath("$.message").value(ResultCode.INTERNAL_ERROR.message()))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not("database connection failed")))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @TestConfiguration
    @RestController
    @RequestMapping("/api/test")
    static class ExceptionFixtureController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ResultCode.NOT_FOUND, "电子书不存在");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TitleRequest request) {
        }

        @GetMapping("/required")
        void required(@RequestParam String title) {
        }

        @GetMapping("/constraint")
        void constraint(@RequestParam @NotBlank(message = "关键词不能为空") String keyword) {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("database connection failed");
        }
    }

    record TitleRequest(@NotBlank(message = "书名不能为空") String title) {
    }
}
