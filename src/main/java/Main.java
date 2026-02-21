import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.FunctionDefinition;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTool;
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

        ChatCompletion response =
                client.chat()
                        .completions()
                        .create(
                                ChatCompletionCreateParams.builder()
                                        .model("anthropic/claude-haiku-4.5")
                                        .addUserMessage(prompt)
                                        .addTool(readTool)
                                        .build());
        
        if (response.choices().isEmpty()) {
            throw new RuntimeException("no choices in response");
        }
        
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.err.println("Logs from your program will appear here!");

        System.out.print(response.choices().get(0).message().content().orElse(""));
        
    }
}

