/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.translation.flink.source;

import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.core.starter.flowcontrol.FlowControlGate;
import org.apache.seatunnel.core.starter.flowcontrol.FlowControlStrategy;
import org.apache.seatunnel.translation.flink.serialization.FlinkRowConverter;

import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.flink.types.Row;

import java.io.IOException;

public class RowCollector implements Collector<SeaTunnelRow> {

    protected final SourceFunction.SourceContext<Row> internalCollector;
    protected final FlinkRowConverter rowSerialization;
    protected final Object checkpointLock;

    private FlowControlGate flowControlGate;
    private volatile boolean emptyThisPollNext;

    public RowCollector(
            SourceFunction.SourceContext<Row> internalCollector,
            Object checkpointLock,
            SeaTunnelDataType<?> dataType,
            FlowControlStrategy flowControlStrategy) {
        this.internalCollector = internalCollector;
        this.checkpointLock = checkpointLock;
        this.rowSerialization = new FlinkRowConverter(dataType);
        if (flowControlStrategy != null) {
            this.flowControlGate = FlowControlGate.create(flowControlStrategy);
        }
    }

    @Override
    public void collect(SeaTunnelRow sourceRecord) {
        try {
            if (sourceRecord != null) {
                if (flowControlGate != null) {
                    // Data source Speed limit
                    flowControlGate.audit(sourceRecord);
                }
            }
            internalCollector.collect(rowSerialization.convert(sourceRecord));
            emptyThisPollNext = false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object getCheckpointLock() {
        return this.checkpointLock;
    }

    @Override
    public boolean isEmptyThisPollNext() {
        return emptyThisPollNext;
    }

    @Override
    public void resetEmptyThisPollNext() {
        this.emptyThisPollNext = true;
    }
}
