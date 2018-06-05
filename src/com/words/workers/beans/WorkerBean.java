/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.words.workers.beans;

import com.google.gson.Gson;
import java.util.Map;
import org.bson.Document;

/**
 *
 * @author BMihai
 */
public class WorkerBean {

    private Object _id;
    private String workerName;
    private String status;
    private long chunkStartOffset;
    private long chunkEndOffset;

    public Object getId() {
        return _id;
    }

    public void setId(Object _id) {
        this._id = _id;
    }

   
    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getChunkStartOffset() {
        return chunkStartOffset;
    }

    public void setChunkStartOffset(long chunkStartOffset) {
        this.chunkStartOffset = chunkStartOffset;
    }

    public long getChunkEndOffset() {
        return chunkEndOffset;
    }

    public void setChunkEndOffset(long chunkEndOffset) {
        this.chunkEndOffset = chunkEndOffset;
    }

   
    public Document toMongo() {
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return Document.parse(json);

    }

    public static WorkerBean toJava(Document obj) {
        Gson gson = new Gson();
        return gson.fromJson(obj.toJson(), WorkerBean.class);

    }

    @Override
    public String toString() {
        return "WorkerBean{" + "workderName=" + workerName + ", status=" + status + ", chunkStartOffset=" + chunkStartOffset / (1024L * 1024L) + "M" + ", chunkEndOffset=" + chunkEndOffset / (1024L * 1024L) + "M";

    }

}
