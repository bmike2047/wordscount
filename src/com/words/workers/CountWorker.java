package com.words.workers;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.ReturnDocument;
import com.words.utils.FirstRun;
import com.words.utils.LogSetup;
import com.words.workers.beans.WordBean;
import com.words.workers.beans.WorkerBean;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import java.util.stream.Stream;
import org.bson.Document;

/**
 *
 * @author BMihai
 */
public class CountWorker {

    private static final Logger log = Logger.getLogger(CountWorker.class.getName());
    public static final String DBNAME = "file_stats";
    public static final String WORKERS_COLL = "workers";
    public static final String RESULTS_COLL = "results";
    public static final String FINAL_COLL = "final_results";
    
    public static MongoDatabase db;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException, InterruptedException, Exception {

        LogSetup.setupLog(log);

        String filePath = System.getProperty("file") != null ? System.getProperty("file") : "/home/claims/Desktop/1.xml";
        String chunks = System.getProperty("chunks") != null ? System.getProperty("chunks") : "10";
        String mongoHost = System.getProperty("mongoHost") != null ? System.getProperty("mongoHost") : "localhost1";
        String mongoPort = System.getProperty("mongoPort") != null ? System.getProperty("mongoPort") : "27017";
        String workerName = System.getProperty("workerName") != null ? System.getProperty("workerName") : "work";
        String bulkSave = System.getProperty("bulkSave") != null ? System.getProperty("bulkSave") : "30";
        String createDB = System.getProperty("createDB") != null ? System.getProperty("createDB") : "false";
        String collector = System.getProperty("collector") != null ? System.getProperty("collector") : "false";

        setupDB(mongoHost, Integer.valueOf(mongoPort));
        if (Boolean.parseBoolean(createDB)) {
            db.drop();
            setupResults();
            FirstRun.setupEverything(filePath, Integer.valueOf(chunks));
        }

        while (!allWorkersAreDone(Long.valueOf(chunks))) {
            WorkerBean wb = findAndLockFreeWorker(workerName);
            if (wb != null) {
                workerProcessData(wb, filePath,Long.valueOf(bulkSave));
            } else {
                if (Boolean.parseBoolean(collector)) {
                    log.info("All data processed. Waiting for other workers to finish");
                    Thread.sleep(15000);
                }
            }
        }
        if (Boolean.parseBoolean(collector)) {
            Collector.collectDataFromWorkers();
            log.info("Collecting all data completed");
        } else {
            log.info("Worker " + workerName + " completed processing all his data. Nothing to do, exiting.");
        }
    }

    private static void setupResults() {
        CountWorker.db.createCollection(RESULTS_COLL); //redundant?
        MongoCollection<Document> res = db.getCollection(RESULTS_COLL);
        res.createIndex(Indexes.ascending("word"));
    }

    /**
     * If all workers are done will call the collector
     *
     * @return
     */
    private static boolean allWorkersAreDone(long chunks) {
        MongoCollection<Document> coll = db.getCollection(WORKERS_COLL);
        Document wk = coll.find(or(eq("status", "FREE"), eq("status", "IN_PROCESSING"))).first();
        return wk == null;

    }

    /**
     * Find a free worker, use atomic operation to avoid concurrency problems
     *
     * @param name
     * @return
     */
    private static WorkerBean findAndLockFreeWorker(String name) {
        MongoCollection<Document> coll = db.getCollection(WORKERS_COLL);
        Document workDoc;
        workDoc = coll.find(and(eq("workerName", name), eq("status", "IN_PROCESSING"))).first();
        if (workDoc != null) {
            return WorkerBean.toJava(workDoc);//failover, recover aborted worker
        }
        //atomic
        workDoc = coll.findOneAndUpdate(new Document("status", "FREE"), new Document("$set", new Document("status", "IN_PROCESSING").append("workerName", name)), new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
        return workDoc != null ? WorkerBean.toJava(workDoc) : null;
    }

    /**
     * detect if first run
     *
     * @return
     */
    private static boolean isFirstRun() {
        MongoCollection<Document> coll = db.getCollection(WORKERS_COLL);
        return coll.count() <= 0;
    }

    /**
     * setup mongodb
     *
     * @param host
     * @param port
     */
    private static void setupDB(String host, int port) {

        MongoClient mongoClient = new MongoClient(host, port);
        db = mongoClient.getDatabase(DBNAME);
  
    }

    /**
     * Process file chunk
     *
     * @param workers
     * @param filePath
     */
    private static void workerProcessData(WorkerBean worker, String filePath, long bulk) {

        MongoCollection<Document> coll = db.getCollection(WORKERS_COLL);
        log.info("****************************************************************");
        log.info("Starting file reading for worker: " + worker.getWorkerName());

        try {

            long start = worker.getChunkStartOffset();
            long end = worker.getChunkEndOffset();
            Path path = Paths.get(filePath);
            ByteBuffer bf;
            try (SeekableByteChannel sbc = Files.newByteChannel(path, StandardOpenOption.READ)) {
                bf = ByteBuffer.allocate((int) (end - start));
                sbc.position(start);
                sbc.read(bf);
                bf.clear();
            }

            InputStream bInput = new ByteArrayInputStream(bf.array());
            InputStreamReader isr = new InputStreamReader(bInput);
            Stream<String> lines = new BufferedReader(isr).lines();

            log.info("File reading finished for worker : " + worker.toString());
            System.gc();
            Thread.sleep(1000L); //sleep a little to allow gc to do his stuff
            log.info("Starting to process data....");
            //TODO: use parallel on a production real multicore machine
            Map<String, Long> wordCount = lines.flatMap(line -> Arrays.stream(line.trim().split("\\W+"))) //we exclude all xml tags
                    .map(word -> word.replaceAll("[^a-zA-Z]", "").toLowerCase().trim()) //we exclude all numbers
                    .filter(word -> word.length() > 0)
                    .map(word -> new WordBean(word, 1))
                    .collect(groupingBy(WordBean::getWord, counting()));

            persistWorkerResults(wordCount, bulk, worker.getWorkerName());
            log.info("Worker finished processing: " + worker.toString() + " found: " + wordCount.size() + " words");
            wordCount.clear();
            coll.updateOne(new Document("workerName", worker.getWorkerName()).append("status", "IN_PROCESSING"), new Document("$set", new Document("status", "COMPLETED")));
            log.info("Worker finished persisting result");
            wordCount = null;
            printMemStats();
            log.info("****************************************************************\n");
            Thread.sleep(1000L);

        } catch (IOException | InterruptedException ex) {
            log.log(Level.SEVERE, "Exception occur", ex);
        }

    }

    private static void persistWorkerResults(Map<String, Long> processedMap, long bulk, String workerName) {
        log.info("Persisting results: ");
        MongoCollection<Document> res = db.getCollection(RESULTS_COLL);
        //divide in small bulks to avoid out of memory on mongodb driver
        long counter = 1;
        List<Document> resList = new ArrayList<>();
        for (Map.Entry<String, Long> entry : processedMap.entrySet()) {
            resList.add(new Document("word", entry.getKey()).append("count", entry.getValue()).append("workerName", workerName));
            if (counter % bulk == 0) {
                res.insertMany(resList, new InsertManyOptions().ordered(false));
                log.info("Persisted: bulk " + counter);
                resList.clear();
            }
            counter++;
        }
        if (resList.size() > 0) {
            res.insertMany(resList, new InsertManyOptions().ordered(false));
            log.info("Persisted last bulk");
            resList.clear();
        }

    }

    public static void printMemStats() {
        Runtime runtime = Runtime.getRuntime();
        // Run the garbage collector
        runtime.gc();
        // Calculate the used memory
        long memory = runtime.totalMemory() - runtime.freeMemory();
        log.info("Used memory is megabytes: " + memory / (1024L * 1024L) + "M");
    }

}
