package com.words.workers;

import com.mongodb.client.MapReduceIterable;
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Indexes.ascending;
import static com.mongodb.client.model.Indexes.descending;
import com.mongodb.client.model.MapReduceAction;
import com.words.utils.LogSetup;
import static com.words.workers.CountWorker.FINAL_COLL;
import static com.words.workers.CountWorker.RESULTS_COLL;
import static com.words.workers.CountWorker.db;
import java.util.logging.Logger;
import org.bson.Document;

/**
 * This class will collect all results form the workers and display them to the
 * user
 *
 * @author BMihai
 */
public class Collector {

    private static final Logger log = Logger.getLogger(Collector.class.getName());

    /**
     * Merge all workers results in one
     *
     * @param bulk
     * @param workers
     * @return
     */
    public static void collectDataFromWorkers() {
        LogSetup.setupLog(log);

        log.info("****************************************************************");
        log.info("Collector started");
        log.info("****************************************************************");

        MongoCollection<Document> coll = db.getCollection(RESULTS_COLL);

        String map = "function(){emit(this.word,this.count);}";
        String reduce = "function(key,values){return Array.sum(values)}";

        MapReduceIterable out = coll.mapReduce(map, reduce)
                .action(MapReduceAction.REPLACE)
                .collectionName(CountWorker.FINAL_COLL)
                .sharded(false);

        out.first();
        MongoCollection<Document> finalRes = db.getCollection(FINAL_COLL);

        Document leastUsed = finalRes.find().sort(ascending("value")).first();
        Document mostUsed = finalRes.find().sort(descending("value")).first();

        log.info("Least used word: \"" + leastUsed.getString("_id") + "\" count: " + leastUsed.get("value"));
        log.info("Most used word: \"" + mostUsed.getString("_id") + "\" count: " + mostUsed.get("value"));
        log.info("****************************************************************");
        CountWorker.printMemStats();
    }
}
