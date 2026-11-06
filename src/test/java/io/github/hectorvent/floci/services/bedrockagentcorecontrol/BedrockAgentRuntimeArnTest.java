package io.github.hectorvent.floci.services.bedrockagentcorecontrol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.core.common.RegionResolver;
import io.github.hectorvent.floci.core.storage.InMemoryStorage;
import io.github.hectorvent.floci.services.bedrockagentcorecontrol.model.AgentRuntime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockAgentRuntimeArnTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void canonicalArnUsesTheRuntimeIdAndStaysStableAcrossVersions() {
        for (String region : List.of("us-east-1", "eu-west-1", "cn-north-1")) {
            BedrockAgentCoreControlService service = new BedrockAgentCoreControlService(
                    new InMemoryStorage<>(), new RegionResolver(region, "000000000000"));
            ObjectNode artifact = mapper.createObjectNode();
            artifact.putObject("containerConfiguration").put("containerUri", "x:latest");
            ObjectNode network = mapper.createObjectNode().put("networkMode", "PUBLIC");
            String partition = region.startsWith("cn-") ? "aws-cn" : "aws";
            String role = "arn:" + partition + ":iam::000000000000:role/agent";
            AgentRuntime runtime = service.createAgentRuntime("arnAgent", artifact, network,
                    role, "first", null, null, null, null, region);
            String expected = "arn:" + partition + ":bedrock-agentcore:" + region
                    + ":000000000000:runtime/" + runtime.getAgentRuntimeId();
            assertEquals(expected, service.arn(runtime, "1", region));
            service.updateAgentRuntime(runtime.getAgentRuntimeId(), artifact, network,
                    role, "second", null, null, null, region);
            assertEquals(expected, service.arn(runtime, "2", region));
        }
    }

    @Test
    void canonicalRuntimeArnsSupportLookupExistenceAndTagRoundTrips() {
        String region = "us-east-1";
        BedrockAgentCoreControlService service = new BedrockAgentCoreControlService(
                new InMemoryStorage<>(), new RegionResolver(region, "000000000000"));
        ObjectNode artifact = mapper.createObjectNode();
        artifact.putObject("containerConfiguration").put("containerUri", "x:latest");
        AgentRuntime runtime = service.createAgentRuntime("lookupAgent", artifact,
                mapper.createObjectNode().put("networkMode", "PUBLIC"),
                "arn:aws:iam::000000000000:role/agent", null, null, null, null, null, region);
        String arn = "arn:aws:bedrock-agentcore:us-east-1:000000000000:runtime/" + runtime.getAgentRuntimeId();
        assertEquals(runtime.getAgentRuntimeId(), assertDoesNotThrow(() -> service.getAgentRuntimeByArn(region, arn))
                .getAgentRuntimeId());
        assertTrue(service.runtimeArnExists(region, arn));
        assertFalse(service.runtimeArnExists(region, "arn:aws:bedrock-agentcore:us-east-1:000000000000:runtime/missing"));
        service.tagByArn(region, arn, Map.of("env", "test"));
        assertEquals(Map.of("env", "test"), service.getTagsByArn(region, arn));
        service.untagByArn(region, arn, List.of("env"));
        assertEquals(Map.of(), service.getTagsByArn(region, arn));
    }
}
