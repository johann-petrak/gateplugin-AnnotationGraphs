/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package gate.plugins.annotationgraphs.tests;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Gate;
import gate.Utils;
import gate.creole.ResourceInstantiationException;
import gate.plugins.annotationgraphs.AnnotationGraph;
import gate.util.GateException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Johann Petrak
 */
public class Test1 {
  
  private static final Logger logger = Logger.getLogger(Test1.class);
  private File pluginHome;
  private File testingDir;

  @Before
  public void setup() throws GateException, IOException {
    
    logger.setLevel(Level.DEBUG);
    Logger rootLogger = Logger.getRootLogger();
    rootLogger.setLevel(Level.DEBUG);
    //logger.setLevel(Level.INFO);
    ConsoleAppender appender = new ConsoleAppender();
    appender.setWriter(new OutputStreamWriter(System.out));
    appender.setLayout(new PatternLayout("%-5p [%t]: %m%n"));
    //logger.addAppender(appender);
    rootLogger.addAppender(appender);
    
    if(!Gate.isInitialised()) {
      Gate.runInSandbox(true);
      Gate.init();
    }
    pluginHome = new File(".");
    pluginHome = pluginHome.getCanonicalFile();
    Gate.getCreoleRegister().registerDirectories(
              pluginHome.toURI().toURL());
    /*
    testingDir = new File(pluginHome,"test");
    URL doc1Url = new File(testingDir,"doc1.xml").toURI().toURL();
    URL doc2Url = new File(testingDir,"doc2.xml").toURI().toURL();
    FeatureMap parms = Factory.newFeatureMap();
    parms.put("sourceUrl", doc1Url);
    doc1 = (Document)Factory.createResource("gate.corpora.DocumentImpl",parms);
    doc2 = (Document)Factory.createResource("gate.corpora.DocumentImpl",parms);
    */
  }
  
  @Test
  public void test01() throws ResourceInstantiationException {
    logger.debug("Running test test01");
    
    Document d = Factory.newDocument(new String(new char[10]).replace('\0', ' '));
    
    AnnotationGraph ag = AnnotationGraph.getAnnotationGraph(d, d.getAnnotations("Set1"));    
    AnnotationSet set = d.getAnnotations("Set1");
    
    ag.addEdgeName("m");
    
    assertEquals(1,ag.getEdgeNames().size());
    assertTrue(ag.getEdgeNames().contains("m"));
    
    Annotation l1 = ann(set,0,1,"L1",Utils.featureMap());
    Annotation m1 = ann(set,1,2,"M1",Utils.featureMap());
    Annotation m2 = ann(set,1,2,"M2",Utils.featureMap());
    Annotation m3 = ann(set,1,2,"M3",Utils.featureMap());
    
    ag.addEdge("m", l1, m1);
    ag.addEdge("m", l1, m2);
    ag.addEdge("m", l1, m3);
    
    assertTrue(ag.hasEdge("m", l1, m1));
    assertTrue(ag.hasEdge("m", l1, m2));
    assertTrue(ag.hasEdge("m", l1, m2));
    assertTrue(!ag.hasEdge("m", m1, m2));
    
    Annotation r1 = ann(set,3,5,"R1",Utils.featureMap());
    ag.addEdge("m",r1,l1);
    
    AnnotationSet set_t1 = ag.getTransitiveAnnotationSet("m", r1);
    assertEquals(4,set_t1.size());
    assertTrue(set_t1.contains(m1));
    assertTrue(set_t1.contains(m2));
    assertTrue(set_t1.contains(m3));
    assertTrue(!set_t1.contains(r1));
        
    ag.removeEdge("m", l1, m1);
    assertTrue(!ag.hasEdge("m", l1, m1));
    
    set.remove(m3);
    
    ag.getAnnotationSet("m",r1);
    
    System.err.println("Document is "+d);
  }
  
  private static Annotation ann(AnnotationSet set, int from, int to, String type, FeatureMap fm) {
    return set.get(Utils.addAnn(set,from,to,type,fm));
  }
  
  
}
