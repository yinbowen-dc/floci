package io.github.hectorvent.floci.services.lambdamicrovms;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class LambdaMicrovmRequestedVersionIntegrationTest {
    private static final String PREFIX = "/2025-09-09";

    @Test
    void requestedVersionSurvivesRunGetAndListWhileOmissionUsesLatest() {
        String arn = request().body(image("s3://bucket/first.zip")).post(PREFIX + "/microvm-images")
                .then().statusCode(201).extract().path("imageArn");
        request().body(image("s3://bucket/second.zip")).put(PREFIX + "/microvm-images/" + arn)
                .then().statusCode(200);
        String firstId = null;
        String latestId = null;
        try {
            JsonPath first = request().body(Map.of("imageIdentifier", arn, "imageVersion", "1.0"))
                    .post(PREFIX + "/microvms").then().statusCode(200).extract().jsonPath();
            firstId = first.getString("microvmId");
            assertEquals("1.0", first.getString("imageVersion"));
            assertEquals("1.0", request().get(PREFIX + "/microvms/" + firstId).then().statusCode(200)
                    .extract().path("imageVersion"));
            assertEquals("1.0", request().get(PREFIX + "/microvms").then().statusCode(200)
                    .extract().path("items.find { it.microvmId == '" + firstId + "' }.imageVersion"));
            JsonPath latest = request().body(Map.of("imageIdentifier", arn))
                    .post(PREFIX + "/microvms").then().statusCode(200).extract().jsonPath();
            latestId = latest.getString("microvmId");
            assertEquals("2.0", latest.getString("imageVersion"));
        } finally {
            if (firstId != null) {
                request().delete(PREFIX + "/microvms/" + firstId).then().statusCode(200);
            }
            if (latestId != null) {
                request().delete(PREFIX + "/microvms/" + latestId).then().statusCode(200);
            }
            request().delete(PREFIX + "/microvm-images/" + arn).then().statusCode(200);
        }
    }

    @Test
    void missingVersionDoesNotCreateAMicrovm() {
        String arn = request().body(image("s3://bucket/first.zip")).post(PREFIX + "/microvm-images")
                .then().statusCode(201).extract().path("imageArn");
        int before = request().get(PREFIX + "/microvms").then().statusCode(200).extract().jsonPath().getList("items").size();
        try {
            request().body(Map.of("imageIdentifier", arn, "imageVersion", "99.0")).post(PREFIX + "/microvms")
                    .then().statusCode(404);
            assertEquals(before, request().get(PREFIX + "/microvms").then().statusCode(200)
                    .extract().jsonPath().getList("items").size());
        } finally {
            request().delete(PREFIX + "/microvm-images/" + arn);
        }
    }

    private Map<String, Object> image(String artifact) {
        return Map.of("name", "version-" + UUID.randomUUID(),
                "baseImageArn", "arn:aws:lambda:us-east-1:aws:microvm-image:al2023-1",
                "buildRoleArn", "arn:aws:iam::731300000001:role/build", "codeArtifact", Map.of("uri", artifact));
    }

    private RequestSpecification request() {
        return given().contentType("application/json").header("Authorization",
                "AWS4-HMAC-SHA256 Credential=731300000001/20261106/us-east-1/lambda/aws4_request");
    }
}
