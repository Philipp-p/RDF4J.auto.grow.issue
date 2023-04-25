package demo;

import converter.IfcConverter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.rio.RDFFormat;
import utils.RepositoryUtils;

public class Application {
  public static final String RDF_4_J_SERVER = "http://localhost:8080/rdf4j-server";

  public static final String IFC_FILE = "BasicWall.ifc";
  public static final String IFC_PATH = "./IFC/" + IFC_FILE;
  public static final String REPOSITORY_ID = "demo_ifc";

  public static void main(String[] args) {
    RepositoryConnection connection = null;

    try {
      logMessage("Loading IFC model...");
      String ifcModel = Files.readString(Path.of(IFC_PATH), Charset.defaultCharset());
      if (ifcModel == null) {
        logMessage("ifcModel is null");
        return;
      }

      logMessage("Converting ifc model to rdf");
      IfcConverter converter = new IfcConverter();
      String rdfModel = converter.toRdf(ifcModel);

      RepositoryUtils repositoryUtils = new RepositoryUtils(RDF_4_J_SERVER);
      if (!repositoryUtils.exists(REPOSITORY_ID)) {
        logMessage("Creating repository");
        repositoryUtils.createRepository(REPOSITORY_ID);
      }

      logMessage("Writing named graph");
      String graph = "http://example.org/" + IFC_FILE;
      connection = repositoryUtils.getConnection(REPOSITORY_ID);

      logMessage("First transaction start");
      connection.begin();

      var factory = SimpleValueFactory.getInstance();
      IRI context = factory.createIRI(graph);
      InputStream inputStream = new ByteArrayInputStream(rdfModel.getBytes());
      connection.add(inputStream, RDFFormat.TURTLE, context);

      logMessage("Committing first transaction");
      connection.commit();
      logMessage("First transaction committed");

    } catch (Exception e) {
      e.printStackTrace();
      if (connection != null && connection.isActive()) {
        connection.rollback();
      }
      throw new RuntimeException(e);
    }
  }

  public static void logMessage(String message) {
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    String formattedDateTime = now.format(formatter);
    System.out.println(formattedDateTime + " " + message);
  }
}
