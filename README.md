tikabooty
=========

**Service to extract text from common office formats, using Apache Tika**

Named *tikabooty* since it combines Apache Tika with Spring Boot

* The idea is that, in a document/text processing pipeline, you'll need to extract
the text from common office format documents and, probably, do something like index
or run named entity recognition (NER) on the result (or, dump it into a data store).

* Input documents are sent via HTTP POST, like this (from the command line):

  `curl -F "file=@my_file.pdf" https://hostname:8080/`

The response will include the file name, plus any headers found in the document, and
a "content" field, which will contain the entire text found within the file.  The
output is currently formatted as `field_name + TAB + field_value + '\n'`.
**Note that, if you deploy to Cloud Foundry, you can use either `https` or `http`.**

When the client invokes this service, it is, in effect, *making a tikabooty call*.

Deploy in Cloud Foundry:

`$ mvn package`

`$ cf login`

`$ cf push tikabooty -p ./target/tikabooty-0.0.1-SNAPSHOT.jar`


* Memory: `-Xmx256m` seems to work, subject to the caveat that certain PDF documents will cause OOM issues.
  Here are references on that: https://issues.apache.org/jira/browse/PDFBOX-1907,
  https://issues.apache.org/jira/browse/PDFBOX-2445.  In Cloud Foundry, 1 GB RAM is recommended for this.
* Edit `application.properties` to alter the logging levels (`logging.level.io.pivotal.text: WARN`)
* Deploy in Jetty, not Tomcat, which has an issue with closing InputStream
* Supported formats (Apache Tika 1.6): http://tika.apache.org/1.6/formats.html
* http://start.spring.io/ was used to bootstrap this Spring Boot project

* TODO
  - Figure out how to eliminate the need to send the name "file" and just POST the content(?)
    Maybe something like path is /${filename}?
  - Come up with a better schema to pass the result to the client.  Currently, it's field_nameTABfield_value,
    but Excel will produce TAB-separated data fields, which would be nice to preserve.  Also, currently, any
    TAB is replaced by "  ".  Maybe for Excel files the internal delimiter can be altered to '|' or something.

