import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import static com.openai.core.ObjectMappers.jsonMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.core.JsonValue;

public class Main {
    public static void main(String[] args) throws JsonProcessingException, IOException, InterruptedException {
        String prompt = null;
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                prompt = args[i + 1];
            }
        }

        if (prompt == null || prompt.isEmpty()) {
            throw new RuntimeException("error: -p flag is required");
        }

        String apiKey = System.getenv("OPENROUTER_API_KEY");
        String baseUrl = System.getenv("OPENROUTER_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "https://openrouter.ai/api/v1";
        }

        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("OPENROUTER_API_KEY is not set");
        }

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        //Read tool parameters
        Map<String, Object> filePathSchema = new HashMap<String, Object>();
        filePathSchema.put("type", "string");
        filePathSchema.put("description", "The path to the file to read");

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("file_path", filePathSchema);

        List<String> required = Arrays.asList("file_path");

        Map<String, Object> parameters = new HashMap<String, Object>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        ChatCompletionTool readTool =
                ChatCompletionTool.builder()
                        .type(JsonValue.from("function"))
                        .function(FunctionDefinition.builder()
                                        .name("Read")
                                        .description("Read and return the contents of a file")
                                        .parameters(JsonValue.from(parameters))
                                        .build()).build();

        

        ChatCompletionCreateParams.Builder conversation =
                ChatCompletionCreateParams.builder()
                        .model("anthropic/claude-haiku-4.5")
                        .addTool(readTool)
                        .addUserMessage(prompt);
        
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.err.println("Logs from your program will appear here!");

        //Agent loop implementation
        int totalIterations = 25;
        for (int iter = 0; iter < totalIterations; iter++) {
            ChatCompletion response = client.chat().completions().create(conversation.build());
            if (response.choices().isEmpty()) throw new RuntimeException("no choices in response");

            ChatCompletionMessage message = response.choices().get(0).message();

            // Always append assistant message
            conversation.addMessage(message);

            if (message.toolCalls().isPresent() && !message.toolCalls().get().isEmpty()) {
                for (Object toolCallObj : message.toolCalls().get()) {
                    JsonNode toolCallNode = jsonMapper().valueToTree(toolCallObj);

                    String toolCallId = toolCallNode.path("id").asText(null);
                    String toolName = toolCallNode.path("function").path("name").asText(null);
                    String arguments = toolCallNode.path("function").path("arguments").asText(null);

                    if (toolCallId == null) {
                        throw new RuntimeException("tool call missing id");
                    }

                    if (toolName == null) {
                        throw new RuntimeException("Tool name not specified");
                    }

                    if (arguments == null) {
                        throw new RuntimeException("File path not specified in arguments");
                    }

                    if (!"Read".equals(toolName))
                        throw new RuntimeException("unsupported tool: " + toolName);

                    JsonNode argsNode = jsonMapper().readTree(arguments);
                    JsonNode filePathNode = argsNode.get("file_path");
                    if (filePathNode == null || filePathNode.isNull()) {
                        throw new RuntimeException("Read tool call missing file_path");
                    }

                    String filePath = filePathNode.asText();
                    byte[] bytes = Files.readAllBytes(Paths.get(filePath));
                    String toolResult = new String(bytes, StandardCharsets.UTF_8);

                    conversation.addMessage(
                            ChatCompletionToolMessageParam.builder()
                                    .toolCallId(toolCallId)
                                    .content(toolResult)
                                    .build());
                }
                continue;
            }
            System.out.print(message.content().orElse(""));
            System.out.flush();
            return;
        }
        throw new RuntimeException("agent loop exceeded max iterations (" + totalIterations + ")");
    }
}

