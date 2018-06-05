package com.words.utils;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;
import com.words.utils.io.BufferedRandomAccessFile;
import com.words.workers.CountWorker;
import com.words.workers.beans.WorkerBean;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import org.bson.Document;

/**
 * This class is responsible to setup workers load and initial db structure
 *
 * @author BMihai
 */
public class FirstRun {

    private static final Logger log = Logger.getLogger(FirstRun.class.getName());

    /**
     * Setup
     *
     * @param filePath
     * @param workersCount
     */
    public static void setupEverything(String filePath, long workersCount) throws IOException {
        LogSetup.setupLog(log);

        log.info("****************************************************************");
        log.info("First run detected. Going to perform setup operations. This worker is also the collector");
        log.info("****************************************************************");
        FirstRun.allocateWorkersAndChunks(filePath, workersCount);
    }

    /**
     * Calculate offset for workers
     *
     * @param filePath
     * @param workersCount
     * @return
     * @throws IOException
     */
    private static List<WorkerBean> allocateWorkersAndChunks(String filePath, long workersCount) throws IOException {
        File file = new File(filePath);
        BufferedRandomAccessFile.DEFAULT_BUFFER_SIZE = 2560;
        List<WorkerBean> workers = new ArrayList<>();
        long filesize = file.length();
        long arraysize = filesize - 1;
        long chunkSize = filesize / workersCount;
        long endOffset = 0;
        long startOffset = 0;
        boolean exit = false;
        log.info("File size: " + file.length() / (1024L * 1024L) + "M");
        while (endOffset < arraysize && !exit) {
            startOffset = endOffset;
            endOffset += chunkSize - 1;
            endOffset = calculateLineOffset(file, endOffset);
            WorkerBean worker = new WorkerBean();
            worker.setChunkStartOffset(startOffset);
            if (endOffset > arraysize) {
                worker.setChunkEndOffset(arraysize);
                exit = true;
            } else {
                worker.setChunkEndOffset(endOffset);
            }
            worker.setStatus("FREE");
            workers.add(worker);
            log.info("Allocated worker: " + worker);
            endOffset = endOffset + 1;
        }

        MongoCollection<Document> coll = CountWorker.db.getCollection(CountWorker.WORKERS_COLL);

        List<WriteModel<Document>> workersColl = workers.stream().map(m -> new InsertOneModel<>(m.toMongo())).collect(toList());
        coll.bulkWrite(workersColl);

        return workers;
    }

    /**
     * Normalize seek to EOL, we don't want to seek in the middle of a word
     *
     * @param file
     * @param offset
     * @return
     * @throws IOException
     */
    private static long calculateLineOffset(File file, long offset) throws IOException {

        BufferedRandomAccessFile raf = null;
        long retoffset = offset;
        try {
            raf = new BufferedRandomAccessFile(file, "r");
            raf.seek(offset);
            raf.getNextLine();
            retoffset = raf.getFilePointer() - 1;
        } finally {
            if (raf != null) {
                raf.close();
            }
        }
        return retoffset;
    }

}
