package com.taskflow.shared.error;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskflow.shared.web.ApiPaths;

class GlobalExceptionHandlerMvcTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ErrorTestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.alwaysExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.alwaysExpect(jsonPath("$.exception").doesNotExist())
			.alwaysExpect(jsonPath("$.trace").doesNotExist())
			.build();

	@Test
	void returnsProblemDetailValidationErrorThroughMvc() throws Exception {
		mockMvc.perform(post(ApiPaths.API + "/error-test/validation")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.type").value("urn:taskflow:problem:validation_failed"))
				.andExpect(jsonPath("$.title").value("Request validation failed"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("One or more request fields are invalid."))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.instance").value("/api/error-test/validation"))
				.andExpect(jsonPath("$.violations[0].field").value("name"))
				.andExpect(jsonPath("$.violations[0].message").value("must not be blank"))
				.andExpect(jsonPath("$.violations[0].code").value("NotBlank"));
	}

	@Test
	void returnsProblemDetailForMalformedJson() throws Exception {
		mockMvc.perform(post(ApiPaths.API + "/error-test/malformed-json")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(jsonPath("$.violations").doesNotExist());
	}

	@Test
	void returnsSafeNotFoundError() throws Exception {
		mockMvc.perform(get(ApiPaths.API + "/error-test/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("The requested resource does not exist."));
	}

	@Test
	void returnsSafeConflictError() throws Exception {
		mockMvc.perform(get(ApiPaths.API + "/error-test/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
				.andExpect(jsonPath("$.detail").value("The request conflicts with the current resource state."));
	}

	@Test
	void returnsSafeUnexpectedError() throws Exception {
		mockMvc.perform(get(ApiPaths.API + "/error-test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
	}

	@Test
	void returnsSafeBadRequestForInvalidParameterType() throws Exception {
		mockMvc.perform(get(ApiPaths.API + "/error-test/parameter").param("count", "secret-input"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-input"))));
	}

	@Test
	void returnsBadRequestForMissingParameter() throws Exception {
		mockMvc.perform(get(ApiPaths.API + "/error-test/parameter"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
	}

	@Test
	void preservesUnsupportedMediaTypeStatus() throws Exception {
		mockMvc.perform(post(ApiPaths.API + "/error-test/validation")
				.contentType(MediaType.TEXT_PLAIN).content("secret-input"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("REQUEST_FAILED"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-input"))));
	}

	@RestController
	@RequestMapping(ApiPaths.API + "/error-test")
	static class ErrorTestController {

		@GetMapping("/parameter")
		void parameter(@RequestParam int count) {
		}

		@PostMapping("/validation")
		void validation(@Valid @RequestBody ValidationRequest request) {
		}

		@PostMapping("/malformed-json")
		void malformedJson(@RequestBody JsonRequest request) {
		}

		@GetMapping("/not-found")
		void notFound() {
			throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
					"RESOURCE_NOT_FOUND", "The requested resource does not exist.");
		}

		@GetMapping("/conflict")
		void conflict() {
			throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
					"RESOURCE_CONFLICT", "The request conflicts with the current resource state.");
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
