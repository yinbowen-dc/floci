package io.github.hectorvent.floci.services.lambdamicrovms;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class LambdaMicrovmVersionArtifactIntegrationTest {
    private static final String PATH = "/2025-09-09/microvm-images";

    @Test
    void oldVersionsKeepTheirBuildSpecificationAfterAnUpdate() {
        String arn = request().body(image("first")).post(PATH).then().statusCode(201).extract().path("imageArn");
        try {
            assertEquals("s3://bucket/first.zip", artifact(arn, "1.0"));
            request().body(image("second")).put(PATH + "/" + arn).then().statusCode(200);
            assertEquals("s3://bucket/first.zip", artifact(arn, "1.0"));
            assertEquals("s3://bucket/second.zip", artifact(arn, "2.0"));
            assertEquals("arn:aws:iam::731200000001:role/first", request()
                    .get(PATH + "/" + arn + "/versions/1.0").then().statusCode(200).extract().path("buildRoleArn"));
            assertEquals("s3://bucket/first.zip", request().get(PATH + "/" + arn + "/versions")
                    .then().statusCode(200).extract().path("items.find { it.imageVersion == '1.0' }.codeArtifact.uri"));
            assertEquals("s3://bucket/second.zip", request().get(PATH + "/" + arn + "/versions")
                    .then().statusCode(200).extract().path("items.find { it.imageVersion == '2.0' }.codeArtifact.uri"));
        } finally {
            request().delete(PATH + "/" + arn).then().statusCode(200);
        }
    }

    private String artifact(String arn, String version) {
        return request().get(PATH + "/" + arn + "/versions/" + version).then().statusCode(200)
                .extract().path("codeArtifact.uri");
    }

    private Map<String, Object> image(String artifact) {
        return Map.of("name", "artifact-" + UUID.randomUUID(),
                "baseImageArn", "arn:aws:lambda:us-east-1:aws:microvm-image:al2023-1",
                "buildRoleArn", "arn:aws:iam::731200000001:role/" + artifact,
                "codeArtifact", Map.of("uri", "s3://bucket/" + artifact + ".zip"));
    }

    private RequestSpecification request() {
        return given().contentType("application/json").header("Authorization",
                "AWS4-HMAC-SHA256 Credential=731200000001/20261106/us-east-1/lambda/aws4_request");
    }
}
