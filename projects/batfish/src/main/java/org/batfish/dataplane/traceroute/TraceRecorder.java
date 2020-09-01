package org.batfish.dataplane.traceroute;

import java.util.List;
import org.batfish.datamodel.flow.Hop;
import org.batfish.datamodel.flow.TraceAndReverseFlow;

/** Used by {@link FlowTracer} to record complete and partial traces. */
public interface TraceRecorder {
  void recordTrace(TraceAndReverseFlow trace);

  default boolean tryRecordPartialTrace(List<Hop> hops) {
    return false;
  }
}
