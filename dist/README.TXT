Words Counter V1.0

1)Description
Java utility to find word frequency count in text files of any size.
It can scale on any number of servers or threads by dividing the target file in small chunks. 
On each node or separate thread the utility(or worker) will have a unique id that will be reused for recovery in case of failure.
All threads communicate via a MongoDB shared collection to distribute load and failover.

2)Requirements
MongoDB server version: 3.6.5
JDK1.8.0_171
Uncompressed and not binary target file like (txt,xml). 
The file needs to by replicated on all machines that we want to use the utility. If the file is located in only one place this means the reading of the file is serial and we cannot distribute the load in parallel.


3)Load distribution and startup
The first time the utility(worker) is run,it will create the DB structure and allocate the file chunks.
It will also be the collector. Once it finished to process all the chunks it will wait for other workers to finish too. 
Then it will invoke a map-reduce command to MongoDB and once MongoDB is done it will display the least and most used word in the file.
All workers perform an internal map-reduce at java level, before dumping the chunk result to the main collection, to avoid storing large volume of data but this will consume some CPU.
All the words count are store in DB to be queried at any time (to perform statistics, etc....)
Load example: chucks: 300, workers: 3  -> each worker will process 100 chunks
Load example: chucks: 30, workers: 30  -> each worker will process 1 chunks
Workers can reside on same or different machines.


4)DB structure
db name: file_stats
collection: workers -> used to store all the workers status and chunk offsets
collection: results -> main collection where all the workers dump they processed data.
collection: final_results -> this is created by the map-reduce command. This is the main collection that we are interested in. We will use this to perform statistics later if we want.


5)Command line example and parameters description
5.1)We will run the first worker like this:
./java -Xmx500m -DworkerName=work1 -Dcollector=true -Dchunks=700 -DbulkSave=80000 -DcreateDB=true -DmongoHost=localhost -DmongoPort=27017 -Dfile="/mnt/hgfs/F/enwiki-20180520-pages-articles-multistream.xml"  -jar  "WordsCount.jar"

--Xmx500m --------------> Memory for worker. As you can see this is low because we are using lots of chunks. This can be tuned based on available resources.
-DworkerName=work1 --------------> worker unique id. We use this for failover
-Dcollector=true --------------> this worker is also the collector so he will wait for other workers to finish and call map-reduce. This can be tuned based on available resources.
-Dchunks=700 --------------> number of chunks we want to divide the file. The smaller the chunks the bigger RAM we need.
-DbulkSave=80000 --------------> number of inserts we send in bulk to MongoDB. A too large value may cause the driver to go in out of memory. This can be tuned based on available resources.
-DcreateDB=true --------------> this will drop an recreate the DB. We want to do this for the first worker
-DmongoHost=localhost
-DmongoPort=27017
-Dfile="/mnt/hgfs/F/enwiki-20180520-pages-articles-multistream.xml" --------------> 64G file
-jar "/dist/WordsCount.jar


5.2)We will run all other workers like this.
./java -Xmx500m -DworkerName=work2 -DbulkSave=80000 -DmongoHost=localhost -DmongoPort=27017  -Dfile="/mnt/hgfs/F/enwiki-20180520-pages-articles-multistream.xml" -jar "WordsCount.jar"
./java -Xmx500m -DworkerName=work3 -DbulkSave=80000 -DmongoHost=localhost -DmongoPort=27017  -Dfile="/mnt/hgfs/F/enwiki-20180520-pages-articles-multistream.xml" -jar "WordsCount.jar"
.
.
.
./java -Xmx500m -DworkerName=work(N) -DbulkSave=80000 -DmongoHost=localhost -DmongoPort=27017  -Dfile="/mnt/hgfs/F/enwiki-20180520-pages-articles-multistream.xml" -jar "WordsCount.jar"


6)Notes
If a worker goes down we will need to re-run it with the same name it had before. Names must be unique.
The accuracy of the utility is based on the regexp and other filters we used to split the words at CountWorker.java, lines:188,198 This can be tuned.
If the command line is too big this is because we depend a lot on the machine we are running the utility on and the file size. Most of the parameters have default values but some tuning is needed.
The utility can be run with the commands provided above.



