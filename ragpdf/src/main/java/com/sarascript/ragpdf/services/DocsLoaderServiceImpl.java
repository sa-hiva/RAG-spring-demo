package com.sarascript.ragpdf.services;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocsLoaderServiceImpl implements DocsLoaderService {

	private final JdbcClient jdbcClient;
	private final VectorStore vectorStore;
	
    @Value("classpath:docs/TFM.pdf")
    private Resource pdfResource1;
    
    @PostConstruct
    @Override
    public void loadDocs() {
    	
    	var count = jdbcClient.sql("select count(*) from vector_store").query(Integer.class).single();
    	
    	if (count > 0) {
    		log.info("La DB no está vacía así que no cargo documentos.");
    		return;
    	}
    	
    	System.out.println("LOADING DOCUMENTS INTO VECTOR STORE");
    	var config = PdfDocumentReaderConfig.builder()
    			.withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
    					.withNumberOfBottomTextLinesToDelete(1)
    					.withNumberOfTopTextLinesToDelete(2)
    					.build()
    					)
    			.withPagesPerDocument(1)
    			.build();
    	
    	var pdfReader = new PagePdfDocumentReader(pdfResource1, config);
    	
//		SIN TOKEN SPLITTER
//    	List<Document> result = pdfReader.get().stream().peek(doc -> log.info("Loading doc: =)" )).toList();
//    	vectorStore.accept(result);
//    	log.info("Loaded {} docs into vector store", result.size());

    	List<Document> rawdocs = pdfReader.get();
    	
    	//Hay que limpiar el texto bruto porque tiene mucho ruido entre medias. Este paso ha demostrado ser súper importante para que me dé respuestas mejores.
    	List<Document> cleanedDocs = rawdocs.stream()
    		    .map(doc -> {
    		        String text = doc.getText();

    		        // dividir en líneas y quitar numeraciones sueltas
    		        text = Arrays.stream(text.split("\n"))
    		                .filter(line -> !line.trim().matches("^\\d+$"))
    		                .collect(Collectors.joining(" "));

    		        // quitar citas tipo [1], [23]
    		        text = text.replaceAll("\\[\\d+\\]", " ");

    		        // quitar citas tipo (Autor, 2023)
    		        text = text.replaceAll("\\([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+,? \\d{4}\\)", " ");

    		        // quitar secciones bibliografía/referencias enteras
    		        if (text.strip().toLowerCase().startsWith("bibliografía") 
    		            || text.strip().toLowerCase().startsWith("referencias")) {
    		            text = "";
    		        }

    		        // normalizar espacios
    		        text = text.replaceAll("\\s+", " ").trim();
    		        return new Document(text, doc.getMetadata());
    		    })
    		    .filter(d -> !d.getText().isBlank()) // descartar vacíos
    		    .toList();



    	// Paso 2: trocear en chunks por tokens
    	TextSplitter splitter = new TokenTextSplitter(400, 50, 50, 1000, false);
    	List<Document> chunks = splitter.apply(cleanedDocs);
    	// Paso 3: guardar en el vector store
    	vectorStore.accept(chunks);
    	log.info("PDF tenía {} páginas -> troceado en {} chunks", rawdocs.size(), chunks.size());
    }
}
