package it.uniroma3;
// librerie base  
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// librerie per l'indicizzazione e la ricerca 
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.simpletext.SimpleTextCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

/* librerie per generare snippet basati sulle query 
 * dell'utente */ 
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.TokenSources;

import org.jsoup.Jsoup;

public class Index {

    /* index_directory: cartella in cui verrà memorizzato l'indice
     * path: percorso dei file da indicizzare */ 
    public static void createIndex(Directory index_directory, Path path) throws Exception {
        
        /* serve nella fase di debbuging 
         * per vedere cosa è stato scritto nell'indice */
    	Codec codec = new SimpleTextCodec();
        Analyzer defaultAnalyzer = new StandardAnalyzer();
        /* crea una mappa con elementi (campo,analyzer) */
        Map<String, Analyzer> perFieldAnalyzers = new HashMap<String,Analyzer>();
        perFieldAnalyzers.put("titolo", new EnglishAnalyzer());
        perFieldAnalyzers.put("contenuto", new StandardAnalyzer());

        /* necessario per poter usare tutti gli analyzer specificati */
        Analyzer analyzer_wrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, perFieldAnalyzers);
        IndexWriterConfig config = new IndexWriterConfig(analyzer_wrapper);
        /* se è stato definito un codec 
         * impostalo nella configurazione */
        if (codec != null) {
            config.setCodec(codec);
        }
        IndexWriter writer = null;
        // Registra il momento iniziale di indicizzazione
        long startTime = System.currentTimeMillis();
        
        try {
            writer = new IndexWriter(index_directory, config);
            /* cancella tutti i file indicizzati in precedenza
             * necessario quando bisogna fare un aggiornamento */
            writer.deleteAll();
            /* apre la cartella dei file da indicizzare e 
             * crea una lista dei file al suo interno */
        	File dir = new File(path.toString());
            File[] files = dir.listFiles();
            int batchSize = 100;
            int fileCount = 0;  // numero di file indicizzati
            if (files != null) {
                /* crea un documento per ogni file */
                long startTimeBatch = System.currentTimeMillis(); /*inizializza conteggio tempo batch */
	            for (File file : files) {
                    if(file.getName().endsWith(".html")) {
                        org.jsoup.nodes.Document docHtml = Jsoup.parse(file,"UTF-8");
                        String title = docHtml.title();
                        String content = docHtml.body().text(); 
                        String fileName = file.getName();

                        Document document = new Document();
                        /* campo indice: titolo dell'articolo */
	                   document.add(new TextField("titolo",title, Field.Store.YES));
	                   /* campo indice: contenuto del file  */
                        document.add(new TextField("contenuto",content,Field.Store.YES));
                        /* Aggiungi il nome del file come campo memorizzato, ma non indicizzato */
                        document.add(new KeywordField("fileName", fileName, Field.Store.YES));

                        /* ogni documento viene aggiunto all'indice*/
	                   writer.addDocument(document);
                       fileCount++;
 
                       if (fileCount % batchSize == 0) {
                            writer.commit(); // Esegui commit ogni batch di 100 file
                            /*calcola tempo di esecuzione del batch */
                            long endTimeBatch = System.currentTimeMillis();
                            long indexingTimeBatch = endTimeBatch - startTimeBatch;
                            System.out.println("Batch " + (fileCount / batchSize) + " completato in " + indexingTimeBatch + " ms\n");
                            startTimeBatch = System.currentTimeMillis(); /*azzera contatore per il nuovo batch */
                       }
                    }
                    else
                        System.out.println("Errore: formato del file diverso da .html");
 
	            } 
            }
                // Commit finale e chiusura
                writer.commit();
                // Registra il momento finale di indicizzazione 
                long endTime = System.currentTimeMillis();
                // Calcola il tempo trascorso per l'indicizzazione 
                long indexingTime = endTime - startTime;
                System.out.println("Tempo di indicizzazione: " + indexingTime + " ms\n");
                try (IndexReader reader = DirectoryReader.open(writer)) {
                    System.out.println("Documenti indicizzati: " + reader.numDocs());
                }
                System.out.println("Indicizzazione completata.");
            
        } catch(Exception e) { // fine blocco try e inizio catch
            System.out.println("Errore durante l'indicizzazione: " + e.getMessage());
            e.printStackTrace();
 
        }
 
        finally {
            // Chiudi il writer alla fine del processo
            if (writer != null)
              writer.close();
        }
    }  

    /* metodo che permette di eseguire una query su un 
     * indice già creato */
    /* Index searcher è un oggetto che permette di fare la 
     * ricerca all'interno di un indice */
	public static void runQuery(IndexSearcher searcher, Query query, Boolean content) throws IOException {
        /* restituisce i primi 10 documenti che fanno match */
        TopDocs hits = searcher.search(query,10);
        System.out.println("Sono stati trovati " + hits.scoreDocs.length + " " + "documenti\n");

        // In questo modo l'Highlighter evidenzia i termini di ricerca
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<b>", "</b>"); 
        Highlighter highlighter = new Highlighter(formatter, new QueryScorer(query));
        highlighter.setTextFragmenter(new SimpleFragmenter(100)); // Limite di 100 caratteri per frammento


        /* per ogni documento trovato nella ricerca stampa titolo e score */
        for (int i = 0; i < hits.scoreDocs.length; i++) {
 
            ScoreDoc scoreDoc = hits.scoreDocs[i];
 
            Document doc = searcher.doc(scoreDoc.doc);
            // Genera uno snippet con evidenziazione, limitato al campo "contenuto"
            String snippet = null;
            if (content) { // se l'utente ha fatto una ricerca per contenuto
                try {
                    snippet = highlighter.getBestFragment(new StandardAnalyzer(), "contenuto", doc.get("contenuto"));
                    if (snippet != null) 
                        // per visualizzare le parole in grassetto da terminale
                        snippet = snippet.replace("<b>", "\u001B[1m").replace("</b>", "\u001B[0m"); // Reset del formato     
                } 
                catch (Exception e) {
                        e.printStackTrace();
                }
            }
            
            System.out.println("file: " + doc.get("fileName"));
            System.out.println("doc"+scoreDoc.doc + ": "+ doc.get("titolo") + " (Score: "+ scoreDoc.score +")");
            if(content)
                System.out.println("Snippet: " + (snippet != null ? snippet : "Nessun estratto rilevante trovato.") + "(...)\n");
            

        }
        System.out.print("\n");
    }
}
