package com.sarascript.ragpdf.controllers;


import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    
    @Value("classpath:prompts/chat.ai.st")
    private Resource stPromptTemplate;
    
    public ChatController(ChatClient.Builder chatBuilder, VectorStore vectorStore) {
    	this.chatClient = chatBuilder.defaultAdvisors().build();
    	this.vectorStore = vectorStore;
    }
	
    @GetMapping("/chat")
    public String generateResponse(@RequestParam String query) throws Exception {
        // Cargar el template del prompt que hemos creado en el chat.ai.st
    	System.out.println("UNO");
        PromptTemplate promptTemplate = new PromptTemplate(stPromptTemplate);

     
        // Construir parámetros del prompt (le metemos la query que viene de la llamada en "input", y en "documents" el resultado de la búsqueda por similitud en nuestra DB Vectorial)
        System.out.println("DOS");
        var promptParameters = new HashMap<String, Object>();
        promptParameters.put("input", query);
        promptParameters.put("documents", String.join("\n", this.findSimilarDocuments(query)));

        // Crear prompt en base a nuestros parámetros definidos arriba
        System.out.println("CUATRO");
        Prompt prompt = promptTemplate.create(promptParameters);

        // Generar respuesta
        System.out.println("CINCO");
        var response = chatClient.prompt(prompt).call().chatResponse();
        return response.getResult().getOutput().getText();
    }
    
    
    private String findSimilarDocuments(String query) {
        List<Document> similarDocuments = vectorStore.similaritySearch(
            SearchRequest.builder().query(query).topK(3).build()
        );

        //Loggeo para ver los chunks que hemos traído en cada llamada y comprobar qué tal está funcionando esto
        similarDocuments.forEach(d -> log.info("Retrieved chunk: {}", d.getText()));

        //Hago el return limitando la longitud de los chunks concatenados para que no sea muy largo, pero esto se puede quitar
        return similarDocuments.stream()
            .map(Document::getText)
            .map(t -> t.length() > 1000 ? t.substring(0, 1000) + "..." : t)
            .map(t -> "DOCUMENT:\n" + t + "\n---\n")
            .collect(Collectors.joining("\n"));
    }
	
}