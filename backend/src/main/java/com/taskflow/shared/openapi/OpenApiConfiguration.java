package com.taskflow.shared.openapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

	@Bean
	OpenAPI taskFlowOpenApi() {
		Components components = new Components()
				.addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
						.scheme("bearer").bearerFormat("JWT")
						.description("Short-lived access token only. Does not replace the HttpOnly refresh-cookie lifecycle."))
				.addSchemas("TaskFlowProblem", new ObjectSchema()
						.required(List.of("type", "title", "status", "detail", "instance", "code"))
						.description("RFC 9457 ProblemDetail. TaskFlow extensions are top-level code and optional validation violations.")
						.addProperty("type", new StringSchema().format("uri"))
						.addProperty("title", new StringSchema())
						.addProperty("status", new IntegerSchema().format("int32"))
						.addProperty("detail", new StringSchema())
						.addProperty("instance", new StringSchema().format("uri-reference"))
						.addProperty("code", new StringSchema())
						.addProperty("violations", new ArraySchema().items(new ObjectSchema()
								.required(List.of("field", "message", "code"))
								.addProperty("field", new StringSchema()).addProperty("message", new StringSchema())
								.addProperty("code", new StringSchema()))));

		addError(components, "AuthenticationRequired", 401, "AUTHENTICATION_REQUIRED", "Authentication required",
				"Authentication is required to access this resource.", "/api/boards");
		addError(components, "InvalidCredentials", 401, "INVALID_CREDENTIALS", "Authentication failed",
				"Invalid email or password.", "/api/auth/login");
		addError(components, "SessionInvalid", 401, "SESSION_INVALID", "Session unavailable",
				"Your session is no longer valid. Please sign in again.", "/api/auth/refresh");
		addError(components, "AccessDenied", 403, "ACCESS_DENIED", "Access denied",
				"You do not have permission to access this resource.", "/api/boards");
		addError(components, "BoardNotFound", 404, "BOARD_NOT_FOUND", "Board not found",
				"The requested board was not found.", "/api/boards/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35");
		addError(components, "ColumnNotFound", 404, "COLUMN_NOT_FOUND", "Column not found",
				"The requested column was not found.", "/api/columns/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35");
		addError(components, "TaskNotFound", 404, "TASK_NOT_FOUND", "Task not found",
				"The requested task was not found.", "/api/tasks/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35");
		addError(components, "ColumnNotEmpty", 409, "COLUMN_NOT_EMPTY", "Column not empty",
				"The column must be empty before it can be deleted.", "/api/columns/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35");
		addError(components, "ColumnOrderConflict", 409, "COLUMN_ORDER_CONFLICT", "Column order conflict",
				"The submitted column order does not match the board's current columns.", "/api/boards/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35/columns/order");
		addError(components, "TaskPlacementConflict", 409, "TASK_PLACEMENT_CONFLICT", "Task placement conflict",
				"The requested task position is not valid for the target column.", "/api/tasks/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35/placement");
		addError(components, "ValidationFailed", 400, "VALIDATION_FAILED", "Request validation failed",
				"One or more request fields are invalid.", "/api/boards");
		addError(components, "MalformedRequest", 400, "MALFORMED_REQUEST", "Malformed request",
				"The request body or parameters could not be read.", "/api/boards");
		addError(components, "InvalidSearchQuery", 400, "INVALID_SEARCH_QUERY", "Invalid search query",
				"Search queries must contain no more than 200 characters.", "/api/boards/8bf24ae0-bd7e-4c3e-b226-38ce5db44b35/tasks/search");
		addError(components, "InternalError", 500, "INTERNAL_ERROR", "Internal server error",
				"An unexpected error occurred.", "/api/boards");
		addError(components, "EmailAlreadyRegistered", 409, "EMAIL_ALREADY_REGISTERED", "Request could not be completed",
				"An account with this email already exists.", "/api/auth/register");
		components.addResponses("InvalidRequest", response("Invalid fields or unreadable body/parameters.", "VALIDATION_FAILED", "MALFORMED_REQUEST"));
		components.addResponses("InvalidSearchRequest", response("Missing/unreadable parameters or query longer than 200 characters after trimming.", "MALFORMED_REQUEST", "INVALID_SEARCH_QUERY"));
		components.addResponses("PlacementNotFound", response("Task or target Column unavailable in the same owned Board.", "TASK_NOT_FOUND", "COLUMN_NOT_FOUND"));
		components.addResponses("CurrentSessionRequired", response("Access token missing/invalid, or its user no longer exists.", "AUTHENTICATION_REQUIRED", "SESSION_INVALID"));

		return new OpenAPI().components(components).info(new Info().title("TaskFlow API").version("0.0.1-SNAPSHOT")
				.description("Authenticated Board / Column / Task management API. "
						+ "Bearer authorization uses short-lived access tokens. Unsafe requests also require the current XSRF-TOKEN cookie value in X-XSRF-TOKEN."));
	}

	@Bean
	OpenApiCustomizer commonOperationDocumentation() {
		return api -> {
			// In OpenAPI 3.1, a nullable type alone does not allow null through an enum constraint.
			Schema<?> createTask = api.getComponents().getSchemas().get("CreateTaskRequest");
			var properties = createTask.getProperties();
			Schema<?> priority = properties.get("priority");
			properties.put("priority", new ComposedSchema().addAnyOfItem(priority)
					.addAnyOfItem(new Schema<>().types(Set.of("null"))));
			api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
				operation.getResponses().addApiResponse("500", new ApiResponse().$ref("#/components/responses/InternalError"));
				if (operation.getSecurity() != null && operation.getSecurity().stream().anyMatch(it -> it.containsKey("bearerAuth"))) {
					operation.getResponses().putIfAbsent("401", new ApiResponse().$ref("#/components/responses/AuthenticationRequired"));
				}
				if (method != PathItem.HttpMethod.GET && method != PathItem.HttpMethod.HEAD && method != PathItem.HttpMethod.OPTIONS) {
					operation.addParametersItem(new Parameter().name("X-XSRF-TOKEN").in("header").required(true)
							.description("Current readable XSRF-TOKEN cookie value. Required even with Bearer; obtain/renew cookie state through /api/auth/csrf and authentication lifecycle responses.")
							.schema(new StringSchema()));
					operation.getResponses().addApiResponse("403", new ApiResponse().$ref("#/components/responses/AccessDenied"));
				}
				// Controllers use inferred JSON responses without restricting runtime content negotiation.
				operation.getResponses().forEach((status, response) -> {
					if (status.startsWith("2") && response.getContent() != null && response.getContent().containsKey("*/*")) {
						response.getContent().addMediaType("application/json", response.getContent().remove("*/*"));
					}
				});
			}));
		};
	}

	private static void addError(Components components, String name, int status, String code, String title, String detail, String instance) {
		Map<String, Object> example = new LinkedHashMap<>();
		example.put("type", "urn:taskflow:problem:" + code.toLowerCase(Locale.ROOT));
		example.put("title", title);
		example.put("status", status);
		example.put("detail", detail);
		example.put("instance", instance);
		example.put("code", code);
		if (code.equals("VALIDATION_FAILED")) {
			example.put("violations", List.of(Map.of("field", "name", "message", "must not be blank", "code", "NotBlank")));
		}
		components.addExamples(code, new Example().value(example));
		components.addResponses(name, response(title + ". " + detail, code));
	}

	private static ApiResponse response(String description, String... codes) {
		MediaType media = new MediaType().schema(new Schema<>().$ref("#/components/schemas/TaskFlowProblem"));
		for (String code : codes) media.addExamples(code, new Example().$ref("#/components/examples/" + code));
		return new ApiResponse().description(description).content(new Content().addMediaType("application/problem+json", media));
	}
}
