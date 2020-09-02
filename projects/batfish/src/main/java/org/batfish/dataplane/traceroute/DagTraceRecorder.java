package org.batfish.dataplane.traceroute;

import static com.google.common.base.Preconditions.checkState;
import static org.batfish.dataplane.traceroute.HopInfoUtils.computeHopInfo;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.batfish.common.util.BatfishObjectMapper;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.flow.Hop;
import org.batfish.datamodel.flow.TraceAndReverseFlow;

/**
 * {@link TraceRecorder} that compresses traces into a {@link TraceDag} and allows partial traces to
 * be recorded by reusing already-computed subgraphs.
 */
public class DagTraceRecorder implements TraceRecorder {
  private final @Nonnull Flow _flow;

  public DagTraceRecorder(@Nonnull Flow flow) {
    _flow = flow;
  }

  /** The key used to lookup Nodes in a TraceDag. */
  private static final class NodeKey {
    private final @Nonnull Flow _initialFlow;
    private final @Nonnull String _hopJson;

    private NodeKey(Flow initialFlow, Hop hop) {
      _initialFlow = initialFlow;
      _hopJson = BatfishObjectMapper.writeStringRuntimeError(hop);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof NodeKey)) {
        return false;
      }
      NodeKey nodeKey = (NodeKey) o;
      return _initialFlow.equals(nodeKey._initialFlow) && _hopJson.equals(nodeKey._hopJson);
    }

    @Override
    public int hashCode() {
      return Objects.hash(_initialFlow, _hopJson);
    }
  }

  /** Traces are generated in DFS order, i.e. grouped by common prefix. */
  @VisibleForTesting
  final class SubDagBuilder {
    private final @Nonnull NodeKey _key;
    final List<Breadcrumb> _breadcrumbs;
    final HopInfo _hopInfo;
    final boolean _isFinalHop;
    @Nullable SubDagBuilder _currentNextHopBuilder;
    final @Nullable List<Integer> _nextHops;

    private SubDagBuilder(NodeKey key, List<Breadcrumb> breadcrumbs, Hop hop, Flow initialFlow) {
      this(breadcrumbs, computeHopInfo(initialFlow, hop), key);
    }

    @VisibleForTesting
    SubDagBuilder(List<Breadcrumb> breadcrumbs, HopInfo hopInfo) {
      this(breadcrumbs, hopInfo, new NodeKey(hopInfo.getInitialFlow(), hopInfo.getHop()));
    }

    SubDagBuilder(List<Breadcrumb> breadcrumbs, HopInfo hopInfo, NodeKey key) {
      _hopInfo = hopInfo;
      _key = key;

      @Nullable Breadcrumb visitedBreadcrumb = _hopInfo.getVisitedBreadcrumb();
      _breadcrumbs =
          visitedBreadcrumb == null
              ? ImmutableList.copyOf(breadcrumbs)
              : ImmutableList.<Breadcrumb>builder()
                  .addAll(breadcrumbs)
                  .add(_hopInfo.getVisitedBreadcrumb())
                  .build();
      _isFinalHop = _hopInfo.getDisposition() != null;
      _nextHops = _isFinalHop ? null : new ArrayList<>();
    }

    boolean tryRecordPartialTrace(List<Hop> hops) {
      assert !_isFinalHop || hops.isEmpty();
      assert _isFinalHop == (_nextHops == null);
      if (hops.isEmpty()) {
        return _isFinalHop;
      }
      Hop nextHop = hops.get(0);
      if (_currentNextHopBuilder != null && _currentNextHopBuilder._hopInfo.getHop() != nextHop) {
        _nextHops.add(_currentNextHopBuilder.build());
        _currentNextHopBuilder = null;
      }
      if (_currentNextHopBuilder == null) {
        NodeKey key = new NodeKey(_hopInfo.getFinalFlow(), nextHop);
        Integer nodeId = findMatchingNode(key, _breadcrumbs);
        if (nodeId != null) {
          if (!_nextHops.contains(nodeId)) {
            _nextHops.add(nodeId);
          }
          return true;
        }
        _currentNextHopBuilder =
            new SubDagBuilder(key, _breadcrumbs, nextHop, _hopInfo.getFinalFlow());
      }

      assert _currentNextHopBuilder._hopInfo.getHop() == nextHop;
      return _currentNextHopBuilder.tryRecordPartialTrace(hops.subList(1, hops.size()));
    }

    int build() {
      checkState(_hopInfos.size() == _constraints.size());
      Breadcrumb visitedBreadcrumb = _hopInfo.getVisitedBreadcrumb();
      Breadcrumb loopDetectedBreadcrumb = _hopInfo.getLoopDetectedBreadcrumb();

      if (_isFinalHop) {
        int nodeId = _hopInfos.size();
        _hopInfos.add(_hopInfo);
        _nodeMap.put(_key, nodeId);
        _successors.add(ImmutableList.of());
        Set<Breadcrumb> requiredBreadcrumbs =
            loopDetectedBreadcrumb == null
                ? ImmutableSet.of()
                : ImmutableSet.of(loopDetectedBreadcrumb);
        Set<Breadcrumb> forbiddenBreadcrumbs =
            visitedBreadcrumb == null
                ? ImmutableSet.of()
                : ImmutableSet.of(_hopInfo.getVisitedBreadcrumb());
        _constraints.add(new NodeConstraints(requiredBreadcrumbs, forbiddenBreadcrumbs));
        return nodeId;
      } else {
        assert _nextHops != null : "_nextHops cannot be null if not the final hop";
        assert visitedBreadcrumb != null : "visitedBreadcrumb cannot be null if no the final hop";

        if (_currentNextHopBuilder != null) {
          _nextHops.add(_currentNextHopBuilder.build());
        }

        int nodeId = _hopInfos.size();
        _hopInfos.add(_hopInfo);
        _nodeMap.put(_key, nodeId);

        _successors.add(ImmutableList.copyOf(_nextHops));
        ImmutableSet.Builder<Breadcrumb> requiredBreadcrumbsBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<Breadcrumb> forbiddenBreadcrumbsBuilder = ImmutableSet.builder();
        forbiddenBreadcrumbsBuilder.add(visitedBreadcrumb);
        for (Integer nextHopId : _nextHops) {
          NodeConstraints constraints = _constraints.get(nextHopId);
          assert !constraints._forbiddenBreadcrumbs.contains(visitedBreadcrumb)
              : "Node's breadcrumb is forbidden by a successor";
          forbiddenBreadcrumbsBuilder.addAll(constraints._forbiddenBreadcrumbs);
          requiredBreadcrumbsBuilder.addAll(
              constraints._requiredBreadcrumbs.contains(visitedBreadcrumb)
                  ? Sets.difference(
                      constraints._requiredBreadcrumbs, ImmutableSet.of(visitedBreadcrumb))
                  : constraints._requiredBreadcrumbs);
        }
        ImmutableSet<Breadcrumb> requiredBreadcrumbs = requiredBreadcrumbsBuilder.build();
        ImmutableSet<Breadcrumb> forbiddenBreadcrumbs = forbiddenBreadcrumbsBuilder.build();
        assert !requiredBreadcrumbs.contains(visitedBreadcrumb) : "A node cannot require itself";
        _constraints.add(new NodeConstraints(requiredBreadcrumbs, forbiddenBreadcrumbs));
        checkState(_hopInfos.size() == _constraints.size());
        return nodeId;
      }
    }
  }

  private static final class NodeConstraints {
    private Set<Breadcrumb> _requiredBreadcrumbs;
    private Set<Breadcrumb> _forbiddenBreadcrumbs;

    private NodeConstraints(
        Set<Breadcrumb> requiredBreadcrumbs, Set<Breadcrumb> forbiddenBreadcrumbs) {
      _requiredBreadcrumbs = ImmutableSet.copyOf(requiredBreadcrumbs);
      _forbiddenBreadcrumbs = ImmutableSet.copyOf(forbiddenBreadcrumbs);
    }

    /**
     * sessionAction will be non-null for checking if we can reuse traces in traceroute. it can be
     * null when de-duping a node after creation.
     */
    boolean matches(List<Breadcrumb> breadcrumbs) {
      return breadcrumbs.containsAll(_requiredBreadcrumbs)
          && _forbiddenBreadcrumbs.stream().noneMatch(breadcrumbs::contains);
    }
  }

  // indices are aligned
  private final List<HopInfo> _hopInfos = new ArrayList<>();
  private final List<List<Integer>> _successors = new ArrayList<>();
  private final List<NodeConstraints> _constraints = new ArrayList<>();
  private final List<Integer> _rootIds = new ArrayList<>();
  private final Multimap<NodeKey, Integer> _nodeMap = HashMultimap.create();
  private SubDagBuilder _rootBuilder = null;
  private boolean _built = false;

  private @Nullable Integer findMatchingNode(NodeKey key, List<Breadcrumb> breadcrumbs) {
    Collection<Integer> ids = _nodeMap.get(key);
    if (ids.isEmpty()) {
      return null;
    }
    List<Integer> matches =
        ids.stream()
            .filter(i -> _constraints.get(i).matches(breadcrumbs))
            .collect(Collectors.toList());
    checkState(matches.size() < 2, "Found 2 matching Trace nodes");
    return matches.isEmpty() ? null : matches.get(0);
  }

  @Override
  public boolean tryRecordPartialTrace(List<Hop> hops) {
    checkState(!_built, "Cannot add traces after the Dag has been built");
    Hop rootHop = hops.get(0);
    if (_rootBuilder != null && _rootBuilder._hopInfo.getHop() != rootHop) {
      buildRoot();
    }
    if (_rootBuilder == null) {
      _rootBuilder =
          new SubDagBuilder(
              ImmutableList.of(), computeHopInfo(_flow, rootHop), new NodeKey(_flow, rootHop));
    }
    return _rootBuilder.tryRecordPartialTrace(hops.subList(1, hops.size()));
  }

  @Override
  public void recordTrace(TraceAndReverseFlow trace) {
    checkState(
        tryRecordPartialTrace(trace.getTrace().getHops()), "Failed to record a complete trace.");
  }

  private void buildRoot() {
    _rootIds.add(_rootBuilder.build());
    _rootBuilder = null;
  }

  public TraceDag build() {
    if (_rootBuilder != null) {
      buildRoot();
    }
    _built = true;
    List<TraceDag.Node> dagNodes = new ArrayList<>(_hopInfos.size());
    for (int i = 0; i < _hopInfos.size(); i++) {
      dagNodes.add(
          new TraceDag.Node(
              _hopInfos.get(i).getHop(),
              _hopInfos.get(i).getFirewallSessionTraceInfo(),
              _hopInfos.get(i).getDisposition(),
              _hopInfos.get(i).getReturnFlow(),
              _successors.get(i)));
    }
    return new TraceDag(dagNodes, _rootIds);
  }
}
