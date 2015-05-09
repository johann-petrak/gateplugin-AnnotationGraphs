package gate.plugins.annotationgraphs;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Resource;
import gate.Utils;
import gate.annotation.AnnotationSetImpl;
import gate.event.AnnotationSetEvent;
import gate.event.AnnotationSetListener;
import gate.event.CreoleEvent;
import gate.event.CreoleListener;
import gate.event.DocumentEvent;
import gate.event.DocumentListener;
import gate.util.GateRuntimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class AnnotationGraph 
  implements AnnotationSetListener, DocumentListener, CreoleListener 
{

  // Constants
  protected static final String AG_FNS_PREFIX = "_ag";
  protected static final String AG_DFN_EDGES = AG_FNS_PREFIX + ".edges";
  protected static final String AG_DFN_DEFEDGE = AG_FNS_PREFIX + ".defedge";
  
  /// global members
  protected boolean isActive = false;
  protected Document doc;
  protected String docName;
  protected AnnotationSet set;
  protected String setName;
  
  protected String defaultName = null;
  
  protected Set<String> edgeSet;
  protected HashMap<String,String> toEdgeNames;
  protected HashMap<String,String> fromEdgeNames;

  
  public AnnotationGraph(Document doc, String setName) {
    this.doc = doc;
    this.set = doc.getAnnotations(setName);
    this.setName = setName;
    this.docName = doc.getName();
    // TODO: initialize any known edge names and other info from the document features!
    List<String> edgeList = (List<String>)doc.getFeatures().get(AG_DFN_EDGES);
    edgeSet = new HashSet<String>();
    if(edgeList != null) {
      edgeSet.addAll(edgeList);
    }
    toEdgeNames = new HashMap<String,String>();
    fromEdgeNames = new HashMap<String,String>();
    for(String edgeName : edgeSet) {
      toEdgeNames.put(edgeName,AG_FNS_PREFIX+".to."+edgeName);
      fromEdgeNames.put(edgeName,AG_FNS_PREFIX+".from."+edgeName);
    }
    defaultName = (String)doc.getFeatures().get(AG_DFN_DEFEDGE);
    // Make it easier to check if the default name is set by letting it never be empty, but
    // either null or a valid name.
    if(defaultName != null && defaultName.isEmpty()) {
      defaultName = null;
    }
    set.addAnnotationSetListener(this);
    Factory.addCreoleListener(this);
    isActive = true;
  }
  
  public static AnnotationGraph getAnnotationGraph(Document doc, String setName) {
    return new AnnotationGraph(doc,setName);
  }
  
  //////////////////////////////
  // START OF API METHODS
  ////////////////////////////// 
  
  public void addEdgeName(String name) {
    ensureActive();
    if(name == null || name.isEmpty()) {
      throw new GateRuntimeException("Cannot use null or the empty string as an edge name!");
    }
    if(!edgeSet.contains(name)) {
      edgeSet.add(name);
      toEdgeNames.put(name, AG_FNS_PREFIX+".to."+name);
      fromEdgeNames.put(name, AG_FNS_PREFIX+".from."+name);
      List<String> edgeList = (List<String>)doc.getFeatures().get(AG_DFN_EDGES);
      if(edgeList == null) {
        edgeList = new ArrayList<String>();
      }
      edgeList.add(name);
      doc.getFeatures().put(AG_DFN_EDGES, edgeList);
    }
  }
  
  public void removeEdgeName(String name) {
    ensureActive();
    if(edgeSet.contains(name)) {
      // First remove all these edges from all annotations in the set
      for(Annotation ann : set) {
        removeEdges(name,ann);
      }
      edgeSet.remove(name);
      toEdgeNames.remove(name);
      fromEdgeNames.remove(name);
      List<String> edgeList = (List<String>)doc.getFeatures().get(AG_DFN_EDGES);
      edgeList.remove(name);
    } else {
      throw new GateRuntimeException("Attempt to remove the non-existing edge name "+name);
    }
  }
  
  /**
   * Returns an immutable but changeable collection of known edge names.
   * @return 
   */
  public Collection<String> getEdgeNames() {
    return Collections.unmodifiableCollection(edgeSet);
  }
  
    
  public void close() {
    deActivate();
  }
  

  
  
  public void addEdge(String edgeName, Annotation from, Annotation to) {
    ensureActive();
    List<Integer> ids = getToEdgesList(edgeName, from);
    ids.add(to.getId());
    ids = getFromEdgesList(edgeName,to);
    ids.add(from.getId());
  }
  
  public void addEdge(Annotation from, Annotation to) {
    ensureDefaultEdge();
    addEdge(defaultName, from, to);
  }
  
  
  public void addBothEdges(String edgeName, Annotation ann1, Annotation ann2) {
    addEdge(edgeName, ann1, ann2);
    addEdge(edgeName, ann2, ann1);
  }
  
  public void addBothEdges(Annotation ann1, Annotation ann2) {
    ensureDefaultEdge();
    addBothEdges(defaultName, ann1, ann2);
  }
  
  
  public void removeEdge(String edgeName, Annotation from, Annotation to) {
    ensureActive();
    List<Integer> ids = getToEdgesList(edgeName, from);
    boolean done = ids.remove(to.getId());
    if(!done) {
      throw new GateRuntimeException("Attempt to remove a non-existing to edge between annotations "+from+" and "+to);
    }
    removeEmptyToEdgeList(edgeName, from);
    ids = getFromEdgesList(edgeName,to);
    done = ids.remove(from.getId());    
    if(!done) {
      throw new GateRuntimeException("Attempt to remove a non-existing from edge between annotations "+from+" and "+to);
    }
    removeEmptyFromEdgeList(edgeName, to);
  }
  
  public void removeEdge(Annotation from, Annotation to) {
    ensureDefaultEdge();
    removeEdge(defaultName, from, to);
  }
  
  public boolean hasEdge(String edgeName, Annotation from, Annotation to) {
    List<Integer> ids = getToEdges(edgeName, from);
    if(ids == null) {
      return false;
    }
    if(ids.contains(to.getId())) {
      return true;
    } else {
      return false;
    }
  }
  
  public boolean hasEdge(Annotation from, Annotation to) {
    ensureDefaultEdge();
    return hasEdge(defaultName, from, to);
  }
  
  /**
   * True if there is any outgoing edge.
   * @param edgeName
   * @return 
   */
  public boolean hasEdges(String edgeName, Annotation ann) {
    List<Integer> ids = getToEdges(edgeName,ann);
    if(ids != null && !ids.isEmpty()) {
      return true;
    } else {
      return false;
    }
  }
  
  public boolean hasEdges(Annotation ann) {
    ensureDefaultEdge();
    return hasEdges(defaultName, ann);
  }
  
  public boolean hasReferencingEdges(String edgeName, Annotation ann) {
    List<Integer> ids = getFromEdges(edgeName,ann);
    if(ids != null && !ids.isEmpty()) {
      return true;
    } else {
      return false;
    }
  }
  
  public void removeEdges(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getToEdges(edgeName,ann);
    int thisId = ann.getId();
    if(ids != null) {
      // remove each individual edge
      Iterator<Integer> it = ids.iterator();
      while(it.hasNext()) {
        Integer id = it.next();
        Annotation tmp = set.get(id);
        List<Integer> otherIds = getFromEdges(edgeName,ann);
        boolean done = otherIds.remove((Integer)thisId);
        if(!done) {
          throw new GateRuntimeException("Unexpected inconsistency!");
        }
        removeEmptyFromEdgeList(edgeName, tmp);
      }
      removeEmptyToEdgeList(edgeName, ann);
    }
  }
  
  public void removeEdges(Annotation ann) {
    ensureDefaultEdge();
    removeEdges(defaultName, ann);
  }
  
  public List<Annotation> getAnnotations(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getToEdges(edgeName, ann);
    List<Annotation> ret = new ArrayList<Annotation>();
    if(ids == null) {
      return ret;
    }
    for(Integer id : ids) {
      // TODO: maybe check if the annotation really exists?
      ret.add(set.get(id));
    }
    return ret;
  }

  public List<Annotation> getAnnotations(Annotation ann) {
    ensureDefaultEdge();
    return getAnnotations(defaultName, ann);
  }
  
  
  public List<Annotation> getReferencingAnnotations(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getFromEdges(edgeName, ann);
    List<Annotation> ret = new ArrayList<Annotation>();
    if(ids == null) {
      return ret;
    }
    for(Integer id : ids) {
      // TODO: maybe check if the annotation really exists?
      ret.add(set.get(id));
    }
    return ret;
  }
  
  public List<Annotation> getReferencingAnnotations(Annotation ann) {
    ensureDefaultEdge();
    return getReferencingAnnotations(defaultName,ann);
  }
  
  
  public AnnotationSet getAnnotationSet(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getToEdges(edgeName, ann);
    AnnotationSet ret = new AnnotationSetImpl(doc);
    if(ids == null) {
      return ret;
    }
    for(Integer id : ids) {
      // TODO: maybe check if the annotation really exists?
      ret.add(set.get(id));
    }
    return ret;
  }
  
  public AnnotationSet getAnnotationSet(Annotation ann) {
    ensureDefaultEdge();
    return getAnnotationSet(defaultName, ann);
  }

  public AnnotationSet getReferencingAnnotationSet(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getFromEdges(edgeName, ann);
    AnnotationSet ret = new AnnotationSetImpl(doc);
    if(ids == null) {
      return ret;
    }
    for(Integer id : ids) {
      // TODO: maybe check if the annotation really exists?
      ret.add(set.get(id));
    }
    return ret;
  }
  
  public AnnotationSet getReferencingAnnotationSet(Annotation ann) {
    ensureDefaultEdge();
    return getReferencingAnnotationSet(defaultName,ann);
  }
  
  public List<FeatureMap> getFeatureMaps(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getToEdges(edgeName, ann);
    List<FeatureMap> ret = new ArrayList<FeatureMap>();
    if(ids == null) {
      return ret;
    }
    for(Integer id : ids) {
      // TODO: maybe check if the annotation really exists?
      ret.add(set.get(id).getFeatures());
    }
    return ret;    
  }

  public List<FeatureMap> getFeatureMaps(Annotation ann) {
    ensureDefaultEdge();
    return getFeatureMaps(defaultName,ann);
  }
  
  public List<FeatureMap> getReferencingFeatureMaps(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getToEdges(edgeName, ann);
    List<FeatureMap> ret = new ArrayList<FeatureMap>();
    if(ids == null) {
      return ret;
    }
    for(Integer id : ids) {
      // TODO: maybe check if the annotation really exists?
      ret.add(set.get(id).getFeatures());
    }
    return ret;    
  }
  
  public List<FeatureMap> getReferencingFeatureMaps(Annotation ann) {
    ensureDefaultEdge();
    return getReferencingFeatureMaps(defaultName, ann);
  }
  
  /**
   * Get the ids of those annotations to which this annotation points.
   * The list of ids returned by this method is immutable but changable. In other words,
   * if a client holds on to this list and the edges of the annotation changes, this list
   * also changes!
   * Note that this will cause the creation of an empty id list for the annotation, if
   * the annotation does not have an edges yet!
   * @param edgeName
   * @param ann
   * @return 
   */
  public List<Integer> getIds(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getToEdgesList(edgeName, ann);
    return Collections.unmodifiableList(ids);
  }
  
  public List<Integer> getIds(Annotation ann) {
    ensureDefaultEdge();
    return getIds(defaultName,ann);
  }
  
  public List<Integer> getReferencingIds(String edgeName, Annotation ann) {
    ensureActive();
    List<Integer> ids = getFromEdgesList(edgeName, ann);
    return Collections.unmodifiableList(ids);    
  }
  
  public List<Integer> getReferencingIds(Annotation ann) {
    ensureDefaultEdge();
    return getReferencingIds(defaultName,ann);
  }
  
  
  public AnnotationSet getTransitiveAnnotationSet(String edgeName, Annotation ann) {
    ensureActive();
    AnnotationSet ret = new AnnotationSetImpl(doc);
    List<Integer> ids = getToEdges(edgeName, ann);
    if(ids != null) {
      // to do this, we add the ids to the working queue, then process the queue
      LinkedList<Integer> q = new LinkedList<Integer>();
      q.addAll(ids);
      while(q.peek() != null) {
        Integer id = q.remove();
        Annotation a = set.get(id);
        if(!ret.contains(a)) {
          ret.add(a);
          ids = getToEdges(edgeName, a);
          if(ids != null) {
            q.addAll(ids);
          }
        }
      }
    }
    return ret;
  }
  
  /**
   * Set the default edge name to subsequently use. 
   * If set to null or the empty string, the default name is cleared and the methods which 
   * expect a default name cannot be used any more.
   * @param name 
   */
  public void setDefaultEdgeName(String name) {
    if(name == null || name.isEmpty()) {
      defaultName = null;      
    } else {
      if(edgeSet.contains(name)) {
        defaultName = name;
      } else {
        throw new GateRuntimeException("Cannot set the default edge name to a non-existing name");
      }
    }
    doc.getFeatures().put(AG_DFN_DEFEDGE, name);    
  }
  
  
  /// METHODS TO HANDLE ANNOTATIONS, FEATURE MAPS etc. but not EDGES

  /**
   * Get a copy of the annotation's feature map, with all edge information removed.
   * This is a shallow copy, only the feature map object is copied.
   * @param ann
   * @return 
   */
  public FeatureMap getFeatureMapCopy(Annotation ann) {
    ensureActive();
    FeatureMap ret = Utils.toFeatureMap(ann.getFeatures());
    for(String edge : edgeSet) {
      ret.remove(toEdgeNames.get(edge));
      ret.remove(fromEdgeNames.get(edge));
    }
    return ret;
  }
  
  
  ///// STATIC METHODS THAT HANDLE MORE THAN ONE ANNOTATION GRAPH
  
  /**
   * Copy all annotations included in the which set from the first annotation graph to the second 
   * annotation graph. 
   * This will copy all annotations from the given collection which from ag1 to ag2. All annotations
   * in which must come from the set associated with ag1. This will copy the annotation type, offsets,
   * and will create a shallow copy of the feature map. All annotations pointed to by an annotation
   * are recursively also copied in the same fashion. In other words, for each annotation in the
   * which set, the transitiveAnnotationSet is first created and then copied to the target set. 
   * If the which set contains annotations which are part of the transitive set of another annotation,
   * then the transitive set of those annotations will be copied separately. In order to prevent
   * multiple copies, the which collection should contain exactly the one annotation for each 
   * transitive set that creates the maximal transitive set that one wants to copy. 
   * @param ag1
   * @param ag2
   * @param which 
   */
  public void copyAnnotations(AnnotationGraph ag1, AnnotationGraph ag2, Collection<Annotation> which) {
    // TODO!!
  }
  
  
  
  // TODO: methods to maybe add:
  // getAnnotationsIterator(edgeName,ann)
  // getReferencingAnnotationsIterator(edgeName,ann)
  
  
  //////////////////////////////
  // END OF API METHODS
  //////////////////////////////
  
  /////////////////////////////
  // HELPER METHODS
  /////////////////////////////
  
  protected void ensureEdge(String edgeName) {
    if(!edgeSet.contains(edgeName)) {
      throw new GateRuntimeException("No edge with that name defined for document "+docName+" set "+setName+": "+edgeName);
    }
  }
  
  protected void ensureDefaultEdge() {
    if(defaultName == null) {
      throw new GateRuntimeException("Cannot use a method that requires a default edge because the default edge is not set");
    }
  }
  
  
  /**
   * Get the list of ids of annotations this annotation points to or null.
   * @param edgeName
   * @param ann
   * @return 
   */
  protected List<Integer> getToEdges(String edgeName, Annotation ann) {
    ensureEdge(edgeName);
    return (List<Integer>)ann.getFeatures().get(toEdgeNames.get(edgeName));
  }
  
  /**
   * Return the list of ids of annotation that point to this annotation or null.
   * @param edgeName
   * @param ann
   * @return 
   */
  protected List<Integer> getFromEdges(String edgeName, Annotation ann) {
    ensureEdge(edgeName);
    return (List<Integer>)ann.getFeatures().get(fromEdgeNames.get(edgeName));
  }
  
  /**
   * Get the list of ids of annotations this annotation points to, or create and return an empty list.
   * @param edgeName
   * @param ann
   * @return 
   */
  protected List<Integer> getToEdgesList(String edgeName, Annotation ann) {
    List<Integer> ret = getToEdges(edgeName,ann);
    if(ret==null) {
      ret = new ArrayList<Integer>();
      ann.getFeatures().put(toEdgeNames.get(edgeName), ret);
    }
    return ret;
  }
  
  protected List<Integer> getFromEdgesList(String edgeName, Annotation ann) {
    List<Integer> ret = getFromEdges(edgeName,ann);
    if(ret==null) {
      ret = new ArrayList<Integer>();
      ann.getFeatures().put(fromEdgeNames.get(edgeName), ret);
    }
    return ret;
  }
  
  protected void removeEmptyToEdgeList(String edgeName, Annotation ann) {
    FeatureMap fm = ann.getFeatures();
    List<Integer> l = (List<Integer>)fm.get(toEdgeNames.get(edgeName));
    if(l != null && l.isEmpty()) {
      fm.remove(toEdgeNames.get(edgeName));
    }
  }
  protected void removeEmptyFromEdgeList(String edgeName, Annotation ann) {
    FeatureMap fm = ann.getFeatures();
    List<Integer> l = (List<Integer>)fm.get(fromEdgeNames.get(edgeName));
    if(l != null && l.isEmpty()) {
      fm.remove(fromEdgeNames.get(edgeName));
    }
  }
  
  protected void ensureActive() {
    if(!isActive) {
      throw new GateRuntimeException("Attempt to use AnnotationGraph for document "+docName+
              " set "+setName+" after document or set were removed");
    }
  }
  
  protected void deActivate() {
    isActive = false;
    doc = null;
    set = null;
    edgeSet = null;
    toEdgeNames = null;
    fromEdgeNames = null;
  }

  @Override
  public void annotationAdded(AnnotationSetEvent ase) {
    // do nothinig, we do not care about added annotations
  }

  @Override
  public void annotationRemoved(AnnotationSetEvent ase) {
    // if an annotation gets removed, we check if it has incoming or outgoing 
    // edges for any of the known edges. If yes, the edge information in all the 
    // connected annotations is updated accordingly
    Annotation ann = ase.getAnnotation();
    System.err.println("DEBUG annotationRemoved for "+ann);
    Integer thisId = ann.getId();
    for(String edgeName : edgeSet) {
      // check the outgoing edges: for each outgoign edge, remove 
      // this annotation id from the other annotation's from list
      List<Integer> ids = getToEdges(edgeName, ann);
      if(ids != null) {
        for(Integer otherId : ids) {
          Annotation a = set.get(otherId);
          List<Integer> otherIds = getFromEdges(edgeName,a);
          if(otherIds != null) {
            otherIds.remove(thisId);
            removeEmptyFromEdgeList(edgeName,a);
          }
        }
      }
      // check the incoming edges: for each incoming edge, remove
      // this annotation id from the other annotation's to list
      ids = getFromEdges(edgeName, ann);
      if(ids != null) {
        for(Integer otherId : ids) {
          Annotation a = set.get(otherId);
          List<Integer> otherIds = getToEdges(edgeName,a);
          if(otherIds != null) {
            otherIds.remove(thisId);
            removeEmptyToEdgeList(edgeName,a);
          }
        }
      }
    }
  }

  @Override
  public void annotationSetAdded(DocumentEvent de) {
    // we do not care about this
  }

  @Override
  public void annotationSetRemoved(DocumentEvent de) {
    String name  = de.getAnnotationSetName();
    if((setName == null && name == null) || (setName != null && setName.equals(name))) {
      // it is our set, de-activate this object!
      deActivate();
    }
  }

  @Override
  public void contentEdited(DocumentEvent de) {
    // do not care about this
  }

  @Override
  public void resourceLoaded(CreoleEvent ce) {
    // do not care
  }

  @Override
  public void resourceUnloaded(CreoleEvent ce) {
    // if our document got unloaded, de-activate
    if((ce.getResource() instanceof Document) && (ce.getResource().equals(doc))) {
      deActivate();
    }
  }

  @Override
  public void datastoreOpened(CreoleEvent ce) {
    // do not care
  }

  @Override
  public void datastoreCreated(CreoleEvent ce) {
    // do not care
  }

  @Override
  public void datastoreClosed(CreoleEvent ce) {
    // do not care
  }

  @Override
  public void resourceRenamed(Resource rsrc, String string, String string1) {
    // do not care
  }
  
}
