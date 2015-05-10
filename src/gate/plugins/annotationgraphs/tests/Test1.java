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
import gate.annotation.AnnotationSetImpl;
import gate.creole.ResourceInstantiationException;
import gate.plugins.annotationgraphs.AnnotationGraph;
import gate.util.GateException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.List;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import java.util.function.Predicate;

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
    
    assertEquals(3,ag.getEdgeSize("m",l1));
    
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
    
    
    int s = ag.getEdgeSize("m", l1);
    assertEquals(1,s);
       
    // TODO: GATE the old grandma does not allow us to do that, do'h
    // ag.filterEdges("m", l1, a -> a.getType().equals("M1"));
    
    ag.grepEdges("m",l1,new Predicate<Annotation>(){ 
      @Override
      public boolean test(Annotation ann) { 
        // remove M1, if it exists (but it doesnt!)
        return !ann.getType().equals("M1");
      }
    } );
    assertEquals(1,ag.getEdgeSize("m",l1));
    ag.grepEdges("m",l1,new Predicate<Annotation>(){ 
      @Override
      public boolean test(Annotation ann) { 
        // do not keep M2, if it exists!
        return !ann.getType().equals("M2");
      }
    } );
    // Grandma ...
    // ag.filterEdges("m", l1, a -> a.getType().equals("M2"));
    assertEquals(0,ag.getEdgeSize("m",l1));
    
    Annotation t4 = ann(set,1,2,"T1",Utils.featureMap());
    Annotation t3 = ann(set,2,3,"T2",Utils.featureMap());
    Annotation t2 = ann(set,3,4,"T3",Utils.featureMap());
    Annotation t1 = ann(set,4,5,"T4",Utils.featureMap());
    
    // Check if creating a chain in document order works
    AnnotationSetImpl tmpSet = new AnnotationSetImpl(set.getDocument());
    tmpSet.add(t1);
    tmpSet.add(t3);
    tmpSet.add(t4);
    tmpSet.add(t2);
    ag.addEdgeName("next");
    ag.addEdgeName("previous");
    ag.makeSequence("previous", "next", tmpSet);
    
    assertEquals(1,ag.getAnnotations("next",t4).size());
    assertEquals("T2",ag.getAnnotations("next",t4).get(0).getType());
    assertEquals(0,ag.getAnnotations("previous",t4).size());
    
    assertEquals(0,ag.getAnnotations("next",t1).size());
    assertEquals(1,ag.getAnnotations("previous",t1).size());
    assertEquals("T3",ag.getAnnotations("previous",t1).get(0).getType());
    
    ag.addSequenceEdges("m", l1, tmpSet);
    
    assertEquals("T1",ag.getAnnotations("m",l1).get(0).getType());
    
    // sort the sequence edges by descending offset
    ag.sortEdges("m", l1, new Comparator<Annotation>() { 
      @Override
      public int compare(Annotation o1, Annotation o2) {
        return o2.getStartNode().getOffset().compareTo(o1.getStartNode().getOffset());
      }
    });
    assertEquals("T4",ag.getAnnotations("m",l1).get(0).getType());

    
    
    // create 10 Cn annotation, of which 5 (2 and 3) are coextensive with at least one other
    ann(set,0,1,"CX",Utils.featureMap("id","C1"));
    ann(set,1,2,"CX",Utils.featureMap("id","C2"));
    ann(set,3,4,"CX",Utils.featureMap("id","C3"));
    ann(set,3,4,"CX",Utils.featureMap("id","C4"));
    ann(set,5,6,"CX",Utils.featureMap("id","C5"));
    ann(set,6,7,"CX",Utils.featureMap("id","C6"));
    ann(set,7,8,"CX",Utils.featureMap("id","C7"));
    ann(set,7,8,"CX",Utils.featureMap("id","C8"));
    ann(set,7,8,"CX",Utils.featureMap("id","C9"));
    ann(set,9,10,"CX",Utils.featureMap("id","C10"));
    // So we should get C3+C4 and C7+C8+C9 (in document order)
    AnnotationSet forCoexts = set.get("CX");
    ag.addEdgeName("coext");
    List<Annotation> coexts = ag.getCoextensiveRangeAnnotations("coext", forCoexts, "COEXT", 2);
    assertEquals(2,coexts.size());
    ag.setDefaultEdgeName("coext");
    assertEquals(2,ag.getAnnotations(coexts.get(0)).size());
    assertEquals(3,ag.getAnnotations(coexts.get(1)).size());
    
    //System.err.println("Set is "+set);

    ag.addEdgeName("n");
    ag.setDefaultEdgeName("n");
    
  }
  
  private static Annotation ann(AnnotationSet set, int from, int to, String type, FeatureMap fm) {
    return set.get(Utils.addAnn(set,from,to,type,fm));
  }
  
  
}
