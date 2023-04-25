/*******************************************************************************
 * Copyright 2017 Chi Zhang
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package converter.rdf2ifc;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.ext.com.google.common.base.Charsets;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.lang.PipedRDFIterator;
import org.apache.jena.riot.lang.PipedRDFStream;
import org.apache.jena.riot.lang.PipedTriplesStream;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

/**
 * @author Chi Abstract class to convert IfcOWL data to IFC STEP data.
 */
public class RDF2IFCConverter {

  private Model schema = ModelFactory.createDefaultModel();
  private IfcVersion ifcVersion;
  private OutputStream outputStream;

  private static final byte[] NEW_LINE = "\n".getBytes(Charsets.UTF_8);
  private static final String NULL = "NULL";
  private static final String OPEN_CLOSE_PAREN = "()";
  private static final String ASTERISK = "*";
  private static final String PAREN_CLOSE_SEMICOLON = ");";
  private static final String DOT_0 = ".0";
  private static final String DASH = "#";
  private static final String DOT = ".";
  private static final String COMMA = ",";
  private static final String OPEN_PAREN = "(";
  private static final String CLOSE_PAREN = ")";
  private static final String BOOLEAN_UNKNOWN = ".U.";
  private static final String SINGLE_QUOTE = "'";
  private static final String DOUBLE_QUOTE = "\"";
  private static final String BOOLEAN_FALSE = ".F.";
  private static final String BOOLEAN_TRUE = ".T.";
  private static final String DOLLAR = "$";

  private final Property ISIFCENTITY = schema.getProperty(IfcOWLSupplement.isIfcEntity.getURI());
  private final Resource OWLLIST = schema.getResource(Namespace.LIST + "OWLList");
  private final Resource ENUMERATION = schema.getResource(Namespace.EXPRESS + "ENUMERATION");
  private final Property ISLISTORARRAY =
      schema.getProperty(IfcOWLSupplement.getURI() + "isListOrArray");
  private final Property ISSET = schema.getProperty(IfcOWLSupplement.getURI() + "isSet");
  private final Property ISOPTIONAL = schema.getProperty(IfcOWLSupplement.getURI() + "isOptional");
  private final Property HASDERIVEATTRIBUTE =
      schema.getProperty(IfcOWLSupplement.getURI() + "hasDeriveAttribute");
  private final Resource SELECT = schema.getResource(Namespace.EXPRESS + "SELECT");
  private final Resource EXPRESS_TRUE = schema.getProperty(Namespace.EXPRESS + "TRUE");
  private final Resource EXPRESS_FALSE = schema.getProperty(Namespace.EXPRESS + "FALSE");
  private final Resource EXPRESS_UNKNOWN = schema.getProperty(Namespace.EXPRESS + "UNKNOWN");

  private final Resource NUMBER = schema.getResource(Namespace.EXPRESS + "NUMBER");
  private final Resource INTEGER = schema.getResource(Namespace.EXPRESS + "INTEGER");
  private final Resource REAL = schema.getResource(Namespace.EXPRESS + "REAL");
  private final Resource BOOLEAN = schema.getResource(Namespace.EXPRESS + "BOOLEAN");
  private final Resource LOGICAL = schema.getResource(Namespace.EXPRESS + "LOGICAL");
  private final Resource STRING = schema.getResource(Namespace.EXPRESS + "STRING");
  private final Resource BINARY = schema.getResource(Namespace.EXPRESS + "BINARY");

  private final Property HASNEXT = schema.getProperty(Namespace.LIST + "hasNext");
  private final Property HASCONTENTS = schema.getProperty(Namespace.LIST + "hasContents");
  private final Property HASBOOLEAN = schema.getProperty(Namespace.EXPRESS + "hasBoolean");
  private final Property HASSTRING = schema.getProperty(Namespace.EXPRESS + "hasString");
  private final Property HASLOGICAL = schema.getProperty(Namespace.EXPRESS + "hasLogical");
  private final Property HASDOUBLE = schema.getProperty(Namespace.EXPRESS + "hasDouble");
  private final Property HASINTEGER = schema.getProperty(Namespace.EXPRESS + "hasInteger");
  private final Property HASHEXBINARY = schema.getProperty(Namespace.EXPRESS + "hasHexBinary");

  private Header header = new Header();

  private HashMap<Resource, List<Property>> class2Properties;

  private String ontologyURI;

  // data structure in memory.
  /** map of local name of IFC entity classes and resources (schema level) */
  private HashMap<String, Resource> entityMap = new HashMap<String, Resource>();
  /** map of local name of enum type resources and resources (schema level) */
  private HashMap<String, Resource> enumMap = new HashMap<String, Resource>();
  /** map of local name of list type resources and resources (schema level) */
  private HashMap<String, Resource> listMap = new HashMap<String, Resource>();
  /** map of local name of resources and value (value can be a string or a list of string) */
  private HashMap<String, Object> valueMap = new HashMap<String, Object>();
  /** map of list instance and the next instance of it */
  private HashMap<String, String> listNextMap = new HashMap<String, String>();
  /** map of local name of a resource and local name of its class */
  private HashMap<String, String> typeMap = new HashMap<String, String>();
  /** map of local name of a ifc instance resouce and the IfcObject instance. */
  private LinkedHashMap<String, IfcObject> ifcMap = new LinkedHashMap<String, IfcObject>();

  private Integer maxLineNum = 0;

  /** Constructor */
  public RDF2IFCConverter() {
    try {
      IfcVersion.initDefaultIfcNsMap();
      IfcVersion.initDefaultNsIfcMap();
    } catch (IfcVersionException e) {
      e.printStackTrace();
    }
  }

  public RDF2IFCConverter(IfcVersion ifcVersion, boolean updateNS) {
    try {
      if (updateNS == false) {
        IfcVersion.initDefaultIfcNsMap();
        IfcVersion.initDefaultNsIfcMap();
      } else {
        IfcVersion.initIfcNsMap();
        IfcVersion.initNsIfcMap();
      }
    } catch (IfcVersionException e) {
      e.printStackTrace();
    }
    this.ifcVersion = ifcVersion;
  }

  public void convert(String inputModel, OutputStream outputStream, Lang lang) throws Exception {
    this.outputStream = outputStream;
    InputStream listSchema =
        getClass().getClassLoader().getResourceAsStream("schema/rdf2ifc/list.ttl");
    schema.read(listSchema, null, "TTL");
    InputStream expSchema =
        getClass().getClassLoader().getResourceAsStream("schema/rdf2ifc/express.ttl");
    schema.read(expSchema, null, "TTL");
    if (ifcVersion == null) {
      ifcVersion = selectSchema(inputModel, lang);
    } else {
      if (ifcVersion != selectSchema(inputModel, lang)) {
        System.out.println("used ifcOWL ontology might not be consistent with the data");
      }
    }
    InputStream in =
        getClass()
            .getClassLoader()
            .getResourceAsStream("schema/rdf2ifc/" + ifcVersion.getLabel() + ".ttl");
    schema.read(in, null, "TTL");
    InputStream in2 =
        getClass()
            .getClassLoader()
            .getResourceAsStream(
                "schema/rdf2ifc/" + ifcVersion.getLabel() + "_Schema_supplement.ttl");
    schema.read(in2, null, "TTL");
    this.ontologyURI = IfcVersion.IfcNSMap.get(ifcVersion);
    processSchema();
    try {
      read(inputModel, lang);
    } catch (NoSuchElementException e) {
      e.printStackTrace();
      throw new IfcVersionException("used ifcOWL ontology does not match the data");
    }
    // reArrangeListValues();
    writeHeader();
    writeData();
    outputStream.close();
    ifcMap.clear();
    valueMap.clear();
    listNextMap.clear();
    listMap.clear();
    entityMap.clear();
    enumMap.clear();
  }

  /**
   * Returns IFC schema version (ifcOWL ontology version) based on input content. It is dertermined
   * by the ontology imported (owl:imports) by the base uri.
   *
   * @param model file path of the input RDF file
   * @param lang RDF language of the input RDF file
   * @return
   */
  private IfcVersion selectSchema(String model, Lang lang)
      throws IfcVersionException, FileNotFoundException {
    PipedRDFIterator<Triple> iter = new PipedRDFIterator<Triple>();
    final PipedRDFStream<Triple> stream = new PipedTriplesStream(iter);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Node ontology = null;
    Runnable parser =
        new Runnable() {
          public void run() {
            RDFDataMgr.parse(stream, new StringReader(model), lang);
          }
        };
    executor.submit(parser);

    while (iter.hasNext()) {
      Triple t = iter.next();
      Node predicate = t.getPredicate();

      if (predicate.equals(OWL.imports.asNode())) {
        ontology = t.getObject();
        // break;
      }
    }
    executor.shutdown();
    if (ontology == null) {
      throw new IfcVersionException("The ifcOWL model did not import used ifcOWL ontology");
    }
    InputStream listSchema =
        getClass().getClassLoader().getResourceAsStream("schema/rdf2ifc/list.ttl");
    schema.read(listSchema, null, "TTL");
    InputStream expSchema =
        getClass().getClassLoader().getResourceAsStream("schema/rdf2ifc/express.ttl");
    schema.read(expSchema, null, "TTL");
    IfcVersion ifcVersion = IfcVersion.NSIfcMap.get(ontology.getURI());
    if (ifcVersion == null) throw new IfcVersionException("Cannot determine required IFC version");
    return IfcVersion.NSIfcMap.get(ontology.getURI());
  }

  /** Preprocess the schema, map entities, properties, enumerations and lists. */
  private void processSchema() {
    class2Properties = IfcOWLUtils.getProperties4Class(schema);
    ExtendedIterator<Statement> stats = schema.listLiteralStatements(null, ISIFCENTITY, true);
    while (stats.hasNext()) {
      Statement s = stats.next();
      entityMap.put(s.getSubject().getLocalName(), s.getSubject());
    }
    stats = schema.listStatements(null, RDF.type, (RDFNode) null);
    while (stats.hasNext()) {
      Statement stat = stats.next();
      if (stat.getObject().equals(ENUMERATION)
          || IfcOWLUtils.hasSuperClass(stat.getObject().asResource(), ENUMERATION)) {
        enumMap.put(stat.getSubject().getLocalName(), stat.getSubject());
      }
    }

    listMap.put(OWLLIST.getLocalName(), OWLLIST);
    Set<Resource> lists = IfcOWLUtils.getAllSubClasses(OWLLIST, schema);
    for (Resource s : lists) {
      listMap.put(s.getLocalName(), s);
    }
  }

  /**
   * Read an IFC RDF model.
   *
   * @param model file path of the IFC RDF file
   * @param lang RDF language of the input IFC RDF file
   * @throws FileNotFoundException
   */
  private void read(String model, Lang lang) throws FileNotFoundException {
    PipedRDFIterator<Triple> iter = new PipedRDFIterator<Triple>();
    //	InputStream in = new FileInputStream(input);
    final PipedRDFStream<Triple> stream = new PipedTriplesStream(iter);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Runnable parser =
        new Runnable() {
          public void run() {
            RDFDataMgr.parse(stream, new StringReader(model), lang);
          }
        };
    executor.submit(parser);
    while (iter.hasNext()) {
      Triple t = iter.next();
      Node subject = t.getSubject();
      Node predicate = t.getPredicate();
      Node object = t.getObject();
      String sn = subject.getLocalName();
      String pn = predicate.getLocalName();
      if (predicate.equals(RDF.type.asNode())) {
        String on = object.getLocalName();
        typeMap.put(sn, on);
        if (isIfcInstance(sn)) {
          IfcObject ifcvo = ifcMap.get(subject.getLocalName());
          if (ifcvo == null) {
            ifcvo = new IfcObject();
            ifcMap.put(sn, ifcvo);
            ifcvo.setList(new HashMap<Integer, Object>());
          }
          ifcvo.setName(on);
          try {
            Integer i = Integer.parseInt(sn.substring(sn.lastIndexOf("_") + 1));
            ifcvo.setLineNum(i);
            maxLineNum = Math.max(maxLineNum, i);
          } catch (NumberFormatException e) {
            continue;
          }
        }
      } else if (pn.equals("hasExpressID")) {
        IfcObject ifcvo = ifcMap.get(subject.getLocalName());
        if (ifcvo == null) {
          ifcvo = new IfcObject();
          ifcMap.put(sn, ifcvo);
          ifcMap.put(sn, ifcvo);
          ifcvo.setList(new HashMap<Integer, Object>());
          ifcvo.setList(new HashMap<Integer, Object>());
        }
        try {
          Integer lineNum = (Integer) object.getLiteralValue();
          ifcvo.setLineNum(lineNum);
          maxLineNum = Math.max(lineNum, maxLineNum);
        } catch (Exception e) {
          System.out.println("Found an invalid express id with resource: " + subject.getURI());
          continue;
        }
      } else if (pn.equals("hasNext")) {
        listNextMap.put(subject.getLocalName(), object.getLocalName());
      } else if (pn.equals("hasContents")) {
        LinkedList<String> list = new LinkedList<String>();
        list.add(object.getLocalName());
        valueMap.put(subject.getLocalName(), list);
      } else if (pn.equalsIgnoreCase("hasDouble")) {
        Double valDouble = null;
        try {
          valDouble = (Double) object.getLiteralValue();
        } catch (Exception e) {
          System.out.println("Found an invalid double value: " + subject.getURI());
          continue;
        }
        String str = "0.00";
        if (valDouble.isInfinite() || (valDouble.isNaN())) {
          System.out.println("Serialize infinite number as 0.0");
        } else {
          if (valDouble != null) {
            str = String.format(Locale.ENGLISH, "%.8f", valDouble);
          }
        }
        valueMap.put(subject.getLocalName(), str);
      } else if (pn.equalsIgnoreCase("hasString")) {
        String valString = null;
        try {
          valString = (String) object.getLiteralValue();
        } catch (Exception e) {
          System.out.println("Found an invalid string value: " + subject.getURI());
          continue;
        }
        valueMap.put(subject.getLocalName(), SINGLE_QUOTE + valString + SINGLE_QUOTE);
      } else if (pn.equals("hasInteger")) {
        Integer valInteger = null;
        try {
          valInteger = (Integer) object.getLiteralValue();
        } catch (Exception e) {
          System.out.println("Found an invalid integer value: " + subject.getURI());
          continue;
        }
        String str = valInteger.toString();
        if (str.endsWith(DOT_0)) {
          str = str.substring(0, str.length() - 2);
        }
        valueMap.put(subject.getLocalName(), str);
      } else if (pn.equals("hasHexBinary")) {
        String str;
        // try{
        // str=object.getLiteralValue();
        // }catch (Exception e){
        // LOGGER.log(Level.WARNING, "Found an invalid hexbinary
        // value:"+subject.getURI()+", try to convert it as string");
        try {
          str = object.getLiteral().toString();
          str = str.substring(0, str.indexOf('^'));
        } catch (Exception ie) {
          System.out.println("Did not convert the binary value: " + subject.getURI());
          continue;
        }
        // }
        valueMap.put(subject.getLocalName(), DOUBLE_QUOTE + str + DOUBLE_QUOTE);
      } else if (pn.equals("hasBoolean")) {
        if (object.equals(
            NodeFactory.createLiteral(Boolean.toString(true), null, XSDDatatype.XSDboolean))) {
          valueMap.put(subject.getLocalName(), BOOLEAN_TRUE);
        } else if (object.equals(
            NodeFactory.createLiteral(Boolean.toString(false), null, XSDDatatype.XSDboolean))) {
          valueMap.put(subject.getLocalName(), BOOLEAN_FALSE);
        } else {
          System.out.println("Found an invalid boolean value: " + subject.getURI());
          continue;
        }
      } else if (pn.equals("hasLogical")) {
        if (object.equals(EXPRESS_TRUE.asNode())) {
          valueMap.put(subject.getLocalName(), BOOLEAN_TRUE);
        } else if (object.equals(EXPRESS_FALSE.asNode())) {
          valueMap.put(subject.getLocalName(), BOOLEAN_FALSE);
        } else if (object.equals(EXPRESS_UNKNOWN.asNode())) {
          valueMap.put(subject.getLocalName(), BOOLEAN_UNKNOWN);
        } else {
          System.out.println("Found an invalid logical value: " + subject.getURI());
          continue;
        }
      } else if (sn.equals("")) {
        if (predicate.equals(IfcHeader.description.asNode())) {
          header.getDescription().add(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.author.asNode())) {
          header.getAuthor().add(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.organization.asNode())) {
          header.getOrganization().add(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.schema_identifiers.asNode())) {
          header.getSchema_identifiers().add(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.implementation_level.asNode())) {
          header.setImplementation_level(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.name.asNode())) {
          header.setName(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.time_stamp.asNode())) {
          header.setTime_stamp(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.preprocessor_version.asNode())) {
          header.setPreprocessor_version(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.originating_system.asNode())) {
          header.setOriginating_system(object.getLiteralValue().toString());
        } else if (predicate.equals(IfcHeader.authorization.asNode())) {
          header.setAuthorization(object.getLiteralValue().toString());
        }
      } else {
        IfcObject ifcvo = ifcMap.get(subject.getLocalName());
        String on = object.getLocalName();
        if (ifcvo == null) {
          ifcvo = new IfcObject();
          ifcvo.setList(new HashMap<Integer, Object>());
          ifcMap.put(subject.getLocalName(), ifcvo);
          //
          setValue(ifcvo, sn, pn, on);
        } else {
          setValue(ifcvo, sn, pn, on);
        }
      }
    }
    executor.shutdown();
  }

  @SuppressWarnings("unchecked")
  private void setValue(IfcObject ifcobject, String sn, String pn, String on) {
    Property p = schema.getProperty(ontologyURI + pn);
    int i = IfcOWLUtils.getPropertyPosition(p, schema);
    Object o = ifcobject.getObjectList().get(i);
    if (o == null) {
      if (isSet(p)) {
        LinkedList<String> list = new LinkedList<String>();
        list.add(on);
        ifcobject.getObjectList().put(i, list);
      } else {
        ifcobject.getObjectList().put(i, on);
      }
    } else {
      if (o instanceof LinkedList<?>) {
        ((LinkedList<String>) o).add(on);
      } else {
        LinkedList<String> list = new LinkedList<String>();
        list.add((String) o);
        list.add(on);
        ifcobject.getObjectList().put(i, list);
      }
    }
  }

  private String writeStringList(List<String> list) {
    String result = OPEN_PAREN;
    if (list == null || list.size() == 0) {
      return result + writeString("") + CLOSE_PAREN;
    }
    for (int i = 0; i < list.size(); i++) {
      if (i < list.size() - 1) {
        result = result + writeString(list.get(i)) + COMMA;
      } else {
        result = result + writeString(list.get(i));
      }
    }
    result = result + CLOSE_PAREN;

    return result;
  }

  /**
   * @param result
   * @return
   */
  private String writeString(String result) {
    if (result == null) {
      return SINGLE_QUOTE + SINGLE_QUOTE;
    }
    return SINGLE_QUOTE + result + SINGLE_QUOTE;
  }

  /**
   * write IFC HEADER.
   *
   * @throws IOException
   */
  private void writeHeader() throws IOException {
    println("ISO-10303-21;");
    println("HEADER;");

    println(
        "FILE_DESCRIPTION"
            + OPEN_PAREN
            + writeStringList(header.getDescription())
            + COMMA
            + writeString(header.getImplementation_level())
            + PAREN_CLOSE_SEMICOLON);
    println(
        "FILE_NAME"
            + OPEN_PAREN
            + writeString(header.getName())
            + COMMA
            + writeString(header.getTime_stamp())
            + COMMA
            + writeStringList(header.getAuthor())
            + COMMA
            + writeStringList(header.getOrganization())
            + COMMA
            + writeString(header.getPreprocessor_version())
            + COMMA
            + writeString(header.getOriginating_system())
            + COMMA
            + writeString(header.getAuthorization())
            + PAREN_CLOSE_SEMICOLON);
    if (header.getSchema_identifiers() != null && header.getSchema_identifiers().size() > 0) {
      println(
          "FILE_SCHEMA"
              + OPEN_PAREN
              + writeStringList(header.getSchema_identifiers())
              + PAREN_CLOSE_SEMICOLON);
    } else {
      println(
          "FILE_SCHEMA"
              + OPEN_PAREN
              + OPEN_PAREN
              + writeString(ifcVersion.getLabel())
              + PAREN_CLOSE_SEMICOLON
              + PAREN_CLOSE_SEMICOLON);
    }
    println("ENDSEC;");
  }

  /**
   * write IFC DATA.
   *
   * @throws IOException
   */
  private void writeData() throws IOException {
    println("DATA;");
    for (String s : ifcMap.keySet()) {
      IfcObject object = ifcMap.get(s);
      if (object.getLineNum() == null) {
        try {
          maxLineNum++;
          object.setLineNum(maxLineNum);
          System.out.println(
              "The IFC object #" + maxLineNum + " does not have express id. Assigned one for it.");
        } catch (NumberFormatException e) {

        }
      }
    }
    for (String s : ifcMap.keySet()) {
      writeLine(ifcMap.get(s));
    }
    println("ENDSEC;");
    println("END-ISO-10303-21;");
  }

  /**
   * Write a line (an IFC object) in IFC DATA. @ ifcobject the ifcobject used.
   *
   * @param ifcobject
   * @throws IOException
   */
  @SuppressWarnings("unchecked")
  private void writeLine(IfcObject ifcobject) throws IOException {
    print(DASH);
    print(String.valueOf(ifcobject.getLineNum()));
    print("= ");
    String upperCase = ifcobject.getName().toUpperCase();
    print(upperCase);
    print(OPEN_PAREN);
    boolean isFirst = true;
    List<Property> properties = class2Properties.get(entityMap.get(ifcobject.getName()));
    HashMap<Integer, Object> values = ifcobject.getObjectList();
    for (int i = 0; i < properties.size(); i++) {
      Resource range = properties.get(i).getProperty(RDFS.range).getObject().asResource();
      if (values.get(i) == null) {
        if (!isFirst) {
          print(COMMA);
        }
        writeNull(ifcobject, properties.get(i));
      } else {
        Object value = values.get(i);
        if (value instanceof LinkedList<?>) {
          if (!isFirst) {
            print(COMMA);
          }
          writeSet((LinkedList<String>) value, properties.get(i), range);
        } else {
          String str = (String) value;
          if (!isFirst) {
            print(COMMA);
          }

          if (isOwlList(str)) {
            writeList(str, properties.get(i), range, false);
          } else if (isIfcInstance(str)) {
            writeIfcInstance(str);
          } else if (isEnumeration(str)) {
            writeEnum(str);
          } else {
            Resource clazz = schema.getResource(ontologyURI + typeMap.get(str));
            if (clazz.equals(range)) {
              writePrimitive(str);
            } else {
              if (IfcOWLUtils.isDirectSubClassOf(range, SELECT, schema)) writeEmbedded(str);
              else writePrimitive(str);
            }
          }
        }
      }
      isFirst = false;
    }
    println(PAREN_CLOSE_SEMICOLON);
  }

  private void println(String line) throws IOException {
    byte[] bytes = line.getBytes(Charsets.UTF_8);
    outputStream.write(bytes, 0, bytes.length);
    outputStream.write(NEW_LINE, 0, NEW_LINE.length);
  }

  private void writePrimitive(String str) throws IOException {
    if (valueMap.get(str) != null) {
      print((String) valueMap.get(str));
    } else print(DOLLAR);
  }

  private void writeNull(IfcObject ifcvo, Property property) throws IOException {
    if ((isSet(property) || isListOrArray(property)) && !isOptional(property)) {
      print(OPEN_CLOSE_PAREN);
    } else if (isDerived(ifcvo, property)) {
      print(ASTERISK);
    } else print(DOLLAR);
  }

  private void writeSet(LinkedList<String> list, Property property, Resource range)
      throws IOException {
    boolean isFirst = true;
    print(OPEN_PAREN);
    for (String str : list) {
      if (!isFirst) {
        print(COMMA);
      }
      if (isIfcInstance(str)) {
        writeIfcInstance(str);
      } else if (isOwlList(str)) {
        writeList(str, property, range, false);
      } else if (isEnumeration(str)) {
        writeEnum(str);
      } else {
        Resource clazz = schema.getResource(ontologyURI + typeMap.get(str));
        if (clazz.equals(range)) {
          writePrimitive(str);
        } else {
          // if (JenaUtil.hasSuperClass(clazz, range) &&
          if (IfcOWLUtils.isDirectSubClassOf(range, SELECT, schema)) writeEmbedded(str);
          else writePrimitive(str);
        }
      }

      isFirst = false;
    }
    print(CLOSE_PAREN);
  }

  private boolean isDerived(IfcObject ifcvo, Property property) {
    if (schema
        .getResource(ontologyURI + ifcvo.getName())
        .hasProperty(HASDERIVEATTRIBUTE, property)) {
      return true;
    }
    return false;
  }

  private void writeEmbedded(String str) throws IOException {
    // if(clazz.getURI().startsWith("http://purl.org/voc/express#")){
    // print("IFC"+clazz.getLocalName().toUpperCase());
    // }
    // else{
    String s = typeMap.get(str);
    print(s != null ? s.toUpperCase() : "");
    // }
    print(OPEN_PAREN);
    writePrimitive(str);
    print(CLOSE_PAREN);
  }

  @SuppressWarnings("unchecked")
  private void writeList(String s, Property property, Resource range, boolean embededTypeList)
      throws IOException {
    Object list = getListValue(s);
    if (list instanceof LinkedList<?>) {
      if (((LinkedList<String>) list).size() == 0) {
        print(OPEN_CLOSE_PAREN);
      } else {
        boolean isFirst = true;
        if (IfcOWLUtils.hasSuperClass(schema.getResource(ontologyURI + typeMap.get(s)), range)
            && IfcOWLUtils.isDirectSubClassOf(range, SELECT, schema)) {
          print(typeMap.get(s).toUpperCase());
          print(OPEN_PAREN);
          embededTypeList = true;
        }
        print(OPEN_PAREN);
        Resource range1 = range;
        if (range.getURI().endsWith("_List")) {
          range1 = schema.getResource(range.getURI().substring(0, range.getURI().length() - 5));
        }
        for (String str : (LinkedList<String>) list) {
          if (!isFirst) {
            print(COMMA);
          }
          if (str == null) {
            print(DOLLAR);
            System.out.println("A list has a null value, it is worth check:" + s);
          }
          if (isIfcInstance(str)) {
            writeIfcInstance(str);
          } else if (isOwlList(str)) {
            writeList(str, property, range1, embededTypeList);
          } else if (isEnumeration(str)) {
            writeEnum(str);
          } else {
            Resource clazz = schema.getResource(ontologyURI + typeMap.get(str));

            if (clazz.equals(range1)) {
              writePrimitive(str);
            } else {
              if (IfcOWLUtils.isDirectSubClassOf(range1, SELECT, schema)
                  && !embededTypeList
                  && !IfcOWLUtils.hasSuperClass(
                      schema.getResource(ontologyURI + typeMap.get(s)),
                      schema.getResource(clazz.getURI() + "_List"))) writeEmbedded(str);
              else writePrimitive(str);
            }
          }

          isFirst = false;
        }
        if (IfcOWLUtils.hasSuperClass(schema.getResource(ontologyURI + typeMap.get(s)), range)
            && IfcOWLUtils.isDirectSubClassOf(range, SELECT, schema)) {
          print(CLOSE_PAREN);
        }
        print(CLOSE_PAREN);
      }
    } else {
      System.out.println("Found a list value which is not a list: " + s);
    }
  }

  @SuppressWarnings({"unchecked"})
  private LinkedList<String> getListValue(String s) {
    LinkedList<String> list = new LinkedList<String>();
    if (valueMap.get(s) != null) {
      list.addAll((LinkedList<String>) valueMap.get(s));
    } else {
      list.add(null);
      System.out.println("Found a list without contents " + s);
    }
    while (listNextMap.get(s) != null) {
      LinkedList<String> next = (LinkedList<String>) valueMap.get(listNextMap.get(s));
      if (next != null) list.addAll(next);
      s = listNextMap.get(s);
    }
    return list;
  }

  private boolean isEnumeration(String str) {
    if (enumMap.get(str) != null) {
      return true;
    }
    return false;
  }

  private boolean isIfcInstance(String str) {
    if (entityMap.get(typeMap.get(str)) != null) {
      return true;
    } else return false;
  }

  private boolean isOwlList(String str) {
    if (listMap.get(typeMap.get(str)) != null
        || (typeMap.get(str) != null && typeMap.get(str).endsWith("_List"))) {
      return true;
    } else return false;
  }

  private boolean isSet(Property property) {
    if (property.hasLiteral(ISSET, true)) {
      return true;
    } else return false;
  }

  private boolean isListOrArray(Property property) {
    if (property.hasLiteral(ISLISTORARRAY, true)) {
      return true;
    } else return false;
  }

  private boolean isOptional(Property property) {
    if (property.hasLiteral(ISOPTIONAL, true)) {
      return true;
    } else return false;
  }

  private void print(String text) throws IOException {
    byte[] bytes = text.getBytes(Charsets.UTF_8);
    outputStream.write(bytes, 0, bytes.length);
  }

  private void writeIfcInstance(String str) throws IOException {
    print(DASH + ifcMap.get(str).getLineNum());
  }

  private void writeEnum(String str) throws IOException {
    if (str.equals(NULL)) {
      print(DOLLAR);
    } else {
      print(DOT);
      print(str);
      print(DOT);
    }
  }

  /**
   * @param s
   */
  private void write(String s) {
    System.out.println(s);
  }
}