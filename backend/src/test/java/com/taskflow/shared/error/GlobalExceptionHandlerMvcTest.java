package com.taskflow.shared.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerMvcTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void returnsValidationErrorThroughTheMvcExceptionPipeline() throws Exception {
		mockMvc.perform(post("/api/error-test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.timestamp").isNotEmpty())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.message").value("Request validation failed."))
				.andExpect(jsonPath("$.path").value("/api/error-test/validation"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("must not be blank"));
	}

	@Test
	void returnsSafeBadRequestForMalformedJson() throws Exception {
		mockMvc.perform(post("/api/error-test/malformed-json")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
				.andExpect(jsonPath("$.message").value("The request is invalid."))
				.andExpect(jsonPath("$.path").value("/api/error-test/malformed-json"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void returnsSafeNotFoundErrorWithRequestPathOnly() throws Exception {
		mockMvc.perform(get("/api/error-test/not-found?submittedSecret=do-not-return-this"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("The requested resource was not found."))
				.andExpect(jsonPath("$.path").value("/api/error-test/not-found"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andExpect(jsonPath("$.submittedSecret").doesNotExist());
	}

	@Test
	void returnsSafeConflictError() throws Exception {
		mockMvc.perform(get("/api/error-test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.errorCode").value("RESOURCE_CONFLICT"))
				.andExpect(jsonPath("$.message").value("The request conflicts with the current resource state."))
				.andExpect(jsonPath("$.path").value("/api/error-test/conflict"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void returnsSafeUnexpectedError() throws Exception {
		mockMvc.perform(get("/api/error-test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_SERVER_ERROR"))
				.andExpect(jsonPath("$.message").value("An unexpected error occurred."))
				.andExpect(jsonPath("$.path").value("/api/error-test/unexpected"))
				.andExpect(jsonPath("$.fieldErrors").doesNotExist())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database password"))));
	}

	@RestController
	@RequestMapping("/api/error-test")
	static class ErrorTestController {

		@PostMapping("/validation")
		void validation(@Valid @RequestBody ValidationRequest request) {
		}

		@PostMapping("/malformed-json")
		void malformedJson(@RequestBody JsonRequest request) {
		}

		@GetMapping("/not-found")
		void notFound() {
			throw new ResourceNotFoundException("sensitive internal detail");
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new ResourceConflictException("sensitive internal detail");
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("database password is secret");
		}
	}

	private record ValidationRequest(@NotBlank String name) {
	}

	private record JsonRequest(String name) {
	}
}
