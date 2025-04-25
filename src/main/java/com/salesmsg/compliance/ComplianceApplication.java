package com.salesmsg.compliance;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootApplication
public class ComplianceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComplianceApplication.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ChatClient chatClient) {
		return args -> {

			final String MESSAGE_VALIDATION_PROMPT = """
            You are an SMS Message Compliance Expert for 10DLC campaigns. Your task is to evaluate
            if the provided sample messages comply with carrier requirements and SMS best practices.
            
            Business Type: %s
            Use Case: %s
            Should Include Phone Number: %s
            Should Include Links: %s
            
            Analyze each message and check for:
            1. Proper identification of the business/sender
            2. Clear opt-out instructions (STOP keyword)
            3. No prohibited content (gambling, adult content, etc.)
            4. Appropriate message length (message parts should be 160 characters or fewer)
            5. No excessive use of capital letters, exclamation points, or URLs
            6. No misleading or deceptive content
            7. Alignment with the stated use case
            8. Proper inclusion of phone numbers and links if specified
            
            Provide your analysis in JSON format with the following structure:
            {
              "overall_compliant": boolean,
              "overall_score": float (0-100),
              "message_results": [
                {
                  "text": string,
                  "compliant": boolean,
                  "matches_use_case": boolean,
                  "has_required_elements": boolean,
                  "missing_elements": [string],
                  "issues": [
                    {
                      "severity": "critical" | "major" | "minor",
                      "description": "string"
                    }
                  ],
                  "suggested_revision": string
                }
              ],
              "recommendations": [string]
            }
            """;

			String systemPrompt = String.format(
					MESSAGE_VALIDATION_PROMPT,
					"My Use Case",
					"My Use Case",
					Boolean.TRUE.equals("Yes") ? "Yes" : "No",
					Boolean.TRUE.equals("Yes") ? "Yes" : "No"
			);

			// Prepare the messages for analysis
			StringBuilder userMessageContent = new StringBuilder("Please analyze these sample messages for 10DLC compliance:\n\n");


			// Create the prompt
			List<Message> promptMessages = new ArrayList<>();
			promptMessages.add(new SystemMessage(systemPrompt));
			promptMessages.add(new UserMessage(userMessageContent.toString()));
			Prompt prompt = new Prompt(promptMessages);

			// Execute the chat completion
			ChatResponse response = chatClient
					.prompt(prompt)
					.call()
					.chatResponse();

			String responseText = response.getResult().getOutput().getText();

			System.out.println(responseText);


		};
	}

}
