package io.pivotal.text;

import org.apache.commons.compress.utils.CountingInputStream;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xml.sax.ContentHandler;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableAutoConfiguration
public class Application extends SpringBootServletInitializer {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Application.class);

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Application.class, args);
  }

  @SuppressWarnings("serial")
  @Bean
  public Servlet dispatcherServlet() {

    return new GenericServlet() {
      // Tika
      private ParseContext context;
      private Detector detector;
      private Parser parser;
      private ContentHandler contentHandler;

      @Override
      public void service(ServletRequest req, ServletResponse resp)
              throws ServletException, IOException {
        long t0 = System.currentTimeMillis(); // For reporting on time taken
        long nBytesProcessed = 0L; // For reporting file size
        PrintWriter writer = resp.getWriter();
        // Get any form (key, value) pairs
        Map<String, String> keyValMap = new HashMap<String, String>();
        for (Enumeration<String> parmEnum = req.getParameterNames(); parmEnum.hasMoreElements(); ) {
          String key = parmEnum.nextElement();
          keyValMap.put(key, req.getParameter(key));
        }
        resp.setContentType("text/plain");

        HttpServletRequest httpReq = (HttpServletRequest) req;
        boolean isMultipart = ServletFileUpload.isMultipartContent(httpReq);
        if (isMultipart) {
          LOG.info("Got a multi-part form submission (with \"file\")");
          // Create a new file upload handler
          ServletFileUpload upload = new ServletFileUpload();
          FileItemIterator iter = null;
          // Tika
          detector = new DefaultDetector();
          parser = new AutoDetectParser(detector);
          context = new ParseContext();
          context.set(Parser.class, parser);
          // Fix for OOM on large PDF files? (https://issues.apache.org/jira/browse/PDFBOX-1907)
          /* But, this doesn't appear to solve anything, and it's 1/3 slower
          PDFParserConfig pdfParserConfig = new PDFParserConfig();
          pdfParserConfig.setUseNonSequentialParser(true);
          context.set(PDFParserConfig.class, pdfParserConfig);
          */

          ByteArrayOutputStream bas = new ByteArrayOutputStream();
          contentHandler = new BodyContentHandler(bas);

          try {
            iter = upload.getItemIterator(httpReq);
          } catch (FileUploadException e) {
            e.printStackTrace();
          }
          try {
            while (iter != null && iter.hasNext()) {
              FileItemStream item = iter.next();
              String name = item.getFieldName();
              //InputStream stream = item.openStream();
              CountingInputStream stream = new CountingInputStream(item.openStream());
              keyValMap.put(name, item.getName());
              for (String key : keyValMap.keySet()) {
                writer.append(key + "\t" + keyValMap.get(key) + "\n");
              }
              if (item.isFormField()) { // NOT the interesting case for this app.
                LOG.info("Got " + name + " = "
                        + Streams.asString(stream));
              } else { // *This* is what we're looking for with this app.
                LOG.info("Got " + name + " = "
                        + item.getName());
                // MIKE: Insert Tika parsing code here, with stream as its input source
                Metadata metadata = new Metadata();
                try {
                  parser.parse(stream, contentHandler, metadata, context);
                  for (String key : metadata.names()) {
                    String value = metadata.get(key);
                    if (value != null) {
                      writer.append(key + "\t" + value + "\n");
                    }
                  }
                  writer.append("content" + "\t" + bas.toString("UTF-8").replaceAll("\\t", "  "));
                } catch (Exception e) {
                  e.printStackTrace();
                } finally {
                  writer.flush();
                }
              }
              long elapsedTime = System.currentTimeMillis() - t0;
              nBytesProcessed = stream.getBytesRead();
              LOG.info("Processed " + nBytesProcessed + " bytes in " + elapsedTime + " ms");
            }
          } catch (FileUploadException e) {
            e.printStackTrace();
          }
        }
      }
    };
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(Application.class);
  }

}

