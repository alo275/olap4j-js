package com.cgalesanco.olap4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.ws.WebServiceException;

import com.cgalesanco.olap4j.json.QueryCellSet;
import com.sun.jersey.spi.container.servlet.PerSession;
import es.cgalesanco.olap4j.query.Query;
import es.cgalesanco.olap4j.query.QueryAxis;
import es.cgalesanco.olap4j.query.QueryHierarchy;
import es.cgalesanco.olap4j.query.Selection;
import org.olap4j.Axis;
import org.olap4j.OlapException;
import org.olap4j.impl.IdentifierParser;
import org.olap4j.mdx.IdentifierNode;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Hierarchy;
import org.olap4j.metadata.Member;

/**
 * JAX-RS resource providing access to an olap4j query stored in the user's session.
 */
@PerSession
@Path("/query")
public class QueryController
{
  private Query _query;

  /**
   * Creates and initializes a query
   */
  public QueryController() {
    _query = createQuery();
  }

  /**
   * Executes the query.
   *
   * @return the CellSet result.
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public QueryCellSet executeQuery() {
    return doExecuteQuery();
  }

  /**
   * Drills a position.
   *
   * @param axisOrdinal the axis ordinal for the drilled position
   * @param memberIds   the list of members for the drilled position
   * @return the CellSet result
   */
  @POST
  @Path("/drill")
  @Produces(MediaType.APPLICATION_JSON)
  public QueryCellSet drill(
    @FormParam("axis") int axisOrdinal,
    @FormParam("position[]") List<String> memberIds) {
    Member[] members = parsePosition(memberIds);
    Axis queryAxis = Axis.Factory.forOrdinal(axisOrdinal);
    _query.getAxis(queryAxis).drill(members);

    return doExecuteQuery();
  }

  /**
   * Undrills (collapse) a position
   *
   * @param axisOrdinal the axis for the position to collapse
   * @param memberIds   the list of members for the position to collapse
   * @return the CellSet result
   */
  @POST
  @Path("/undrill")
  @Produces(MediaType.APPLICATION_JSON)
  public QueryCellSet undrill(
    @FormParam("axis") int axisOrdinal,
    @FormParam("position[]") List<String> memberIds) {
    Member[] members = parsePosition(memberIds);
    Axis queryAxis = Axis.Factory.forOrdinal(axisOrdinal);
    _query.getAxis(queryAxis).undrill(members);

    return doExecuteQuery();
  }

  /**
   * Adds a new hierarchy to a query axis
   *
   * @param axisOrdinal   the axis where the hierarchy is to be added.
   * @param hierarchyName the unique name of the hierarchy to add.
   * @return the CellSet result
   */
  @POST
  @Path("/hierarchies/add")
  @Produces(MediaType.APPLICATION_JSON)
  public QueryCellSet addHierarchy(@FormParam("axis") int axisOrdinal, @FormParam("hierarchy") String hierarchyName) {
    try {
      QueryAxis axis = _query.getAxis(Axis.Factory.forOrdinal(axisOrdinal));
      final QueryHierarchy queryHierarchy = _query.getHierarchy(hierarchyName);
      for (Member root : queryHierarchy.getHierarchy().getRootMembers()) {
        queryHierarchy.include(Selection.Operator.DESCENDANTS, root);
      }
      axis.addHierarchy(queryHierarchy);

      return doExecuteQuery();
    } catch (OlapException e) {
      throw new WebServiceException(e);
    }
  }

  @POST
  @Path("hierarchies/move")
  @Produces(MediaType.APPLICATION_JSON)
  public QueryCellSet moveHierarchy(@FormParam("hierarchy") String hierarchyName,
                                    @FormParam("axis") int targetAxisOrdinal, @FormParam("position") int targetPos) {
    QueryHierarchy hierarchy = _query.getHierarchy(hierarchyName);
    QueryAxis axis = _query.getAxis(Axis.Factory.forOrdinal(targetAxisOrdinal));
    int hierarchySrcPos = axis.getHierarchies().indexOf(hierarchy);
    if (hierarchySrcPos < 0) {
      axis.addHierarchy(hierarchy);
      hierarchySrcPos = axis.getHierarchies().size() - 1;
    }
    if (targetPos > hierarchySrcPos) {
      for (; targetPos > hierarchySrcPos; ++hierarchySrcPos) {
        axis.pushDown(hierarchySrcPos);
      }
    } else if (targetPos < hierarchySrcPos) {
      for (; targetPos < hierarchySrcPos; --hierarchySrcPos) {
        axis.pullUp(hierarchySrcPos);
      }
    }

    return doExecuteQuery();
  }

  /**
   * Removes a hierarchy form an axis.
   *
   * @param axisOrdinal   The axis where the hierarchy is to be removed.
   * @param hierarchyName The unique name of the hierarchy to be removed.
   * @return the CellSet result
   */
  @POST
  @Path("/hierarchies/remove")
  @Produces(MediaType.APPLICATION_JSON)
  public QueryCellSet removeHierarchy(@FormParam("axis") int axisOrdinal, @FormParam("hierarchy") String hierarchyName) {
    QueryAxis axis = _query.getAxis(Axis.Factory.forOrdinal(axisOrdinal));
    QueryHierarchy hierarchy = _query.getHierarchy(hierarchyName);
    axis.removeHierarchy(hierarchy);

    return doExecuteQuery();
  }

  /**
   * All the hierarchies in the cube used by the current query.
   *
   * @return The list of hierarchies
   */
  @GET
  @Path("/hierarchies")
  @Produces(MediaType.APPLICATION_JSON)
  public List<HierarchyInfo> getHierarchies() {
    final Cube cube = _query.getCube();
    List<HierarchyInfo> result = new ArrayList<HierarchyInfo>();
    for (Hierarchy h : cube.getHierarchies()) {
      result.add(new HierarchyInfo(h));
    }
    return result;
  }

  @GET
  @Path("/hierarchies/{name}/roots")
  @Produces(MediaType.APPLICATION_JSON)
  public List<HierarchyMemberInfo> getHierarchyRoots(@PathParam("name") String name) {
    try {
    QueryHierarchy qh = _query.getHierarchy(name);
    List<Member> roots = qh.getHierarchy().getRootMembers();
    List<HierarchyMemberInfo> result = new ArrayList<HierarchyMemberInfo>(roots.size());
    for(Member r : roots) {
      result.add(new HierarchyMemberInfo(qh,r));
    }
    return result;
    } catch(OlapException ex) {
      throw new RuntimeException(ex);
    }
  }

  @GET
  @Path("/hierarchies/{uniqueName}/children")
  @Produces(MediaType.APPLICATION_JSON)
  public List<HierarchyMemberInfo> getMemberChildren(@PathParam("uniqueName") String memberUnique) {
    try {
      Member m = _query.getCube().lookupMember(IdentifierParser.parseIdentifier(memberUnique));
      QueryHierarchy qh = _query.getHierarchy(m.getHierarchy().getName());
      List<? extends Member> children = m.getChildMembers();
      List<HierarchyMemberInfo> result = new ArrayList<HierarchyMemberInfo>(children.size());
      for(Member child : children) {
        result.add(new HierarchyMemberInfo(qh,child));
      }
      return result;
    } catch(OlapException ex) {
      throw new RuntimeException(ex);
    }
  }

  @GET
  @Path("/hierarchies/{memberUniqueName}/{op}/descendants")
  @Produces(MediaType.APPLICATION_JSON)
  public List<HierarchyMemberUpdate> applyOperation(
    @PathParam("memberUniqueName") String uniqueName, @PathParam("op") String operation) {
    Member m;
    try {
      m = _query.getCube().lookupMember(IdentifierParser.parseIdentifier(uniqueName));
    QueryHierarchy qh = _query.getHierarchy(m.getHierarchy().getName());
    if ( "include".equalsIgnoreCase(operation)) {
      qh.include(Selection.Operator.DESCENDANTS, m);
    } else {
      qh.exclude(Selection.Operator.DESCENDANTS, m);
    }
    List<HierarchyMemberUpdate> updates = new LinkedList<HierarchyMemberUpdate>();
    while( m != null ) {
      updates.add(0, new HierarchyMemberUpdate(qh, m));
      m = m.getParentMember();
    }
    return updates;
    } catch (OlapException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Helper to initialize a query.
   *
   * @return the new query.
   */
  private Query createQuery() {
    try {
      Cube salesCube = CubeProvider.getCube();
      Query q = new Query("q", salesCube);
      final QueryHierarchy storeDim = q.getHierarchy("Store");
      Member root = storeDim.getHierarchy().getRootMembers().get(0);
      storeDim.include(Selection.Operator.DESCENDANTS, root);
      q.getAxis(Axis.ROWS).addHierarchy(storeDim);

      final QueryHierarchy productHie = q.getHierarchy("Product");
      Member productRoot = productHie.getHierarchy().getRootMembers().get(0);
      productHie.include(Selection.Operator.DESCENDANTS, productRoot);
      final QueryAxis axis = q.getAxis(Axis.ROWS);
      axis.addHierarchy(productHie);

      final QueryHierarchy measuresHie = q.getHierarchy("Measures");
      for (Member measure : measuresHie.getHierarchy().getRootMembers()) {
        measuresHie.include(Selection.Operator.MEMBER, measure);
      }
      q.getAxis(Axis.COLUMNS).addHierarchy(measuresHie);
      return q;
    } catch (OlapException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Helper to execute the current query.
   *
   * @return the CellSet result.
   */
  private QueryCellSet doExecuteQuery() {
    try {
      return new QueryCellSet(_query, _query.execute());
    } catch (OlapException e) {
      throw new WebServiceException(e);
    }
  }

  /**
   * Helper to parse a list of member unique names into the corresponding list of members.
   *
   * @param memberIds the list of member unique names.
   * @return The corresponding list of members.
   */
  private Member[] parsePosition(final List<String> memberIds) {
    try {
      Cube cube = _query.getCube();
      Member[] members = new Member[memberIds.size()];
      for (int i = 0; i < members.length; ++i) {
        members[i] = cube.lookupMember(IdentifierNode.parseIdentifier(memberIds.get(i)).getSegmentList());
      }
      return members;
    } catch (OlapException e) {
      throw new WebServiceException(e);
    }
  }

  /**
   * DTO holding the information returned for a Hierarchy.
   */
  private class HierarchyInfo implements Serializable
  {
    private String _uniqueName;
    private String _caption;

    public HierarchyInfo(Hierarchy h) {
      _uniqueName = h.getUniqueName();
      _caption = h.getCaption();
    }

    @SuppressWarnings("unused")
    public String getUniqueName() {
      return _uniqueName;
    }

    @SuppressWarnings("unused")
    public String getCaption() {
      return _caption;
    }
  }

  private class HierarchyMemberUpdate implements Serializable {
    private String _name;
    private boolean _included;
    private boolean _isLeaf;
    private Map<Selection.Operator,Character> _includeOps;
    private Map<Selection.Operator,Character> _excludeOps;

    public HierarchyMemberUpdate(QueryHierarchy qh, Member m) {
      _name = m.getName();
      _included = qh.isIncluded(m);
      _includeOps = new EnumMap<Selection.Operator, Character>(Selection.Operator.class);
      _excludeOps = new EnumMap<Selection.Operator, Character>(Selection.Operator.class);

      try {
        _isLeaf = m.getChildMemberCount() == 0;
      } catch (OlapException e) {
        throw new RuntimeException(e);
      }
      setAllowedOps(qh, Selection.Operator.MEMBER, m, _isLeaf);
      setAllowedOps(qh, Selection.Operator.CHILDREN, m, _isLeaf);
      setAllowedOps(qh, Selection.Operator.DESCENDANTS, m, _isLeaf);
    }

    public boolean isLeaf() {
      return _isLeaf;
    }

    public String getName() {
      return _name;
    }

    public boolean isIncluded() {
      return _included;
    }

    public Map<Selection.Operator, Character> getExcludeOps() {
      return _excludeOps;
    }

    public Map<Selection.Operator, Character> getIncludeOps() {
      return _includeOps;
    }

    private void setAllowedOps(QueryHierarchy qh, Selection.Operator op, Member m, boolean isLeaf) {
      if ( op != Selection.Operator.MEMBER && isLeaf ) {
        _includeOps.put(op, null);
        _excludeOps.put(op, null);
        return;
      }

      Selection.Sign effectiveSign = qh.getEffectiveSignAt(m,op);
      boolean scopeOverride;
      switch(op) {
        case MEMBER:
          scopeOverride = false;
          break;
        case CHILDREN:
          scopeOverride = qh.hasOverridingChildren(m);
          break;
        case DESCENDANTS:
          scopeOverride = qh.hasOverridingDescendants(m);
          break;
        default:
          throw new IllegalArgumentException();
      }

      _includeOps.put(op, effectiveSign != Selection.Sign.INCLUDE || scopeOverride ? 'A' : null);
      _excludeOps.put(op, effectiveSign != Selection.Sign.EXCLUDE || scopeOverride ? 'A' : null);
    }

  }

  private class HierarchyMemberInfo extends HierarchyMemberUpdate {
    private String _caption;
    private String _uniqueName;

    public HierarchyMemberInfo(QueryHierarchy qh, Member m) {
      super(qh,m);
      _caption = m.getCaption();
      _uniqueName = m.getUniqueName();
    }

    public String getCaption() {
      return _caption;
    }

    public String getUniqueName() {
      return _uniqueName;
    }

  }
}
