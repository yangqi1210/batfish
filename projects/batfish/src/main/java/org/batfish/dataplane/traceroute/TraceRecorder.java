package org.batfish.dataplane.traceroute;

import java.util.List;
import org.batfish.datamodel.Flow;
import org.batfish.datamodel.flow.Hop;
import org.batfish.datamodel.flow.TraceAndReverseFlow;

public interface TraceRecorder {
  void recordTrace(TraceAndReverseFlow trace);

  default boolean tryRecordPartialTrace(
      Iterable<Breadcrumb> breadcrumbs, List<Hop> hops, Flow finalHopFlow, Hop finalHop) {
    return false;
  }
}
