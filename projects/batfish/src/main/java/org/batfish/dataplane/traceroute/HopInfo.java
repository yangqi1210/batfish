package org.batfish.dataplane.traceroute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import javax.annotation.Nullable;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.FlowDisposition;
import org.batfish.datamodel.flow.FirewallSessionTraceInfo;
import org.batfish.datamodel.flow.Hop;

/** Records information about a {@link Hop trace hop} needed to build a {@link TraceDag}. */
public final class HopInfo {
  private final Hop _hop;
  // Flow as it entered the hop
  private final Flow _initialFlow;
  // Flow when exited (or stopped within) the hop
  private final Flow _finalFlow;
  private final @Nullable FlowDisposition _disposition;
  private final @Nullable Flow _returnFlow;
  private final @Nullable FirewallSessionTraceInfo _firewallSessionTraceInfo;
  private final @Nullable Breadcrumb _loopDetectedBreadcrumb;
  private final @Nullable Breadcrumb _visitedBreadcrumb;

  HopInfo(
      Hop hop,
      Flow initialFlow,
      Flow finalFlow,
      @Nullable FlowDisposition disposition,
      @Nullable Flow returnFlow,
      @Nullable FirewallSessionTraceInfo firewallSessionTraceInfo,
      @Nullable Breadcrumb loopDetectedBreadcrumb,
      @Nullable Breadcrumb visitedBreadcrumb) {
    checkArgument(
        loopDetectedBreadcrumb == null || visitedBreadcrumb == null,
        "Cannot have loopDetectBreadcrumb and visitedBreadcrumbs");
    checkArgument(
        (disposition != null && disposition.isSuccessful()) == (returnFlow != null),
        "return flow should be present if and only if the hop has a successful disposition");
    _disposition = disposition;
    _firewallSessionTraceInfo = firewallSessionTraceInfo;
    _hop = hop;
    _initialFlow = initialFlow;
    _finalFlow = finalFlow;
    _returnFlow = returnFlow;
    _loopDetectedBreadcrumb = loopDetectedBreadcrumb;
    _visitedBreadcrumb = visitedBreadcrumb;
  }

  public static Builder builder(Hop hop, Flow initialFlow) {
    return new Builder(hop, initialFlow);
  }

  public Hop getHop() {
    return _hop;
  }

  Flow getInitialFlow() {
    return _initialFlow;
  }

  @Nullable
  Breadcrumb getLoopDetectedBreadcrumb() {
    return _loopDetectedBreadcrumb;
  }

  Breadcrumb getVisitedBreadcrumb() {
    return _visitedBreadcrumb;
  }

  /** Returns the return flow of this hop, if the trace ends here. */
  @Nullable
  Flow getReturnFlow() {
    return _returnFlow;
  }

  @Nullable
  FirewallSessionTraceInfo getFirewallSessionTraceInfo() {
    return _firewallSessionTraceInfo;
  }

  @Nullable
  public FlowDisposition getDisposition() {
    return _disposition;
  }

  Flow getFinalFlow() {
    return _finalFlow;
  }

  static final class Builder {
    private final Hop _hop;
    private final Flow _initialFlow;
    private @Nullable Flow _returnFlow;
    private @Nullable Breadcrumb _loopDetectedBreadcrumb;
    private @Nullable Breadcrumb _visitedBreadcrumb;
    private @Nullable FlowDisposition _flowDisposition;
    private @Nullable FirewallSessionTraceInfo _firewallSessionTraceInfo;

    Builder(Hop hop, Flow initialFlow) {
      _hop = hop;
      _initialFlow = initialFlow;
    }

    Builder setLoopDetectedBreadcrumb(Breadcrumb breadcrumb) {
      checkState(_loopDetectedBreadcrumb == null, "loopDetectedBreadcrumb must be null");
      checkState(_visitedBreadcrumb == null, "visitedBreadcrumbs must be null");
      _loopDetectedBreadcrumb = breadcrumb;
      return this;
    }

    Builder setVisitedBreadcrumb(Breadcrumb breadcrumb) {
      checkState(_loopDetectedBreadcrumb == null, "loopDetectedBreadcrumb must be null");
      checkState(_visitedBreadcrumb == null, "visitedBreadcrumbs must be null");
      _visitedBreadcrumb = breadcrumb;
      return this;
    }

    Builder setFlowDisposition(FlowDisposition flowDisposition) {
      checkState(_flowDisposition == null, "flow disposition already set");
      checkArgument(!flowDisposition.isSuccessful(), "must include a returnFlow if flow disposition is successful.");
      _flowDisposition = flowDisposition;
      return this;
    }

    Builder setFlowDisposition(FlowDisposition flowDisposition, Flow returnFlow) {
      checkState(_flowDisposition == null, "flow disposition already set");
      checkArgument(flowDisposition.isSuccessful());
      _flowDisposition = flowDisposition;
      _returnFlow = returnFlow;
      return this;
    }

    Builder setFirewallSessionTraceInfo(FirewallSessionTraceInfo firewallSessionTraceInfo) {
      checkState(_firewallSessionTraceInfo == null, "session info already set");
      _firewallSessionTraceInfo = firewallSessionTraceInfo;
      return this;
    }

    public HopInfo build(Flow finalFlow) {
      return new HopInfo(
          _hop,
          _initialFlow,
          finalFlow,
          _flowDisposition,
          _returnFlow,
          _firewallSessionTraceInfo,
          _loopDetectedBreadcrumb,
          _visitedBreadcrumb);
    }
  }
}
