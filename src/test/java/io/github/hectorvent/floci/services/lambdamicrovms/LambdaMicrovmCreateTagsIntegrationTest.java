package io.github.hectorvent.floci.services.lambdamicrovms;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class LambdaMicrovmCreateTagsIntegrationTest {
    private static final String PATH = "/2025-09-09/microvm-images";

    @Test
    void createTagsAreVisibleThroughBothReadsWithoutChangingTheCreateShape() {
        Map<String, String> tags = Map.of("team", "sandbox", "purpose", "create-test");
        Map<String, Object> body = image();
        body.put("tags", tags);
        JsonPath created = request().body(body).post(PATH).then().statusCode(201)
                .extract().jsonPath();
        String arn = created.getString("imageArn");
        try {
            assertNull(created.get("tags"));
            assertEquals(tags, request().get(PATH + "/" + arn).then().statusCode(200).extract().path("tags"));
            assertEquals(tags, request().get("/2017-03-31/tags/" + arn).then().statusCode(200).extract().path("Tags"));
        } finally {
            request().delete(PATH + "/" + arn).then().statusCode(200);
        }
    }

    @Test
    void omittedTagsRemainEmpty() {
        String arn = request().body(image()).post(PATH).then().statusCode(201).extract().path("imageArn");
        try {
            assertEquals(Map.of(), request().get(PATH + "/" + arn).then().statusCode(200).extract().path("tags"));
        } finally {
            request().delete(PATH + "/" + arn).then().statusCode(200);
        }
    }

    private Map<String, Object> image() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "tags-" + UUID.randomUUID());
        body.put("baseImageArn", "arn:aws:lambda:us-east-1:aws:microvm-image:al2023-1");
        body.put("buildRoleArn", "arn:aws:iam::731500000001:role/build");
        body.put("codeArtifact", Map.of("uri", "s3://bucket/code.zip"));
        return body;
    }

    private RequestSpecification request() {
        return given().contentType("application/json").header("Authorization",
                "AWS4-HMAC-SHA256 Credential=731500000001/20261106/us-east-1/lambda/aws4_request");
    }
}
