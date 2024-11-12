package it.uniroma3;

import it.uniroma3.Index;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File;
import java.util.Scanner;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
 
public class SearchApplication {

    // Metodo per verificare se l'indice esiste
    private static boolean isIndexExists(Path indexPath) {
         File indexDirectory = indexPath.toFile();
        // Verifica che la directory esista e sia effettivamente una directory
        if (!indexDirectory.exists() || !indexDirectory.isDirectory()) {
            return false;
        }
        // Controlla se la directory contiene effettivamente dei file visibili o se ci sono file di lock
        String[] files = indexDirectory.list();
        if (files != null && files.length > 0) {
            // Aggiungi qui un controllo sui file di lock di Lucene, se necessario
            for (String file : files) {
                if (file.endsWith("write.lock")) {
                    return true; // Indica che l'indice esiste a causa del file di lock
                }
            }
        }
        return false; // Nessun file di lock o altri file, quindi l'indice non esiste
}

    public static void main(String args[]) throws Exception {
        
        Directory directory = null;
        Scanner scanner = new Scanner(System.in);
        Long start_searchTime,end_searchTime;
        Long searchTime; 
        /* file da indicizzare */
        Path path = Paths.get("/users/giorgia/ingegneria-dei-dati/all_htmls");
        Path indexPath = Paths.get("/users/giorgia/ingegneria-dei-dati/homework2/index");

        System.out.println(path.toString());
 
        try {

            directory = FSDirectory.open(indexPath);
            // Verifica se l'indice esiste già
            if (isIndexExists(indexPath)) 
                System.out.println("Indice trovato, caricamento...");
            else {
                System.out.println("Creazione dell'indice in corso...");
                Index.createIndex(directory, path);
            }
            IndexReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            Boolean exit = false; // per interrompere la ricerca 
            Boolean content; // l'utente fa una ricerca per contenuto
            while (!exit) {
                
                System.out.println("Vuoi fare una ricerca per titolo o per contenuto?");
                System.out.print("[T/C]? ");
                String field = scanner.nextLine();
                
                System.out.print("Inserisci una query: ");
                String input_query = scanner.nextLine(); 
                System.out.println("Ricerca dei risultati in corso...");
                Query query;
                QueryParser queryParser; 
                
                
                start_searchTime = System.currentTimeMillis();
                if (field.equals("T")) {
                    content = false; 
                    queryParser = new QueryParser("titolo", new EnglishAnalyzer());
                    query = queryParser.parse(input_query);
                    Index.runQuery(searcher,query,content);

                    } 
                else if (field.equals("C")) {
                    content = true;
                    queryParser = new QueryParser("contenuto", new EnglishAnalyzer());
                    query = queryParser.parse(input_query);
                    Index.runQuery(searcher, query,content);

                   } 
                 else 
                    System.out.println("Campo non valido");
                
                end_searchTime = System.currentTimeMillis();
                searchTime = end_searchTime - start_searchTime; 
                System.out.print("\nTempo di ricerca: "+ searchTime); 
                System.out.print("\nVuoi uscire [y/n]? ");
                String exit_status = scanner.nextLine().trim(); 
                if(exit_status.equalsIgnoreCase("y"))
                    exit = true; 

            } 
        
        } catch (Exception e) {

            e.printStackTrace();

        } finally {

            directory.close();
            scanner.close();
        }
    }
}
 